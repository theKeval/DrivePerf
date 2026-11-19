package com.driveperf.feature.replay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpModalBottomSheet
import com.driveperf.designsystem.DpProvenance
import com.driveperf.designsystem.DpProvenanceGlyph
import com.driveperf.domain.trip.Provenance
import com.driveperf.presentation.replay.ReplayCopy
import com.driveperf.presentation.replay.ReplayPinSheet

internal const val PIN_SHEET_TAG = "replay_pin_sheet"

/**
 * S-RPL-03, the stop or max-speed popover (F-RPL-02 AC2–3): a small sheet with the value in plain text (never accent,
 * never coloured, no animation: the speed-neutral rule), its provenance, the rows and "Jump to this moment".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PinSheet(sheet: ReplayPinSheet, controls: ReplayControls) {
    DpModalBottomSheet(onDismissRequest = controls::clearSelection, modifier = Modifier.testTag(PIN_SHEET_TAG)) {
        Column(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(sheet.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (sheet.subtitle.isNotEmpty()) {
                Text(sheet.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                Modifier.clearAndSetSemantics { contentDescription = sheet.accessibilityLabel },
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(sheet.value, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold)
                Text(sheet.unit, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ProvenanceLabel(sheet.provenance)
            }
            if (sheet.rows.isNotEmpty()) {
                Column {
                    sheet.rows.forEachIndexed { i, row ->
                        if (i > 0) HorizontalDivider()
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(row.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(row.value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Text(sheet.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = controls::jumpToPin, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(ReplayCopy.JUMP)
            }
        }
    }
}

/** The provenance glyph and word (F-DQ-05, components.md §10.2). */
@Composable
internal fun ProvenanceLabel(provenance: Provenance, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DpProvenanceGlyph(provenance.dp(), color)
        Text(provenance.label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

internal fun Provenance.dp(): DpProvenance = when (this) {
    Provenance.OBSERVED -> DpProvenance.OBSERVED
    Provenance.DERIVED -> DpProvenance.DERIVED
    Provenance.ESTIMATED -> DpProvenance.ESTIMATED
}
