package com.driveperf.pipeline.capture

import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.ActivitySource
import com.driveperf.domain.capture.AutoDetectHealth
import com.driveperf.domain.capture.AutoDetectWatchdog
import com.driveperf.domain.capture.CaptureBatch
import com.driveperf.domain.capture.CaptureHeader
import com.driveperf.domain.capture.CaptureLog
import com.driveperf.domain.capture.CaptureMark
import com.driveperf.domain.capture.CaptureMarkType
import com.driveperf.domain.capture.FinalisedTrip
import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.LocationSource
import com.driveperf.domain.capture.MotionSource
import com.driveperf.domain.capture.NoOpAutoDetectWatchdog
import com.driveperf.domain.capture.ParkedRegionMonitor
import com.driveperf.domain.capture.PowerState
import com.driveperf.domain.capture.PowerStatus
import com.driveperf.domain.capture.PreviewStore
import com.driveperf.domain.capture.RearmReason
import com.driveperf.domain.capture.RecordedAccelSample
import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.RecorderStatus
import com.driveperf.domain.capture.TimeZoneProvider
import com.driveperf.domain.capture.TripRecorder
import com.driveperf.domain.capture.TripStore
import com.driveperf.domain.capture.WakeEvent
import com.driveperf.domain.capture.WakeKind
import com.driveperf.domain.capture.WatchdogState
import com.driveperf.domain.capture.WatchdogStore
import com.driveperf.domain.trip.CaptureMeta
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.tripfile.AnalysisFileStore
import com.driveperf.domain.tripfile.FileResult
import com.driveperf.domain.tripfile.TripFileKind
import com.driveperf.domain.tripfile.TripFileRef
import com.driveperf.domain.tripfile.TripFileStore
import com.driveperf.foundation.Clock
import com.driveperf.foundation.Units
import com.driveperf.foundation.newId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The device ports the recorder drives. [watchdog] schedules the F-CAP-14 notification; the default does nothing. */
public data class CapturePorts(
    val location: LocationSource,
    val motion: MotionSource,
    val activity: ActivitySource,
    val power: PowerState,
    val parkedRegion: ParkedRegionMonitor,
    val files: TripFileStore,
    val watchdog: AutoDetectWatchdog = NoOpAutoDetectWatchdog,
)

/**
 * Where the recorder persists. [previews] and [analysis] store preview tracks and R1 files; null skips them (tests).
 * [watchdog] keeps the F-CAP-14 check-in across process starts; null keeps it in memory only.
 */
public data class CapturePersistence(
    val store: TripStore,
    val log: CaptureLog,
    val finaliser: TripFinaliser,
    val previews: PreviewStore? = null,
    val analysis: AnalysisFileStore? = null,
    val watchdog: WatchdogStore? = null,
)

/**
 * The [TripRecorder]: runs [TripMachine] and executes its effects against the ports. Every input goes through one
 * channel and is handled in order by a single coroutine in [scope], so callbacks may arrive on any thread. Pass a
 * scope on a single-threaded or `limitedParallelism(1)` dispatcher off the main thread.
 *
 * The engine itself never decides anything: guards and timings live in the reducer, and the watchdog schedule in
 * [WatchdogPolicy]. Capture analytics go to [analytics] (consent-gated by the caller); null sends none.
 */
