package com.driveperf.platform.capture

import com.driveperf.domain.capture.ActivitySource
import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.LocationSource
import com.driveperf.domain.capture.MotionSource
import com.driveperf.domain.tripfile.FileResult
import com.driveperf.domain.tripfile.TripFileKind
import com.driveperf.domain.tripfile.TripFileStore
import com.driveperf.foundation.Clock
import com.driveperf.foundation.DomainError
import com.driveperf.foundation.PermissionKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.TimeUnit

/** The three sources a trip records from, and the bus they report failures on. */
class CaptureSources(
    val location: LocationSource,
    val motion: MotionSource,
    val activity: ActivitySource,
    val failures: CaptureFailureBus,
)

/** How often the file is synced and how long without a fix before GPS is reported lost. */
class RecorderTimings(val syncEveryMs: Long = 5_000L, val gpsLostAfterMs: Long = 10_000L)

/**
 * Runs one manually started trip: starts the three sources with sinks that append every sample to the R0 file (synced at
 * least every [RecorderTimings.syncEveryMs]) and records the trip in the [TripIndex] when it stops.
 *
 * INTERIM (DRIVE-72): this stands in for DRIVE-62's `TripRecorder` state machine (`onWake(MANUAL)`, `stop()`). It starts
 * recording the moment [start] is called (nothing is lost to a confirmation step), filters nothing for scoring and
 * writes the interim text R0 ([R0Lines]). When the core's recorder lands the service calls it instead and this class is
 * deleted; the sources, the clock and the file store stay.
 *
 * Sinks run on the sources' threads, the sync timer on [scope]. A [Session] holds the lock between them and a `closed`
 * flag, so nothing is written or published after [stop] has returned.
 */
class InterimTripRecorder(
    private val sources: CaptureSources,
    private val clock: Clock,
    private val store: TripFileStore,
    private val index: TripIndex,
    private val scope: CoroutineScope,
    private val timings: RecorderTimings = RecorderTimings(),
) {
    private val mutableState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val state: StateFlow<CaptureState> get() = mutableState

    private var session: Session? = null
    private var job: Job? = null

    /** Starts a trip. Returns false when one is already recording. */
    @Synchronized
    fun start(): Boolean {
        if (session != null) return false
        val current = Session(UUID.randomUUID().toString(), clock.wallMs(), clock.monotonicNs())
        session = current
        store.append(current.tripId, TripFileKind.R0, R0Lines.HEADER.toByteArray())
        mutableState.value = current.recording(GpsStatus.SEARCHING)
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Subscribe before the sources start: a missing permission is reported synchronously from `start`.
            launch(start = CoroutineStart.UNDISPATCHED) { watchFailures(current) }
            launch { syncPeriodically(current) }
        }
        startSources(current)
        return true
    }

    /** Stops the trip, syncs and closes the file, and returns the saved entry (null when nothing was recorded). */
    fun stop(): TripEntry? = finish(null)

    private fun startSources(current: Session) {
        sources.location.start { samples ->
            append(current, R0Lines.locations(samples))
            current.onFixes(samples)
        }
        sources.motion.start(MOTION_RATE_HZ) { samples -> append(current, R0Lines.motion(samples)) }
        sources.activity.start { samples -> append(current, R0Lines.activity(samples)) }
    }

    private fun append(current: Session, text: String) {
        val result = current.write(text)
        if (result != null && result != FileResult.OK) fail(current, result.toError())
    }

    private suspend fun watchFailures(current: Session) {
        sources.failures.failures.collect { error ->
            // Without motion or activity a trip is still a GNSS trip; losing location ends it.
            val locationLost = error is DomainError.Capture.PermissionDenied && error.kind == PermissionKind.LOCATION
            if (locationLost) fail(current, error)
        }
    }

    private suspend fun syncPeriodically(current: Session) {
        while (true) {
            delay(timings.syncEveryMs)
            current.syncAndCheckGps()
        }
    }

    private fun fail(current: Session, error: DomainError) {
        if (current.markFailed()) scope.launch { finish(error, only = current) }
    }

    /** Ends the running trip, or, when [only] is given, that session only (a late failure must not end a newer trip). */
    private fun finish(error: DomainError?, only: Session? = null): TripEntry? {
        val current = synchronized(this) {
            val running = session?.takeIf { only == null || it === only }
            if (running != null) {
                session = null
                job?.cancel()
                job = null
            }
            running
        } ?: return null
        sources.location.stop()
        sources.motion.stop()
        sources.activity.stop()
        current.close()
        store.sync(current.tripId, TripFileKind.R0)
        val entry = if (current.fixCount == 0) {
            store.deleteTrip(current.tripId) // nothing was recorded: leave no empty trip behind
            null
        } else {
            current.entry().also(index::add)
        }
        mutableState.value = error?.let(CaptureState::Failed) ?: CaptureState.Idle
        return entry
    }

    private fun FileResult.toError(): DomainError = when (this) {
        FileResult.DISK_FULL -> DomainError.Storage.DiskFull
        FileResult.UNAVAILABLE -> DomainError.Storage.Unavailable
        else -> DomainError.Unexpected("r0-write")
    }

    /** Per-trip bookkeeping. Its lock orders the sinks, the sync timer and [finish]. */
    private inner class Session(val tripId: String, val startedWallMs: Long, val startedMonoNs: Long) {
        private val stats = LiveTripStats()
        private val gaps = MovingGapTracker()
        private var lastFixMonoNs = startedMonoNs
        private var closed = false
        private var failed = false

        val fixCount: Int
            @Synchronized get() = stats.fixCount

        /** Appends to R0. Null when the session is closed (the late sample is dropped, not written). */
        @Synchronized
        fun write(text: String): FileResult? = if (closed) null else store.append(tripId, TripFileKind.R0, text.toByteArray())

        @Synchronized
        fun onFixes(samples: List<LocationSample>) {
            if (closed) return
            val now = clock.monotonicNs()
            samples.forEach {
                stats.onFix(it)
                gaps.onFix(it)
            }
            lastFixMonoNs = now
            mutableState.value = recording(GpsStatus.GOOD)
        }

        @Synchronized
        fun syncAndCheckGps() {
            if (closed) return
            store.sync(tripId, TripFileKind.R0)
            val sinceFixMs = TimeUnit.NANOSECONDS.toMillis(clock.monotonicNs() - lastFixMonoNs)
            if (stats.fixCount > 0 && sinceFixMs > timings.gpsLostAfterMs) mutableState.value = recording(GpsStatus.LOST)
        }

        /** True the first time it is called, so one failure ends the trip once. */
        @Synchronized
        fun markFailed(): Boolean = !failed.also { failed = true }

        @Synchronized
        fun close() {
            closed = true
        }

        fun recording(gps: GpsStatus) = CaptureState.Recording(
            tripId = tripId,
            startedWallMs = startedWallMs,
            startedMonoNs = startedMonoNs,
            distanceM = stats.distanceM,
            fixCount = stats.fixCount,
            gps = gps,
        )

        @Synchronized
        fun entry() = TripEntry(
            tripId = tripId,
            startedWallMs = startedWallMs,
            durationS = TimeUnit.NANOSECONDS.toSeconds(clock.monotonicNs() - startedMonoNs),
            distanceM = stats.distanceM,
            fixCount = stats.fixCount,
            gpsDeliveryWhileMovingPercent = gaps.deliveryPercent,
        )
    }

    private companion object {
        const val MOTION_RATE_HZ = 50
    }
}
