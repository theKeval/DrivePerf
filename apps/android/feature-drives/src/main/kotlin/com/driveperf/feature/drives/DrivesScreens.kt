package com.driveperf.feature.drives

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpEmptyState
import com.driveperf.designsystem.DpQualityGlyph
import com.driveperf.designsystem.DpStatusChip
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.presentation.drives.DrivesFilter
import com.driveperf.presentation.drives.DrivesIntent
import com.driveperf.presentation.drives.DrivesState
import com.driveperf.presentation.drives.TripCopy
import com.driveperf.presentation.drives.TripRowUi
import com.driveperf.presentation.drives.TripSectionUi
import com.driveperf.presentation.score.ComparisonTone
import com.driveperf.presentation.score.ScoreBadgeUi

private const val DIMMED_ALPHA = 0.6f

/**
 * The Drives tab root (S-HOME-01): the latest drives, with a way to all of them. [bottomPadding] leaves room for the
 * floating capture toolbar so the last row is never hidden behind it.
 */
@Composable
fun DrivesHomeScreen(
    state: DrivesState,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    onAction: (DrivesIntent) -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.isEmpty) {
            item {
                DpEmptyState(
                    title = stringResource(R.string.drives_empty_title),
                    body = stringResource(R.string.drives_empty_body),
                )
            }
            if (state.seeAll) {
                item {
                    TextButton(onClick = onSeeAll) {
                        Text(stringResource(R.string.drives_see_not_drives, state.notDrives.size))
                    }
                }
            }
        } else {
            item {
                Text(
                    text = stringResource(R.string.drives_latest),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
            }
            items(state.latest, key = { it.id }) { TripRow(it, state.canCorrect, onAction) }
            if (state.seeAll) {
                item { TextButton(onClick = onSeeAll) { Text(stringResource(R.string.drives_see_all)) } }
            }
        }
    }
}

/**
 * Every recorded drive, newest first and grouped by week with sticky headers (S-HIS-01, DRIVE-126 AC1), with the
 * "Not drives" filter (F-CAP-04 AC3). The list is lazy and keyed, so 1,000 drives compose only what is on screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AllDrivesScreen(
    state: DrivesState,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    onAction: (DrivesIntent) -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.canCorrect) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.filter == DrivesFilter.DRIVES,
                        onClick = { onAction(DrivesIntent.Filter(DrivesFilter.DRIVES)) },
                        label = { Text(stringResource(R.string.drives_filter_drives)) },
                    )
                    FilterChip(
                        selected = state.filter == DrivesFilter.NOT_DRIVES,
                        onClick = { onAction(DrivesIntent.Filter(DrivesFilter.NOT_DRIVES)) },
                        label = { Text(stringResource(R.string.drives_filter_not_drives)) },
                    )
                }
            }
        }
        if (state.filter == DrivesFilter.NOT_DRIVES) {
            item {
                Text(
                    text = stringResource(R.string.drives_not_drives_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (state.isEmpty) {
            item {
                DpEmptyState(
                    title = stringResource(R.string.drives_empty_title),
                    body = stringResource(R.string.drives_empty_body),
                )
            }
        }
        if (state.filter == DrivesFilter.DRIVES && !state.isEmpty) drivesFilterItems(state.history, onAction)
        if (state.filter == DrivesFilter.NOT_DRIVES) {
            items(state.notDrives, key = { it.id }) { TripRow(it, state.canCorrect, onAction) }
        } else {
            state.sections.forEach { section ->
                stickyHeader(key = "week-${section.id}") { WeekHeader(section) }
                items(section.rows, key = { it.id }) { TripRow(it, state.canCorrect, onAction) }
            }
            state.sinceText?.let { since ->
                item(key = "since") {
                    Text(
                        since,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** A week's sticky header: "This week" and "3 drives · avg 84". */
