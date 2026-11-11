package com.driveperf.app.bridge

import android.content.Context
import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.ActivitySource
import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.RecorderStatus
import com.driveperf.domain.capture.TripRecorder
import com.driveperf.domain.capture.WakeEvent
import com.driveperf.domain.capture.WakeKind
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.feature.capture.FinishedTrip
import com.driveperf.feature.capture.CaptureController
import com.driveperf.feature.capture.CaptureSnapshot
import com.driveperf.feature.capture.GpsHealth
import com.driveperf.feature.onboarding.PermissionReader
import com.driveperf.feature.onboarding.PermissionState
import com.driveperf.foundation.Clock
import com.driveperf.platform.capture.CaptureStarter
import com.driveperf.platform.capture.TripClock
import com.driveperf.platform.location.FixWatch
import com.driveperf.platform.location.GpsStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Connects the capture screens to the shared core: Start is a manual wake through the foreground service, Stop goes to
 * the core's `TripRecorder`, and the snapshot is its status plus the GNSS state, re-read every second so "GPS lost"
 * shows while the car stands still.
 */
@Suppress("LongParameterList") // the seams it bridges
class PlatformCaptureController(
    private val context: Context,
    private val recorder: TripRecorder,
    private val fixes: FixWatch,
    private val clock: Clock,
    private val permissions: PermissionReader,
    scope: CoroutineScope,
    private val trips: TripRepository,
    private val activity: ActivitySource,
    private val onPermissions: (PermissionState) -> Unit = {},
) : CaptureController {
    private val tripClock = TripClock()

    override val snapshot: StateFlow<CaptureSnapshot> = combine(recorder.status, ticks()) { status, _ ->
        snapshotOf(status, tripClock.startedWallMs(status, clock.wallMs()), fixes.status())
    }.stateIn(scope, SharingStarted.Eagerly, CaptureSnapshot.Idle)

    private val mutableCanRecord = MutableStateFlow(permissions.read().fineLocation)
    override val canRecord: StateFlow<Boolean> = mutableCanRecord

    /**
     * Re-reads permissions; called when the app comes to the foreground and after onboarding, since Settings may have
     * changed them. [onPermissions] lets the wake path follow (automatic trips need background location).
     */
    fun refreshPermissions() {
        val current = permissions.read()
        mutableCanRecord.value = current.fineLocation
        onPermissions(current)
    }

    override fun start() {
        CaptureStarter.start(context, WakeEvent(WakeKind.MANUAL, clock.monotonicNs(), clock.wallMs(), "app"))
    }

    override fun stop() {
        recorder.stop()
    }

    /** Passenger escape (F-CAP-08): the core finalises the trip as a passenger trip, or deletes it and its samples. */
    override fun markPassenger(keep: Boolean) {
        recorder.markPassenger(keep)
    }

    override suspend fun finishedTrip(tripId: String): FinishedTrip? = trips.trip(tripId)?.let(::finishedOf)

    /** The OS activity since the trip ended; a newer, non-automotive reading (or none) means it has stopped. */
    override suspend fun isMoving(sinceWallMs: Long): Boolean {
        val samples = suspendCancellableCoroutine { continuation ->
            activity.queryHistory(sinceWallMs, clock.wallMs()) { continuation.resume(it) }
        }
        return stillAutomotive(samples)
    }

    private fun ticks() = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MS)
        }
    }

    internal companion object {
        private const val TICK_MS = 1_000L

        /** True when the latest reading in [samples] is automotive at medium confidence or better. */
        fun stillAutomotive(samples: List<ActivitySample>): Boolean {
            val latest = samples.maxByOrNull { it.wallMs } ?: return false
            return latest.type == ActivityType.AUTOMOTIVE && latest.confidence >= ActivityConfidence.MEDIUM
        }

        /** A committed trip as the saved screen shows it; a trip still recording or discarded is null. */
        fun finishedOf(trip: Trip): FinishedTrip? {
            if (trip.status != TripStatus.FINALISED && trip.status != TripStatus.INTERRUPTED) return null
            val durationS = trip.durationS ?: 0.0
            return FinishedTrip(
                id = trip.id,
                manual = trip.source == TripSource.MANUAL,
                startedAtMs = trip.startedAt,
                endedAtMs = trip.endedAt ?: (trip.startedAt + (durationS * MS_PER_S).toLong()),
                distanceM = trip.distanceM ?: 0.0,
                durationS = durationS.toLong(),
                interrupted = trip.status == TripStatus.INTERRUPTED,
            )
        }

        private const val MS_PER_S = 1_000.0

        /** A candidate is not shown (spec §1.4); a start the core could not open its files for is a failure. */
        fun snapshotOf(status: RecorderStatus, startedWallMs: Long?, gps: GpsStatus): CaptureSnapshot = when {
            status.state == RecorderState.IDLE && status.startFailed -> CaptureSnapshot.Failed
            startedWallMs == null -> CaptureSnapshot.Idle
            else -> CaptureSnapshot.Recording(
                startedWallMs = startedWallMs,
                distanceM = status.distanceM,
                isManual = status.isManual,
                tripId = status.tripId,
                gps = when (gps) {
                    GpsStatus.SEARCHING -> GpsHealth.SEARCHING
                    GpsStatus.GOOD -> GpsHealth.GOOD
                    GpsStatus.LOST -> GpsHealth.LOST
                },
            )
        }
    }
}
