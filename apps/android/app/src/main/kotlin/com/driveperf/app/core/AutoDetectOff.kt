package com.driveperf.app.core

import com.driveperf.feature.onboarding.PermissionState

/**
 * Why automatic trips are off, for Home's "Auto-detect off" notice (F-ONB-04.6, S-HOME-01). `null` means they are on.
 * The same rule as [WakePath.automaticAllowed], with the reason kept so the notice can say what to fix.
 */
enum class AutoDetectOff {
    /** The user chose manual trips (S-ONB-08): nothing to fix in Settings. */
    OptedOut,
    NoLocation,
    NoBackgroundLocation,
    NoActivityRecognition,
    ;

    /** Whether Android's permission settings can fix it (false when the user simply chose manual trips). */
    val fixableInSettings: Boolean get() = this != OptedOut

    companion object {
        fun of(permissions: PermissionState, optedIn: Boolean): AutoDetectOff? = when {
            !optedIn -> OptedOut
            !permissions.fineLocation -> NoLocation
            !permissions.backgroundLocation -> NoBackgroundLocation
            !permissions.activityRecognition -> NoActivityRecognition
            else -> null
        }
    }
}