@Suppress("TooManyFunctions", "LongParameterList") // one executor per effect type; one parameter per dependency
public class TripRecorderEngine(
    private val ports: CapturePorts,
    private val persistence: CapturePersistence,
    private val clock: Clock,
    private val zones: TimeZoneProvider,
    private val scope: CoroutineScope,
    private val config: CaptureConfig = CaptureConfig(),
    private val analytics: Analytics? = null,
) : TripRecorder {
    private val machine = TripMachine(config)
    private val watchdogPolicy = WatchdogPolicy(config)

    /** The F-CAP-14 bookkeeping, read once from [CapturePersistence.watchdog]. Touched only on the engine coroutine. */
    private var watchdog: WatchdogState? = null
    private val inputs = Channel<Input>(Channel.UNLIMITED)
    private val mutableStatus = MutableStateFlow(RecorderStatus.IDLE)
    private val timers = mutableMapOf<TimerId, Job>()

    /** Recorded-accelerometer queries still running, by trip. Touched only on the engine coroutine. */
    private val backfills = mutableMapOf<String, CompletableDeferred<List<RecordedAccelSample>>>()
    private var state = MachineState()
    private var gnssOn = false
    private var lastSyncNs = 0L

    override val status: StateFlow<RecorderStatus> = mutableStatus.asStateFlow()

    init {
        scope.launch { for (input in inputs) handle(input) }
    }

    override fun onLaunch() {
        onLaunch(RearmReason.LAUNCH)
    }

    override fun onLaunch(reason: RearmReason) {
        inputs.trySend(Input.Launch(reason))
    }

    override fun onForeground() {
        inputs.trySend(Input.Simple { MachineEvent.Foreground(it) })
    }

    override fun onWatchdogOpened() {
        inputs.trySend(Input.Simple { MachineEvent.WatchdogOpened(it) })
    }

    override fun setTriggersAvailable(available: Boolean) {
        inputs.trySend(Input.Simple { MachineEvent.TriggersAvailable(it, available) })
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
        data class Launch(val reason: RearmReason) : Input

        data class Wake(val event: WakeEvent) : Input

        data class Simple(val make: (Long) -> MachineEvent) : Input

        data class Backfill(val tripId: String, val result: CompletableDeferred<List<RecordedAccelSample>>) : Input
    }

    private suspend fun handle(input: Input) {
        val now = clock.monotonicNs()
        val event = when (input) {
            is Input.Launch -> launchEvent(now, input.reason)
            is Input.Wake -> MachineEvent.Wake(now, clock.wallMs(), input.event, newHeader(input.event, now))
            is Input.Simple -> input.make(now)
            is Input.Backfill -> {
                backfillArrived(input)
                return
            }
        }
        apply(event, now)
    }

    /** Reduces [event] and runs its effects. An effect that fails is fed back at once, before any status is shown. */
    private suspend fun apply(event: MachineEvent, now: Long) {
        val transition = machine.reduce(state, event)
        state = transition.state
        for (effect in transition.effects) {
            val failure = execute(effect)
            if (failure != null) return apply(failure, now)
        }
        publishStatus(now)
    }

    private suspend fun launchEvent(now: Long, reason: RearmReason): MachineEvent.Launch {
        watchdogState()
        val store = persistence.store
        val recording = store.recordingTrip()
        val recovery = recording?.let { recoveryFor(it) }
        if (recording != null && recovery == null) store.deleteNow(recording.id)
        return MachineEvent.Launch(
            now, clock.wallMs(), dbAvailable = true, parking = store.lastParking(), recovering = recovery, reason = reason,
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

    /** Runs [effect]; returns the event to feed back when it failed. */
    private suspend fun execute(effect: MachineEffect): MachineEvent? {
        if (effect is MachineEffect.CreateTrip) return createTrip(effect)
        if (!executeSensor(effect)) executeOther(effect)
        return null
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
            is MachineEffect.ConfirmTrip -> confirmTrip(effect)
            is MachineEffect.RunBackfill -> runBackfill(effect)
            is MachineEffect.Append -> append(effect)
            is MachineEffect.Mark -> {
                val mark = CaptureMark(effect.tNs, effect.type, effect.detail)
                persistence.log.append(effect.tripId, CaptureBatch(marks = listOf(mark)))
            }
            is MachineEffect.Finalise -> finalise(effect)
            is MachineEffect.Discard -> discard(effect.tripId)
            else -> executeDetection(effect)
        }
    }

    /** Analytics and the F-CAP-14 check-ins. */
    private suspend fun executeDetection(effect: MachineEffect) {
        when (effect) {
            is MachineEffect.Track -> analytics?.track(effect.event)
            MachineEffect.CheckIn -> checkIn()
            MachineEffect.WatchdogOff -> watchdogOff()
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

    /** Opens R0 first: without it nothing would be kept, so a failure drops the trip (returns [MachineEvent.LogFailed]). */
    private suspend fun createTrip(effect: MachineEffect.CreateTrip): MachineEvent? {
        val h = effect.header
        val wall = clock.wallMs()
        if (!persistence.log.open(h)) return MachineEvent.LogFailed(clock.monotonicNs(), h.tripId)
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
        return null
    }

    /** The query answers on any thread, so its callback only completes the result and queues it. */
    private fun runBackfill(effect: MachineEffect.RunBackfill) {
        val result = CompletableDeferred<List<RecordedAccelSample>>()
        backfills[effect.tripId] = result
        ports.motion.queryRecordedAccelerometer(effect.fromWallMs, effect.toWallMs) { samples ->
            result.complete(samples)
            inputs.trySend(Input.Backfill(effect.tripId, result))
        }
    }

    /** A backfill that arrived while recording. Ignored when finalise or discard already took it. */
    private suspend fun backfillArrived(input: Input.Backfill) {
        if (backfills[input.tripId] !== input.result) return
        backfills.remove(input.tripId)
        appendBackfill(input.tripId, input.result.await())
    }

    /** Finalisation reads R0 once, so a query still running is waited for (bounded) first. */
    private suspend fun awaitBackfill(tripId: String) {
        val pending = backfills.remove(tripId) ?: return
        val samples = withTimeoutOrNull((config.backfillWaitS * MS_PER_S).toLong()) { pending.await() }
        if (samples != null) appendBackfill(tripId, samples)
    }

    private suspend fun appendBackfill(tripId: String, samples: List<RecordedAccelSample>) {
        if (samples.isNotEmpty()) persistence.log.append(tripId, CaptureBatch(recordedAccel = samples))
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
        if (!ok) send { t -> MachineEvent.AppendFailed(t, effect.tripId, hadMotion = effect.batch.motion.isNotEmpty()) }
    }

    private fun schedule(effect: MachineEffect.Schedule) {
        timers.remove(effect.id)?.cancel()
        timers[effect.id] = scope.launch {
            delay(effect.delayNs / Units.NANOS_PER_MILLI)
            send { now -> MachineEvent.Timer(now, effect.id) }
        }
    }

    private suspend fun finalise(effect: MachineEffect.Finalise) {
        val startedNs = clock.monotonicNs()
        val log = persistence.log
        awaitBackfill(effect.tripId)
        log.sync(effect.tripId)
        val capture = log.read(effect.tripId)
        val trip = state.trip
        val result = capture?.let {
            persistence.finaliser.finalise(
                FinaliseRequest(it, effect.endNs, effect.kind, trip?.manual ?: false, effect.interruption, clock.wallMs()),
            )
        }
        val finished = result?.trips.orEmpty()
        // A split whose first part was too short to keep still commits the later parts (§1.6, F-CAP-17).
        if (finished.none { it.trip.id == effect.tripId }) discard(effect.tripId)
        finished.forEach { commit(it) }
        val parking = result?.parking
        if (parking != null) persistence.store.setLastParking(parking)
        if (finished.isNotEmpty()) {
            analytics?.track(CaptureEvent.finaliseMs((clock.monotonicNs() - startedNs) / Units.NANOS_PER_MILLI))
        }
        send { now -> MachineEvent.Finalised(now, parking) }
    }

    /**
     * Commits one finished trip (data-model.md §2.3 step 2): replaces its R0 when it is part of a split, writes and
     * hashes R1, compresses R0, then commits every row with the file references in one transaction. A split part whose
     * R0 could not be replaced keeps the original log untouched and is committed without an R0 reference, so a partial
     * or unsplit capture is never recorded as that part's R0.
     */
    private suspend fun commit(finished: FinalisedTrip) {
        val id = finished.trip.id
        val r0Kept = finished.splitCapture?.let { persistence.log.replace(it.header, it.batches()) } ?: true
        val r1 = finished.analysis?.let { persistence.analysis?.write(finished.trip, it) }
        val r0 = if (r0Kept && ports.files.compress(id, TripFileKind.R0) == FileResult.OK) r0Ref(id) else null
        persistence.store.saveFinalised(finished.copy(analysis = null, splitCapture = null, files = listOfNotNull(r0, r1)))
        if (finished.preview.isNotEmpty()) persistence.previews?.write(id, finished.preview)
    }

    private fun r0Ref(tripId: String): TripFileRef? = ports.files.describe(tripId, TripFileKind.R0, withSha256 = true)?.let {
        val sha = it.sha256 ?: return null
        TripFileRef(TripFileKind.R0, R0_FORMAT_VERSION, sha, it.sizeBytes, "trips/$tripId/r0.v$R0_FORMAT_VERSION.pb.gz")
    }

    /** Drops a candidate or a discarded trip: its rows, its R0 and its folder, so nothing of it is left on disk. */
    private suspend fun discard(tripId: String) {
        backfills.remove(tripId)
        persistence.store.deleteNow(tripId)
        persistence.log.delete(tripId)
        ports.files.deleteTrip(tripId)
    }

    private suspend fun watchdogState(): WatchdogState =
        watchdog ?: (persistence.watchdog?.read() ?: WatchdogState()).also { watchdog = it }

    /** F-CAP-14: records the check-in and moves the notification ahead; reports a notification that came due. */
    private suspend fun checkIn() {
        val result = watchdogPolicy.checkIn(watchdogState(), clock.wallMs())
        saveWatchdog(result.state)
        result.state.dueWallMs?.let { ports.watchdog.schedule(it) }
        if (result.fired) analytics?.track(CaptureEvent.watchdogFired)
    }

    private suspend fun watchdogOff() {
        ports.watchdog.cancel()
        saveWatchdog(watchdogPolicy.off(watchdogState()))
    }

    private suspend fun saveWatchdog(next: WatchdogState) {
        watchdog = next
        persistence.watchdog?.write(next)
    }

    private fun publishStatus(now: Long) {
        val trip = state.trip
        val phase = state.phase
        mutableStatus.value = if (trip == null || phase == RecorderState.IDLE) {
            RecorderStatus.IDLE.copy(
                autoDetectEnabled = state.autoDetect,
                startFailed = state.startFailed,
                autoDetectHealth = health(),
                lastCheckInWallMs = watchdog?.lastCheckInWallMs,
            )
        } else {
            RecorderStatus(
                state = phase,
                tripId = trip.id,
                isManual = trip.manual,
                elapsedS = (now - trip.header.startMonoNs).toDouble() / Units.NANOS_PER_SECOND,
                distanceM = state.distanceM,
                autoDetectEnabled = state.autoDetect,
                autoDetectHealth = health(),
                lastCheckInWallMs = watchdog?.lastCheckInWallMs,
            )
        }
    }

    private fun health(): AutoDetectHealth = when {
        !state.autoDetect -> AutoDetectHealth.OFF
        !state.triggersAvailable -> AutoDetectHealth.NEEDS_ATTENTION
        else -> AutoDetectHealth.ON
    }

    private companion object {
        const val MS_PER_S = 1000.0
        const val R0_FORMAT_VERSION = 1
    }
}
