package com.driveperf.feature.drives

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpEventGlyph
import com.driveperf.designsystem.DpProvenanceGlyph
import com.driveperf.designsystem.DpQualityGlyph
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.domain.score.ContributorId
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.presentation.drives.DrivesIntent
import com.driveperf.presentation.drives.DrivesState
import com.driveperf.presentation.drives.ExplainerSource
import com.driveperf.presentation.drives.TripCopy
import com.driveperf.presentation.score.BreakdownUi
import com.driveperf.presentation.score.ContributorUi
import com.driveperf.presentation.score.ExplainerSectionKind
import com.driveperf.presentation.score.ExplainerSectionUi
import com.driveperf.presentation.score.HowScoresWorkUi
import com.driveperf.presentation.score.ScoreUi

/**
 * S-SCO-01 Smoothness breakdown (DRIVE-87, DRIVE-82): the hero ring with its comparison and basis, "Compared with",
 * the four contributors (one with events opens its event rows), the route's last runs and the links. The destination
 * sends [DrivesIntent.ShowBreakdown] for [tripId].
 */
@Composable
fun ScoreBreakdownScreen(
    state: DrivesState,
    tripId: String,
    modifier: Modifier = Modifier,
    onAction: (DrivesIntent) -> Unit = {},
) {
    val detail = state.detail?.takeIf { it.id == tripId }
    val score = detail?.score
    val breakdown = detail?.breakdown
    if (score == null || breakdown == null) return
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(detail.subtitle, style = MaterialTheme.typography.bodyMedium, color = DrivePerfTheme.extendedColors.text2)
        Hero(score, breakdown)
        if (score.isScored) {
            if (breakdown.options.isNotEmpty()) ComparedWith(breakdown)
            WhatMovedIt(score.contributors, breakdown.contributorsFooter, { onAction(DrivesIntent.TapContributor(it)) }) {
                onAction(DrivesIntent.OpenEvidence(tripId, it))
            }
            Runs(breakdown)
        } else {
            breakdown.notScoredFooter?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = DrivePerfTheme.extendedColors.text2) }
        }
        TextButton(onClick = { onAction(DrivesIntent.OpenHowScoresWork(ExplainerSource.BREAKDOWN)) }) {
            Text(stringResource(R.string.score_how_scores_work))
        }
        if (score.isScored) {
            Column {
                Text(stringResource(R.string.score_needed_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.score_needed_body), style = MaterialTheme.typography.bodyMedium,
                    color = DrivePerfTheme.extendedColors.text2)
            }
        }
    }
}

@Composable
private fun Hero(score: ScoreUi, breakdown: BreakdownUi) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = cardColor, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(20.dp).clearAndSetSemantics { contentDescription = score.accessibilityLabel },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScoreRing(score.smoothness, score.baseline, 168.dp, label = stringResource(R.string.score_smoothness))
            if (!score.isScored) Text(stringResource(R.string.score_not_scored), style = MaterialTheme.typography.titleMedium)
            score.comparison?.let { ComparisonChip(it) }
            score.recordLine?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = DrivePerfTheme.extendedColors.record) }
            score.paceNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = DrivePerfTheme.extendedColors.text2) }
            val line = if (score.isScored) breakdown.basisLine else breakdown.notScoredText.orEmpty()
            if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** "Compared with": the route's runs or the usual drives of the same kind, the used one selected. */
@Composable
private fun ComparedWith(breakdown: BreakdownUi) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.score_compared_with))
        Surface(shape = MaterialTheme.shapes.large, color = cardColor, modifier = Modifier.fillMaxWidth()) {
            Column {
                breakdown.options.forEachIndexed { index, option ->
                    if (index > 0) HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp)
                            .selectable(selected = option.selected, enabled = false, role = Role.RadioButton, onClick = {}),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option.selected, onClick = null, enabled = option.selected)
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(option.title, style = MaterialTheme.typography.titleSmall)
                            Text(option.detail, style = MaterialTheme.typography.bodySmall, color = DrivePerfTheme.extendedColors.text2)
                        }
                        option.value?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
        }
        Text(breakdown.optionsFooter, style = MaterialTheme.typography.bodySmall, color = DrivePerfTheme.extendedColors.text2)
    }
}

/**
 * "What moved it": the four contributors; one with events expands to its event rows, and each row opens its evidence
 * ([onEvent], DRIVE-87 AC2).
 */
