package com.driveperf.app.navigation

import com.driveperf.feature.capture.AttentionReason
import com.driveperf.feature.capture.AutoDetectStatus

/**
 * What the shell can ask the app to do, and what it shows about automatic trips: [autoDetect] drives the toolbar, the
 * Home banner and the capture status sheet (F-CAP-02); [forceStopNotice] is Android's re-arm notice (F-CAP-14).
 */
data class ShellCallbacks(
    val onTabSelected: (Tab) -> Unit,
    val onProfile: () -> Unit,
    val onStart: () -> Unit,
    val onOpenDrive: () -> Unit,
    val onFixLocation: () -> Unit,
    /** "Fix" on a failing check in the capture status sheet. */
    val onFixReason: (AttentionReason) -> Unit = {},
    /** S-SET-03, from the sheet and from the manual-mode notice's "Turn on". */
    val onOpenRecordingSettings: () -> Unit = {},
    val autoDetect: AutoDetectStatus? = null,
    val forceStopNotice: Boolean = false,
    val onDismissForceStop: () -> Unit = {},
)
