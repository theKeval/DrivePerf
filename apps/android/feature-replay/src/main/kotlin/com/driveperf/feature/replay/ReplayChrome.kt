package com.driveperf.feature.replay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DoubleState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpIcons
import com.driveperf.domain.replay.SegmentStyle

internal const val SPAN_CHIP_TAG = "replay_span_chip"

/** Back · "Replay" + subtitle · legend (S-RPL-01 top cluster; Layers and Analysis arrive with their own stories). */
@Composable
internal fun TopBar(header: ReplayHeader, onLegend: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalIconButton(onClick = header.onBack, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(DpIcons.Back), contentDescription = stringResource(R.string.replay_back))
        }
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(
                    stringResource(R.string.replay_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                if (header.subtitle.isNotEmpty()) {
                    Text(
                        header.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        onLegend?.let { open ->
            FilledTonalIconButton(onClick = open, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_replay_info), contentDescription = stringResource(R.string.replay_legend))
            }
        }
    }
}

/** Speed · Elapsed · Distance (S-RPL-01). Speed is neutral text; without GPS it reads "—". */
@Composable
internal fun Readouts(state: ReplayUiState.Ready, time: DoubleState, modifier: Modifier = Modifier) {
    val units = state.units
    val speed by remember(state.replay, units) {
        derivedStateOf { ReplayFormatting.speed(state.replay.frameAt(time.doubleValue).speedMps, units) }
    }
    val elapsed by remember { derivedStateOf { ReplayFormatting.clock(time.doubleValue) } }
    val distance by remember(state.timeline, units) {
        derivedStateOf { ReplayFormatting.distance(state.timeline.distanceAt(time.doubleValue), units) }
    }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        // Intrinsic height: at XL text the labels wrap below the values instead of being clipped.
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 64.dp).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val speedText = speed
            if (speedText == null) {
                Readout(stringResource(R.string.replay_no_value), "", R.string.replay_speed)
            } else {
                Readout(speedText, ReplayFormatting.speedUnit(units), R.string.replay_speed)
            }
            VerticalDivider()
            Readout(elapsed, "", R.string.replay_elapsed)
            VerticalDivider()
            Readout(distance, ReplayFormatting.distanceUnit(units), R.string.replay_distance)
        }
    }
}

@Composable
private fun RowScope.Readout(value: String, unit: String, label: Int) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (unit.isNotEmpty()) {
                Text(" $unit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The span chip while the marker is inside an overlay span (F-RPL-08 AC2). */
@Composable
internal fun SpanChip(state: ReplayUiState.Ready, time: DoubleState, modifier: Modifier = Modifier) {
    val span by remember(state.timeline) { derivedStateOf { state.timeline.spanAt(time.doubleValue) } }
    val current = span ?: return
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.testTag(SPAN_CHIP_TAG),
    ) {
        Row(
            Modifier.heightIn(min = 32.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LineSwatch(current, Modifier.width(20.dp).height(12.dp))
            Text(stringResource(current.label()), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** A short sample of a line style, for the chip and the legend. */
@Composable
internal fun LineSwatch(style: SegmentStyle, modifier: Modifier = Modifier) {
    val look = lineLook(style)
    Canvas(modifier) {
        val start = Offset(0f, size.height / 2)
        val end = Offset(size.width, size.height / 2)
        val effect = if (look.dash.isEmpty()) null else PathEffect.dashPathEffect(look.dash.map { it.toPx() }.toFloatArray())
        look.casing?.let { drawLine(it, start, end, look.casingWidth.toPx(), StrokeCap.Round) }
        drawLine(look.color, start, end, look.width.toPx().coerceAtLeast(2.dp.toPx()), StrokeCap.Round, effect)
    }
}

/** A small note over the map: offline, loading tiles, summary only, or the "Play your drive" hint. */
@Composable
internal fun Note(text: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    }
}
