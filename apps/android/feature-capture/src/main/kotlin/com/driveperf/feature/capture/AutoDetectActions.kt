package com.driveperf.feature.capture

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.driveperf.designsystem.DpHealth

/** What the floating toolbar can ask for. */
class CaptureToolbarActions(
    val onStart: () -> Unit,
    val onOpenDrive: () -> Unit,
    val onFixLocation: () -> Unit,
    /** The status text while not recording: opens the capture status sheet (S-HOME-02). */
    val onOpenStatus: () -> Unit = {},
)

/** What the capture status sheet can ask the app to do. */
class CaptureStatusActions(
    val onDismiss: () -> Unit,
    /** "Fix" on a failing check: the system permission dialog, or DrivePerf's App info when Android no longer shows it. */
    val onFix: (AttentionReason) -> Unit,
    /** The Recording settings row (S-SET-03), in place of "Pause auto-detect" until DRIVE-152. */
    val onOpenRecordingSettings: () -> Unit,
)

/** The health glyph for each mode. */
fun AutoDetectMode.health(): DpHealth = when (this) {
    AutoDetectMode.Ready -> DpHealth.Ok
    AutoDetectMode.NeedsAttention -> DpHealth.Fix
    AutoDetectMode.Off -> DpHealth.Off
}

/** The glyph's spoken label: what its colour means (accessibility.md: never colour alone). */
@Composable
fun DpHealth.label(): String = stringResource(
    when (this) {
        DpHealth.Ok -> R.string.status_glyph_ok
        DpHealth.Fix -> R.string.status_glyph_fix
        DpHealth.Off -> R.string.status_glyph_off
    },
)
