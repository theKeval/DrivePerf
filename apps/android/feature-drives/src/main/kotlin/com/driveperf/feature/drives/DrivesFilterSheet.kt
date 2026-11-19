package com.driveperf.feature.drives

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpIcons
import com.driveperf.designsystem.DpModalBottomSheet
import com.driveperf.designsystem.DpQualityGlyph
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.presentation.drives.DrivesIntent
import com.driveperf.presentation.drives.FilterChipUi
import com.driveperf.presentation.drives.FilterSheetUi
import com.driveperf.presentation.drives.HistoryFilterAction
import com.driveperf.presentation.drives.HistoryFilterUi

// S-HIS-01's filter chips and S-HIS-02 Filter sheet (DRIVE-126 AC3), drawn from the shared presenter's state: the list
// is filtered there, not per row here, so 1,000 drives stay cheap (AC2).

/** Test tag of the "Filters" chip on All drives. */
const val DRIVES_FILTERS_TAG: String = "drives_filters"

/** Test tag of S-HIS-02's "Show n drives" button. */
const val DRIVES_FILTERS_SHOW_TAG: String = "drives_filters_show"

/**
 * "Filters" then the applied filters (× removes one), "Clear" and the quick filters, with "8 drives · filtered". S-HIS-02
 * opens from here, so it is hosted here too.
 */
@Composable
internal fun DrivesFilterRow(history: HistoryFilterUi, onAction: (DrivesIntent) -> Unit, modifier: Modifier = Modifier) {
    val send = { action: HistoryFilterAction -> onAction(DrivesIntent.History(action)) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = history.active,
                onClick = { send(HistoryFilterAction.OpenSheet) },
                label = { Text(if (history.active) "${history.buttonLabel} · ${history.activeCount}" else history.buttonLabel) },
                modifier = Modifier.testTag(DRIVES_FILTERS_TAG).semantics { contentDescription = history.buttonAccessibilityLabel },
            )
            history.applied.forEach { chip ->
                InputChip(
                    selected = true,
                    onClick = { send(HistoryFilterAction.Toggle(chip.key)) },
                    label = { Text(chip.label) },
                    trailingIcon = { Icon(painterResource(R.drawable.ic_trip_edit_close), null, Modifier.size(18.dp)) },
                    modifier = Modifier.clearAndSetSemantics { contentDescription = chip.accessibilityLabel },
                )
            }
            if (history.active) TextButton(onClick = { send(HistoryFilterAction.Clear) }) { Text(history.clearLabel) }
            history.suggestions.forEach { chip ->
                FilterChip(
                    selected = false,
                    onClick = { send(HistoryFilterAction.Toggle(chip.key)) },
                    label = { Text(chip.label) },
                    modifier = Modifier.clearAndSetSemantics { contentDescription = chip.accessibilityLabel },
                )
            }
        }
        history.sheet?.let { DrivesFilterSheet(it, onAction) }
        history.subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = DrivePerfTheme.extendedColors.text2,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** The filter row and the no-match state at the top of All drives. */
internal fun LazyListScope.drivesFilterItems(history: HistoryFilterUi, onAction: (DrivesIntent) -> Unit) {
    item(key = "filters") { DrivesFilterRow(history, onAction) }
    item(key = "filters-empty") { DrivesFilterEmpty(history, onAction) }
}

/** "No drives match." with "Clear filters". */
@Composable
internal fun DrivesFilterEmpty(history: HistoryFilterUi, onAction: (DrivesIntent) -> Unit) {
    val title = history.emptyTitle ?: return
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        history.emptyBody?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = DrivePerfTheme.extendedColors.text2) }
        TextButton(onClick = { onAction(DrivesIntent.History(HistoryFilterAction.Clear)) }) { Text(history.clearLabel) }
    }
}

/** S-HIS-02, while the state opens it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DrivesFilterSheet(sheet: FilterSheetUi, onAction: (DrivesIntent) -> Unit) {
    val send = { action: HistoryFilterAction -> onAction(DrivesIntent.History(action)) }
    DpModalBottomSheet(
        onDismissRequest = { send(HistoryFilterAction.CloseSheet) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(sheet.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(
                sheet.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = DrivePerfTheme.extendedColors.text2,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            sheet.groups.forEach { group ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(group.title, style = MaterialTheme.typography.labelLarge, color = DrivePerfTheme.extendedColors.text2,
                        modifier = Modifier.semantics { heading() })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.chips.forEach { chip -> SheetChip(chip) { send(HistoryFilterAction.ToggleDraft(chip.key)) } }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { send(HistoryFilterAction.ResetDraft) }, enabled = sheet.canReset) { Text(sheet.resetLabel) }
            Button(
                onClick = { send(HistoryFilterAction.Apply) },
                enabled = sheet.canShow,
                modifier = Modifier.weight(1f).testTag(DRIVES_FILTERS_SHOW_TAG),
            ) {
                Text(sheet.showLabel)
            }
        }
    }
}

/** An M3 FilterChip with the quality glyph where it has one, and its drive count. */
@Composable
private fun SheetChip(chip: FilterChipUi, onClick: () -> Unit) {
    val quality = chip.quality
    FilterChip(
        selected = chip.selected,
        onClick = onClick,
        label = {
            Text(chip.label)
            Text(" ${chip.count}", color = DrivePerfTheme.extendedColors.text3)
        },
        leadingIcon = when {
            chip.selected -> {
                { Icon(painterResource(DpIcons.Check), null, Modifier.size(18.dp)) }
            }
            quality != null -> {
                { DpQualityGlyph(quality.dp(), size = 14.dp, ink = MaterialTheme.colorScheme.surface) }
            }
            else -> null
        },
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = chip.accessibilityLabel
            selected = chip.selected
        },
    )
}
