package com.driveperf.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpSwitchRow
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.domain.settings.Appearance
import com.driveperf.domain.settings.Settings
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.DisplayUnit

@Composable
fun SettingsHubScreen(
    summary: String,
    onUnits: () -> Unit,
    onRecording: () -> Unit,
    onHealth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsColumn(modifier) {
        Card { SettingsLink(stringResource(R.string.health_check), onHealth) }
        Text(stringResource(R.string.recording_capture_section), style = MaterialTheme.typography.titleSmall)
        Card { SettingsLink(stringResource(R.string.recording_title), onRecording) }
        Text(stringResource(R.string.preferences), style = MaterialTheme.typography.titleSmall)
        Card { SettingsLink(stringResource(R.string.units_display), onUnits, summary) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitsAndDisplayScreen(
    state: Settings,
    regionalUnits: UnitSystem,
    onChanged: ((Settings) -> Settings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val units = state.units ?: regionalUnits
    SettingsColumn(modifier) {
        Text(stringResource(R.string.units), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            UnitSystem.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == units,
                    onClick = { onChanged { it.copy(units = option) } },
                    shape = SegmentedButtonDefaults.itemShape(index, UnitSystem.entries.size),
                ) { Text(unitName(option)) }
            }
        }
        Card {
            UnitRow(R.string.distance, units.display(DisplayUnit.KILOMETRES).symbol)
            UnitRow(R.string.speed, units.display(DisplayUnit.KM_PER_HOUR).symbol)
            UnitRow(R.string.elevation, units.display(DisplayUnit.METRES).symbol)
            UnitRow(R.string.g_force, stringResource(R.string.g_always))
        }
        Text(stringResource(R.string.units_footer, unitName(regionalUnits)), style = MaterialTheme.typography.bodyMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.preview), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.preview_route), style = MaterialTheme.typography.titleLarge)
                val distance = units.display(DisplayUnit.KILOMETRES)
                val elevation = units.display(DisplayUnit.METRES)
                Text(
                    stringResource(
                        R.string.preview_values, distance.format(37_900.0), distance.symbol,
                        elevation.format(90.0), elevation.symbol,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
        Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleSmall)
        Card(Modifier.selectableGroup()) {
            Appearance.entries.forEach { option ->
                AppearanceRow(option, option == state.appearance) { onChanged { it.copy(appearance = option) } }
            }
        }
        Text(stringResource(R.string.appearance_footer), style = MaterialTheme.typography.bodyMedium)
        Card {
            DpSwitchRow(
                title = stringResource(R.string.wallpaper_colours), description = stringResource(R.string.wallpaper_detail),
                checked = state.dynamicColour, onCheckedChange = { on -> onChanged { it.copy(dynamicColour = on) } },
            )
        }
        Text(stringResource(R.string.text_size), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.text_size_detail), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun SettingsColumn(modifier: Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsLink(title: String, onClick: () -> Unit, detail: String? = null) {
    ListItem(
        onClick = onClick, supportingContent = detail?.let { { Text(it) } },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) { Text(title) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UnitRow(label: Int, symbol: String) {
    ListItem(
        trailingContent = { Text(symbol) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) { Text(stringResource(label)) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppearanceRow(option: Appearance, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick),
        leadingContent = { RadioButton(selected, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        supportingContent = when (option) {
            Appearance.SYSTEM -> { { Text(stringResource(R.string.system_detail)) } }
            Appearance.DARK -> { { Text(stringResource(R.string.dark_detail)) } }
            Appearance.LIGHT -> null
        },
    ) { Text(appearanceName(option)) }
}

@Composable
fun unitName(units: UnitSystem): String = stringResource(if (units == UnitSystem.METRIC) R.string.metric else R.string.imperial)

@Composable
fun appearanceName(appearance: Appearance): String = stringResource(when (appearance) {
    Appearance.SYSTEM -> R.string.system
    Appearance.LIGHT -> R.string.light
    Appearance.DARK -> R.string.dark
})

@Preview @Composable
private fun UnitsPreview() { DrivePerfTheme { UnitsAndDisplayScreen(Settings(), UnitSystem.METRIC, {}) } }

@Preview @Composable
private fun HubPreview() { DrivePerfTheme { SettingsHubScreen("Metric · System", {}, {}, {}) } }
