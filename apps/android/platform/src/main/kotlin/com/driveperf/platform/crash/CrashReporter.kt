package com.driveperf.platform.crash

import com.driveperf.domain.capture.RecorderStatus

/**
 * Crash and error reporting switch (ADR-007). Reporting is off until the user opts in; [setEnabled] is called at launch
 * with the stored choice and again whenever the user changes it.
 */
interface CrashReporter {
    /** False when the build has no crash-reporting account configured, so the toggle can say so. */
    val isAvailable: Boolean

    fun setEnabled(enabled: Boolean)

    /**
     * The recorder's state for the allow-listed trip tags (`in_trip`, `capture_mode`, `trip_state`, F-ANA-03 AC2).
     * Called on every change; kept while reporting is off and applied when it starts.
     */
    fun setTrip(status: RecorderStatus) = Unit
}
