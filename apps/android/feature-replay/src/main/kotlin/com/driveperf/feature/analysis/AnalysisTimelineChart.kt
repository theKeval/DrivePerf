package com.driveperf.feature.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DoubleState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.driveperf.domain.analysis.AnalysisChannel
import com.driveperf.domain.replay.SegmentStyle
import com.driveperf.presentation.analysis.AnalysisTimeline
import com.driveperf.presentation.analysis.TimelineBand
import com.driveperf.presentation.analysis.TimelineLane
import kotlin.math.abs

internal const val TIMELINE_TAG = "analysis_timeline"

/** Lane heights from the S-ANL-01 spec; XL text grows them 1.5× (accessibility). */
internal fun laneHeight(channel: AnalysisChannel): Dp = when (channel) {
    AnalysisChannel.SPEED -> 44.dp
    AnalysisChannel.LON_G, AnalysisChannel.LAT_G -> 64.dp
    AnalysisChannel.ELEVATION -> 34.dp
    AnalysisChannel.JERK -> 40.dp
    AnalysisChannel.QUALITY -> 14.dp
}

private val LANE_GAP = 6.dp
private val EVENT_LANE = 20.dp
private val AXIS_ROW = 16.dp
private val SNAP = 8.dp
private const val XL_SCALE = 1.5f
private const val XL_FONT = 1.3f

/**
 * The synced timeline (S-ANL-01): every lane on one x-axis, the quality bands across them, the event lane and the
 * cursor with one value bubble per lane. The cursor is read in the draw phase only, so a drag redraws without
 * recomposing. Drag moves it (snapping to ticks within 8 dp), tap jumps, pinch zooms around it, double-tap resets.
 */
@Suppress("LongParameterList") // the timeline, the cursor, the controls and the table action
@Composable
internal fun AnalysisTimelineChart(
    timeline: AnalysisTimeline,
    cursor: DoubleState,
    controls: AnalysisControls,
    onViewTable: () -> Unit,
    viewTableLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = analysisColors()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val scale = if (density.fontScale >= XL_FONT) XL_SCALE else 1f
    val heights = timeline.lanes.map { laneHeight(it.channel) * scale }
    val total = heights.fold(0.dp) { a, h -> a + h + LANE_GAP } + EVENT_LANE + AXIS_ROW
    val haptics = LocalHapticFeedback.current
    val current = rememberUpdatedState(timeline)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val paths = remember(timeline, heights) { LanePaths() }
    BoxWithConstraints(modifier.fillMaxWidth().height(total)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val snapShare = with(density) { SNAP.toPx() } / widthPx.coerceAtLeast(1f)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(total)
                .testTag(TIMELINE_TAG)
                .semantics {
                    contentDescription = timeline.summary
                    customActions = listOf(CustomAccessibilityAction(viewTableLabel) { onViewTable(); true })
                }
                .timelineGestures(widthPx, snapShare, controls) { snapped ->
                    if (snapped) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
        ) {
            val rects = laneRects(timeline.lanes, heights.map { it.toPx() }, LANE_GAP.toPx())
            drawBands(current.value.bands, rects, colors)
            timeline.lanes.forEachIndexed { i, lane -> drawLane(lane, rects[i], colors, paths, measurer, labelStyle) }
            val eventTop = (rects.lastOrNull()?.bottom ?: 0f) + LANE_GAP.toPx()
            timeline.ticks.forEach { t ->
                val c = Offset((t.x * size.width).toFloat(), eventTop + EVENT_LANE.toPx() / 2)
                eventMark(t.type, t.needed, c, EVENT_ICON.toPx(), colors.event(t.type), colors.surface)
            }
            drawAxis(timeline, eventTop + EVENT_LANE.toPx(), measurer, labelStyle)
            drawCursor(timeline, cursor.doubleValue, rects, colors, measurer)
        }
        TickButtons(timeline, heights, controls)
    }
}

private val EVENT_ICON = 12.dp
private val TICK_TARGET = 48.dp

/**
 * Each event tick is a 48 dp button for TalkBack and touch: "Hard braking, 10:41 into the drive." A tap opens its
 * evidence (DRIVE-39 AC1); a drag across it still only scrubs, snapping to it.
 */
