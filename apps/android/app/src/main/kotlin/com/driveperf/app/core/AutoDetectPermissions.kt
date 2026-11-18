package com.driveperf.app.core

import com.driveperf.domain.capture.AutoDetectHealth
import com.driveperf.domain.capture.RecorderStatus
import com.driveperf.feature.capture.AutoDetectReadiness
import com.driveperf.feature.capture.AutoDetectStatus
import com.driveperf.feature.capture.CapturePermissions
import com.driveperf.feature.onboarding.PermissionState
import com.driveperf.platform.wake.WakeStatus

/**
 * The permissions onboarding reads, as the capture rules see them (F-CAP-02). One rule set decides both whether OS wakes
 * may start a trip ([WakePath.automaticAllowed]) and what Home, the status sheet and Recording settings say about it.
 */
fun PermissionState.forCapture(): CapturePermissions = CapturePermissions(
    location = hasLocation,
    precise = fineLocation,
    background = backgroundLocation,
    activity = activityRecognition,
)

/**
 * The status every surface shows: the user's choice, the permissions, the wake registration and the core's check-in and
 * pause (F-CAP-15).
 */
fun autoDetectStatus(
    optedIn: Boolean,
    permissions: PermissionState,
    wake: WakeStatus,
    lastCheckInMs: Long?,
    pause: CorePause = CorePause.NONE,
): AutoDetectStatus =
    AutoDetectReadiness.status(
        optedIn = optedIn,
        permissions = permissions.forCapture(),
        activityUpdatesFailed = wake.registrationFailed,
        lastCheckInMs = lastCheckInMs,
        paused = pause.paused,
        pausedUntilMs = pause.untilMs,
    )

/** The core's pause as the status needs it: whether automatic detection is paused, and until when (null: indefinitely). */
data class CorePause(val paused: Boolean, val untilMs: Long?) {
    companion object {
        val NONE: CorePause = CorePause(paused = false, untilMs = null)

        fun of(status: RecorderStatus): CorePause =
            CorePause(status.autoDetectHealth == AutoDetectHealth.PAUSED, status.autoDetectPausedUntilWallMs)
    }
}
