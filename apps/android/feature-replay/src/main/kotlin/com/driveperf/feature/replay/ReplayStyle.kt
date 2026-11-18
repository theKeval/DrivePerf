package com.driveperf.feature.replay

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpEstimated
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.domain.replay.SegmentStyle

/** How a line looks: dashes are `on/off` lengths in dp, empty for solid. One table for both renderers and the legend. */
@Immutable
internal data class LineLook(
    val color: Color,
    val width: Dp,
    val dash: List<Dp> = emptyList(),
    val casing: Color? = null,
    val casingWidth: Dp = 0.dp,
)

/**
 * S-RPL-01 route styles (F-RPL-08 AC1): observed solid on a casing, estimated dashed 4/4 at 60 %, no GPS a thin dotted
 * gap stroke 1/6 (a broken line, never the route), excluded the faded route.
 */
@Composable
internal fun lineLook(style: SegmentStyle): LineLook {
    val data = DrivePerfTheme.dataColors
    return when (style) {
        SegmentStyle.OBSERVED -> LineLook(data.route, ROUTE_WIDTH, casing = data.routeCasing, casingWidth = CASING_WIDTH)
        SegmentStyle.ESTIMATED -> LineLook(data.route.copy(alpha = DpEstimated.ALPHA), ROUTE_WIDTH, listOf(4.dp, 4.dp))
        SegmentStyle.NO_GPS -> LineLook(data.gapStroke, GAP_WIDTH, listOf(1.dp, 6.dp))
        SegmentStyle.EXCLUDED -> LineLook(data.ghost, ROUTE_WIDTH)
    }
}

/** The chip and legend words for a style ("Estimated start", "No GPS", "Phone moved — not scored"). */
@StringRes
internal fun SegmentStyle.label(): Int = when (this) {
    SegmentStyle.OBSERVED -> R.string.replay_span_observed
    SegmentStyle.ESTIMATED -> R.string.replay_span_estimated
    SegmentStyle.NO_GPS -> R.string.replay_span_no_gps
    SegmentStyle.EXCLUDED -> R.string.replay_span_excluded
}

@StringRes
internal fun SegmentStyle.legend(): Int = when (this) {
    SegmentStyle.OBSERVED -> R.string.replay_legend_observed
    SegmentStyle.ESTIMATED -> R.string.replay_legend_estimated
    SegmentStyle.NO_GPS -> R.string.replay_legend_no_gps
    SegmentStyle.EXCLUDED -> R.string.replay_legend_excluded
}

/** Draw order: faint styles first, so the observed route sits on top where segments meet. */
internal val DrawOrder = listOf(SegmentStyle.NO_GPS, SegmentStyle.EXCLUDED, SegmentStyle.ESTIMATED, SegmentStyle.OBSERVED)

internal val ROUTE_WIDTH = 3.8.dp
internal val CASING_WIDTH = 7.5.dp
private val GAP_WIDTH = 1.5.dp

/** Marker (S-RPL-01): 16 dp white dot, 3 dp route ring, 36 dp halo at 20 %, 18 dp heading wedge of 40°. */
internal object MarkerLook {
    val dot = 16.dp
    val ring = 3.dp
    val halo = 36.dp
    const val HALO_ALPHA = 0.2f
    val wedge = 18.dp
    const val WEDGE_DEGREES = 40f
}
