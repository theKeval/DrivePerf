package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.ActivitySource
import com.driveperf.domain.capture.CaptureBatch
import com.driveperf.domain.capture.CaptureHeader
import com.driveperf.domain.capture.CaptureLog
import com.driveperf.domain.capture.CaptureMark
import com.driveperf.domain.capture.CaptureMarkType
import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.LocationSource
import com.driveperf.domain.capture.MotionSource
import com.driveperf.domain.capture.ParkedRegionMonitor
import com.driveperf.domain.capture.PowerState
import com.driveperf.domain.capture.PowerStatus
import com.driveperf.domain.capture.RecordedAccelSample
import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.RecorderStatus
import com.driveperf.domain.capture.TimeZoneProvider
import com.driveperf.domain.capture.TripRecorder
import com.driveperf.domain.capture.TripStore
import com.driveperf.domain.capture.WakeEvent
import com.driveperf.domain.capture.WakeKind
import com.driveperf.domain.trip.CaptureMeta
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.tripfile.TripFileKind
import com.driveperf.domain.tripfile.TripFileStore
import com.driveperf.foundation.Clock
import com.driveperf.foundation.Units
import com.driveperf.foundation.newId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The device ports the recorder drives. */
public data class CapturePorts(
    val location: LocationSource,
    val motion: MotionSource,
    val activity: ActivitySource,
    val power: PowerState,
    val parkedRegion: ParkedRegionMonitor,
    val files: TripFileStore,
)

/** Where the recorder persists. */
public data class CapturePersistence(val store: TripStore, val log: CaptureLog, val finaliser: TripFinaliser)

/**
 * The [TripRecorder]: runs [TripMachine] and executes its effects against the ports. Every input goes through one
 * channel and is handled in order by a single coroutine in [scope], so callbacks may arrive on any thread. Pass a
 * scope on a single-threaded or `limitedParallelism(1)` dispatcher off the main thread.
 *
 * The engine itself never decides anything: guards and timings live in the reducer.
 */
