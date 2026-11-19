package com.driveperf.feature.replay

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DoubleState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpDataColors
import com.driveperf.designsystem.DpEvent
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.designsystem.dpEventColor
import com.driveperf.domain.replay.EventMarker
import com.driveperf.domain.replay.Replay
import com.driveperf.domain.replay.ReplaySpan
import com.driveperf.domain.replay.SegmentStyle
import com.driveperf.domain.replay.StopPin
import com.driveperf.domain.trip.EventType

internal const val SCRUBBER_TAG = "replay_scrubber"

/** Keyboard and TalkBack step (S-RPL-01 accessibility: adjustable ±30 s). */
private const val STEP_S = 30.0
private const val SECONDS_PER_MINUTE = 60f

/**
 * Event ticks and stop ticks on the scrubber, at the same times as the map pins (F-RPL-03 AC3, F-RPL-02 AC3). [labels]
 * are the event pins' TalkBack labels by event id.
 */
internal class ScrubberTicks(val events: List<EventMarker>, val stops: List<StopPin>, val labels: Map<String, String> = emptyMap()) {
    companion object {
        val None = ScrubberTicks(emptyList(), emptyList())
    }
}

/** A drag within this distance of an event tick snaps to it (S-RPL-01). */
private val SNAP = 8.dp

/** Half the 48 dp target of an event tick. */
private val TICK_REACH = 24.dp

/** Event ticks sit this far above the track's centre. */
private val TICK_RISE = 12.dp

/**
 * The event a tap at ([x], [y]) on a [width] × [height] scrubber opens: a tap on the tick lane (the upper half, where
 * event ticks sit) within [reachPx] of a tick. Elsewhere a tap seeks, as before.
 */
@Suppress("LongParameterList") // the tap, the scrubber's size, the ticks and their reach
internal fun tickAt(x: Float, y: Float, width: Int, height: Int, events: List<EventMarker>, duration: Double, reachPx: Float): String? {
    if (duration <= 0 || y >= height / 2f) return null
    fun distance(e: EventMarker) = kotlin.math.abs((e.tPeakS / duration * width).toFloat() - x)
    return events.minByOrNull(::distance)?.takeIf { distance(it) <= reachPx }?.eventId
}

/**
 * The S-RPL-01 scrubber: 4 dp track, played part in the route colour, the quality spans marked on the track (estimated
 * dashed, no GPS as an outlined gap, excluded hatched; DRIVE-61), event ticks 12 dp above it and stop ticks under it,
 * and a 16 dp thumb that grows to 22 dp while dragging. Dragging moves the marker in the same frame (AC3) and snaps to
 * an event tick within 8 dp, with a selection haptic. The thumb is drawn from [time] without recomposing.
 */
@Suppress("LongParameterList") // the playhead, the trip and its ticks, the play state and the controls
@Composable
internal fun ReplayScrubber(
    time: DoubleState,
    replay: Replay,
    scrubbing: Boolean,
    controls: ReplayControls,
    modifier: Modifier = Modifier,
    ticks: ScrubberTicks = ScrubberTicks.None,
) {
    val colors = ScrubberColors(
        DrivePerfTheme.dataColors,
        track = MaterialTheme.colorScheme.surfaceContainerHighest,
        gap = MaterialTheme.colorScheme.surface,
    )
    val eventColors = DpEvent.entries.associateWith { dpEventColor(it) }
    val thumb by animateDpAsState(if (scrubbing) 22.dp else 16.dp, label = "thumb")
    val latest by rememberUpdatedState(controls)
    val haptics = LocalHapticFeedback.current
    val snapPx = with(LocalDensity.current) { SNAP.toPx() }
    val duration = replay.durationS
    val marks = remember(replay) { replay.spans.filter { it.style != SegmentStyle.OBSERVED } }
    var snapped by remember { mutableStateOf<String?>(null) }
    fun toTime(x: Float, width: Int): Double = (x / width.coerceAtLeast(1)).coerceIn(0f, 1f) * duration

    /** The drag position, snapped to an event tick within [SNAP]. */
    fun dragTime(x: Float, width: Int): Double {
        val w = width.coerceAtLeast(1)
        val near = ticks.events.minByOrNull { kotlin.math.abs(it.tPeakS / duration * w - x) }
            ?.takeIf { kotlin.math.abs(it.tPeakS / duration * w - x) <= snapPx }
        if (near?.eventId != snapped) {
            snapped = near?.eventId
            if (near != null) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
        return near?.tPeakS ?: toTime(x, width)
    }

    // A tap or an accessibility step: a scrub with no drag, so it resumes the play state too.
    fun seek(tS: Double) = with(latest) {
        scrubStart()
        scrubTo(tS)
        scrubEnd()
    }
    val reachPx = with(LocalDensity.current) { TICK_REACH.toPx() }
    Box(modifier.fillMaxWidth().height(48.dp)) {
        Spacer(
            Modifier
                .fillMaxSize()
                .testTag(SCRUBBER_TAG)
                .scrubberSemantics(time, duration, ticks, ::seek)
                .pointerInput(duration, ticks) {
                    detectTapGestures {
                        val tick = tickAt(it.x, it.y, size.width, size.height, ticks.events, duration, reachPx)
                        if (tick != null) latest.selectEvent(tick) else seek(toTime(it.x, size.width))
                    }
                }
            .pointerInput(duration, ticks) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        latest.scrubStart()
                        latest.scrubTo(dragTime(it.x, size.width))
                    },
                    onDragEnd = { latest.scrubEnd() },
                    onDragCancel = { latest.scrubEnd() },
                    ) { change, _ -> latest.scrubTo(dragTime(change.position.x, size.width)) }
                }
                .scrubberDrawing(time, duration, marks, colors, thumb)
                .tickDrawing(duration, ticks, eventColors, DrivePerfTheme.dataColors.stop),
        )
        TickTargets(ticks, duration) { latest.selectEvent(it) }
    }
}

