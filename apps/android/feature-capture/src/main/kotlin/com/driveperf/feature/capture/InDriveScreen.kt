package com.driveperf.feature.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpIcons
import com.driveperf.designsystem.DrivePerfTheme

/**
 * The locked in-drive screen (S-DRV-01): status only. It shows elapsed time, distance so far and GPS health, and nothing
 * that invites a glance while moving: no score, no speed, no map, no haptics (safety rules in screen-map.md section 2.2).
 * The one control is a large Stop button (96 dp, `layout.android.driveButton`) that asks for confirmation first.
 */
@Composable
fun InDriveScreen(
    state: CaptureUiState.Recording,
    onStopRequested: () -> Unit,
    onStopDismissed: () -> Unit,
    onStopConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The drive screen is always dark on pure black (tokens.json `driveBg`), whatever the system theme: less glare at night.
    DrivePerfTheme(darkTheme = true) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = DrivePerfTheme.extendedColors.driveBg,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            DriveContent(state, onStopRequested, onStopDismissed, onStopConfirmed)
        }
    }
}

@Composable
private fun DriveContent(
    state: CaptureUiState.Recording,
    onStopRequested: () -> Unit,
    onStopDismissed: () -> Unit,
    onStopConfirmed: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
        DriveStatus(state, modifier = Modifier.align(Alignment.Center))
        StopControl(onStopRequested, modifier = Modifier.align(Alignment.BottomCenter))
    }
    if (state.confirmingStop) {
        AlertDialog(
            onDismissRequest = onStopDismissed,
            title = { Text(stringResource(R.string.drive_stop_title)) },
            text = { Text(stringResource(R.string.drive_stop_body)) },
            confirmButton = { TextButton(onClick = onStopConfirmed) { Text(stringResource(R.string.drive_stop_confirm)) } },
            dismissButton = { TextButton(onClick = onStopDismissed) { Text(stringResource(R.string.drive_stop_keep)) } },
        )
    }
}

@Composable
private fun DriveStatus(state: CaptureUiState.Recording, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.drive_title),
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(text = state.elapsed, style = MaterialTheme.typography.displayLargeEmphasized)
        Text(
            text = stringResource(R.string.drive_distance, state.distanceKm),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = when (state.gps) {
                GpsHealth.SEARCHING -> stringResource(R.string.drive_gps_searching)
                GpsHealth.GOOD -> stringResource(R.string.drive_gps_good)
                GpsHealth.LOST -> stringResource(R.string.drive_gps_lost)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StopControl(onStopRequested: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.drive_locked_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onStopRequested,
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Icon(
                painter = painterResource(DpIcons.Stop),
                contentDescription = stringResource(R.string.drive_stop),
                modifier = Modifier.size(40.dp),
            )
        }
    }
}
