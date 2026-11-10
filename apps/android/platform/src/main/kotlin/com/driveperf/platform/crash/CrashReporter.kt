package com.driveperf.platform.crash

/**
 * Crash and error reporting switch (ADR-007). Reporting is off until the user opts in; [setEnabled] is called at launch
 * with the stored choice and again whenever the user changes it. Mirrors the `CrashReporter` port in `:shared:domain`.
 */
interface CrashReporter {
    fun setEnabled(enabled: Boolean)
}
