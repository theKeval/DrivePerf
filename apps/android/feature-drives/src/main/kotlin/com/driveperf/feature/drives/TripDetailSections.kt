package com.driveperf.feature.drives

// Trip detail's sections below the stat tiles (S-TRP-01): events, highlights and the tip.

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpEventGlyph
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.domain.score.ContributorId
import com.driveperf.presentation.drives.TripEventsUi
import com.driveperf.presentation.drives.TripLinkCopy
import com.driveperf.presentation.score.ContributorUi
import com.driveperf.presentation.score.EventRowUi
import com.driveperf.presentation.score.TipUi

/**
 * "Events · n": counts by type, then the rows; "No abrupt inputs on this drive." without any. Each row, pinned or not,
 * opens its evidence ([onEvent], DRIVE-55); one reported "Not accurate?" is left out and opens nothing.
 */
@Composable
internal fun EventsSection(events: TripEventsUi, onEvent: (String) -> Unit = {}) {
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
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                events.rows.forEach { EventRow(it, Modifier.opensEvidence(it, onEvent)) }
            }
        }
    }
}

/**
 * The positive card first (✦ Cleanest stretch), then the tip card (components.md §18): the card opens S-SCO-04
 * ([onTip]) and "See the moment" its event's evidence ([onTipEvidence], DRIVE-102).
 */
@Composable
internal fun Highlights(cleanest: String?, tip: TipUi?, onTip: () -> Unit = {}, onTipEvidence: () -> Unit = {}) {
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
        tip?.let { TipCard(it, onTip, onTipEvidence) }
    }
}

@Composable
private fun TipCard(tip: TipUi, onTip: () -> Unit, onTipEvidence: () -> Unit) {
    Surface(
        onClick = onTip,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
        modifier = Modifier.fillMaxWidth().testTag(TIP_CARD_TAG),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tip.kicker, style = MaterialTheme.typography.labelLarge, color = DrivePerfTheme.extendedColors.text2)
            Text(tip.text, style = MaterialTheme.typography.bodyLarge)
            Text(tip.why, style = MaterialTheme.typography.bodyMedium, color = DrivePerfTheme.extendedColors.text2)
            if (tip.eventId != null) {
                TextButton(onClick = onTipEvidence, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("${TripLinkCopy.SEE_THE_MOMENT} ›")
                }
            }
        }
    }
}

/** Test tag of trip detail's tip card. */
const val TIP_CARD_TAG: String = "trip_detail_tip"

/** An event row that opens its evidence, unless it was reported "Not accurate?" and left out. */
internal fun Modifier.opensEvidence(event: EventRowUi, onEvent: (String) -> Unit): Modifier =
    if (event.excluded) this else clickable(onClickLabel = TripLinkCopy.OPEN_EVIDENCE, role = Role.Button) { onEvent(event.id) }

/**
 * The four contributors on trip detail; one with events expands to its event rows, each opening its evidence
 * (DRIVE-87 AC2), as on S-SCO-01. [onOpen] records `contributor_tapped`.
 */
@Composable
internal fun TripContributors(contributors: List<ContributorUi>, onOpen: (ContributorId) -> Unit, onEvent: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    ContributorList(contributors, open, onToggle = { id, opening ->
        open = if (opening) id.name else null
        if (opening) onOpen(id)
    }, onEvent = onEvent)
}

/** Contributor rows, [open] one showing its events; shared by trip detail and S-SCO-01. */
@Composable
internal fun ContributorList(
    contributors: List<ContributorUi>,
    open: String?,
    onToggle: (ContributorId, Boolean) -> Unit,
    onEvent: (String) -> Unit,
) {
    Column {
        contributors.forEachIndexed { index, contributor ->
            if (index > 0) HorizontalDivider()
            val hasEvents = contributor.events.isNotEmpty()
            val isOpen = hasEvents && open == contributor.id.name
            ContributorRow(
                contributor,
                if (hasEvents) {
                    Modifier.clickable(
                        onClickLabel = if (isOpen) TripLinkCopy.HIDE_EVENTS else TripLinkCopy.SHOW_EVENTS,
                        role = Role.Button,
                    ) { onToggle(contributor.id, !isOpen) }
                } else {
                    Modifier
                },
                trailing = if (hasEvents) ({ Text(if (isOpen) "⌃" else "⌄", style = MaterialTheme.typography.titleMedium) }) else null,
            )
            if (isOpen) contributor.events.forEach { EventRow(it, Modifier.opensEvidence(it, onEvent).padding(start = 8.dp)) }
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
}
