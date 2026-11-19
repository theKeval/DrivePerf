package com.driveperf.feature.analysis

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import com.driveperf.designsystem.DpEvent
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.designsystem.dpEventColor
import com.driveperf.domain.analysis.AnalysisChannel
import com.driveperf.domain.analysis.LanePath
import com.driveperf.domain.trip.EventType
import com.driveperf.presentation.analysis.LegendKey

/** The colours one Analysis screen draws with, read once per composition from the theme. */
@Suppress("LongParameterList") // one per data colour token the screen draws with
@Immutable
internal class AnalysisColors(
    val speed: Color,
    val longG: Color,
    val latG: Color,
    val elevation: Color,
    val elevationFill: Color,
    val jerk: Color,
    val hatch: Color,
    val gap: Color,
    val cursor: Color,
    val grid: Color,
    val axis: Color,
    val cloud: Color,
    val envelope: Color,
    val dot: Color,
    val ring: Color,
    val route: Color,
    val casing: Color,
    val faded: Color,
    val marker: Color,
    val land: Color,
    val good: Color,
    val brake: Color,
    val accel: Color,
    val corner: Color,
    val surface: Color,
) {
    fun line(channel: AnalysisChannel): Color = when (channel) {
        AnalysisChannel.SPEED -> speed
        AnalysisChannel.LON_G -> longG
        AnalysisChannel.LAT_G -> latG
        AnalysisChannel.ELEVATION -> elevation
        AnalysisChannel.JERK -> jerk
        AnalysisChannel.QUALITY -> good
    }

    fun legend(key: LegendKey): Color = when (key) {
        LegendKey.SPEED -> speed
        LegendKey.LON_G -> longG
        LegendKey.LAT_G -> latG
        LegendKey.ELEVATION -> elevation
        LegendKey.JERK -> jerk
        LegendKey.ESTIMATED -> speed.copy(alpha = ESTIMATED_ALPHA)
        LegendKey.NO_GPS -> gap
        LegendKey.NOT_SCORED -> hatch
    }

    fun event(type: EventType): Color = when (type) {
        EventType.BRAKE -> brake
        EventType.ACCEL -> accel
        EventType.CORNER -> corner
    }

    companion object {
        const val ESTIMATED_ALPHA = 0.6f
    }
}

@Composable
internal fun analysisColors(): AnalysisColors {
    val d = DrivePerfTheme.dataColors
    return AnalysisColors(
        speed = d.speed, longG = d.longG, latG = d.latG, elevation = d.elevation, elevationFill = d.elevationFill,
        jerk = d.jerk, hatch = d.excludedHatch, gap = d.gapStroke, cursor = d.cursor, grid = d.grid, axis = d.axis,
        cloud = d.ggCloud, envelope = d.ggEnvelope, dot = d.ggDot, ring = d.ggRing, route = d.route, casing = d.routeCasing,
        faded = d.routeFaded, marker = d.marker, land = d.mapLand, good = d.qualityGood,
        brake = dpEventColor(DpEvent.BRAKE), accel = dpEventColor(DpEvent.ACCEL), corner = dpEventColor(DpEvent.CORNER),
        surface = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
    )
}

/** [path] in [box]: x across, y up. `NaN` breaks the line. */
internal fun LanePath.toPath(box: Rect): Path {
    val p = Path()
    var pen = false
    for (i in 0 until size) {
        val y = ys[i]
        if (y.isNaN()) {
            pen = false
            continue
        }
        val o = Offset(box.left + (xs[i] * box.width).toFloat(), box.bottom - (y * box.height).toFloat())
        if (pen) p.lineTo(o.x, o.y) else p.moveTo(o.x, o.y)
        pen = true
    }
    return p
}

/** The area under [path] down to the lane's floor, for elevation. Gaps close their own pieces. */
internal fun LanePath.toArea(box: Rect): Path {
    val p = Path()
    var startX = Float.NaN
    var lastX = 0f
    fun close() {
        if (!startX.isNaN()) {
            p.lineTo(lastX, box.bottom)
            p.lineTo(startX, box.bottom)
            p.close()
        }
        startX = Float.NaN
    }
    for (i in 0 until size) {
        val y = ys[i]
        if (y.isNaN()) {
            close()
            continue
        }
        val x = box.left + (xs[i] * box.width).toFloat()
        val yy = box.bottom - (y * box.height).toFloat()
        if (startX.isNaN()) {
            p.moveTo(x, box.bottom)
            p.lineTo(x, yy)
            startX = x
        } else {
            p.lineTo(x, yy)
        }
        lastX = x
    }
    close()
    return p
}

/** 45° hatching over [rect] (excluded spans: "Phone moved — not scored"). */
internal fun DrawScope.hatch(rect: Rect, color: Color, spacing: Float, width: Float) {
    clipRect(rect.left, rect.top, rect.right, rect.bottom) {
        var x = rect.left - rect.height
        while (x < rect.right) {
            drawLine(color, Offset(x, rect.bottom), Offset(x + rect.height, rect.top), width)
            x += spacing
        }
    }
}

/** The event mark: square braking, circle acceleration, diamond cornering; hollow when "Needed" (§9.1). */
@Suppress("LongParameterList") // the mark and its colours
internal fun DrawScope.eventMark(type: EventType, needed: Boolean, center: Offset, size: Float, color: Color, surface: Color) {
    val r = size / 2
    val shape = Path().apply {
        when (type) {
            EventType.BRAKE -> addRect(Rect(center.x - r * SQUARE, center.y - r * SQUARE, center.x + r * SQUARE, center.y + r * SQUARE))
            EventType.ACCEL -> addOval(Rect(center, r))
            EventType.CORNER -> {
                moveTo(center.x, center.y - r)
                lineTo(center.x + r, center.y)
                lineTo(center.x, center.y + r)
                lineTo(center.x - r, center.y)
                close()
            }
        }
    }
    drawPath(shape, if (needed) surface else color)
    drawPath(shape, color, style = Stroke(width = size * OUTLINE))
}

internal fun dash(on: Float, off: Float): PathEffect = PathEffect.dashPathEffect(floatArrayOf(on, off))

internal fun Size.fitRect(padding: Float): Rect = Rect(padding, padding, width - padding, height - padding)

private const val SQUARE = 0.82f
private const val OUTLINE = 0.14f
