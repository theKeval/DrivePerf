package com.driveperf.feature.drives

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpEventGlyph
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.domain.replay.SegmentStyle
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.presentation.analysis.MapPoint
import com.driveperf.presentation.drives.TripLinkCopy
import com.driveperf.presentation.drives.TripMapUi

/** Test tag of trip detail's map hero. */
const val TRIP_MAP_TAG: String = "trip_detail_map"

/**
 * The S-TRP-01 map hero (DRIVE-55): the route in Glide over the plain map canvas, drawn from the preview track as the
 * Analysis map card is (no tiles, no street names), with the estimated start dashed, GPS gaps dotted, stop capsules,
 * the neutral max-speed triangle and the event pins by shape. The quality badge and "Estimated start" sit top-left and
 * "Replay" bottom-left. Tapping anywhere opens Replay.
 */
@Composable
internal fun TripMapHero(map: TripMapUi, quality: QualityBadge?, onReplay: () -> Unit) {
    val replayLabel = stringResource(R.string.drives_replay)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = DrivePerfTheme.dataColors.mapLand,
        modifier = Modifier
            .fillMaxWidth()
            .height(MAP_HEIGHT)
            .testTag(TRIP_MAP_TAG)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClickLabel = replayLabel, role = Role.Button, onClick = onReplay)
            .clearAndSetSemantics {
                contentDescription = map.accessibilityLabel
                role = Role.Button
                onClick(replayLabel) { onReplay(); true }
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val fit = with(density) { MapFit(map, Size(maxWidth.toPx(), maxHeight.toPx()), PAD.toPx(), TOP_INSET.toPx()) }
            RouteCanvas(map, fit)
            map.pins.forEach { pin ->
                val at = fit.toScreen(pin.at)
                DpEventGlyph(
                    pin.type.dp(),
                    Modifier.offset(with(density) { at.x.toDp() } - PIN / 2, with(density) { at.y.toDp() } - PIN / 2),
                    needed = pin.needed,
                    size = PIN,
                )
            }
            Row(
                Modifier.align(Alignment.TopStart).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                quality?.let { QualityBadgeChip(it) }
                if (map.estimatedStart) Chip(TripLinkCopy.ESTIMATED_START)
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_drives_replay), contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(replayLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

/** The route, the stops and the max-speed triangle; the pins are glyph composables on top. */
@Composable
private fun RouteCanvas(map: TripMapUi, fit: MapFit) {
    val data = DrivePerfTheme.dataColors
    val colors = RouteColors(data.route, data.routeCasing, data.routeFaded, data.gapStroke, data.stop, data.maxSpeed)
    Canvas(Modifier.fillMaxSize()) {
        map.segments.forEach { s -> drawSegment(s.style, s.xs, s.ys, fit, colors) }
        map.stops.forEach { p ->
            val o = fit.toScreen(p)
            val w = STOP_W.dp.toPx()
            val h = STOP_H.dp.toPx()
            drawRoundRect(colors.stop, Offset(o.x - w / 2, o.y - h / 2), Size(w, h), CornerRadius(h / 2))
        }
        map.maxSpeed?.let { p ->
            val o = fit.toScreen(p)
            val r = TRIANGLE.dp.toPx()
            val path = Path().apply {
                moveTo(o.x, o.y - r)
                lineTo(o.x + r, o.y + r * 0.8f)
                lineTo(o.x - r, o.y + r * 0.8f)
                close()
            }
            drawPath(path, colors.maxSpeed)
        }
    }
}

private class RouteColors(val route: Color, val casing: Color, val faded: Color, val gap: Color, val stop: Color, val maxSpeed: Color)

/** Fits the map's metres into the card with a uniform scale, north up, below the chips. */
private class MapFit(map: TripMapUi, size: Size, pad: Float, top: Float) {
    private val scale: Float
    private val ox: Float
    private val oy: Float

    init {
        val w = map.widthM.coerceAtLeast(1.0).toFloat()
        val h = map.heightM.coerceAtLeast(1.0).toFloat()
        scale = minOf((size.width - 2 * pad) / w, (size.height - pad - top) / h)
        ox = (size.width - map.widthM.toFloat() * scale) / 2
        oy = top + (size.height - top - pad + map.heightM.toFloat() * scale) / 2
    }

    fun toScreen(p: MapPoint): Offset = Offset(ox + p.x.toFloat() * scale, oy - p.y.toFloat() * scale)
}

private fun DrawScope.drawSegment(style: SegmentStyle, xs: DoubleArray, ys: DoubleArray, fit: MapFit, colors: RouteColors) {
    if (xs.isEmpty()) return
    val path = Path()
    xs.indices.forEach { i ->
        val o = fit.toScreen(MapPoint(xs[i], ys[i]))
        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
    }
    val w = ROUTE_WIDTH.dp.toPx()
    when (style) {
        SegmentStyle.OBSERVED -> {
            drawPath(path, colors.casing, style = Stroke(CASING_WIDTH.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, colors.route, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        SegmentStyle.ESTIMATED -> {
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            drawPath(path, colors.route.copy(alpha = ESTIMATED_ALPHA), style = Stroke(w, pathEffect = dash))
        }
        SegmentStyle.NO_GPS -> {
            val dots = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 6.dp.toPx()))
            drawPath(path, colors.gap, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = dots))
        }
        SegmentStyle.EXCLUDED -> drawPath(path, colors.faded, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** 232 tall in the spec; the chips and the Replay capsule take the top and bottom insets. */
private val MAP_HEIGHT: Dp = 232.dp
private val PAD: Dp = 28.dp
private val TOP_INSET: Dp = 52.dp
private val PIN: Dp = 22.dp
private const val ROUTE_WIDTH = 3.8f
private const val CASING_WIDTH = 7.5f
private const val ESTIMATED_ALPHA = 0.6f
private const val STOP_W = 14
private const val STOP_H = 8
private const val TRIANGLE = 6