@Suppress("TooManyFunctions") // one executor per effect type
public class TripRecorderEngine(
    private val ports: CapturePorts,
    private val persistence: CapturePersistence,
    private val clock: Clock,
    private val zones: TimeZoneProvider,
    private val scope: CoroutineScope,
    private val config: CaptureConfig = CaptureConfig(),
) : TripRecorder {
    private val machine = TripMachine(config)
    private val inputs = Channel<Input>(Channel.UNLIMITED)
    private val mutableStatus = MutableStateFlow(RecorderStatus.IDLE)
    private val timers = mutableMapOf<TimerId, Job>()
    private var state = MachineState()
    private var gnssOn = false
    private var lastSyncNs = 0L

    override val status: StateFlow<RecorderStatus> = mutableStatus.asStateFlow()

    init {
        scope.launch { for (input in inputs) handle(input) }
    }

    override fun onLaunch() {
        inputs.trySend(Input.Launch)
    }

    override fun onWake(event: WakeEvent) {
        inputs.trySend(Input.Wake(event))
    }

    override fun stop() {
        inputs.trySend(Input.Simple { MachineEvent.UserStop(it) })
    }

    override fun markPassenger(keep: Boolean) {
        inputs.trySend(Input.Simple { MachineEvent.Passenger(it, keep) })
    }

    override fun setAutoDetect(enabled: Boolean) {
        inputs.trySend(Input.Simple { MachineEvent.AutoDetect(it, enabled) })
    }

    private sealed interface Input {
        data object Launch : Input

        data class Wake(val event: WakeEvent) : Input

        data class Simple(val make: (Long) -> MachineEvent) : Input

        data class Backfill(val tripId: String, val samples: List<RecordedAccelSample>) : Input
    }

    private suspend fun handle(input: Input) {
        val now = clock.monotonicNs()
        val event = when (input) {
            Input.Launch -> launchEvent(now)
            is Input.Wake -> MachineEvent.Wake(now, clock.wallMs(), input.event, newHeader(input.event, now))
            is Input.Simple -> input.make(now)
            is Input.Backfill -> {
                persistence.log.append(input.tripId, CaptureBatch(recordedAccel = input.samples))
                return
            }
        }
        val transition = machine.reduce(state, event)
        state = transition.state
        transition.effects.forEach { execute(it) }
        publishStatus(now)
    }

    private suspend fun launchEvent(now: Long): MachineEvent.Launch {
        val store = persistence.store
        val recording = store.recordingTrip()
        val recovery = recording?.let { recoveryFor(it) }
        if (recording != null && recovery == null) store.deleteNow(recording.id)
        return MachineEvent.Launch(
            now, clock.wallMs(), dbAvailable = true, parking = store.lastParking(), recovering = recovery,
        )
    }

    private suspend fun recoveryFor(trip: Trip): Recovery? {
        val capture = persistence.log.read(trip.id) ?: return null
        val h = capture.header
        val lastNs = listOfNotNull(
            capture.fixes.lastOrNull()?.tMonoNs,
            capture.motion.lastOrNull()?.tMonoNs,
            capture.marks.lastOrNull()?.tMonoNs,
        ).maxOrNull() ?: h.startMonoNs
        val confirmed = trip.source == TripSource.MANUAL || capture.marks.any { it.type == CaptureMarkType.CONFIRMED }
        val moving = (capture.fixes.lastOrNull()?.speedMps ?: 0.0) >= config.stationarySpeedMps
        return Recovery(
            header = h,
            manual = trip.source == TripSource.MANUAL,
            confirmed = confirmed,
            lastSampleWallMs = h.startWallMs + (lastNs - h.startMonoNs) / Units.NANOS_PER_MILLI,
            wasMoving = moving,
        )
    }

    private fun newHeader(event: WakeEvent, now: Long): CaptureHeader {
        val wall = clock.wallMs()
        return CaptureHeader(
            tripId = newId(),
            startMonoNs = now,
            startWallMs = wall,
            tzOffsetMin = zones.offsetMinAt(wall),
            tzId = zones.zoneId(),
            wakeKind = event.kind,
            sourceDetail = event.sourceDetail,
            nominalImuHz = config.imuNominalHz,
            parking = state.parking,
        )
    }

    private suspend fun execute(effect: MachineEffect) {
        if (!executeSensor(effect)) executeOther(effect)
    }

    /** Sensor effects; false when [effect] is not one. */
    private fun executeSensor(effect: MachineEffect): Boolean {
        when (effect) {
            MachineEffect.StartGnss -> startGnss()
            MachineEffect.StopGnss -> stopGnss()
            is MachineEffect.StartImu -> ports.motion.start(effect.hz) { send { now -> MachineEvent.Motion(now, it) } }
            is MachineEffect.SetImuRate -> ports.motion.setRate(effect.hz)
            MachineEffect.StopImu -> ports.motion.stop()
            MachineEffect.StartActivity -> ports.activity.start { send { now -> MachineEvent.Activity(now, it) } }
            MachineEffect.StopActivity -> ports.activity.stop()
            MachineEffect.StartPower -> startPower()
            MachineEffect.StopPower -> ports.power.stop()
            else -> return false
        }
        return true
    }

    private suspend fun executeOther(effect: MachineEffect) {
        when (effect) {
            is MachineEffect.ArmParkedRegion -> ports.parkedRegion.arm(effect.center, effect.radiusM)
            is MachineEffect.QueryActivityHistory -> queryHistory(effect)
            is MachineEffect.Schedule -> schedule(effect)
            is MachineEffect.Cancel -> timers.remove(effect.id)?.cancel()
            is MachineEffect.CreateTrip -> createTrip(effect)
            is MachineEffect.ConfirmTrip -> confirmTrip(effect)
            is MachineEffect.RunBackfill -> ports.motion.queryRecordedAccelerometer(effect.fromWallMs, effect.toWallMs) {
                if (it.isNotEmpty()) inputs.trySend(Input.Backfill(effect.tripId, it))
            }
            is MachineEffect.Append -> append(effect)
            is MachineEffect.Mark -> {
                val mark = CaptureMark(effect.tNs, effect.type, effect.detail)
                persistence.log.append(effect.tripId, CaptureBatch(marks = listOf(mark)))
            }
            is MachineEffect.Finalise -> finalise(effect)
            is MachineEffect.Discard -> discard(effect.tripId)
            else -> Unit
        }
    }

    private fun send(make: (Long) -> MachineEvent) {
        inputs.trySend(Input.Simple(make))
    }

    private fun startGnss() {
        if (gnssOn) return
        gnssOn = true
        ports.location.start { fixes: List<LocationSample> -> send { now -> MachineEvent.Fixes(now, fixes) } }
    }

    private fun stopGnss() {
        gnssOn = false
        ports.location.stop()
    }

    private fun startPower() {
        val current: PowerStatus = ports.power.current()
        send { now -> MachineEvent.Power(now, current) }
        ports.power.start { status -> send { now -> MachineEvent.Power(now, status) } }
    }

    private fun queryHistory(effect: MachineEffect.QueryActivityHistory) {
        ports.activity.queryHistory(effect.fromWallMs, effect.toWallMs) { samples: List<ActivitySample> ->
            send { now -> MachineEvent.ActivityHistory(now, samples) }
        }
    }

    private suspend fun createTrip(effect: MachineEffect.CreateTrip) {
        val h = effect.header
        val wall = clock.wallMs()
        persistence.log.open(h)
        persistence.store.insertRecording(
            Trip(
                id = h.tripId,
                status = TripStatus.RECORDING,
                kind = TripKind.DRIVER,
                source = if (effect.manual) TripSource.MANUAL else TripSource.AUTO,
                sourceDetail = h.sourceDetail,
                startedAt = h.startWallMs,
                startTzOffsetMin = h.tzOffsetMin,
                tzId = h.tzId,
                captureMeta = CaptureMeta(h.wakeKind, null, null, null, null),
                createdAt = wall,
                updatedAt = wall,
            ),
        )
        lastSyncNs = h.startMonoNs
        val free = ports.files.freeSpaceBytes()
        send { now -> MachineEvent.FreeSpace(now, free) }
    }

    private suspend fun confirmTrip(effect: MachineEffect.ConfirmTrip) {
        val trip = persistence.store.recordingTrip() ?: return
        if (trip.id != effect.tripId) return
        val source = if (effect.manual) TripSource.MANUAL else trip.source
        val detail = if (effect.manual) trip.sourceDetail ?: WakeKind.MANUAL.name.lowercase() else trip.sourceDetail
        persistence.store.updateRecording(trip.copy(source = source, sourceDetail = detail, updatedAt = clock.wallMs()))
    }

    private suspend fun append(effect: MachineEffect.Append) {
        val ok = persistence.log.append(effect.tripId, effect.batch)
        val now = clock.monotonicNs()
        if (now - lastSyncNs >= secondsNs(config.r0SyncS)) {
            persistence.log.sync(effect.tripId)
            lastSyncNs = now
            val free = ports.files.freeSpaceBytes()
            if (free < config.minFreeBytes) send { t -> MachineEvent.FreeSpace(t, free) }
        }
        if (!ok) send { t -> MachineEvent.FreeSpace(t, 0L) }
    }

    private fun schedule(effect: MachineEffect.Schedule) {
        timers.remove(effect.id)?.cancel()
        timers[effect.id] = scope.launch {
            delay(effect.delayNs / Units.NANOS_PER_MILLI)
            send { now -> MachineEvent.Timer(now, effect.id) }
        }
    }

    private suspend fun finalise(effect: MachineEffect.Finalise) {
        val log = persistence.log
        log.sync(effect.tripId)
        val capture = log.read(effect.tripId)
        val trip = state.trip
        val result = capture?.let {
            persistence.finaliser.finalise(
                FinaliseRequest(it, effect.endNs, effect.kind, trip?.manual ?: false, effect.interruption, clock.wallMs()),
            )
        }
        val finished = result?.trip
        if (finished == null) {
            discard(effect.tripId)
        } else {
            persistence.store.saveFinalised(finished)
            ports.files.compress(effect.tripId, TripFileKind.R0)
        }
        val parking = result?.parking
        if (parking != null) persistence.store.setLastParking(parking)
        send { now -> MachineEvent.Finalised(now, parking) }
    }

    private suspend fun discard(tripId: String) {
        persistence.store.deleteNow(tripId)
        persistence.log.delete(tripId)
    }

    private fun publishStatus(now: Long) {
        val trip = state.trip
        val phase = state.phase
        mutableStatus.value = if (trip == null || phase == RecorderState.IDLE) {
            RecorderStatus.IDLE.copy(autoDetectEnabled = state.autoDetect)
        } else {
            RecorderStatus(
                state = phase,
                tripId = trip.id,
                isManual = trip.manual,
                elapsedS = (now - trip.header.startMonoNs).toDouble() / Units.NANOS_PER_SECOND,
                distanceM = state.distanceM,
                autoDetectEnabled = state.autoDetect,
            )
        }
    }
}
