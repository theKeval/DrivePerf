package com.driveperf.feature.capture

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.driveperf.designsystem.DpIcons
import com.driveperf.designsystem.DrivePerfTheme

/**
 * The locked in-drive screen (S-DRV-01): status only. A recording indicator, elapsed time, distance and, once the
 * Garage exists, the car name, and nothing that invites a glance while moving: no score, no speed, no events, no tips,
 * no map, no navigation, no haptics (safety rules in screen-map.md section 2.2). The controls are a large Stop (it asks
 * for confirmation first) and a large "I'm a passenger". The state changes at most once a second (the elapsed text).
 */
@Composable
fun InDriveScreen(
    state: CaptureUiState.Recording,
    actions: InDriveActions,
    modifier: Modifier = Modifier,
) {
    DriveSurface(modifier) {
        BackHandler(enabled = true) {} // no way out but the two buttons
        DriveContent(state, actions)
    }
}

/** The always-dark pure-black drive surface (tokens.json `driveBg`), whatever the system theme: less glare at night. */
@Composable
internal fun DriveSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    LightSystemBarIcons()
    DrivePerfTheme(darkTheme = true) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = DrivePerfTheme.extendedColors.driveBg,
            contentColor = MaterialTheme.colorScheme.onSurface,
            content = content,
        )
    }
}

@Composable
private fun DriveContent(state: CaptureUiState.Recording, actions: InDriveActions) {
    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
        DriveHeader(state, modifier = Modifier.align(Alignment.TopStart))
        DriveNumbers(state, modifier = Modifier.align(Alignment.Center))
        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StopButton(actions.stop.onRequested)
            PassengerButton(actions.passenger.onRequested)
        }
    }
    if (state.confirmingStop) StopDialog(state.shortTrip, actions)
    PassengerSheet(state.passengerStep, actions)
}

@Composable
private fun StopDialog(shortTrip: Boolean, actions: InDriveActions) {
    AlertDialog(
        onDismissRequest = actions.stop.onDismissed,
        title = { Text(stringResource(R.string.drive_stop_title)) },
        text = {
            Text(stringResource(if (shortTrip) R.string.drive_stop_body_short else R.string.drive_stop_body))
        },
        confirmButton = {
            TextButton(onClick = actions.stop.onConfirmed, modifier = Modifier.heightIn(min = DriveTouchMin)) {
                Text(stringResource(if (shortTrip) R.string.drive_stop_confirm_short else R.string.drive_stop_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.stop.onDismissed, modifier = Modifier.heightIn(min = DriveTouchMin)) {
                Text(stringResource(R.string.drive_stop_keep))
            }
        },
    )
}

@Composable
private fun DriveHeader(state: CaptureUiState.Recording, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Text(
                text = stringResource(R.string.drive_title),
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.weight(1f),
            )
            state.carName?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        Text(
            text = stringResource(
                if (state.manual) R.string.drive_started_manual else R.string.drive_started_auto,
                state.startedAtLabel,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DriveNumbers(state: CaptureUiState.Recording, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.drive_elapsed_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun StopButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = DriveButtonHeight),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Icon(painter = painterResource(DpIcons.Stop), contentDescription = null, modifier = Modifier.size(28.dp))
        Text(
            text = stringResource(R.string.drive_stop),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** The passenger escape (F-CAP-08): a button as large as Stop, outlined so it is not mistaken for it. */
@Composable
internal fun PassengerButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = DriveButtonHeight),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(text = stringResource(R.string.drive_passenger), style = MaterialTheme.typography.titleLarge)
    }
}

/** Draws the status and navigation bar icons light while this screen shows, then restores the previous look. */
@Composable
private fun LightSystemBarIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val lightStatus = controller.isAppearanceLightStatusBars
            val lightNav = controller.isAppearanceLightNavigationBars
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            onDispose {
                controller.isAppearanceLightStatusBars = lightStatus
                controller.isAppearanceLightNavigationBars = lightNav
            }
        }
    }
}
