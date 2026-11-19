package com.driveperf.feature.drives

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.presentation.drives.DrivesIntent
import com.driveperf.presentation.drives.DrivesState
import com.driveperf.presentation.drives.ExplainerSource
import com.driveperf.presentation.drives.TripDetailUi
import com.driveperf.presentation.score.ScoreUi

/** The data-quality sheet over trip detail, when the state opens it. */
@Composable
internal fun DrivesSheets(state: DrivesState, onAction: (DrivesIntent) -> Unit) {
    state.quality?.let { QualitySheet(it, onDismiss = { onAction(DrivesIntent.CloseQuality) }) }
}

/**
 * S-TRP-01 Trip detail, a push screen (DRIVE-55, coordinator decision 1): title, state banners, the meta row, Replay
 * and Analysis, the Smoothness card with its comparison and contributors (hidden on passenger and not-a-drive trips),
 * stat tiles with provenance on long-press (F-DQ-05), events by type, the positive card and the tip, then data quality
 * and "What was this?". Story, Share and Export wait for their epics; the Pro lock is deferred.
 *
 * The destination sends [DrivesIntent.Open] for [tripId]; until the trip is listed this shows nothing.
 */
@Composable
fun TripDetailScreen(
    state: DrivesState,
    tripId: String,
    modifier: Modifier = Modifier,
    onAction: (DrivesIntent) -> Unit = {},
) {
    val detail = state.detail?.takeIf { it.id == tripId }
    if (detail == null) {
        if (state.loaded && state.trips.none { it.id == tripId } && state.notDrives.none { it.id == tripId }) {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.trip_deleted), color = DrivePerfTheme.extendedColors.text2)
            }
        }
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Title(detail)
        detail.banners.forEach { Banner(it, detail.id, onAction) }
        detail.map?.let { TripMapHero(it, detail.quality) { onAction(DrivesIntent.OpenReplay(detail.id)) } }
        MetaRow(detail)
        Actions(detail, onAction)
        if (!detail.scoresHidden) detail.score?.let { ScoreCard(it, detail.id, onAction) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            detail.tiles.forEach { StatTile(it, Modifier.width(104.dp)) }
        }
        Text(
            stringResource(R.string.drives_provenance_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!detail.scoresHidden) {
            detail.events?.let { EventsSection(it) { id -> onAction(DrivesIntent.OpenEvidence(detail.id, id)) } }
            Highlights(
                detail.cleanest,
                detail.tip,
                onTip = { onAction(DrivesIntent.OpenTip(detail.id)) },
                onTipEvidence = { onAction(DrivesIntent.OpenTipEvidence(detail.id)) },
            )
        }
        DetailsList(detail, state.canCorrect, onAction)
    }
    DrivesSheets(state, onAction)
}

/** "The Commute" over "Thursday morning · 5 Nov". */
@Composable
private fun Title(detail: TripDetailUi) {
    Column {
        Text(detail.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(detail.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Car chip (or "Passenger") · how it started · start–end time. */
@Composable
private fun MetaRow(detail: TripDetailUi) {
    val meta = detail.meta ?: return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically) {
        val car = if (meta.passenger) stringResource(R.string.drives_passenger) else meta.car
        car?.let {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(it, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
        Text("${meta.source} · ${meta.timeRange}", style = MaterialTheme.typography.bodyMedium, color = DrivePerfTheme.extendedColors.text2)
    }
}

/** S-TRP-01 action row: Replay (filled primary, radius 28) and Analysis (tonal). */
@Composable
private fun Actions(detail: TripDetailUi, onAction: (DrivesIntent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { onAction(DrivesIntent.OpenReplay(detail.id)) },
            modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag(REPLAY_BUTTON_TAG),
        ) {
            Icon(painterResource(R.drawable.ic_drives_replay), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.drives_replay), modifier = Modifier.padding(start = 8.dp))
        }
        FilledTonalButton(
            onClick = { onAction(DrivesIntent.OpenAnalysis(detail.id)) },
            enabled = detail.canAnalyse,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag(ANALYSIS_BUTTON_TAG),
        ) {
            Text(stringResource(R.string.drives_analysis))
        }
    }
}

/** Test tag of trip detail's Replay button. */
const val REPLAY_BUTTON_TAG: String = "trip_detail_replay"

/** Test tag of trip detail's Analysis button. */
const val ANALYSIS_BUTTON_TAG: String = "trip_detail_analysis"

/** The Smoothness card: ring 124 + comparison chip and lines, the four contributors, "Smoothness breakdown ›". */
@Composable
private fun ScoreCard(score: ScoreUi, tripId: String, onAction: (DrivesIntent) -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = cardColor, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.clearAndSetSemantics { contentDescription = score.accessibilityLabel },
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScoreRing(score.smoothness, score.baseline, 124.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.score_smoothness), style = MaterialTheme.typography.labelLarge,
                        color = DrivePerfTheme.extendedColors.text2)
                    if (!score.isScored) {
                        Text(stringResource(R.string.score_not_scored), style = MaterialTheme.typography.titleMedium)
                        score.notScoredReason?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                    score.comparison?.let { ComparisonChip(it) }
                    score.recordLine?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
                        color = DrivePerfTheme.extendedColors.record) }
                    score.basedOn?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = DrivePerfTheme.extendedColors.text2) }
                    score.paceNote?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                        color = DrivePerfTheme.extendedColors.text2) }
                }
            }
            TextButton(onClick = { onAction(DrivesIntent.OpenHowScoresWork(ExplainerSource.TRIP_DETAIL)) }) {
                Text(stringResource(R.string.score_how_scores_work))
            }
            TripContributors(score.contributors, onOpen = { onAction(DrivesIntent.TapContributor(it)) }) {
                onAction(DrivesIntent.OpenEvidence(tripId, it))
            }
            if (score.isScored || score.contributors.isNotEmpty()) {
                HorizontalDivider()
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onAction(DrivesIntent.OpenBreakdown(tripId)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.score_breakdown_link), style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f))
                Text("›", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Data quality (→ S-DQ-01) and "What was this?" (S-TRP-02 type picker). */
@Composable
private fun DetailsList(detail: TripDetailUi, canCorrect: Boolean, onAction: (DrivesIntent) -> Unit) {
    if (detail.quality == null && !canCorrect) return
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            detail.quality?.let { badge ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable { onAction(DrivesIntent.OpenQuality(detail.id)) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.drives_data_quality), Modifier.weight(1f))
                    QualityBadgeChip(badge)
                }
            }
            if (canCorrect) {
                if (detail.quality != null) HorizontalDivider()
                CorrectionRow(detail.correction) { onAction(DrivesIntent.Correct(detail.id, it)) }
            }
        }
    }
}
