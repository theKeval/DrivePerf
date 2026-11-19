package com.driveperf.feature.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DoubleState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.driveperf.domain.replay.SegmentStyle
import com.driveperf.presentation.analysis.AnalysisMap
import com.driveperf.presentation.analysis.AnalysisTimeline
import com.driveperf.presentation.analysis.GgView
import com.driveperf.presentation.analysis.MapPoint

/** Fits [AnalysisMap]'s metres into a box with a uniform scale, north up. */
private class MapFit(map: AnalysisMap, size: Size, pad: Float) {
    private val scale: Float
    private val ox: Float
    private val oy: Float

    init {
        val w = map.widthM.coerceAtLeast(1.0).toFloat()
        val h = map.heightM.coerceAtLeast(1.0).toFloat()
        scale = minOf((size.width - 2 * pad) / w, (size.height - 2 * pad) / h)
        ox = (size.width - map.widthM.toFloat() * scale) / 2
        oy = (size.height + map.heightM.toFloat() * scale) / 2
    }

    fun toScreen(p: MapPoint): Offset = Offset(ox + p.x.toFloat() * scale, oy - p.y.toFloat() * scale)

    fun toMap(o: Offset): MapPoint = MapPoint(((o.x - ox) / scale).toDouble(), ((oy - o.y) / scale).toDouble())
}

/**
 * The S-ANL-01 map card: the route in its quality styles, the event pins and the cursor marker (read in the draw phase,
 * so it follows a drag in the same frame). Tapping snaps the cursor to the nearest track point (AC2).
 */
@Composable
@Suppress("LongParameterList") // the map, the cursor it follows, and its tap
internal fun AnalysisMapCanvas(
    map: AnalysisMap,
    timeline: AnalysisTimeline,
    cursor: DoubleState,
    label: String,
    onTap: (MapPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = analysisColors()
    Canvas(
        modifier
            .semantics { contentDescription = label }
            .pointerInput(map) { detectTapGestures { o -> onTap(MapFit(map, size.toSizeF(), PAD.dp.toPx()).toMap(o)) } },
    ) {
        drawRect(colors.land)
        val fit = MapFit(map, size, PAD.dp.toPx())
        map.segments.forEach { s -> drawRoute(s.style, s.xs, s.ys, fit, colors) }
        map.pins.forEach { p -> eventMark(p.type, p.needed, fit.toScreen(p.at), PIN.dp.toPx(), colors.event(p.type), colors.surface) }
        timeline.readout(cursor.doubleValue).marker?.let { m ->
            val o = fit.toScreen(m)
            drawCircle(colors.casing, MARKER.dp.toPx() + 2.dp.toPx(), o)
            drawCircle(colors.marker, MARKER.dp.toPx(), o)
        }
    }
}

private const val PAD = 14
private const val PIN = 14
private const val MARKER = 6

private fun androidx.compose.ui.unit.IntSize.toSizeF(): Size = Size(width.toFloat(), height.toFloat())

private fun DrawScope.drawRoute(style: SegmentStyle, xs: DoubleArray, ys: DoubleArray, fit: MapFit, colors: AnalysisColors) {
    if (xs.isEmpty()) return
    val path = Path()
    xs.indices.forEach { i ->
        val o = fit.toScreen(MapPoint(xs[i], ys[i]))
        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
    }
    val w = 3.8.dp.toPx()
    when (style) {
        SegmentStyle.OBSERVED -> {
            drawPath(path, colors.casing, style = Stroke(7.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, colors.route, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        SegmentStyle.ESTIMATED -> {
            val estimated = colors.route.copy(alpha = AnalysisColors.ESTIMATED_ALPHA)
            drawPath(path, estimated, style = Stroke(w, pathEffect = dash(4.dp.toPx(), 4.dp.toPx())))
        }
        SegmentStyle.NO_GPS -> {
            val dotted = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = dash(1.dp.toPx(), 6.dp.toPx()))
            drawPath(path, colors.gap, style = dotted)
        }
        SegmentStyle.EXCLUDED -> drawPath(path, colors.faded, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * The friction circle (F-RPL-06): density cloud, envelope, rings every 0.1 g, event pins at their peaks and the dot at
 * the cursor. [expanded] adds the ring labels (S-ANL-02 AC2).
 */
@Composable
internal fun GgCanvas(gg: GgView, timeline: AnalysisTimeline?, cursor: DoubleState, expanded: Boolean, modifier: Modifier = Modifier) {
    val colors = analysisColors()
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.semantics { contentDescription = gg.accessibilityLabel }) {
        val r = size.minDimension / 2 - (if (expanded) 18 else 6).dp.toPx()
        val c = center
        val k = r / gg.scaleG.toFloat()
        fun at(lat: Double, lon: Double) = Offset(c.x + lat.toFloat() * k, c.y - lon.toFloat() * k)
        drawLine(colors.grid, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1f)
        drawLine(colors.grid, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1f)
        gg.rings.forEach { ring ->
            val rr = ring.g.toFloat() * k
            drawCircle(colors.ring, rr, c, style = Stroke(1f, pathEffect = dash(2.dp.toPx(), 3.dp.toPx())))
            if (expanded) {
                val t = measurer.measure(ring.label, TextStyle(fontSize = 9.5.sp, color = labelColor))
                drawText(t, topLeft = Offset(c.x + 2.dp.toPx(), c.y - rr - t.size.height))
            }
        }
        val dotR = (if (expanded) 2.5f else 1.6f).dp.toPx()
        gg.dots.forEach { d ->
            val alpha = if (d.emphasised) CLOUD_ALPHA * (CLOUD_FLOOR + (1 - CLOUD_FLOOR) * d.weight.toFloat()) else DIM_ALPHA
            drawCircle(colors.cloud.copy(alpha = alpha), dotR, at(d.latG, d.lonG))
        }
        if (gg.envelope.isNotEmpty()) {
            val env = Path()
            gg.envelope.forEachIndexed { i, p -> at(p.latG, p.lonG).let { if (i == 0) env.moveTo(it.x, it.y) else env.lineTo(it.x, it.y) } }
            env.close()
            drawPath(env, colors.envelope, style = Stroke(1.6.dp.toPx(), join = StrokeJoin.Round))
        }
        val pin = (if (expanded) 16 else 10).dp.toPx()
        gg.pins.forEach { p -> eventMark(p.type, p.needed, at(p.at.latG, p.at.lonG), pin, colors.event(p.type), colors.surface) }
        timeline?.readout(cursor.doubleValue)?.gg?.let { d ->
            drawCircle(colors.casing, 5.dp.toPx(), at(d.latG, d.lonG))
            drawCircle(colors.dot, 4.dp.toPx(), at(d.latG, d.lonG))
        }
    }
}

private const val CLOUD_ALPHA = 0.38f
private const val CLOUD_FLOOR = 0.35f
private const val DIM_ALPHA = 0.06f
