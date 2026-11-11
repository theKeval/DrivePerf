package com.driveperf.app.navigation

import com.driveperf.app.core.AutoDetectOff

/** What the shell can ask the app to do, and the notices it shows ([autoDetectOff] is Home's "Auto-detect off", F-ONB-04.6). */
data class ShellCallbacks(
    val onTabSelected: (Tab) -> Unit,
    val onProfile: () -> Unit,
    val onStart: () -> Unit,
    val onOpenDrive: () -> Unit,
    val onFixLocation: () -> Unit,
    /** "Fix" on the Auto-detect off notice: Android's permission settings for DrivePerf. */
    val onFixAutoDetect: () -> Unit = {},
    val autoDetectOff: AutoDetectOff? = null,
)
