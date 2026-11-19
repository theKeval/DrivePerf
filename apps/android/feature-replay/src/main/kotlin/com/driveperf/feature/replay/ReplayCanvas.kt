package com.driveperf.feature.replay

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DoubleState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.domain.replay.Replay
import com.driveperf.domain.replay.ReplayFrame
import com.driveperf.domain.replay.SegmentStyle
import kotlin.math.cos
import kotlin.math.sin

/** Test tag of the plain-canvas map. */
internal const val CANVAS_MAP_TAG = "replay_canvas_map"

/**
 * The plain-canvas renderer (F-RPL-01 AC5): the same segments, start, end and marker as the Google map, projected from
 * the trip's bounding box onto the land colour and grid, with no road layer. Used without a Maps key, offline and while
 * tiles load. Paths are built once per size; each frame only reads [time] and draws the marker.
 */
@Composable
internal fun ReplayCanvas(
    replay: Replay,
    time: DoubleState,
    insets: PaddingValues,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val looks = DrawOrder.associateWith { lineLook(it) }
    val data = DrivePerfTheme.dataColors
    val direction = LocalLayoutDirection.current
    Spacer(
        modifier
            .semantics { this.contentDescription = contentDescription }
            .drawWithCache {
                val projection = ReplayProjection(replay.bbox, size.width, size.height, insets.toPx(this, direction))
                val segments = replay.segments(projection.zoomBucket(density))
                val lines = segments
                    .filter { it.points.size >= 2 }
                    .sortedBy { DrawOrder.indexOf(it.style) }
                    .map { segment ->
                        val path = Path()
                        segment.points.forEachIndexed { i, p ->
                            val x = projection.x(p.lng)
                            val y = projection.y(p.lat)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        RouteLine(path, looks.getValue(segment.style), this)
                    }
                val drawn = segments.flatMap { it.points }
                val start = drawn.firstOrNull()?.let { Offset(projection.x(it.lng), projection.y(it.lat)) }
                val end = drawn.lastOrNull()?.let { Offset(projection.x(it.lng), projection.y(it.lat)) }
                val marker = MarkerPainter(this, data.route, data.marker)
                val gridStep = GRID_STEP.toPx()
                val startStroke = Stroke(2.dp.toPx())
                val startRadius = 6.dp.toPx()
                val endRadius = 4.dp.toPx()
                onDrawBehind {
                    drawRect(data.mapLand)
                    drawGrid(gridStep, data.grid)
                    for (line in lines) line.draw(this)
                    start?.let { drawCircle(data.route, radius = startRadius, center = it, style = startStroke) }
                    end?.let { drawCircle(data.route, radius = endRadius, center = it) }
                    val frame = replay.frameAt(time.doubleValue)
                    if (frame.style != SegmentStyle.NO_GPS) {
                        marker.draw(this, projection.x(frame.lng), projection.y(frame.lat), frame)
                    }
                }
            },
    )
}

private val GRID_STEP = 40.dp

private fun DrawScope.drawGrid(step: Float, color: Color) {
    var x = step
    while (x < size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height))
        x += step
    }
    var y = step
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y))
        y += step
    }
}

private fun PaddingValues.toPx(density: Density, direction: LayoutDirection) = with(density) {
    ReplayProjection.Insets(
        left = calculateLeftPadding(direction).toPx(),
        top = calculateTopPadding().toPx(),
        right = calculateRightPadding(direction).toPx(),
        bottom = calculateBottomPadding().toPx(),
    )
}

/** One projected segment with its pre-built strokes. */
private class RouteLine(private val path: Path, private val look: LineLook, density: Density) {
    private val casing: Stroke? = look.casing?.let {
        with(density) { Stroke(look.casingWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round) }
    }
    private val stroke: Stroke = with(density) {
        val effect = if (look.dash.isEmpty()) null else PathEffect.dashPathEffect(look.dash.map { it.toPx() }.toFloatArray())
        Stroke(look.width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect)
    }

    fun draw(scope: DrawScope) {
        val casingColor = look.casing
        if (casing != null && casingColor != null) scope.drawPath(path, casingColor, style = casing)
        scope.drawPath(path, look.color, style = stroke)
    }
}

/** The replay marker, with its wedge path built once; [draw] allocates nothing. */
private class MarkerPainter(density: Density, private val ring: Color, private val dot: Color) {
    private val haloRadius: Float
    private val ringRadius: Float
    private val dotRadius: Float
    private val wedge = Path()

    init {
        with(density) {
            haloRadius = MarkerLook.halo.toPx() / 2
            dotRadius = MarkerLook.dot.toPx() / 2
            ringRadius = dotRadius + MarkerLook.ring.toPx()
            // Pointing north from the ring's edge; rotated to the bearing when drawn.
            val tip = ringRadius + MarkerLook.wedge.toPx() - dotRadius
            val half = Math.toRadians(MarkerLook.WEDGE_DEGREES / 2.0)
            wedge.moveTo(0f, -tip)
            wedge.lineTo((ringRadius * sin(half)).toFloat(), (-ringRadius * cos(half)).toFloat())
            wedge.lineTo((-ringRadius * sin(half)).toFloat(), (-ringRadius * cos(half)).toFloat())
            wedge.close()
        }
    }

    fun draw(scope: DrawScope, x: Float, y: Float, frame: ReplayFrame) = with(scope) {
        translate(x, y) {
            drawCircle(ring, radius = haloRadius, center = Offset.Zero, alpha = MarkerLook.HALO_ALPHA)
            frame.bearingDeg?.let { bearing -> rotate(bearing.toFloat(), pivot = Offset.Zero) { drawPath(wedge, ring) } }
            drawCircle(ring, radius = ringRadius, center = Offset.Zero)
            drawCircle(dot, radius = dotRadius, center = Offset.Zero)
        }
    }
}