@Composable
private fun TickButtons(timeline: AnalysisTimeline, heights: List<Dp>, controls: AnalysisControls) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val top = heights.fold(0.dp) { a, h -> a + h + LANE_GAP } + EVENT_LANE / 2 - TICK_TARGET / 2
        timeline.ticks.forEach { tick ->
            Box(
                Modifier
                    .offset(x = maxWidth * tick.x.toFloat() - TICK_TARGET / 2, y = top)
                    .size(TICK_TARGET)
                    .semantics {
                        role = Role.Button
                        contentDescription = tick.accessibilityLabel
                        onClick { controls.openEvidence(tick.eventId); true }
                    }
                    .pointerInput(tick.eventId) { detectTapGestures { controls.openEvidence(tick.eventId) } },
            )
        }
    }
}

private fun Modifier.timelineGestures(width: Float, snapShare: Float, controls: AnalysisControls, onSnap: (Boolean) -> Unit) =
    this
        .pointerInput(controls, width) {
            var last: String? = null
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                if (drag != null) {
                    fun move(x: Float) {
                        val id = controls.drag((x / width).toDouble(), snapShare.toDouble())
                        onSnap(id != null && id != last)
                        last = id
                    }
                    move(drag.position.x)
                    horizontalDrag(drag.id) { change ->
                        move(change.position.x)
                        change.consume()
                    }
                    controls.dragEnd()
                }
            }
        }
        .pointerInput(controls) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } >= 2) {
                        val zoom = event.calculateZoom()
                        if (abs(zoom - 1f) > PINCH_STEP) {
                            controls.pinch(zoom.toDouble())
                            event.changes.forEach { it.consume() }
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
        .pointerInput(controls, width) {
            detectTapGestures(
                onTap = { p ->
                    controls.drag((p.x / width).toDouble(), snapShare.toDouble())
                    controls.dragEnd()
                },
                onDoubleTap = { controls.resetZoom() },
            )
        }

private const val PINCH_STEP = 0.01f

/** Lane paths cached per timeline, so a cursor frame never rebuilds them. */
private class LanePaths {
    val lines = HashMap<AnalysisChannel, Path>()
    val areas = HashMap<AnalysisChannel, Path>()
    var size: Size = Size.Zero
}

private fun laneRects(lanes: List<TimelineLane>, heights: List<Float>, gap: Float): List<Rect> {
    var y = 0f
    return lanes.indices.map { i ->
        Rect(0f, y, Float.MAX_VALUE, y + heights[i]).also { y += heights[i] + gap }
    }
}

private fun DrawScope.drawBands(bands: List<TimelineBand>, rects: List<Rect>, colors: AnalysisColors) {
    val bottom = rects.lastOrNull()?.bottom ?: return
    bands.forEach { b ->
        val r = Rect((b.x0.coerceAtLeast(0.0) * size.width).toFloat(), 0f, (b.x1.coerceAtMost(1.0) * size.width).toFloat(), bottom)
        when (b.style) {
            SegmentStyle.ESTIMATED -> drawRect(colors.speed.copy(alpha = BAND_ALPHA / 2), r.topLeft, r.size)
            SegmentStyle.NO_GPS -> drawRect(colors.gap.copy(alpha = BAND_ALPHA), r.topLeft, r.size)
            SegmentStyle.EXCLUDED -> hatch(r, colors.hatch, HATCH_SPACING.dp.toPx(), 1.dp.toPx())
            SegmentStyle.OBSERVED -> Unit
        }
    }
}

private const val BAND_ALPHA = 0.18f
private const val HATCH_SPACING = 6

@Suppress("LongParameterList") // the lane, its box and what it draws with
private fun DrawScope.drawLane(
    lane: TimelineLane,
    box: Rect,
    colors: AnalysisColors,
    cache: LanePaths,
    measurer: TextMeasurer,
    label: TextStyle,
) {
    val r = Rect(0f, box.top, size.width, box.bottom)
    drawRoundRect(colors.grid.copy(alpha = GRID_ALPHA), r.topLeft, r.size, CornerRadius(4.dp.toPx()), style = Stroke(1f))
    lane.zeroY?.let { z ->
        val y = r.bottom - (z * r.height).toFloat()
        drawLine(colors.axis, Offset(0f, y), Offset(size.width, y), 1f)
    }
    if (cache.size != size) {
        cache.lines.clear()
        cache.areas.clear()
        cache.size = size
    }
    val path = lane.path
    if (path != null) {
        val line = cache.lines.getOrPut(lane.channel) { path.toPath(r) }
        val color = colors.line(lane.channel)
        if (lane.channel == AnalysisChannel.ELEVATION) {
            drawPath(cache.areas.getOrPut(lane.channel) { path.toArea(r) }, colors.elevationFill)
        }
        val effect = if (lane.channel == AnalysisChannel.LAT_G) dash(6.dp.toPx(), 3.dp.toPx()) else null
        val split = (lane.dashedUntilX * size.width).toFloat()
        val width = (if (lane.channel == AnalysisChannel.JERK) 1.2f else 1.6f).dp.toPx()
        val estimated = Stroke(width, pathEffect = dash(4.dp.toPx(), 4.dp.toPx()))
        clipRect(right = split) { drawPath(line, color.copy(alpha = AnalysisColors.ESTIMATED_ALPHA), style = estimated) }
        clipRect(left = split) { drawPath(line, color, style = Stroke(width, pathEffect = effect)) }
    }
    if (lane.channel == AnalysisChannel.QUALITY) drawRect(colors.good.copy(alpha = GRID_ALPHA), r.topLeft, r.size)
    val title = measurer.measure("${lane.title}  ${lane.rangeLabel}", label.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp))
    if (lane.channel != AnalysisChannel.QUALITY) drawText(title, topLeft = Offset(4.dp.toPx(), r.top + 2.dp.toPx()))
}

private const val GRID_ALPHA = 0.5f

private fun DrawScope.drawAxis(timeline: AnalysisTimeline, top: Float, measurer: TextMeasurer, style: TextStyle) {
    timeline.axisTicks.forEachIndexed { i, t ->
        val text = measurer.measure(t.label, style.copy(fontSize = 9.5.sp))
        val x = (t.x * size.width).toFloat()
        val left = when (i) {
            0 -> x
            timeline.axisTicks.lastIndex -> x - text.size.width
            else -> x - text.size.width / 2
        }
        drawText(text, topLeft = Offset(left, top))
    }
}

private fun DrawScope.drawCursor(
    timeline: AnalysisTimeline,
    tS: Double,
    rects: List<Rect>,
    colors: AnalysisColors,
    measurer: TextMeasurer,
) {
    val x = timeline.xOf(tS)
    if (x !in 0.0..1.0) return
    val px = (x * size.width).toFloat()
    drawLine(colors.cursor, Offset(px, 0f), Offset(px, size.height - AXIS_ROW.toPx()), 1.5.dp.toPx())
    val readout = timeline.readout(tS)
    readout.values.forEachIndexed { i, v ->
        val r = rects.getOrNull(i) ?: return@forEachIndexed
        if (v.y.isNaN() && timeline.lanes[i].channel != AnalysisChannel.SPEED) return@forEachIndexed
        val text = measurer.measure(v.text, TextStyle(fontSize = 11.sp, color = colors.cursor, fontWeight = FontWeight.SemiBold))
        val y = if (v.y.isNaN()) r.center.y else (r.bottom - (v.y.coerceIn(0.0, 1.0) * r.height)).toFloat()
        val left = if (px + text.size.width + 8.dp.toPx() > size.width) px - text.size.width - 6.dp.toPx() else px + 6.dp.toPx()
        val top = (y - text.size.height / 2f).coerceIn(r.top, (r.bottom - text.size.height).coerceAtLeast(r.top))
        val halo = Size(text.size.width + 6.dp.toPx(), text.size.height.toFloat())
        drawRoundRect(colors.surface, Offset(left - 3.dp.toPx(), top), halo, CornerRadius(4.dp.toPx()))
        drawText(text, topLeft = Offset(left, top))
        if (!v.y.isNaN()) drawCircle(colors.line(v.channel), 3.dp.toPx(), Offset(px, y))
    }
}
