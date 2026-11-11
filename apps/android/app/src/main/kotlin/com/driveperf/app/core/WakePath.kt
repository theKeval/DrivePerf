package com.driveperf.app.core

import android.content.Context
import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.TripRecorder
import com.driveperf.feature.onboarding.PermissionState
import com.driveperf.platform.capture.CaptureForegroundService
import com.driveperf.platform.capture.CaptureStarter
import com.driveperf.platform.wake.ActivityTransitions
import com.driveperf.platform.wake.GeofenceParkedRegionMonitor
import com.driveperf.platform.wake.WakeDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Automatic trips on Android (DRIVE-206): routes OS wakes to the capture service, keeps the activity transitions
 * registered while the permissions allow automatic trips, and puts a trip the core resumed after a kill back into a
 * foreground service.
 *
 * Automatic detection is on when both background location and activity recognition are granted: without background
 * location a wake could not record, and the transitions need activity recognition. The explicit opt-in screen
 * (S-ONB-08) is a later story.
 */
class WakePath(
    private val context: Context,
    private val recorder: TripRecorder,
    private val transitions: ActivityTransitions,
    private val parkedRegion: GeofenceParkedRegionMonitor,
) {
    /** Call once from `Application.onCreate`, after the core started and before `onLaunch`. */
    fun install(permissions: PermissionState, scope: CoroutineScope) {
        WakeDispatcher.setHandler { wake -> CaptureStarter.start(context, wake) }
        refresh(permissions)
        recorder.status.onEach { status ->
            if (status.state != RecorderState.IDLE && !CaptureForegroundService.running) resumeInForeground()
        }.launchIn(scope)
    }

    /**
     * Re-reads the permissions: at launch and whenever the app comes back to the foreground. When automatic trips are
     * allowed it also registers the parked region again if an earlier attempt failed, for example because background
     * location was granted in Settings after the last trip ended.
     */
    fun refresh(permissions: PermissionState) {
        val automatic = automaticAllowed(permissions)
        recorder.setAutoDetect(automatic)
        if (automatic) {
            transitions.arm()
            parkedRegion.rearmIfNeeded()
        } else {
            transitions.disarm()
        }
    }

    private fun resumeInForeground() {
        try {
            CaptureStarter.resume(context)
        } catch (_: IllegalStateException) {
            // Android refused a background start (ForegroundServiceStartNotAllowedException); the service's own
            // restart (START_STICKY) or the next wake brings the trip back into the foreground.
        }
    }

    companion object {
        fun automaticAllowed(p: PermissionState): Boolean = p.fineLocation && p.backgroundLocation && p.activityRecognition
    }
}
