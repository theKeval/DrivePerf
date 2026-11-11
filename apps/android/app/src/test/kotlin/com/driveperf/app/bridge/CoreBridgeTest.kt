package com.driveperf.app.bridge

import com.driveperf.app.core.WakePath
import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.RecorderStatus
import com.driveperf.feature.capture.CaptureSnapshot
import com.driveperf.feature.capture.GpsHealth
import com.driveperf.feature.onboarding.PermissionState
import com.driveperf.platform.location.GpsStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreBridgeTest {
    private val recording = RecorderStatus(RecorderState.RECORDING, "t1", true, 60.0, 1234.0, true)

    @Test
    fun `recorder status maps to what the capture screens show`() {
        assertEquals(
            CaptureSnapshot.Recording(5_000L, 1234.0, GpsHealth.LOST),
            PlatformCaptureController.snapshotOf(recording, 5_000L, GpsStatus.LOST),
        )
        assertEquals(CaptureSnapshot.Idle, PlatformCaptureController.snapshotOf(RecorderStatus.IDLE, null, GpsStatus.SEARCHING))
        assertEquals(
            CaptureSnapshot.Failed,
            PlatformCaptureController.snapshotOf(RecorderStatus.IDLE.copy(startFailed = true), null, GpsStatus.SEARCHING),
        )
    }

    @Test
    fun `automatic trips need fine and background location and activity recognition`() {
        val all = PermissionState(fineLocation = true, backgroundLocation = true, activityRecognition = true)
        assertTrue(WakePath.automaticAllowed(all))
        assertFalse(WakePath.automaticAllowed(all.copy(backgroundLocation = false)))
        assertFalse(WakePath.automaticAllowed(all.copy(activityRecognition = false)))
        assertFalse(WakePath.automaticAllowed(all.copy(fineLocation = false)))
    }
}