@Composable
private fun WhatMovedIt(contributors: List<ContributorUi>, footer: String, onTap: (ContributorId) -> Unit, onEvent: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(contributors.firstOrNull { it.events.isNotEmpty() }?.id?.name) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.score_what_moved_it))
        Surface(shape = MaterialTheme.shapes.large, color = cardColor, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                ContributorList(contributors, open, onToggle = { id, opening ->
                    open = if (opening) id.name else null
                    if (opening) onTap(id)
                }, onEvent = onEvent)
            }
        }
        Text(footer, style = MaterialTheme.typography.bodySmall, color = DrivePerfTheme.extendedColors.text2)
    }
}

/** "Your last n runs of {route}": an area sparkline ending at this drive. */
@Composable
private fun Runs(breakdown: BreakdownUi) {
    val title = breakdown.runsTitle ?: return
    val runs = breakdown.runs
    val line = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        Surface(shape = MaterialTheme.shapes.large, color = cardColor, modifier = Modifier.fillMaxWidth()) {
            Canvas(
                Modifier.fillMaxWidth().height(64.dp).padding(16.dp)
                    .clearAndSetSemantics { contentDescription = breakdown.runsSummary.orEmpty() },
            ) {
                val min = (runs.min() - SPARK_PAD).toFloat()
                val max = (runs.max() + SPARK_PAD).toFloat()
                val step = size.width / (runs.size - 1).coerceAtLeast(1)
                val points = runs.mapIndexed { i, v -> Offset(i * step, size.height * (1 - (v - min) / (max - min))) }
                val path = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                drawPath(path, line, style = Stroke(2.dp.toPx()))
                drawCircle(line, 4.dp.toPx(), points.last())
            }
        }
    }
}

private const val SPARK_PAD = 3

/**
 * S-SCO-02 How scores work (DRIVE-113, DRIVE-108): the example ring, then each section in order (events with their
 * thresholds, the quality badges, every provenance), and the scoring version with its date.
 */
@Composable
fun HowScoresWorkScreen(explainer: HowScoresWorkUi, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = cardColor, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(explainer.exampleScore, explainer.exampleBaseline, 120.dp,
                    Modifier.clearAndSetSemantics { contentDescription = explainer.exampleAccessibility })
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(explainer.heroTitle, style = MaterialTheme.typography.titleMedium)
                    Text(explainer.heroBody, style = MaterialTheme.typography.bodyMedium)
                    Text(explainer.notchKey, style = MaterialTheme.typography.bodySmall, color = DrivePerfTheme.extendedColors.text2)
                }
            }
        }
        explainer.sections.forEach { ExplainerSection(it, explainer) }
        Text(explainer.versionLine, style = MaterialTheme.typography.bodySmall, color = DrivePerfTheme.extendedColors.text3)
    }
}

@Composable
private fun ExplainerSection(section: ExplainerSectionUi, explainer: HowScoresWorkUi) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(section.title)
        Surface(shape = MaterialTheme.shapes.large, color = cardColor, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                section.paragraphs.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                when (section.kind) {
                    ExplainerSectionKind.EVENTS -> explainer.thresholds.forEach { threshold ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            DpEventGlyph(threshold.type.dp(), size = 28.dp)
                            Column {
                                Text(threshold.label, style = MaterialTheme.typography.titleSmall)
                                Text(threshold.text, style = MaterialTheme.typography.bodySmall,
                                    color = DrivePerfTheme.extendedColors.text2)
                            }
                        }
                    }
                    ExplainerSectionKind.QUALITY -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        QualityBadge.entries.forEach { badge ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                DpQualityGlyph(badge.dp(), size = 18.dp, ink = MaterialTheme.colorScheme.surface)
                                Text(TripCopy.badgeWord(badge), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    ExplainerSectionKind.PROVENANCE -> explainer.provenance.forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            DpProvenanceGlyph(line.provenance.dp(), MaterialTheme.colorScheme.onSurface, size = 18.dp)
                            Column {
                                Text(line.label, style = MaterialTheme.typography.titleSmall)
                                Text(line.meaning, style = MaterialTheme.typography.bodySmall, color = DrivePerfTheme.extendedColors.text2)
                            }
                        }
                    }
                    ExplainerSectionKind.TEXT -> Unit
                }
                section.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = DrivePerfTheme.extendedColors.text2) }
            }
        }
    }
}
