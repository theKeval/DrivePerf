package com.driveperf.feature.settings

import com.driveperf.domain.settings.SettingsVehicle
import com.driveperf.domain.settings.Settings
import androidx.compose.material3.RadioButton
import com.driveperf.designsystem.DpSwitchRow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpHealth
import com.driveperf.designsystem.DpHealthGlyph
import com.driveperf.designsystem.DpIcons
import com.driveperf.designsystem.DrivePerfTheme

/**
 * The status row under the switch: the same words as the capture toolbar ("Auto-detect on · ready" / "Last check-in 18
 * min ago", or "Auto-detect needs attention" / the first reason), resolved by the app from the capture feature.
 */
data class RecordingStatusLine(
    val health: DpHealth,
    /** The glyph's spoken meaning (OK, Needs fixing). */
    val healthLabel: String,
    val title: String,
    val detail: String?,
)

/**
 * The Pause auto-detect row (F-CAP-15): "Pause auto-detect", or "Resume auto-detect" while [paused]. It opens S-HOME-03;
 * the status row above it says until when.
 */
data class RecordingPauseLine(val paused: Boolean, val title: String, val detail: String)

/** What S-SET-03 draws. [status] and [pause] are shown only while automatic trips are on. */
data class RecordingSettingsUiState(
    val automaticTrips: Boolean,
    val status: RecordingStatusLine?,
    val pause: RecordingPauseLine? = null,
    val preferences: Settings = Settings(),
    val cars: List<SettingsVehicle> = emptyList(),
)

/** Independent native actions for S-SET-03, matching the capture status screen's action bundle. */
data class RecordingSettingsActions(
    val onAutomaticTripsChanged: (Boolean) -> Unit = {},
    val onStatusClick: () -> Unit = {},
    val onPauseClick: () -> Unit = {},
    val onPreferencesChanged: ((Settings) -> Settings) -> Unit = {},
    val onGarageClick: () -> Unit = {},
)

/**
 * S-SET-03 Recording settings (F-SET-01 AC3), reached from Profile: the Automatic trips switch and, while it is on, the
 * capture status, Pause, Garage default car and battery saver recording.
 */
@Composable
fun RecordingSettingsScreen(
    state: RecordingSettingsUiState,
    actions: RecordingSettingsActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.automaticTrips) ManualModeBanner()
        Text(
            text = stringResource(R.string.recording_capture_section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp).semantics { heading() },
        )
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            AutomaticTripsRow(state.automaticTrips, actions.onAutomaticTripsChanged)
            val status = state.status
            if (state.automaticTrips && status != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                StatusRow(status, actions.onStatusClick)
            }
            val pause = state.pause
            if (state.automaticTrips && pause != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                PauseRow(pause, actions.onPauseClick)
            }
        }
        if (state.automaticTrips) {
            Text(
                text = stringResource(R.string.recording_promise),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        RecordingPreferences(state, actions)
    }
}

@Composable
private fun RecordingPreferences(state: RecordingSettingsUiState, actions: RecordingSettingsActions) {
        Card {
            if (state.cars.isEmpty()) {
                Text(stringResource(R.string.no_cars), Modifier.padding(16.dp))
                SettingsLink(stringResource(R.string.add_car), actions.onGarageClick)
            } else {
                Text(stringResource(R.string.default_car), Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
                CarChoice(stringResource(R.string.no_default), state.preferences.defaultVehicleId == null) {
                    actions.onPreferencesChanged { it.copy(defaultVehicleId = null) }
                }
                state.cars.forEach { car ->
                    CarChoice(car.name, state.preferences.defaultVehicleId == car.id) {
                        actions.onPreferencesChanged { it.copy(defaultVehicleId = car.id) }
                    }
                }
                SettingsLink(stringResource(R.string.manage_cars), actions.onGarageClick)
            }
        }
        Card {
            DpSwitchRow(
                title = stringResource(R.string.battery_saver), description = stringResource(R.string.battery_detail),
                checked = state.preferences.batterySaverRecording,
                onCheckedChange = { on -> actions.onPreferencesChanged { it.copy(batterySaverRecording = on) } },
            )
            if (state.preferences.batterySaverRecording) {
                Text(stringResource(R.string.usable_data), Modifier.padding(16.dp))
            }
        }
        Text(stringResource(R.string.battery_footer), style = MaterialTheme.typography.bodyMedium)
        Card { SettingsLink(stringResource(R.string.health_check), actions.onStatusClick) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CarChoice(name: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick),
        leadingContent = { RadioButton(selected, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) { Text(name) }
}

@Composable
private fun ManualModeBanner() {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.recording_manual_banner),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AutomaticTripsRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    // Not the `checked` ListItem: it tints the row as selected, which S-SET-03 does not show. The whole row toggles.
    ListItem(
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = { Icon(painterResource(DpIcons.Drives), contentDescription = null) },
        supportingContent = {
            Text(stringResource(if (checked) R.string.recording_auto_on else R.string.recording_auto_off))
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                thumbContent = if (checked) {
                    { Icon(painterResource(DpIcons.Check), contentDescription = null, Modifier.size(SwitchDefaults.IconSize)) }
                } else {
                    null
                },
            )
        },
    ) { Text(stringResource(R.string.recording_auto_title)) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusRow(status: RecordingStatusLine, onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = { DpHealthGlyph(status.health, status.healthLabel) },
        supportingContent = status.detail?.let { { Text(it) } },
        trailingContent = { Icon(painterResource(DpIcons.ChevronRight), contentDescription = null) },
    ) { Text(status.title) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PauseRow(pause: RecordingPauseLine, onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = { Icon(painterResource(if (pause.paused) DpIcons.Play else DpIcons.Pause), contentDescription = null) },
        supportingContent = { Text(pause.detail) },
        trailingContent = { Icon(painterResource(DpIcons.ChevronRight), contentDescription = null) },
    ) { Text(pause.title) }
}

@Preview
@Composable
private fun RecordingSettingsOnPreview() {
    DrivePerfTheme {
        RecordingSettingsScreen(
            RecordingSettingsUiState(
                automaticTrips = true,
                status = RecordingStatusLine(DpHealth.Ok, "OK", "Auto-detect on · ready", "Last check-in 18 min ago"),
                pause = RecordingPauseLine(paused = false, "Pause auto-detect", "Stop automatic trips for a while"),
            ),
            actions = RecordingSettingsActions(),
        )
    }
}

@Preview
@Composable
private fun RecordingSettingsOffPreview() {
    DrivePerfTheme {
        RecordingSettingsScreen(RecordingSettingsUiState(automaticTrips = false, status = null), RecordingSettingsActions())
    }
}
