package com.driveperf.feature.replay

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpEvent
import com.driveperf.designsystem.DpEventGlyph
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.designsystem.dpEventColor
import com.driveperf.domain.trip.EventType
import com.driveperf.presentation.replay.ReplayEventPin
import com.driveperf.presentation.replay.ReplayOverlay

// The faces of the S-RPL-01 pins (components.md §9.1): what each pin draws inside its 48 dp target.

private val EVENT_PIN = 26.dp
private const val PULSE_SCALE = 2.4f
private const val PULSE_ALPHA = 0.75f

/** The glyph, with the pulse ring behind it while [pulse] runs from 0 to 1. */
@Composable
internal fun EventPinFace(pin: ReplayEventPin, pulse: Float) {
    val event = pin.marker.type.dp()
    val color = dpEventColor(event)
    val reduced = rememberReducedMotion()
    Box(contentAlignment = Alignment.Center) {
        if (pulse > 0f && pulse < 1f) {
            Canvas(Modifier.size(EVENT_PIN)) {
                val r = size.minDimension / 2
                if (reduced) {
                    drawCircle(color, radius = r + 3.dp.toPx(), style = Stroke(2.dp.toPx()), alpha = 1f - pulse)
                } else {
                    drawCircle(color, radius = r * (1f + (PULSE_SCALE - 1f) * pulse), alpha = PULSE_ALPHA * (1f - pulse))
                }
            }
        }
        DpEventGlyph(event, needed = pin.marker.needed, size = EVENT_PIN)
    }
}

/** A stop capsule with the pause bars (`icon-pinStop.svg`). */
@Composable
internal fun StopPinFace() {
    val data = DrivePerfTheme.dataColors
    Canvas(Modifier.size(width = 24.dp, height = 18.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(data.stop, size = size, cornerRadius = r)
        drawRoundRect(data.pinRing, size = size, cornerRadius = r, style = Stroke(1.5.dp.toPx()))
        val bar = 2.2.dp.toPx()
        val top = size.height * 0.28f
        val bottom = size.height * 0.72f
        for (x in listOf(size.width * 0.4f, size.width * 0.6f)) {
            drawLine(data.pinGlyph, Offset(x, top), Offset(x, bottom), bar, StrokeCap.Round)
        }
    }
}

/** The max speed: a neutral outline triangle, never animated or emphasised (D5/D6, motion.md §3.6). */
@Composable
internal fun MaxSpeedPinFace() {
    val data = DrivePerfTheme.dataColors
    Canvas(Modifier.size(22.dp)) {
        val path = Path().apply {
            moveTo(size.width / 2, size.height * 0.1f)
            lineTo(size.width * 0.92f, size.height * 0.88f)
            lineTo(size.width * 0.08f, size.height * 0.88f)
            close()
        }
        drawPath(path, data.mapLand)
        drawPath(path, data.maxSpeed, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
    }
}

/** The system's "Remove animations" setting. */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

internal fun EventType.dp(): DpEvent = when (this) {
    EventType.BRAKE -> DpEvent.BRAKE
    EventType.ACCEL -> DpEvent.ACCEL
    EventType.CORNER -> DpEvent.CORNER
}

/** The stats line under the scrubber: "103 km/h max · 2 stops · 2 events", or "No abrupt inputs on this drive." */
internal fun ReplayOverlay.statsLine(): String = noEventsText?.let { "$summary · $it" } ?: summary
