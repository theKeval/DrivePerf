package com.driveperf.presentation.analysis

import com.driveperf.domain.analysis.AnalysisChannel
import com.driveperf.domain.analysis.GgPoint
import com.driveperf.domain.analysis.LanePath
import com.driveperf.domain.replay.SegmentStyle
import com.driveperf.domain.trip.EventType

// What the apps draw for S-ANL-01/02/03. Every x is a share of the visible window, `[0, 1]` left to right, and every
// lane y a share of the lane height, `[0, 1]` bottom to top, so the apps only scale to their own pixels.

/**
 * One timeline lane. [path] is null for [AnalysisChannel.QUALITY], which draws [AnalysisTimeline.bands] as a strip,
 * and when the trip has no data for the channel ([empty]). The line is dashed left of [dashedUntilX] (the estimated
 * start, F-RPL-08). [zeroY] is the zero line of the G and jerk lanes.
 */
public data class TimelineLane(
    val channel: AnalysisChannel,
    val title: String,
    val rangeLabel: String,
    val path: LanePath?,
    val dashedUntilX: Double,
    val zeroY: Double?,
) {
    public val empty: Boolean get() = path == null && channel != AnalysisChannel.QUALITY
}

/** A quality overlay band: estimated start (dashed), no GPS (filled, the speed line breaks), excluded (hatched). */
public data class TimelineBand(val style: SegmentStyle, val x0: Double, val x1: Double, val label: String?)

/** An event tick on the timeline's event lane, aligned with the map pins (F-RPL-05 AC3). */
public data class EventTick(
    val eventId: String,
    val type: EventType,
    val needed: Boolean,
    val x: Double,
    val tPeakS: Double,
    val accessibilityLabel: String,
)

/** An x-axis label. */
public data class AxisTick(val x: Double, val label: String)

/** What the legend shows, swatch and pattern. */
public enum class LegendKey { SPEED, LON_G, LAT_G, ELEVATION, JERK, ESTIMATED, NO_GPS, NOT_SCORED }

public data class LegendItem(val key: LegendKey, val label: String)

/** A lane's value at the cursor: [text] for the bubble ("—" in a gap) and its height [y] (`NaN` when none). */
public data class LaneValue(val channel: AnalysisChannel, val text: String, val y: Double)

/**
 * Everything under the cursor at [tS] (F-RPL-05 AC2): the readout row, one bubble per lane, the map marker and the
 * G-G dot. Built by [AnalysisTimeline.readout] in the same frame as the drag.
 *
 * @property chip "No GPS" / "Phone moved — not scored" / "Estimated start" while the cursor is in such a span.
 * @property marker the map marker, null in a GPS gap (the marker is hidden, never interpolated).
 * @property gg the G-G dot, null where nothing scorable was measured.
 */
public data class CursorReadout(
    val tS: Double,
    val x: Double,
    val headline: String,
    val chip: String?,
    val values: List<LaneValue>,
    val marker: MapPoint?,
    val gg: GgPoint?,
    val accessibilityValue: String,
)

/** A point on [AnalysisMap], in metres east ([x]) and north ([y]) of the map's south-west corner. */
public data class MapPoint(val x: Double, val y: Double)

/** A stretch of the route in one style, as two coordinate arrays in metres. */
public class MapSegment(public val style: SegmentStyle, public val xs: DoubleArray, public val ys: DoubleArray)

/** An event pin on the map. Only pinned events (a position in measured time) appear. */
public data class MapPin(val eventId: String, val type: EventType, val needed: Boolean, val at: MapPoint)

/** A stat tile beside the G-G card. [detail] is the line under the value, if any. */
public data class StatTile(val title: String, val value: String, val detail: String?)

/** One row of S-ANL-03. [enabled] is false when the trip has no data for it (elevation without altitude). */
public data class ChannelOption(
    val channel: AnalysisChannel,
    val title: String,
    val description: String,
    val on: Boolean,
    val pro: Boolean,
    val enabled: Boolean,
    val accessibilityLabel: String,
)

/** A cloud dot of the G-G plot. [emphasised] is false when the filter is on another event type (drawn at 6 %). */
public data class GgDot(val latG: Double, val lonG: Double, val weight: Double, val emphasised: Boolean)

/** An event pin at its peak on the G-G plot. */
public data class GgPin(val eventId: String, val type: EventType, val needed: Boolean, val at: GgPoint)

/** A filter chip of S-ANL-02: [type] null is "All". */
public data class GgFilterChip(val type: EventType?, val label: String, val selected: Boolean)

/** One "Peaks on this drive" row: per event type, its count and its largest event. */
public data class GgPeakRow(val type: EventType, val title: String, val detail: String, val value: String, val eventId: String)

/** A labelled ring, every 0.1 g. */
public data class GgRing(val g: Double, val label: String)

/**
 * The friction circle (F-RPL-06), for the S-ANL-01 card and S-ANL-02. Excluded spans are not plotted (AC3).
 *
 * @property caption "86% of your inputs stayed within 0.2 g", null with nothing to plot.
 * @property filterCaption "Showing cornering, 31 moments" while a filter is on (colour is not the only cue).
 * @property partBanner "3 min left out: phone moved. …" when part of the drive was excluded.
 */
public data class GgView(
    val dots: List<GgDot>,
    val envelope: List<GgPoint>,
    val pins: List<GgPin>,
    val rings: List<GgRing>,
    val scaleG: Double,
    val caption: String?,
    val filterCaption: String?,
    val filters: List<GgFilterChip>,
    val peaks: List<GgPeakRow>,
    val partBanner: String?,
    val accessibilityLabel: String,
) {
    public val empty: Boolean get() = dots.isEmpty()
}

/** A row of the Android "View as table" action: every 30 s plus every event. */
public data class TableRow(val time: String, val speed: String, val lonG: String, val latG: String, val note: String?)
