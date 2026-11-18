package com.driveperf.feature.capture

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.driveperf.designsystem.DpIcons
import com.driveperf.designsystem.DrivePerfTheme

/**
 * The capture status control on the Drives tab: an M3 Expressive horizontal floating toolbar (vibrant) with the status
 * text and a floating action button (tokens.json `layout.android.floatingToolbar`, components.md section on the shell).
 *
 * Ready: the button starts a drive and the text opens the capture status sheet (S-HOME-02). Recording: the button (and the
 * text) open the in-drive screen; stopping happens there, behind a confirmation. No location permission: the button
 * explains and offers to fix it. The second line names the first reason auto-detect needs attention (F-CAP-02).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CaptureToolbar(
    state: CaptureUiState,
    autoDetect: AutoDetectStatus?,
    actions: CaptureToolbarActions,
) {
    val onStart = actions.onStart
    val onOpenDrive = actions.onOpenDrive
    val onFixLocation = actions.onFixLocation
    val onOpenStatus = actions.onOpenStatus
    val recording = state as? CaptureUiState.Recording
    val (iconRes, description, action) = when (state) {
        CaptureUiState.Ready, CaptureUiState.Failed -> Triple(DpIcons.Record, stringResource(R.string.capture_start_description), onStart)
        CaptureUiState.NeedsLocation -> Triple(DpIcons.Record, stringResource(R.string.capture_fix_location_description), onFixLocation)
        is CaptureUiState.Recording -> Triple(DpIcons.Stop, stringResource(R.string.capture_open_drive_description), onOpenDrive)
    }
    HorizontalFloatingToolbar(
        expanded = true,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = action) {
                Icon(painter = painterResource(iconRes), contentDescription = description)
            }
        },
        content = {
            val openStatus = stringResource(R.string.capture_open_status_description)
            val body = when {
                recording != null -> Modifier.clickable(onClick = onOpenDrive)
                autoDetect != null -> Modifier.clickable(onClickLabel = openStatus, role = Role.Button, onClick = onOpenStatus)
                else -> Modifier
            }
            Column(modifier = body) {
                CaptureToolbarLabel(state, autoDetect)
            }
        },
    )
}

@Composable
private fun CaptureToolbarLabel(state: CaptureUiState, autoDetect: AutoDetectStatus?) {
    val autoDetectLabel = autoDetect?.let { stringResource(it.mode.label) }
    val status = when (state) {
        CaptureUiState.Ready -> autoDetectLabel ?: stringResource(R.string.capture_toolbar_ready)
        CaptureUiState.NeedsLocation -> stringResource(R.string.capture_toolbar_needs_location)
        CaptureUiState.Failed -> stringResource(R.string.capture_toolbar_failed)
        is CaptureUiState.Recording -> stringResource(R.string.capture_toolbar_recording, state.elapsed)
    }
    val detail = when (state) {
        CaptureUiState.Ready -> autoDetect?.let { autoDetectDetail(it) }
        CaptureUiState.NeedsLocation, CaptureUiState.Failed -> autoDetectLabel
        is CaptureUiState.Recording -> null
    }
    Text(text = status, style = MaterialTheme.typography.labelLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (detail != null) {
        Text(text = detail, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The second line of the toolbar and of Recording settings' status row: the first reason when auto-detect needs
 * attention, the end of a pause while paused, else the last check-in while it is on.
 */
@Composable
fun autoDetectDetail(status: AutoDetectStatus): String? = when (status.mode) {
    AutoDetectMode.NeedsAttention -> status.reasons.firstOrNull()?.let { stringResource(it.shortText) }
    AutoDetectMode.Ready -> status.lastCheckInMs?.let {
        stringResource(R.string.capture_last_check_in, checkInAgo(it, rememberNowMs()))
    }
    AutoDetectMode.Paused -> pausedUntilLine(status.pausedUntilMs)
    AutoDetectMode.Off -> null
}

/** The toolbar's first line for each mode ("Auto-detect on · ready"). */
val AutoDetectMode.label: Int
    get() = when (this) {
        AutoDetectMode.Off -> R.string.capture_auto_detect_off
        AutoDetectMode.Ready -> R.string.capture_auto_detect_on
        AutoDetectMode.NeedsAttention -> R.string.capture_auto_detect_attention
        AutoDetectMode.Paused -> R.string.capture_auto_detect_paused
    }

@Preview
@Composable
private fun CaptureToolbarPreview() {
    DrivePerfTheme {
        CaptureToolbar(
            CaptureUiState.Ready,
            AutoDetectReadiness.status(optedIn = true, permissions = CapturePermissions.ALL.copy(background = false)),
            CaptureToolbarActions(onStart = {}, onOpenDrive = {}, onFixLocation = {}),
        )
    }
}
