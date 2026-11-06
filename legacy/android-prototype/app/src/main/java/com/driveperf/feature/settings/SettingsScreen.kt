package com.driveperf.feature.settings

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.driveperf.R
import com.driveperf.domain.model.DistanceUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    showBackButton: Boolean = true,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = if (showBackButton) {
                    {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                } else {
                    {}
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            SectionHeader("Trip tracking")
            UnitPreferenceRow(
                title = stringResource(R.string.settings_distance_unit_title),
                subtitle = stringResource(R.string.settings_distance_unit_subtitle),
                selectedUnit = state.distanceUnit,
                onUnitSelected = viewModel::setDistanceUnit,
            )
            HorizontalDivider()
            SettingsToggleRow(
                title = "Auto-detect trips",
                subtitle = "Automatically start recording when you get in a vehicle",
                checked = state.autoDetectEnabled,
                onCheckedChange = viewModel::setAutoDetect,
            )
            HorizontalDivider()

            SectionHeader("Data & privacy")
            SettingsToggleRow(
                title = "Cloud sync",
                subtitle = "Upload trip summaries to enable multi-device history (optional)",
                checked = state.cloudSyncEnabled,
                onCheckedChange = viewModel::setCloudSync,
            )
            HorizontalDivider()

            Spacer(Modifier.height(24.dp))
            Text(
                "DrivePerf stores all trip data on your device. Cloud sync uploads anonymised trip summaries only. You can delete all data from this screen at any time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Renders the explicit two-option unit selector used by Settings for distance and speed display
 * as an accessible radio-button group.
 *
 * @param title Section label shown above the options.
 * @param subtitle Supporting text describing what the preference affects.
 * @param selectedUnit The currently persisted unit choice.
 * @param onUnitSelected Callback invoked when the user picks a different unit.
 */
@Composable
private fun UnitPreferenceRow(
    title: String,
    subtitle: String,
    selectedUnit: DistanceUnit,
    onUnitSelected: (DistanceUnit) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(2.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Column(modifier = Modifier.selectableGroup()) {
            UnitOptionRow(
                label = stringResource(R.string.settings_unit_kilometres),
                selected = selectedUnit == DistanceUnit.KILOMETERS,
                onClick = { onUnitSelected(DistanceUnit.KILOMETERS) },
            )
            UnitOptionRow(
                label = stringResource(R.string.settings_unit_miles),
                selected = selectedUnit == DistanceUnit.MILES,
                onClick = { onUnitSelected(DistanceUnit.MILES) },
            )
        }
    }
}

@Composable
private fun UnitOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