@Composable
private fun WeekHeader(section: TripSectionUi) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .padding(vertical = 8.dp)
                .clearAndSetSemantics {
                    heading()
                    contentDescription = section.accessibilityLabel
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(section.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(section.meta, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A trip card (components.md §12): tap pushes trip detail ([DrivesIntent.OpenTrip]); long-press offers "What was this?"
 * (M3E contextual actions); an open question is answered under the card. The score badge sits at the trailing edge.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TripRow(trip: TripRowUi, canCorrect: Boolean, onAction: (DrivesIntent) -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    val whatWasThis = stringResource(R.string.drives_what_was_this)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onAction(DrivesIntent.OpenTrip(trip.id)) },
                        onLongClick = if (canCorrect) ({ menu = true }) else null,
                    )
                    .alpha(if (trip.dimmed) DIMMED_ALPHA else 1f)
                    .padding(16.dp)
                    .clearAndSetSemantics {
                        contentDescription = trip.accessibilityLabel
                        if (canCorrect) customActions = listOf(CustomAccessibilityAction(whatWasThis) { menu = true; true })
                    },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = trip.title, style = MaterialTheme.typography.titleMediumEmphasized)
                        Text(
                            "${trip.subtitle} · ${trip.stats}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    trip.score?.let { ScoreBadge(it) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    trip.quality?.let { QualityBadgeChip(it) } ?: DpStatusChip(label = stringResource(R.string.drives_recorded))
                    if (trip.interrupted) DpStatusChip(label = stringResource(R.string.drives_interrupted), attention = true)
                    if (trip.short) DpStatusChip(label = stringResource(R.string.drives_short))
                    trip.kindLabel?.let { DpStatusChip(label = it) }
                    trip.car?.let { DpStatusChip(label = it) }
                }
                trip.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            CorrectionMenu(expanded = menu, onDismiss = { menu = false }) { onAction(DrivesIntent.Correct(trip.id, it)) }
        }
        val question = trip.question
        if (question != null && canCorrect) {
            QuestionBar(question, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                onAction(DrivesIntent.Answer(trip.id, it))
            }
        }
    }
}

/** "What was this?" (S-TRP-02 type picker, F-CAP-04). */
@Composable
internal fun CorrectionMenu(expanded: Boolean, onDismiss: () -> Unit, onPick: (TripCorrection) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        TripCopy.correctionOptions.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.title) },
                onClick = {
                    onDismiss()
                    onPick(option.correction)
                },
            )
        }
    }
}

/** "Was this a bus or train?" with Yes / No (F-CAP-04 AC2–3). */
@Composable
internal fun QuestionBar(question: Suspect, modifier: Modifier = Modifier, onAnswer: (Boolean) -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(if (question == Suspect.CYCLING) R.string.drives_question_bike else R.string.drives_question_transit),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { onAnswer(true) }) { Text(stringResource(R.string.drives_yes)) }
            Button(onClick = { onAnswer(false) }) { Text(stringResource(R.string.drives_no)) }
        }
    }
}

/** The compact quality badge: glyph + word, never colour alone (components.md §10.1). */
@Composable
internal fun QualityBadgeChip(badge: QualityBadge, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DpQualityGlyph(badge.dp(), size = 14.dp, ink = MaterialTheme.colorScheme.surface)
            Text(TripCopy.badgeWord(badge), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Preview
@Composable
private fun DrivesHomeScreenPreview() {
    DrivePerfTheme {
        DrivesHomeScreen(
            state = DrivesState(
                loaded = true,
                canCorrect = true,
                trips = listOf(
                    TripRowUi(
                        "a", "The Commute", "Tuesday morning · 10 Nov", "12.4 km", "25 min", quality = QualityBadge.GOOD,
                        score = ScoreBadgeUi("85", "+2", ComparisonTone.BETTER),
                    ),
                    TripRowUi(
                        "b", "Monday evening", "9 Nov · 5:10 PM", "9.1 km", "18 min", quality = QualityBadge.USABLE, interrupted = true,
                    ),
                    TripRowUi(
                        "c", "Monday morning", "9 Nov · 8:02 AM", "11.0 km", "31 min", quality = QualityBadge.LIMITED,
                        question = Suspect.TRANSIT,
                    ),
                ),
            ),
            onSeeAll = {},
        )
    }
}
