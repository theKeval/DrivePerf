package com.driveperf.feature.drives

// Trip detail's sections below the stat tiles (S-TRP-01): events, highlights and the tip.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpEventGlyph
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.presentation.drives.TripEventsUi
import com.driveperf.presentation.score.TipUi

/** "Events · n": counts by type, then the rows; "No abrupt inputs on this drive." without any. */
@Composable
internal fun EventsSection(events: TripEventsUi) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(events.title)
        events.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
            color = DrivePerfTheme.extendedColors.text2); return@Column }
        if (events.rows.isEmpty()) {
            Surface(shape = MaterialTheme.shapes.large, color = cardColor, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(events.emptyTitle, style = MaterialTheme.typography.titleSmall)
                    Text(events.emptyBody, style = MaterialTheme.typography.bodyMedium, color = DrivePerfTheme.extendedColors.text2)
                }
            }
            return@Column
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            events.counts.forEach { count ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = count.accessibilityLabel },
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        DpEventGlyph(count.type.dp(), size = 16.dp)
                        Text("${count.label} ${count.count}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        Surface(shape = MaterialTheme.shapes.large, color = cardColor, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { events.rows.forEach { EventRow(it) } }
        }
    }
}

/** The positive card first (✦ Cleanest stretch), then the tip card (components.md §18). */
@Composable
internal fun Highlights(cleanest: String?, tip: TipUi?) {
    if (cleanest == null && tip == null) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.score_highlights))
        cleanest?.let {
            Surface(shape = MaterialTheme.shapes.large, color = cardColor, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.score_cleanest), style = MaterialTheme.typography.labelLarge,
                        color = DrivePerfTheme.extendedColors.text2)
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        tip?.let { TipCard(it) }
    }
}

@Composable
private fun TipCard(tip: TipUi) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tip.kicker, style = MaterialTheme.typography.labelLarge, color = DrivePerfTheme.extendedColors.text2)
            Text(tip.text, style = MaterialTheme.typography.bodyLarge)
            Text(tip.why, style = MaterialTheme.typography.bodyMedium, color = DrivePerfTheme.extendedColors.text2)
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
}
