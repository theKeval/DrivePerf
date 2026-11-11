package com.driveperf.feature.drives

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

private const val LATEST_COUNT = 5
private const val DIMMED_ALPHA = 0.6f

/**
 * The Drives tab root (S-HOME-01): the latest drives, with a way to all of them. [bottomPadding] leaves room for the
 * floating capture toolbar so the last row is never hidden behind it.
 */
@Composable
fun DrivesHomeScreen(
    state: DrivesUiState,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    onAction: (DrivesAction) -> Unit = {},
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
        } else {
            item {
                Text(
                    text = stringResource(R.string.drives_latest),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
            }
            items(state.trips.take(LATEST_COUNT), key = { it.id }) { TripRow(it, state.canCorrect, onAction) }
            if (state.trips.size > LATEST_COUNT) {
                item { TextButton(onClick = onSeeAll) { Text(stringResource(R.string.drives_see_all)) } }
            }
            item {
                Text(
                    text = stringResource(R.string.drives_notice_analysis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    DrivesSheets(state, onAction)
}

/** Every recorded drive, newest first (S-HIS-01), with the "Not drives" filter (F-CAP-04 AC3). */
@Composable
fun AllDrivesScreen(
    state: DrivesUiState,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    onAction: (DrivesAction) -> Unit = {},
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
                        onClick = { onAction(DrivesAction.Filter(DrivesFilter.DRIVES)) },
                        label = { Text(stringResource(R.string.drives_filter_drives)) },
                    )
                    FilterChip(
                        selected = state.filter == DrivesFilter.NOT_DRIVES,
                        onClick = { onAction(DrivesAction.Filter(DrivesFilter.NOT_DRIVES)) },
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
        items(state.shown, key = { it.id }) { TripRow(it, state.canCorrect, onAction) }
    }
    DrivesSheets(state, onAction)
}

/**
 * A trip card (components.md §12): tap opens the trip sheet; long-press offers "What was this?" (M3E contextual
 * actions); an open question is answered under the card.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TripRow(trip: TripRowUi, canCorrect: Boolean, onAction: (DrivesAction) -> Unit, modifier: Modifier = Modifier) {
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
                        onClick = { onAction(DrivesAction.Open(trip.id)) },
                        onLongClick = if (canCorrect) ({ menu = true }) else null,
                    )
                    .alpha(if (trip.kindLabel != null) DIMMED_ALPHA else 1f)
                    .padding(16.dp)
                    .clearAndSetSemantics {
                        contentDescription = rowDescription(trip)
                        if (canCorrect) customActions = listOf(CustomAccessibilityAction(whatWasThis) { menu = true; true })
                    },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = trip.start, style = MaterialTheme.typography.titleMediumEmphasized)
                Text(trip.summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    trip.quality?.let { QualityBadgeChip(it) } ?: DpStatusChip(label = stringResource(R.string.drives_recorded))
                    if (trip.interrupted) DpStatusChip(label = stringResource(R.string.drives_interrupted), attention = true)
                    if (trip.short) DpStatusChip(label = stringResource(R.string.drives_short))
                    trip.kindLabel?.let { DpStatusChip(label = it) }
                }
            }
            CorrectionMenu(expanded = menu, onDismiss = { menu = false }) { onAction(DrivesAction.Correct(trip.id, it)) }
        }
        val question = trip.question
        if (question != null && canCorrect) {
            QuestionBar(question, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                onAction(DrivesAction.Answer(trip.id, it))
            }
        }
    }
}

private fun rowDescription(trip: TripRowUi): String = listOfNotNull(
    trip.start,
    trip.summary,
    trip.kindLabel,
    "Interrupted".takeIf { trip.interrupted },
    "Short trip, not scored".takeIf { trip.short },
    trip.quality?.let { "${TripPresenter.badgeWord(it)} data" },
).joinToString(", ")

/** "What was this?" (S-TRP-02 type picker, F-CAP-04). */
@Composable
internal fun CorrectionMenu(expanded: Boolean, onDismiss: () -> Unit, onPick: (TripCorrection) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        TripCorrection.entries.forEach { correction ->
            DropdownMenuItem(
                text = { Text(TripPresenter.correctionTitle(correction)) },
                onClick = {
                    onDismiss()
                    onPick(correction)
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
            Text(TripPresenter.badgeWord(badge), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Preview
@Composable
private fun DrivesHomeScreenPreview() {
    DrivePerfTheme {
        DrivesHomeScreen(
            state = DrivesUiState(
                loaded = true,
                canCorrect = true,
                trips = listOf(
                    TripRowUi("a", "Today, 7:42 AM", "25 min · 12.4 km", QualityBadge.GOOD),
                    TripRowUi("b", "Yesterday, 5:10 PM", "18 min · 9.1 km", QualityBadge.USABLE, interrupted = true),
                    TripRowUi("c", "Mon, 8:02 AM", "31 min · 11.0 km", QualityBadge.LIMITED, question = Suspect.TRANSIT),
                ),
            ),
            onSeeAll = {},
        )
    }
}
