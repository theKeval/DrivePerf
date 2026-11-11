package com.driveperf.feature.account

/** What the Profile screen draws. Consent is opt-in and changeable at any time (ADR-007, S-PRV-07). */
data class ProfileUiState(
    val usageStatistics: Boolean,
    val crashReports: Boolean,
    val crashReportingAvailable: Boolean,
    val versionName: String,
    /** Which shared core build the app is running on, from `DrivePerfCore`. */
    val coreDescription: String,
)
