package com.driveperf.feature.replay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.domain.replay.EventEvidence
import kotlin.math.abs

internal const val EVIDENCE_CHART_TAG = "evidence_chart"

/** The lanes' share of the chart: speed on top, G below. */
private const val SPEED_SHARE = 0.38f
private const val LANE_GAP = 0.06f
private const val MIN_G_RANGE = 0.3

/**
 * The S-RPL-02 evidence trace (F-RPL-04 AC1): speed (neutral, top lane) and longitudinal (solid) and lateral (dashed)
 * G in one lane, from 10 s before to 10 s after the peak, with start, peak and end guides. Patterns, not colour, tell
 * the G lines apart. [speedPerMps] converts to the user's unit. [summary] is what TalkBack reads for the whole chart.
 */
@Composable
internal fun EvidenceChart(evidence: EventEvidence, speedPerMps: Double, summary: String, modifier: Modifier = Modifier) {
    val data = DrivePerfTheme.dataColors
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.clearAndSetSemantics { contentDescription = summary }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Spacer(
            Modifier.fillMaxWidth().height(118.dp).testTag(EVIDENCE_CHART_TAG).drawWithCache {
                val t0 = evidence.tPeakS - (evidence.tPeakS - evidence.t0S).coerceAtLeast(evidence.t1S - evidence.tPeakS)
                val t1 = evidence.tPeakS + (evidence.t1S - evidence.tPeakS).coerceAtLeast(evidence.tPeakS - evidence.t0S)
                val span = (t1 - t0).coerceAtLeast(1.0)
                fun x(t: Double) = ((t - t0) / span * size.width).toFloat()
                val speedBottom = size.height * SPEED_SHARE
                val gTop = size.height * (SPEED_SHARE + LANE_GAP)
                val gMid = (gTop + size.height) / 2
                val gHalf = (size.height - gTop) / 2
                val gRange = maxOf(evidence.maxAbsG, MIN_G_RANGE)
                fun gy(g: Double) = (gMid - g / gRange * gHalf).toFloat()
                val vMax = (evidence.maxSpeedMps * speedPerMps).coerceAtLeast(1.0)
                fun sy(mps: Double) = (speedBottom - mps * speedPerMps / vMax * speedBottom * 0.9).toFloat()
                val speed = polyline(evidence.speed.map { Offset(x(it.tS), sy(it.speedMps)) })
                val lon = polyline(evidence.g.map { Offset(x(it.tS), gy(it.lonG)) })
                val lat = polyline(evidence.g.map { Offset(x(it.tS), gy(it.latG)) })
                val line = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
                val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 3.dp.toPx()))
                val dashed = Stroke(2.dp.toPx(), cap = StrokeCap.Butt, pathEffect = dash)
                val guide = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))
                val thin = 1.dp.toPx()
                onDrawBehind {
                    drawLine(grid, Offset(0f, gMid), Offset(size.width, gMid), thin)
                    for (t in listOf(evidence.tStartS, evidence.tEndS)) {
                        drawLine(grid, Offset(x(t), 0f), Offset(x(t), size.height), thin, pathEffect = guide)
                    }
                    drawLine(data.cursor, Offset(x(evidence.tPeakS), 0f), Offset(x(evidence.tPeakS), size.height), thin * 1.5f)
                    drawPath(speed, data.speed, style = line)
                    drawPath(lat, data.latG, style = dashed)
                    drawPath(lon, data.longG, style = line)
                }
            },
        )
        Row(Modifier.fillMaxWidth()) {
            AxisLabel(stringResource(R.string.evidence_axis_before), Modifier.weight(1f))
            AxisLabel(stringResource(R.string.evidence_axis_peak))
            Spacer(Modifier.weight(1f))
            AxisLabel(stringResource(R.string.evidence_axis_after))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendSwatch(data.speed, dashed = false, stringResource(R.string.evidence_legend_speed))
            LegendSwatch(data.longG, dashed = false, stringResource(R.string.evidence_legend_long_g))
            LegendSwatch(data.latG, dashed = true, stringResource(R.string.evidence_legend_lat_g))
        }
    }
}

/** A path through [points] in order. */
private fun polyline(points: List<Offset>): Path = Path().apply {
    points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
}

@Composable
private fun AxisLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun LegendSwatch(color: Color, dashed: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Spacer(
            Modifier.width(18.dp).height(8.dp).drawWithCache {
                val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 2.dp.toPx())) else null
                onDrawBehind {
                    drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(), pathEffect = effect)
                }
            },
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The longitudinal-G sparkline on S-SCO-04 (components.md §18): 48 dp, zero line, peak marked. */
@Composable
internal fun Sparkline(values: List<Double>, peakIndex: Int, modifier: Modifier = Modifier) {
    val data = DrivePerfTheme.dataColors
    val grid = MaterialTheme.colorScheme.outlineVariant
    Spacer(
        modifier.fillMaxWidth().height(48.dp).drawWithCache {
            val range = maxOf(values.maxOfOrNull { abs(it) } ?: 0.0, MIN_G_RANGE)
            val step = size.width / (values.size - 1).coerceAtLeast(1)
            fun y(v: Double) = (size.height / 2 - v / range * size.height / 2 * 0.9).toFloat()
            val path = Path().apply { values.forEachIndexed { i, v -> if (i == 0) moveTo(0f, y(v)) else lineTo(i * step, y(v)) } }
            val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
            onDrawBehind {
                drawLine(grid, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
                drawPath(path, data.longG, style = stroke)
                values.getOrNull(peakIndex)?.let { drawCircle(data.longG, 3.5.dp.toPx(), Offset(peakIndex * step, y(it))) }
            }
        },
    )
}

