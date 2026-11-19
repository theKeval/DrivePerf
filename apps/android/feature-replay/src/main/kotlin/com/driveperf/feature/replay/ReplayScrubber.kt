package com.driveperf.feature.replay

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DoubleState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpDataColors
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.domain.replay.Replay
import com.driveperf.domain.replay.ReplaySpan
import com.driveperf.domain.replay.SegmentStyle

internal const val SCRUBBER_TAG = "replay_scrubber"

/** Keyboard and TalkBack step (S-RPL-01 accessibility: adjustable ±30 s). */
private const val STEP_S = 30.0
private const val SECONDS_PER_MINUTE = 60f

/**
 * The S-RPL-01 scrubber: 4 dp track, played part in the route colour, the quality spans marked on the track (estimated
 * dashed, no GPS as an outlined gap, excluded hatched; DRIVE-61), and a 16 dp thumb that grows to 22 dp while dragging.
 * Dragging moves the marker in the same frame (AC3). The thumb is drawn from [time] without recomposing.
 */
@Composable
internal fun ReplayScrubber(
    time: DoubleState,
    replay: Replay,
    scrubbing: Boolean,
    controls: ReplayControls,
    modifier: Modifier = Modifier,
) {
    val colors = ScrubberColors(
        DrivePerfTheme.dataColors,
        track = MaterialTheme.colorScheme.surfaceContainerHighest,
        gap = MaterialTheme.colorScheme.surface,
    )
    val thumb by animateDpAsState(if (scrubbing) 22.dp else 16.dp, label = "thumb")
    val latest by rememberUpdatedState(controls)
    val duration = replay.durationS
    val marks = remember(replay) { replay.spans.filter { it.style != SegmentStyle.OBSERVED } }
    fun toTime(x: Float, width: Int): Double = (x / width.coerceAtLeast(1)).coerceIn(0f, 1f) * duration

    // A tap or an accessibility step: a scrub with no drag, so it resumes the play state too.
    fun seek(tS: Double) = with(latest) {
        scrubStart()
        scrubTo(tS)
        scrubEnd()
    }
    Spacer(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag(SCRUBBER_TAG)
            .scrubberSemantics(time, duration, ::seek)
            .pointerInput(duration) { detectTapGestures { seek(toTime(it.x, size.width)) } }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        latest.scrubStart()
                        latest.scrubTo(toTime(it.x, size.width))
                    },
                    onDragEnd = { latest.scrubEnd() },
                    onDragCancel = { latest.scrubEnd() },
                ) { change, _ -> latest.scrubTo(toTime(change.position.x, size.width)) }
            }
            .scrubberDrawing(time, duration, marks, colors, thumb),
    )
}

/** "Replay position, 12 min of 33 min." Adjustable, with ±30 s actions. Minute steps, so playback does not re-announce. */
@Composable
private fun Modifier.scrubberSemantics(time: DoubleState, duration: Double, seek: (Double) -> Unit): Modifier {
    val minute by remember { derivedStateOf { ReplayFormatting.minutes(time.doubleValue) } }
    val position = stringResource(R.string.replay_position, minute, ReplayFormatting.minutes(duration))
    val back = stringResource(R.string.replay_back_30)
    val forward = stringResource(R.string.replay_forward_30)
    return semantics {
        contentDescription = position
        progressBarRangeInfo = ProgressBarRangeInfo(minute * SECONDS_PER_MINUTE, 0f..duration.toFloat())
        setProgress { seek(it.toDouble()); true }
        customActions = listOf(
            CustomAccessibilityAction(back) { seek(time.doubleValue - STEP_S); true },
            CustomAccessibilityAction(forward) { seek(time.doubleValue + STEP_S); true },
        )
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
