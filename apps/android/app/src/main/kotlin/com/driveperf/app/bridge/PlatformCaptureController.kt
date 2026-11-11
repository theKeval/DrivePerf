package com.driveperf.app.bridge

import android.content.Context
import com.driveperf.feature.capture.CaptureController
import com.driveperf.feature.capture.CaptureSnapshot
import com.driveperf.feature.capture.GpsHealth
import com.driveperf.feature.onboarding.PermissionReader
import com.driveperf.platform.capture.CaptureStarter
import com.driveperf.platform.capture.CaptureState
import com.driveperf.platform.capture.GpsStatus
import com.driveperf.platform.capture.InterimTripRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Connects the capture screens to the platform: Start and Stop go to the foreground service, state comes from the
 * recorder. INTERIM (DRIVE-72): when DRIVE-62's `TripRecorder` presenter lands, this maps from that instead.
 */
class PlatformCaptureController(
    private val context: Context,
    recorder: InterimTripRecorder,
    private val permissions: PermissionReader,
    scope: CoroutineScope,
) : CaptureController {
    override val snapshot: StateFlow<CaptureSnapshot> = recorder.state
        .map { it.toSnapshot() }
        .stateIn(scope, SharingStarted.Eagerly, CaptureSnapshot.Idle)

    private val mutableCanRecord = MutableStateFlow(permissions.read().fineLocation)
    override val canRecord: StateFlow<Boolean> = mutableCanRecord

    /** Re-reads permissions; called when the app comes to the foreground, since Settings may have changed them. */
    fun refreshPermissions() {
        mutableCanRecord.value = permissions.read().fineLocation
    }

    override fun start() {
        CaptureStarter.start(context)
    }

    override fun stop() {
        CaptureStarter.stop(context)
    }
}

private fun CaptureState.toSnapshot(): CaptureSnapshot = when (this) {
    CaptureState.Idle -> CaptureSnapshot.Idle
    is CaptureState.Failed -> CaptureSnapshot.Failed
    is CaptureState.Recording -> CaptureSnapshot.Recording(
        startedWallMs = startedWallMs,
        distanceM = distanceM,
        gps = when (gps) {
            GpsStatus.SEARCHING -> GpsHealth.SEARCHING
            GpsStatus.GOOD -> GpsHealth.GOOD
            GpsStatus.LOST -> GpsHealth.LOST
        },
    )
}
