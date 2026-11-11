package com.driveperf.app.bridge

import android.content.Context
import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.RecorderStatus
import com.driveperf.domain.capture.TripRecorder
import com.driveperf.domain.capture.WakeEvent
import com.driveperf.domain.capture.WakeKind
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

    private fun ticks() = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MS)
        }
    }

    internal companion object {
        private const val TICK_MS = 1_000L

        /** A candidate is not shown (spec §1.4); a start the core could not open its files for is a failure. */
        fun snapshotOf(status: RecorderStatus, startedWallMs: Long?, gps: GpsStatus): CaptureSnapshot = when {
            status.state == RecorderState.IDLE && status.startFailed -> CaptureSnapshot.Failed
            startedWallMs == null -> CaptureSnapshot.Idle
            else -> CaptureSnapshot.Recording(
                startedWallMs = startedWallMs,
                distanceM = status.distanceM,
                gps = when (gps) {
                    GpsStatus.SEARCHING -> GpsHealth.SEARCHING
                    GpsStatus.GOOD -> GpsHealth.GOOD
                    GpsStatus.LOST -> GpsHealth.LOST
                },
            )
        }
    }
}
