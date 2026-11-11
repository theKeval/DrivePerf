package com.driveperf.feature.drives

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipScope
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpEstimated
import com.driveperf.designsystem.DpProvenance
import com.driveperf.designsystem.DpProvenanceGlyph
import com.driveperf.designsystem.DpQuality
import com.driveperf.designsystem.DpQualityGlyph
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.designsystem.dpEstimated
import com.driveperf.designsystem.dpQualityColor
import com.driveperf.domain.trip.OverlayStyle
import com.driveperf.domain.trip.Provenance
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.TripCorrection
import kotlinx.coroutines.launch

private const val HATCH_STEP_DP = 5f

/**
 * S-DQ-01 Data-quality detail sheet (F-DQ-04 AC2): full height, header, one honest sentence, the estimated start,
 * the coverage strip, the details with provenance, and the provenance legend.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QualitySheet(sheet: QualitySheetUi, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = fullHeightSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        QualitySheetContent(sheet)
    }
}

@Composable
internal fun QualitySheetContent(sheet: QualitySheetUi) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DpQualityGlyph(sheet.badge.dp(), size = 24.dp, ink = MaterialTheme.colorScheme.surface)
            Text(sheet.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = CircleShape,
                        color = dpQualityColor(sheet.badge.dp()).copy(alpha = 0.14f),
                        modifier = Modifier.size(56.dp),
                    ) {}
                    DpQualityGlyph(sheet.badge.dp(), size = 34.dp, ink = MaterialTheme.colorScheme.surface)
                }
                Text(sheet.hero, style = MaterialTheme.typography.bodyLarge)
            }
        }
        sheet.estimatedStart?.let {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
            }
        }
        Section(stringResource(R.string.dq_this_drive)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CoverageStrip(
                    sheet.coverage,
                    Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = sheet.coverageDescription },
                )
                CoverageLegend()
                Text(sheet.scoredLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Section(stringResource(R.string.dq_details)) {
            Column {
                sheet.rows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider()
                    DetailRow(row)
                }
            }
        }
        Section(stringResource(R.string.dq_labels)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                sheet.legend.forEach { p ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        DpProvenanceGlyph(p.dp(), MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${p.label} · ${p.meaning}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) { content() }
    }
}

/** Title, then value with its provenance glyph and word; warn tone when it lowered the badge. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailRow(row: ProvenancedValue) {
    val tooltip = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    val showHow = stringResource(R.string.drives_show_how_measured)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { ProvenanceTooltip(row) },
        state = tooltip,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clearAndSetSemantics {
                    contentDescription = row.contentDescription + (row.note?.let { " $it." } ?: "")
                    customActions = listOf(CustomAccessibilityAction(showHow) { scope.launch { tooltip.show() }; true })
                },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.label, style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        listOf(if (row.estimated) DpEstimated.text(row.value) else row.value, row.unit)
                            .filter { it.isNotEmpty() }
                            .joinToString(" "),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (row.lowersBadge) DrivePerfTheme.extendedColors.warning else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.dpEstimated(row.estimated),
                    )
                    DpProvenanceGlyph(row.provenance.dp(), MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        row.provenance.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            row.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/**
 * The S-DQ-01 coverage strip (14 dp, fully rounded): measured solid, estimated dashed at 60 %, GPS gaps and
 * interruptions outlined, left-out time hatched. Patterns carry the meaning, not colour alone.
 */
@Composable
internal fun CoverageStrip(spans: List<CoverageSpanUi>, modifier: Modifier = Modifier) {
    val measured = DrivePerfTheme.dataColors.route
    val gap = DrivePerfTheme.dataColors.gapStroke
    val hatch = DrivePerfTheme.dataColors.excludedHatch
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier.height(14.dp)) {
        val h = size.height
        drawRoundRect(measured, cornerRadius = CornerRadius(h / 2, h / 2))
        spans.forEach { span ->
            val x0 = span.from * size.width
            val w = (span.to - span.from) * size.width
            when (span.style) {
                OverlayStyle.ESTIMATED_START, OverlayStyle.SHORT_GAP -> {
                    drawRect(empty, Offset(x0, 0f), Size(w, h))
                    drawLine(
                        measured.copy(alpha = DpEstimated.ALPHA), Offset(x0, h / 2), Offset(x0 + w, h / 2), h / 2,
                        pathEffect = DpEstimated.dash(density),
                    )
                }
                OverlayStyle.LONG_GAP, OverlayStyle.INTERRUPTED, OverlayStyle.PAUSED -> {
                    drawRect(empty, Offset(x0, 0f), Size(w, h))
                    drawRect(gap, Offset(x0, 0f), Size(w, h), style = Stroke(1.5f * density))
                }
                OverlayStyle.EXCLUDED -> {
                    drawRect(empty, Offset(x0, 0f), Size(w, h))
                    clipRect(x0, 0f, x0 + w, h) {
                        var x = x0 - h
                        while (x < x0 + w) {
                            drawLine(hatch, Offset(x, h), Offset(x + h, 0f), density)
                            x += HATCH_STEP_DP * density
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverageLegend() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.clearAndSetSemantics { }) {
        listOf(
            null to R.string.dq_legend_measured,
            OverlayStyle.ESTIMATED_START to R.string.dq_legend_estimated,
            OverlayStyle.LONG_GAP to R.string.dq_legend_no_gps,
            OverlayStyle.EXCLUDED to R.string.dq_legend_left_out,
        ).forEach { (style, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CoverageStrip(style?.let { listOf(CoverageSpanUi(it, 0f, 1f)) }.orEmpty(), Modifier.width(22.dp))
                Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Preview
@Composable
private fun QualitySheetPreview() {
    DrivePerfTheme {
        Surface(color = Color.Unspecified) {
            QualitySheetContent(
                QualitySheetUi(
                    badge = QualityBadge.USABLE,
                    title = "Usable data",
                    hero = "Mostly solid, with 41 s without GPS. The score is fair; that stretch is drawn dotted.",
                    estimatedStart = null,
                    coverage = listOf(
                        CoverageSpanUi(OverlayStyle.ESTIMATED_START, 0f, 0.03f),
                        CoverageSpanUi(OverlayStyle.SHORT_GAP, 0.6f, 0.62f),
                    ),
                    scoredLine = "Scored 33 of 33 min",
                    coverageDescription = "Drive coverage, 33 minutes.",
                    rows = listOf(ProvenancedValue("gnssCoverage", "GPS coverage", "97", "%", Provenance.DERIVED, "")),
                    legend = Provenance.entries,
                ),
            )
        }
    }
}