internal const val EVENT_TICK_TAG = "replay_event_tick"

/**
 * TalkBack, Switch Access and Voice Access: each event tick is a 48 dp button that opens its evidence, read after the
 * scrubber. The targets have no pointer input, so touches reach the scrubber, which tells a tap on a tick from a drag.
 */
@Composable
private fun TickTargets(ticks: ScrubberTicks, duration: Double, select: (String) -> Unit) {
    if (ticks.events.isEmpty() || duration <= 0) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        for (event in ticks.events) {
            val label = stringResource(R.string.replay_event_tick, ticks.labels[event.eventId].orEmpty())
            Spacer(
                Modifier
                    .size(TICK_REACH * 2)
                    .offset(
                        x = maxWidth * (event.tPeakS / duration).toFloat() - TICK_REACH,
                        y = maxHeight / 2 - TICK_RISE - TICK_REACH,
                    )
                    .testTag(EVENT_TICK_TAG)
                    .semantics {
                        role = Role.Button
                        contentDescription = label
                        onClick { select(event.eventId); true }
                    },
            )
        }
    }
}

/** "Replay position, 12 min of 33 min." Adjustable, with ±30 s actions. Minute steps, so playback does not re-announce. */
@Composable
private fun Modifier.scrubberSemantics(time: DoubleState, duration: Double, ticks: ScrubberTicks, seek: (Double) -> Unit): Modifier {
    val minute by remember { derivedStateOf { ReplayFormatting.minutes(time.doubleValue) } }
    val position = stringResource(R.string.replay_position, minute, ReplayFormatting.minutes(duration))
    val back = stringResource(R.string.replay_back_30)
    val forward = stringResource(R.string.replay_forward_30)
    val nextEvent = stringResource(R.string.replay_next_event)
    val previousEvent = stringResource(R.string.replay_previous_event)
    val nextStop = stringResource(R.string.replay_next_stop)
    return semantics {
        contentDescription = position
        progressBarRangeInfo = ProgressBarRangeInfo(minute * SECONDS_PER_MINUTE, 0f..duration.toFloat())
        setProgress { seek(it.toDouble()); true }
        customActions = listOfNotNull(
            CustomAccessibilityAction(back) { seek(time.doubleValue - STEP_S); true },
            CustomAccessibilityAction(forward) { seek(time.doubleValue + STEP_S); true },
            CustomAccessibilityAction(nextEvent) {
                ticks.events.firstOrNull { it.tPeakS > time.doubleValue + EPSILON_S }?.let { seek(it.tPeakS) } != null
            }.takeIf { ticks.events.isNotEmpty() },
            CustomAccessibilityAction(previousEvent) {
                ticks.events.lastOrNull { it.tPeakS < time.doubleValue - EPSILON_S }?.let { seek(it.tPeakS) } != null
            }.takeIf { ticks.events.isNotEmpty() },
            CustomAccessibilityAction(nextStop) {
                ticks.stops.firstOrNull { it.atS > time.doubleValue + EPSILON_S }?.let { seek(it.atS) } != null
            }.takeIf { ticks.stops.isNotEmpty() },
        )
    }
}

/** Seeking to a tick lands exactly on it; the next one is strictly after. */
private const val EPSILON_S = 0.5

