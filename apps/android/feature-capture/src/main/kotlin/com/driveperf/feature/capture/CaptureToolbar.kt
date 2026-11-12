package com.driveperf.feature.capture

import android.text.format.DateUtils
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.driveperf.designsystem.DpIcons
import com.driveperf.designsystem.DrivePerfTheme

/**
 * The capture status control on the Drives tab: an M3 Expressive horizontal floating toolbar (vibrant) with the status
 * text and a floating action button (tokens.json `layout.android.floatingToolbar`, components.md section on the shell).
 *
 * Ready: the button starts a drive. Recording: the button (and the text) open the in-drive screen; stopping happens
 * there, behind a confirmation. No location permission: the button explains and offers to fix it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CaptureToolbar(
    state: CaptureUiState,
    autoDetect: CaptureAutoDetect?,
    onStart: () -> Unit,
    onOpenDrive: () -> Unit,
    onFixLocation: () -> Unit,
) {
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
            Column(modifier = if (recording != null) Modifier.clickable(onClick = onOpenDrive) else Modifier) {
                CaptureToolbarLabel(state, autoDetect)
            }
        },
    )
}

@Composable
private fun CaptureToolbarLabel(state: CaptureUiState, autoDetect: CaptureAutoDetect?) {
    val autoDetectLabel = autoDetect?.let { stringResource(it.label) }
    val status = when (state) {
        CaptureUiState.Ready -> autoDetectLabel ?: stringResource(R.string.capture_toolbar_ready)
        CaptureUiState.NeedsLocation -> stringResource(R.string.capture_toolbar_needs_location)
        CaptureUiState.Failed -> stringResource(R.string.capture_toolbar_failed)
        is CaptureUiState.Recording -> stringResource(R.string.capture_toolbar_recording, state.elapsed)
    }
    val detail = when (state) {
        CaptureUiState.Ready -> autoDetect?.lastCheckInMs?.let { checkInText(it) }
        CaptureUiState.NeedsLocation, CaptureUiState.Failed -> autoDetectLabel
        is CaptureUiState.Recording -> null
    }
    Text(text = status, style = MaterialTheme.typography.labelLargeEmphasized)
    if (detail != null) Text(text = detail, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun checkInText(wallMs: Long): String {
    val time = DateUtils.formatDateTime(
        LocalContext.current, wallMs, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME,
    )
    return stringResource(R.string.capture_last_check_in, time)
}

@Preview
@Composable
private fun CaptureToolbarPreview() {
    DrivePerfTheme {
        CaptureToolbar(CaptureUiState.Ready, CaptureAutoDetect.on(lastCheckInMs = null), {}, {}, {})
    }
}
