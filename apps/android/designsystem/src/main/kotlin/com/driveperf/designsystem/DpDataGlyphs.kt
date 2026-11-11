package com.driveperf.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Meaning-carrying glyphs (components.md §10): fixed shapes on both platforms, never swapped for Material Symbols.
// Geometry is DP_ICONS.quality (16 × 16) and DP_ICONS.provenance (12 × 12) in prototypes/shared/icons.js.

/** Data-quality badges, in badge order. */
public enum class DpQuality { GOOD, USABLE, LIMITED, UNSCORABLE }

/** Provenance kinds (F-DQ-05). */
public enum class DpProvenance { OBSERVED, DERIVED, ESTIMATED }

/** The quality colour of [quality] (`--dp-data-quality-*`). */
@Composable
public fun dpQualityColor(quality: DpQuality): Color {
    val data = DrivePerfTheme.dataColors
    return when (quality) {
        DpQuality.GOOD -> data.qualityGood
        DpQuality.USABLE -> data.qualityUsable
        DpQuality.LIMITED -> data.qualityLimited
        DpQuality.UNSCORABLE -> data.qualityUnscorable
    }
}

/** Good = filled disc + check · Usable = half disc · Limited = triangle "!" · Unscorable = slashed ring. */
@Composable
public fun DpQualityGlyph(quality: DpQuality, modifier: Modifier = Modifier, size: Dp = 16.dp, ink: Color = Color.Black) {
    val color = dpQualityColor(quality)
    Canvas(modifier.size(size)) {
        val k = this.size.width / 16f
        when (quality) {
            DpQuality.GOOD -> {
                drawCircle(color, 7f * k, center)
                val check = Path().apply { moveTo(4.8f * k, 8.2f * k); lineTo(6.9f * k, 10.3f * k); lineTo(11.2f * k, 5.9f * k) }
                drawPath(check, ink, style = Stroke(1.8f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            DpQuality.USABLE -> {
                drawCircle(color, 6.2f * k, center, style = Stroke(1.6f * k))
                drawArc(color, -90f, 180f, true, Offset(1.8f * k, 1.8f * k), Size(12.4f * k, 12.4f * k), style = Fill)
            }
            DpQuality.LIMITED -> {
                val tri = Path().apply { moveTo(8f * k, 1.6f * k); lineTo(14.8f * k, 13.6f * k); lineTo(1.2f * k, 13.6f * k); close() }
                drawPath(tri, color)
                drawPath(tri, color, style = Stroke(1.2f * k, join = StrokeJoin.Round))
                drawLine(ink, Offset(8f * k, 6f * k), Offset(8f * k, 9.6f * k), 1.7f * k, StrokeCap.Round)
                drawCircle(ink, 1f * k, Offset(8f * k, 11.6f * k))
            }
            DpQuality.UNSCORABLE -> {
                drawCircle(color, 6.2f * k, center, style = Stroke(1.6f * k))
                drawLine(color, Offset(3.6f * k, 12.4f * k), Offset(12.4f * k, 3.6f * k), 1.6f * k, StrokeCap.Round)
            }
        }
    }
}

/** Observed = filled dot · Derived = ring with dot · Estimated = dashed ring. */
@Composable
public fun DpProvenanceGlyph(provenance: DpProvenance, color: Color, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    Canvas(modifier.size(size)) { drawProvenance(provenance, color) }
}

private fun DrawScope.drawProvenance(provenance: DpProvenance, color: Color) {
    val k = size.width / 12f
    when (provenance) {
        DpProvenance.OBSERVED -> drawCircle(color, 4f * k, center)
        DpProvenance.DERIVED -> {
            drawCircle(color, 4.2f * k, center, style = Stroke(1.4f * k))
            drawCircle(color, 1.6f * k, center)
        }
        DpProvenance.ESTIMATED -> drawCircle(
            color, 4.2f * k, center,
            style = Stroke(1.4f * k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * k, 1.8f * k))),
        )
    }
}