/** Event ticks 12 dp above the track (by shape, hollow when needed) and stop ticks just under it. */
private fun Modifier.tickDrawing(
    duration: Double,
    ticks: ScrubberTicks,
    colors: Map<DpEvent, Color>,
    stop: Color,
): Modifier = drawWithCache {
    val trackTop = (size.height - 4.dp.toPx()) / 2
    val tickY = trackTop - TICK_RISE.toPx()
    val r = 3.5.dp.toPx()
    val hollow = Stroke(1.5.dp.toPx())
    val stopTop = trackTop + 4.dp.toPx() + 2.dp.toPx()
    val stopLen = 5.dp.toPx()
    val stopW = 2.dp.toPx()
    fun x(t: Double): Float = (t / duration.coerceAtLeast(1.0) * size.width).toFloat()
    onDrawBehind {
        for (s in ticks.stops) drawLine(stop, Offset(x(s.atS), stopTop), Offset(x(s.atS), stopTop + stopLen), stopW, StrokeCap.Round)
        for (e in ticks.events) {
            val color = colors.getValue(e.type.dp())
            val c = Offset(x(e.tPeakS), tickY)
            val style = if (e.needed) hollow else Fill
            when (e.type) {
                EventType.BRAKE -> drawRoundRect(color, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), CornerRadius(r / 2), style = style)
                EventType.ACCEL -> drawCircle(color, r, c, style = style)
                EventType.CORNER -> drawPath(
                    Path().apply {
                        moveTo(c.x, c.y - r * 1.3f)
                        lineTo(c.x + r * 1.3f, c.y)
                        lineTo(c.x, c.y + r * 1.3f)
                        lineTo(c.x - r * 1.3f, c.y)
                        close()
                    },
                    color,
                    style = style,
                )
            }
        }
    }
}

private class ScrubberColors(data: DpDataColors, val track: Color, val gap: Color) {
    val played = data.route
    val gapStroke = data.gapStroke
    val thumbDot = data.marker
}

private fun Modifier.scrubberDrawing(
    time: DoubleState,
    duration: Double,
    marks: List<ReplaySpan>,
    colors: ScrubberColors,
    thumb: Dp,
): Modifier = drawWithCache {
    val track = TrackGeometry(this, size, duration)
    val thumbR = thumb.toPx() / 2
    val ringW = 3.dp.toPx()
    onDrawBehind {
        drawRoundRect(colors.track, Offset(0f, track.top), Size(size.width, track.height), track.radius)
        val now = track.x(time.doubleValue)
        drawRoundRect(colors.played, Offset(0f, track.top), Size(now, track.height), track.radius)
        for (span in marks) track.drawMark(this, span, colors)
        drawCircle(colors.played, thumbR, Offset(now, size.height / 2))
        drawCircle(colors.thumbDot, thumbR - ringW, Offset(now, size.height / 2))
    }
}

/** The track's measurements and span marks, built once per size. */
private class TrackGeometry(density: Density, private val size: Size, private val duration: Double) {
    val height: Float = with(density) { 4.dp.toPx() }
    val top: Float = (size.height - height) / 2
    val radius = CornerRadius(height / 2)
    private val dash = with(density) { PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())) }
    private val outline = Stroke(with(density) { 1.dp.toPx() })
    private val hatchStep = with(density) { 4.dp.toPx() }
    private val hatchWidth = with(density) { 1.dp.toPx() }

    fun x(t: Double): Float = (t / duration.coerceAtLeast(1.0) * size.width).toFloat()

    fun drawMark(scope: DrawScope, span: ReplaySpan, colors: ScrubberColors) = with(scope) {
        val x0 = x(span.t0S)
        val x1 = x(span.t1S).coerceAtLeast(x0 + 2f)
        val mid = top + height / 2
        when (span.style) {
            SegmentStyle.ESTIMATED -> {
                drawRect(colors.track, Offset(x0, top), Size(x1 - x0, height))
                drawLine(colors.played, Offset(x0, mid), Offset(x1, mid), height, pathEffect = dash)
            }
            SegmentStyle.NO_GPS -> {
                drawRect(colors.gap, Offset(x0, top), Size(x1 - x0, height))
                drawRect(colors.gapStroke, Offset(x0, top), Size(x1 - x0, height), style = outline)
            }
            SegmentStyle.EXCLUDED -> clipRect(x0, top, x1, top + height) {
                drawRect(colors.track, Offset(x0, top), Size(x1 - x0, height))
                var hx = x0 - height
                while (hx < x1) {
                    drawLine(colors.gapStroke, Offset(hx, top + height), Offset(hx + height, top), hatchWidth, StrokeCap.Butt)
                    hx += hatchStep
                }
            }
            SegmentStyle.OBSERVED -> Unit
        }
    }
}
