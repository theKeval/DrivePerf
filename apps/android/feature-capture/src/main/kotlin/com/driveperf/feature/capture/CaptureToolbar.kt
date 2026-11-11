package com.driveperf.feature.capture

import androidx.compose.foundation.clickable
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
import com.driveperf.designsystem.DpIcons

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
    onStart: () -> Unit,
    onOpenDrive: () -> Unit,
    onFixLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recording = state as? CaptureUiState.Recording
    val status = when (state) {
        CaptureUiState.Ready -> stringResource(R.string.capture_toolbar_ready)
        CaptureUiState.NeedsLocation -> stringResource(R.string.capture_toolbar_needs_location)
        CaptureUiState.Failed -> stringResource(R.string.capture_toolbar_failed)
        is CaptureUiState.Recording -> stringResource(R.string.capture_toolbar_recording, state.elapsed)
    }
    val (iconRes, description, action) = when (state) {
        CaptureUiState.Ready, CaptureUiState.Failed -> Triple(DpIcons.Record, stringResource(R.string.capture_start_description), onStart)
        CaptureUiState.NeedsLocation -> Triple(DpIcons.Record, stringResource(R.string.capture_fix_location_description), onFixLocation)
        is CaptureUiState.Recording -> Triple(DpIcons.Stop, stringResource(R.string.capture_open_drive_description), onOpenDrive)
    }
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = action) {
                Icon(painter = painterResource(iconRes), contentDescription = description)
            }
        },
        content = {
            Text(
                text = status,
                style = MaterialTheme.typography.labelLargeEmphasized,
                modifier = if (recording != null) Modifier.clickable(onClick = onOpenDrive) else Modifier,
            )
        },
    )
}
