package com.driveperf.feature.replay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DoubleState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.domain.replay.ElevationProfile

internal const val ELEVATION_TAG = "replay_elevation"

/**
 * The elevation mini-profile under the scrubber (F-RPL-02 AC4): a 24 dp area of the smoothed GNSS altitude on the same
 * time axis as the scrubber, with a cursor at the marker that moves with [time] without recomposing, then the total
 * climb and descent ("↗ 118 m ↘ 103 m").
 */
@Composable
internal fun ElevationRow(profile: ElevationProfile, durationS: Double, text: String, time: DoubleState) {
    val data = DrivePerfTheme.dataColors
    Row(
        Modifier.fillMaxWidth().testTag(ELEVATION_TAG).clearAndSetSemantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(
            Modifier.weight(1f).height(24.dp).drawWithCache {
                val span = durationS.coerceAtLeast(1.0)
                fun x(t: Double) = (t / span * size.width).toFloat()
                fun y(fraction: Double) = (size.height * (1 - fraction)).toFloat()
                val range = (profile.maxM - profile.minM).takeIf { it > 0 }
                fun fraction(m: Double) = range?.let { (m - profile.minM) / it } ?: HALF
                val line = Path()
                profile.points.forEachIndexed { i, p ->
                    if (i == 0) line.moveTo(x(p.tS), y(fraction(p.elevM))) else line.lineTo(x(p.tS), y(fraction(p.elevM)))
                }
                val area = Path().apply {
                    addPath(line)
                    lineTo(x(profile.points.last().tS), size.height)
                    lineTo(x(profile.points.first().tS), size.height)
                    close()
                }
                val stroke = Stroke(1.5.dp.toPx())
                val cursorW = 1.5.dp.toPx()
                val dot = 3.dp.toPx()
                onDrawBehind {
                    drawPath(area, data.elevationFill)
                    drawPath(line, data.elevation, style = stroke)
                    val t = time.doubleValue
                    val cx = x(t)
                    drawLine(data.cursor, Offset(cx, 0f), Offset(cx, size.height), cursorW)
                    drawCircle(data.cursor, dot, Offset(cx, y(profile.heightFractionAt(t))))
                }
            },
        )
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val HALF = 0.5
