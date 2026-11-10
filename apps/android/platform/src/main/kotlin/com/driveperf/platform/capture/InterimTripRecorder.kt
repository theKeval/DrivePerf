package com.driveperf.platform.capture

import com.driveperf.platform.ports.ActivitySource
import com.driveperf.platform.ports.CaptureFailure
import com.driveperf.platform.ports.Clock
import com.driveperf.platform.ports.LocationSample
import com.driveperf.platform.ports.LocationSource
import com.driveperf.platform.ports.MotionSource
import com.driveperf.platform.ports.TripFileStore
import com.driveperf.platform.ports.TripFileWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Runs one manually started trip: collects the three sources, appends every sample to the trip file (flushed at least
 * every [flushEveryMs]) and records the trip in the [TripIndex] when it stops.
 *
 * INTERIM (DRIVE-72): this stands in for DRIVE-62's `TripRecorder` state machine. It starts recording the moment
 * [start] is called (nothing is lost to a confirmation step) and filters nothing for scoring. When the core's recorder
 * lands, the service calls it instead and this class is deleted; the sources, clock and file store stay.
 *
 * All sample writes go through one writer, which is internally synchronised, so the three collectors can run on any
 * thread of [dispatcher].
 */
class InterimTripRecorder(
    private val location: LocationSource,
    private val motion: MotionSource,
    private val activity: ActivitySource,
    private val clock: Clock,
    private val store: TripFileStore,
    private val index: TripIndex,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val flushEveryMs: Long = DEFAULT_FLUSH_MS,
    private val gpsLostAfterMs: Long = DEFAULT_GPS_LOST_MS,
) {
    private val mutableState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val state: StateFlow<CaptureState> get() = mutableState

    private var job: Job? = null
    private var session: Session? = null

    /** Starts a trip. Returns false when one is already recording. */
    @Synchronized
    fun start(): Boolean {
        if (job?.isActive == true) return false
        val tripId = UUID.randomUUID().toString()
        val startedMono = clock.monotonicNs()
        val startedWall = clock.wallMs()
        val current = Session(tripId, startedWall, startedMono)
        session = current
        mutableState.value = current.recording(GpsStatus.SEARCHING)
        job = scope.launch(dispatcher) { run(current) }
        return true
    }

    /** Stops the trip, waits until the file is flushed and closed, and returns the saved entry (null when empty). */
    suspend fun stop(): TripEntry? {
        val running = synchronized(this) { job.also { job = null } } ?: return null
        running.cancelAndJoin()
        val finished = synchronized(this) { session.also { session = null } } ?: return null
        val entry = finished.entry(clock)
        mutableState.value = CaptureState.Idle
        if (finished.stats.fixCount == 0) {
            store.delete(finished.tripId) // nothing was recorded: leave no empty trip behind
            return null
        }
        index.add(entry)
        return entry
    }

    private suspend fun run(current: Session) {
        val writer = store.open(current.tripId)
        try {
            coroutineScope {
                launch { collectLocation(current, writer) }
                launch { collectMotion(writer) }
                launch { collectActivity(writer) }
                launch { tick(current, writer) }
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            writer.flush()
            writer.close()
        }
    }

    private suspend fun collectLocation(current: Session, writer: TripFileWriter) {
        location.samples()
            .catch { failure ->
                if (failure is CaptureFailure) fail(failure) else throw failure
            }
            .collect { sample ->
                writer.append(sample)
                current.onFix(sample, clock.monotonicNs())
                mutableState.value = current.recording(GpsStatus.GOOD)
            }
    }

    private suspend fun collectMotion(writer: TripFileWriter) {
        // A device with no usable motion sensor still records a GNSS-only trip; the core marks its quality.
        motion.samples().catch { failure -> if (failure !is CaptureFailure) throw failure }.collect { writer.append(it) }
    }

    private suspend fun collectActivity(writer: TripFileWriter) {
        // Activity hints are optional: without the permission the trip is recorded from location and motion alone.
        activity.events().catch { failure -> if (failure !is CaptureFailure) throw failure }.collect { writer.append(it) }
    }

    private suspend fun tick(current: Session, writer: TripFileWriter) {
        while (true) {
            delay(flushEveryMs)
            writer.flush()
            val sinceFix = TimeUnit.NANOSECONDS.toMillis(clock.monotonicNs() - current.lastFixMonoNs)
            if (current.stats.fixCount > 0 && sinceFix > gpsLostAfterMs) {
                mutableState.value = current.recording(GpsStatus.LOST)
            }
        }
    }

    private fun fail(failure: CaptureFailure) {
        mutableState.value = CaptureState.Failed(failure)
        scope.launch(dispatcher) { stop() }
    }

    /** Mutable per-trip bookkeeping. Written from the location collector only; read by [stop] after the job ended. */
    private class Session(val tripId: String, val startedWallMs: Long, val startedMonoNs: Long) {
        val stats = LiveTripStats()
        val gaps = MovingGapTracker()

        @Volatile
        var lastFixMonoNs: Long = startedMonoNs

        fun onFix(sample: LocationSample, nowMonoNs: Long) {
            stats.onFix(sample)
            gaps.onFix(sample)
            lastFixMonoNs = nowMonoNs
        }

        fun recording(gps: GpsStatus) = CaptureState.Recording(
            tripId = tripId,
            startedWallMs = startedWallMs,
            startedMonoNs = startedMonoNs,
            distanceM = stats.distanceM,
            fixCount = stats.fixCount,
            gps = gps,
        )

        fun entry(clock: Clock) = TripEntry(
            tripId = tripId,
            startedWallMs = startedWallMs,
            durationS = TimeUnit.NANOSECONDS.toSeconds(clock.monotonicNs() - startedMonoNs),
            distanceM = stats.distanceM,
            fixCount = stats.fixCount,
            gpsDeliveryWhileMovingPercent = gaps.deliveryPercent,
        )
    }

    private companion object {
        const val DEFAULT_FLUSH_MS = 5_000L
        const val DEFAULT_GPS_LOST_MS = 10_000L
    }
}
