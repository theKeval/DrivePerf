package com.driveperf.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The event pin (components.md §9.1): its shape carries the type, so colour is never the only signal. Geometry is
// DP_ICONS.pin in prototypes/shared/icons.js: a 28 × 28 box in a 30 × 30 view box (-1 … 29).

/** Detected event types, in `EventType` order. */
public enum class DpEvent { BRAKE, ACCEL, CORNER }

/** The event colour of [event] (`--dp-data-event-*`). */
@Composable
public fun dpEventColor(event: DpEvent): Color {
    val data = DrivePerfTheme.dataColors
    return when (event) {
        DpEvent.BRAKE -> data.eventBrake
        DpEvent.ACCEL -> data.eventAccel
        DpEvent.CORNER -> data.eventCorner
    }
}

/**
 * Hard braking = rounded square · hard acceleration = circle · hard cornering = diamond. [needed] (F-SCO-05) draws it
 * hollow: no fill, an event-colour outline and glyph. 28 dp on the map, 16 dp in chips, 14 dp on the timeline. Callers
 * say the type (and "marked as needed") in words: the glyph is decorative.
 */
@Composable
public fun DpEventGlyph(event: DpEvent, modifier: Modifier = Modifier, needed: Boolean = false, size: Dp = 28.dp) {
    val color = dpEventColor(event)
    val data = DrivePerfTheme.dataColors
    val surface = androidx.compose.material3.MaterialTheme.colorScheme.surface
    Canvas(modifier.size(size)) {
        val k = this.size.width / VIEW_BOX
        translate(k, k) {
            val fill = if (needed) surface else color
            val ring = if (needed) color else data.pinRing
            val glyph = if (needed) color else data.pinGlyph
            drawShape(event, k, fill, ring, if (needed) NEEDED_RING else RING)
            drawMark(event, k, glyph)
        }
    }
}

private const val VIEW_BOX = 30f
private const val RING = 1.8f
private const val NEEDED_RING = 2.2f

private fun DrawScope.drawShape(event: DpEvent, k: Float, fill: Color, ring: Color, width: Float) {
    val stroke = Stroke(width * k, join = StrokeJoin.Round)
    when (event) {
        DpEvent.BRAKE -> {
            val corner = CornerRadius(6.2f * k)
            drawRoundRect(fill, Offset(3f * k, 3f * k), Size(22f * k, 22f * k), corner)
            drawRoundRect(ring, Offset(3f * k, 3f * k), Size(22f * k, 22f * k), corner, style = stroke)
        }
        DpEvent.ACCEL -> {
            drawCircle(fill, 11.5f * k, Offset(14f * k, 14f * k))
            drawCircle(ring, 11.5f * k, Offset(14f * k, 14f * k), style = stroke)
        }
        DpEvent.CORNER -> {
            val diamond = Path().apply {
                moveTo(14f * k, 1.6f * k)
                lineTo(24.6f * k, 12.2f * k)
                quadraticTo(25.5f * k, 14f * k, 24.6f * k, 15.8f * k)
                lineTo(14f * k, 26.4f * k)
                lineTo(3.4f * k, 15.8f * k)
                quadraticTo(2.5f * k, 14f * k, 3.4f * k, 12.2f * k)
                close()
            }
            drawPath(diamond, fill)
            drawPath(diamond, ring, style = stroke)
        }
    }
}

private fun DrawScope.drawMark(event: DpEvent, k: Float, color: Color) {
    val stroke = Stroke(2.4f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val mark = when (event) {
        DpEvent.BRAKE -> Path().apply {
            moveTo(9f * k, 10.2f * k); lineTo(14f * k, 14.8f * k); lineTo(19f * k, 10.2f * k)
            moveTo(9f * k, 18.6f * k); lineTo(19f * k, 18.6f * k)
        }
        DpEvent.ACCEL -> Path().apply {
            moveTo(9f * k, 15.4f * k); lineTo(14f * k, 10.8f * k); lineTo(19f * k, 15.4f * k)
            moveTo(9f * k, 9f * k); lineTo(19f * k, 9f * k)
        }
        DpEvent.CORNER -> Path().apply {
            moveTo(9.5f * k, 18.5f * k)
            cubicTo(9.5f * k, 13.5f * k, 12.1f * k, 10f * k, 17f * k, 10f * k)
            moveTo(14.5f * k, 7.4f * k); lineTo(17.4f * k, 10f * k); lineTo(14.5f * k, 12.6f * k)
        }
    }
    drawPath(mark, color, style = stroke)
}
