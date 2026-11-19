package com.driveperf.presentation.analysis

import com.driveperf.domain.analysis.AnalysisAxis
import com.driveperf.domain.analysis.AnalysisChannel
import com.driveperf.domain.analysis.AxisScale
import com.driveperf.domain.analysis.LaneRange
import com.driveperf.domain.analysis.TripAnalysis
import com.driveperf.domain.replay.SegmentStyle
import com.driveperf.domain.settings.UnitSystem
import kotlin.math.abs
import kotlin.math.ceil

/** Where the cursor lands after a drag or tap: on an event's peak when it snapped to one (haptic selection). */
public data class CursorSnap(val tS: Double, val eventId: String?)

/**
 * The timeline for one window `[x0, x1)` of one axis, with the user's channels (S-ANL-01). Built when the axis, zoom,
 * window, channels or units change, never per frame; the cursor reads it through [readout], [timeAt] and [snap].
 */
@Suppress("LongParameterList", "TooManyFunctions") // the lanes and their per-frame lookups
public class AnalysisTimeline internal constructor(
    public val axis: AnalysisAxis,
    public val x0: Double,
    public val x1: Double,
    public val lanes: List<TimelineLane>,
    public val bands: List<TimelineBand>,
    public val ticks: List<EventTick>,
    public val axisTicks: List<AxisTick>,
    public val legend: List<LegendItem>,
    public val summary: String,
    private val analysis: TripAnalysis,
    private val map: AnalysisMap?,
    private val scale: AxisScale,
    private val ranges: Map<AnalysisChannel, LaneRange>,
    private val units: UnitSystem,
) {
    private val width: Double get() = x1 - x0

    /** Trip time at [x], a share of the window. */
    public fun timeAt(x: Double): Double = scale.tOf(x0 + x.coerceIn(0.0, 1.0) * width)

    /** [tS] as a share of the window (outside `[0, 1]` when it is off screen). */
    public fun xOf(tS: Double): Double = if (width <= 0) 0.0 else (scale.xOf(tS) - x0) / width

    /** The cursor at [x]; within [tolerance] (a share of the window, 8 px on screen) of a tick it snaps to its peak. */
    public fun snap(x: Double, tolerance: Double): CursorSnap {
        val tick = ticks.filter { abs(it.x - x) <= tolerance }.minByOrNull { abs(it.x - x) }
        return if (tick != null) CursorSnap(tick.tPeakS, tick.eventId) else CursorSnap(timeAt(x), null)
    }

    /** Everything under the cursor at [tS], for the readout row, the lane bubbles, the map marker and the G-G dot. */
    public fun readout(tS: Double): CursorReadout {
        val t = tS.coerceIn(0.0, analysis.data.durationS)
        val chip = analysis.data.bands.firstOrNull { t >= it.t0S && t < it.t1S }?.label
        val values = lanes.map { lane -> laneValue(lane.channel, t, chip) }
        val headline = "${AnalysisCopy.clock(t)} elapsed · ${AnalysisCopy.distance(analysis.data.distance.xOf(t), units)}"
        val spoken = listOf(headline.replace(" · ", ", ")) + values.map { "${AnalysisCopy.laneTitle(it.channel)} ${it.text}" }
        return CursorReadout(
            tS = t,
            x = xOf(t),
            headline = headline,
            chip = chip,
            values = values,
            marker = map?.markerAt(t),
            gg = analysis.data.gg.dotAt(t),
            accessibilityValue = (spoken + listOfNotNull(chip)).joinToString(". "),
        )
    }

    private fun laneValue(channel: AnalysisChannel, t: Double, chip: String?): LaneValue {
        if (channel == AnalysisChannel.QUALITY) return LaneValue(channel, chip ?: "Observed", Double.NaN)
        val v = analysis.data.track(channel)?.series?.valueAt(t) ?: Double.NaN
        val range = ranges[channel]
        return LaneValue(channel, if (v.isNaN()) AnalysisCopy.NO_VALUE else format(channel, v), range?.norm(v) ?: Double.NaN)
    }

    private fun format(channel: AnalysisChannel, v: Double): String = when (channel) {
        AnalysisChannel.SPEED -> AnalysisCopy.speed(v, units)
        AnalysisChannel.LON_G, AnalysisChannel.LAT_G -> AnalysisCopy.signedG(v)
        AnalysisChannel.ELEVATION -> AnalysisCopy.elevation(v, units)
        AnalysisChannel.JERK -> AnalysisCopy.fixed(v, 2) + " g/s"
        AnalysisChannel.QUALITY -> AnalysisCopy.NO_VALUE
    }

    /** The Android "View as table" rows (S-ANL-01 accessibility): every 30 s, plus every event at its peak. */
    public fun tableRows(): List<TableRow> {
        val d = analysis.data
        val steps = generateSequence(0.0) { it + TABLE_STEP_S }.takeWhile { it <= d.durationS }.map { it to null }.toList()
        val events = analysis.markers.map { it.tPeakS to AnalysisCopy.typeSpoken(it.type) }
        return (steps + events).sortedBy { it.first }.map { (t, note) ->
            TableRow(
                time = AnalysisCopy.clock(t),
                speed = d.speed?.series?.valueAt(t)?.takeUnless { it.isNaN() }?.let { AnalysisCopy.speed(it, units) } ?: AnalysisCopy.NO_VALUE,
                lonG = d.lonG?.series?.valueAt(t)?.takeUnless { it.isNaN() }?.let(AnalysisCopy::signedG) ?: AnalysisCopy.NO_VALUE,
                latG = d.latG?.series?.valueAt(t)?.takeUnless { it.isNaN() }?.let(AnalysisCopy::signedG) ?: AnalysisCopy.NO_VALUE,
                note = note,
            )
        }
    }

    internal companion object {
        /** Columns per lane: about 2 × 400 points whatever the trip length or zoom. */
        const val BUCKETS = 400
        const val TABLE_STEP_S = 30.0
        const val G_RANGE = 0.6
        const val JERK_RANGE = 0.3
        private const val AXIS_TICKS = 5
        private const val ELEVATION_MIN_SPAN_M = 10.0
        private const val ELEVATION_PAD = 0.1

        @Suppress("LongParameterList") // the window and the user's choices
        fun build(
            analysis: TripAnalysis,
            map: AnalysisMap?,
            axis: AnalysisAxis,
            x0: Double,
            x1: Double,
            channels: Set<AnalysisChannel>,
            units: UnitSystem,
        ): AnalysisTimeline {
            val data = analysis.data
            val scale = data.axis(axis)
            val width = (x1 - x0).takeIf { it > 0 } ?: 1.0
            fun xOf(t: Double) = (scale.xOf(t) - x0) / width
            val on = AnalysisChannel.entries.filter { it in channels }
            val ranges = on.mapNotNull { c -> range(analysis, c, units)?.let { c to it } }.toMap()
            val dashedUntil = if (data.estimatedUntilS > 0) xOf(data.estimatedUntilS).coerceIn(0.0, 1.0) else 0.0
            val lanes = on.map { c ->
                val range = ranges[c]
                TimelineLane(
                    channel = c,
                    title = AnalysisCopy.laneTitle(c),
                    rangeLabel = rangeLabel(c, range, units),
                    path = range?.let { r -> data.track(c)?.path(scale, x0, x0 + width, BUCKETS, r) },
                    dashedUntilX = dashedUntil,
                    zeroY = range?.takeIf { c in ZERO_LINE }?.norm(0.0),
                )
            }
            val bands = data.bands.map { TimelineBand(it.style, xOf(it.t0S), xOf(it.t1S), it.label) }
                .filter { it.x1 > 0.0 && it.x0 < 1.0 && it.x1 > it.x0 }
            val ticks = analysis.markers.map { m ->
                val needed = if (m.needed) " Marked as needed." else ""
                EventTick(
                    m.eventId, m.type, m.needed, xOf(m.tPeakS), m.tPeakS,
                    "${AnalysisCopy.typeSpoken(m.type)}, ${AnalysisCopy.clock(m.tPeakS)} into the drive.$needed",
                )
            }.filter { it.x in 0.0..1.0 }
            val axisTicks = (0 until AXIS_TICKS).map { i ->
                val f = i.toDouble() / (AXIS_TICKS - 1)
                val x = x0 + f * width
                AxisTick(f, if (axis == AnalysisAxis.TIME) AnalysisCopy.clock(x) else AnalysisCopy.distance(x, units))
            }
            return AnalysisTimeline(
                axis, x0, x0 + width, lanes, bands, ticks, axisTicks, legend(on, data.bands.map { it.style }.toSet()),
                summary(analysis, units), analysis, map, scale, ranges, units,
            )
        }

        private val ZERO_LINE = setOf(AnalysisChannel.LON_G, AnalysisChannel.LAT_G, AnalysisChannel.JERK)

        private fun range(analysis: TripAnalysis, channel: AnalysisChannel, units: UnitSystem): LaneRange? {
            val track = analysis.data.track(channel) ?: return if (channel == AnalysisChannel.QUALITY) LaneRange(0.0, 1.0) else null
            return when (channel) {
                AnalysisChannel.SPEED -> LaneRange(0.0, speedTopMps(track.series.maxValue(), units))
                AnalysisChannel.LON_G, AnalysisChannel.LAT_G -> LaneRange(-G_RANGE, G_RANGE)
                AnalysisChannel.JERK -> LaneRange(-JERK_RANGE, JERK_RANGE)
                AnalysisChannel.ELEVATION -> elevationRange(track.series.minValue(), track.series.maxValue())
                AnalysisChannel.QUALITY -> LaneRange(0.0, 1.0)
            }
        }

        /** 0–120 km/h (0–75 mph), raised in 20 km/h (15 mph) steps when the drive was faster. */
        private fun speedTopMps(maxMps: Double, units: UnitSystem): Double {
            val metric = units == UnitSystem.METRIC
            val base = if (metric) 120.0 else 75.0
            val step = if (metric) 20.0 else 15.0
            val factor = if (metric) AnalysisCopy.MPS_TO_KMH else AnalysisCopy.MPS_TO_MPH
            val shown = if (maxMps.isNaN()) 0.0 else maxMps * factor
            return maxOf(base, ceil(shown / step) * step) / factor
        }

        private fun elevationRange(min: Double, max: Double): LaneRange {
            val span = maxOf(max - min, ELEVATION_MIN_SPAN_M)
            val mid = (min + max) / 2
            val half = span * (1 + 2 * ELEVATION_PAD) / 2
            return LaneRange(mid - half, mid + half)
        }

        private fun rangeLabel(channel: AnalysisChannel, range: LaneRange?, units: UnitSystem): String = when {
            range == null -> AnalysisCopy.NO_VALUE
            channel == AnalysisChannel.SPEED -> "0–${AnalysisCopy.fixed(AnalysisCopy.speedValue(range.max, units), 0)} ${AnalysisCopy.speedUnit(units)}"
            channel == AnalysisChannel.LON_G || channel == AnalysisChannel.LAT_G -> "±${AnalysisCopy.fixed(G_RANGE, 1)} g"
            channel == AnalysisChannel.JERK -> "±${AnalysisCopy.fixed(JERK_RANGE, 1)} g/s"
            channel == AnalysisChannel.ELEVATION ->
                "${AnalysisCopy.elevation(range.min, units).substringBefore(' ')}–${AnalysisCopy.elevation(range.max, units)}"
            else -> ""
        }

        private fun legend(on: List<AnalysisChannel>, styles: Set<SegmentStyle>): List<LegendItem> = buildList {
            val lines = mapOf(
                AnalysisChannel.SPEED to LegendKey.SPEED,
                AnalysisChannel.LON_G to LegendKey.LON_G,
                AnalysisChannel.LAT_G to LegendKey.LAT_G,
                AnalysisChannel.ELEVATION to LegendKey.ELEVATION,
                AnalysisChannel.JERK to LegendKey.JERK,
            )
            on.forEach { c -> lines[c]?.let { add(LegendItem(it, AnalysisCopy.laneTitle(c))) } }
            if (SegmentStyle.ESTIMATED in styles) add(LegendItem(LegendKey.ESTIMATED, "Estimated"))
            if (SegmentStyle.NO_GPS in styles) add(LegendItem(LegendKey.NO_GPS, "No GPS"))
            if (SegmentStyle.EXCLUDED in styles) add(LegendItem(LegendKey.NOT_SCORED, "Not scored"))
        }

        /** "Timeline, 33 minutes. Speed peaked at 103 kilometres per hour. 2 events, the largest 0.44 g." */
        private fun summary(analysis: TripAnalysis, units: UnitSystem): String {
            val d = analysis.data
            val parts = mutableListOf("Timeline, ${AnalysisCopy.minutes(d.durationS)} minutes.")
            d.speed?.series?.maxValue()?.takeUnless { it.isNaN() }?.let {
                parts += "Speed peaked at ${AnalysisCopy.fixed(AnalysisCopy.speedValue(it, units), 0)} ${AnalysisCopy.spokenSpeedUnit(units)}."
            }
            val events = analysis.events
            parts += when (events.size) {
                0 -> "No events."
                1 -> "1 event, ${AnalysisCopy.g(events.first().peakG)}."
                else -> "${events.size} events, the largest ${AnalysisCopy.g(events.maxOf { abs(it.peakG) })}."
            }
            return parts.joinToString(" ")
        }
    }
}
