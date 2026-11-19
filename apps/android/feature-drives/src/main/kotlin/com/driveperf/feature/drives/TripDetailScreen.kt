package com.driveperf.feature.drives

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipScope
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpEstimated
import com.driveperf.designsystem.DpEventGlyph
import com.driveperf.designsystem.DpProvenance
import com.driveperf.designsystem.DpProvenanceGlyph
import com.driveperf.designsystem.DpQuality
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.designsystem.dpEstimated
import com.driveperf.domain.trip.Provenance
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.presentation.drives.BannerAction
import com.driveperf.presentation.drives.DrivesIntent
import com.driveperf.presentation.drives.DrivesState
import com.driveperf.presentation.drives.ExplainerSource
import com.driveperf.presentation.drives.ProvenancedValue
import com.driveperf.presentation.drives.TripBannerUi
import com.driveperf.presentation.drives.TripCopy
import com.driveperf.presentation.drives.TripDetailUi
import com.driveperf.presentation.drives.TripEventsUi
import com.driveperf.presentation.score.ScoreUi
import com.driveperf.presentation.score.TipUi
import kotlinx.coroutines.launch

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
            detail.events?.let { EventsSection(it) }
            Highlights(detail.cleanest, detail.tip)
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
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
                    Text(stringResource(R.string.score_smoothness), style = MaterialTheme.typography.labelLarge, color = DrivePerfTheme.extendedColors.text2)
                    if (!score.isScored) {
                        Text(stringResource(R.string.score_not_scored), style = MaterialTheme.typography.titleMedium)
                        score.notScoredReason?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                    score.comparison?.let { ComparisonChip(it) }
                    score.recordLine?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = DrivePerfTheme.extendedColors.record) }
                    score.basedOn?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = DrivePerfTheme.extendedColors.text2) }
                    score.paceNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = DrivePerfTheme.extendedColors.text2) }
                }
            }
            TextButton(onClick = { onAction(DrivesIntent.OpenHowScoresWork(ExplainerSource.TRIP_DETAIL)) }) {
                Text(stringResource(R.string.score_how_scores_work))
            }
            score.contributors.forEach { ContributorRow(it) }
            if (score.isScored || score.contributors.isNotEmpty()) {
                HorizontalDivider()
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onAction(DrivesIntent.OpenBreakdown(tripId)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.score_breakdown_link), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("›", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** "Events · n": counts by type, then the rows; "No abrupt inputs on this drive." without any. */
@Composable
internal fun EventsSection(events: TripEventsUi) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(events.title)
        events.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = DrivePerfTheme.extendedColors.text2); return@Column }
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
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
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
private fun Highlights(cleanest: String?, tip: TipUi?) {
    if (cleanest == null && tip == null) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.score_highlights))
        cleanest?.let {
            Surface(shape = MaterialTheme.shapes.large, color = cardColor, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.score_cleanest), style = MaterialTheme.typography.labelLarge, color = DrivePerfTheme.extendedColors.text2)
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

@Composable
private fun Banner(banner: TripBannerUi, tripId: String, onAction: (DrivesIntent) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (banner.attention) DrivePerfTheme.extendedColors.warningContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(banner.text, style = MaterialTheme.typography.bodyMedium)
            when (banner.action) {
                is BannerAction.Answer -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onAction(DrivesIntent.Answer(tripId, true)) }) { Text(stringResource(R.string.drives_yes)) }
                    Button(onClick = { onAction(DrivesIntent.Answer(tripId, false)) }) { Text(stringResource(R.string.drives_no)) }
                }
                BannerAction.Restore -> TextButton(onClick = { onAction(DrivesIntent.Correct(tripId, TripCorrection.DRIVER)) }) {
                    Text(stringResource(R.string.drives_restore))
                }
                null -> Unit
            }
        }
    }
}

@Composable
private fun CorrectionRow(current: TripCorrection, onPick: (TripCorrection) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable { open = true }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.drives_what_was_this), Modifier.weight(1f))
            Text(TripCopy.correctionTitle(current), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CorrectionMenu(expanded = open, onDismiss = { open = false }, onPick = onPick)
    }
}

/** One number tile (components.md §5) with its provenance glyph; long-press shows the provenance tooltip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatTile(value: ProvenancedValue, modifier: Modifier = Modifier) {
    val tooltip = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    val showHow = stringResource(R.string.drives_show_how_measured)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { ProvenanceTooltip(value) },
        state = tooltip,
    ) {
        Surface(
            modifier = modifier.clearAndSetSemantics {
                contentDescription = value.accessibilityLabel
                customActions = listOf(CustomAccessibilityAction(showHow) { scope.launch { tooltip.show() }; true })
            },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(value.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    DpProvenanceGlyph(value.provenance.dp(), MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.dpEstimated(value.estimated), verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (value.estimated) DpEstimated.text(value.value) else value.value,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (value.unit.isNotEmpty()) {
                        Text(
                            " ${value.unit}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                value.note?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The provenance tooltip (components.md §10.2, Android RichTooltip): glyph + word + legend line + metric line. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TooltipScope.ProvenanceTooltip(value: ProvenancedValue) {
    RichTooltip(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DpProvenanceGlyph(value.provenance.dp(), MaterialTheme.colorScheme.onSurface, size = 14.dp)
                Text(value.provenance.label)
            }
        },
    ) {
        Text(listOf(value.provenance.meaning, value.meaning).filter { it.isNotEmpty() }.joinToString("\n"))
    }
}

/** The padding a pushed Drives screen leaves under its last row. */
internal val ScreenPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)

internal fun QualityBadge.dp(): DpQuality = when (this) {
    QualityBadge.GOOD -> DpQuality.GOOD
    QualityBadge.USABLE -> DpQuality.USABLE
    QualityBadge.LIMITED -> DpQuality.LIMITED
    QualityBadge.UNSCORABLE -> DpQuality.UNSCORABLE
}

internal fun Provenance.dp(): DpProvenance = when (this) {
    Provenance.OBSERVED -> DpProvenance.OBSERVED
    Provenance.DERIVED -> DpProvenance.DERIVED
    Provenance.ESTIMATED -> DpProvenance.ESTIMATED
}
