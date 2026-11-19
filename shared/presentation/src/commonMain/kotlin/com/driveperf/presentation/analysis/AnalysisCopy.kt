package com.driveperf.presentation.analysis

import com.driveperf.domain.analysis.AnalysisChannel
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.EventType
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The S-ANL-01, S-ANL-02 and S-ANL-03 copy (design specs DRIVE-45 and DRIVE-50) and number formatting, so both apps
 * show the same words and values (ADR-002 section 4, coordinator decision 3).
 */
public object AnalysisCopy {
    public const val TITLE: String = "Analysis"
    public const val GG_TITLE: String = "Friction circle"
    public const val CHANNELS_TITLE: String = "Channels"
    public const val CHANNELS_SUB: String = "Choose what the timeline shows. Your choice is remembered."
    public const val CHANNELS_PRO_SECTION: String = "Pro channels"
    public const val CHANNELS_FOOTER: String =
        "Jerk shows how sharply G changes; sample quality marks every observed, estimated, missing or left-out sample."
    public const val LAST_CHANNEL: String = "Keep at least one channel on"
    public const val AXIS_TIME: String = "Time"
    public const val AXIS_DISTANCE: String = "Distance"
    public const val FOOTNOTE: String =
        "Drag the timeline to move the cursor; pinch or use − / + to zoom. Tap the map to jump to that point."
    public const val NOT_FOUND: String = "This drive’s analysis isn’t available."
    public const val SUMMARY_ONLY: String = "Detailed trace not kept. The timeline shows the drive’s summary track."
    public const val NO_MAP: String = "No map for this drive: there was no GPS."
    public const val GG_EMPTY: String = "Nothing on this drive could be measured fairly enough to plot."
    public const val GG_FOOTER: String = "Peak G is for your own review. It never appears on share cards, records or Wrapped."
    public const val GG_HOW_1: String =
        "Each dot is a moment of the drive: up is accelerating, down is braking, left and right are turns. The outline " +
            "is your envelope, the furthest you went in each direction."
    public const val GG_HOW_2: String =
        "Smoother drives stay close to the middle. The scale is fixed at ±0.6 g so drives stay comparable."
    public const val GG_HOW_TITLE: String = "How to read it"
    public const val GG_PEAKS_TITLE: String = "Peaks on this drive"
    public const val GG_AXES: String = "Accel ↑ · Brake ↓ · ← Left · Right →"
    public const val NO_VALUE: String = "—"
    public const val REPLAY: String = "Replay"

    /** "Preparing 33 min of channels…" (S-ANL-01 Load). */
    public fun loading(durationS: Double?): String =
        if (durationS == null || durationS <= 0) "Preparing channels…" else "Preparing ${minutes(durationS)} min of channels…"

    public fun channelTitle(channel: AnalysisChannel): String = when (channel) {
        AnalysisChannel.SPEED -> "Speed"
        AnalysisChannel.LON_G -> "Longitudinal G"
        AnalysisChannel.LAT_G -> "Lateral G"
        AnalysisChannel.ELEVATION -> "Elevation"
        AnalysisChannel.JERK -> "Jerk"
        AnalysisChannel.QUALITY -> "Sample quality"
    }

    /** The short lane label on the timeline. */
    public fun laneTitle(channel: AnalysisChannel): String = when (channel) {
        AnalysisChannel.LON_G -> "Long G"
        AnalysisChannel.LAT_G -> "Lat G"
        else -> channelTitle(channel)
    }

    public fun channelDescription(channel: AnalysisChannel): String = when (channel) {
        AnalysisChannel.SPEED -> "How fast you went"
        AnalysisChannel.LON_G -> "Speeding up and slowing down"
        AnalysisChannel.LAT_G -> "Turning left and right"
        AnalysisChannel.ELEVATION -> "Up and down hills"
        AnalysisChannel.JERK -> "How sharply G changes"
        AnalysisChannel.QUALITY -> "Observed, estimated, gaps, left out"
    }

    /** How the line looks, for the picker's accessibility label (patterns, not colour alone). */
    public fun channelPattern(channel: AnalysisChannel): String = when (channel) {
        AnalysisChannel.SPEED -> "solid grey line"
        AnalysisChannel.LON_G -> "solid blue line"
        AnalysisChannel.LAT_G -> "dashed amber line"
        AnalysisChannel.ELEVATION -> "shaded area"
        AnalysisChannel.JERK -> "thin solid line"
        AnalysisChannel.QUALITY -> "band of patterns"
    }

    public fun typeTitle(type: EventType): String = when (type) {
        EventType.BRAKE -> "Braking"
        EventType.ACCEL -> "Accel"
        EventType.CORNER -> "Cornering"
    }

    /** "Hard braking", for ticks and pins. */
    public fun typeSpoken(type: EventType): String = when (type) {
        EventType.BRAKE -> "Hard braking"
        EventType.ACCEL -> "Quick acceleration"
        EventType.CORNER -> "Sharp cornering"
    }

    /** The `gg_filter {type}` value. */
    public fun filterKey(type: EventType?): String = when (type) {
        null -> "all"
        EventType.BRAKE -> "braking"
        EventType.ACCEL -> "accel"
        EventType.CORNER -> "cornering"
    }

    // Numbers -----------------------------------------------------------------------------------------------------

    /** "12:04" (minutes:seconds) or "1:02:04". */
    public fun clock(seconds: Double): String {
        val total = maxOf(0.0, seconds).roundToLong()
        val h = total / SECONDS_PER_HOUR
        val m = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val s = total % SECONDS_PER_MINUTE
        return if (h > 0) "$h:${two(m)}:${two(s)}" else "$m:${two(s)}"
    }

    /** Whole minutes, at least 1 for any time at all. */
    public fun minutes(seconds: Double): Long = maxOf(1L, (seconds / SECONDS_PER_MINUTE).roundToLong())

    /** [value] with [decimals] fixed decimals, a real minus sign, and no `-0`. */
    public fun fixed(value: Double, decimals: Int): String {
        var scale = 1L
        repeat(decimals) { scale *= TEN }
        val scaled = (abs(value) * scale).roundToLong()
        val whole = scaled / scale
        val frac = scaled % scale
        val sign = if (value < 0 && scaled != 0L) MINUS else ""
        return if (decimals == 0) "$sign$whole" else "$sign$whole.${frac.toString().padStart(decimals, '0')}"
    }

    /** "+0.12 g", "−0.21 g". */
    public fun signedG(g: Double): String = (if (g > 0 && fixed(g, 2) != "0.00") "+" else "") + fixed(g, 2) + " g"

    /** "0.44 g". */
    public fun g(g: Double): String = fixed(abs(g), 2) + " g"

    public fun speedUnit(units: UnitSystem): String = if (units == UnitSystem.METRIC) "km/h" else "mph"

    public fun distanceUnit(units: UnitSystem): String = if (units == UnitSystem.METRIC) "km" else "mi"

    public fun spokenSpeedUnit(units: UnitSystem): String =
        if (units == UnitSystem.METRIC) "kilometres per hour" else "miles per hour"

    /** m/s in the user's speed unit. */
    public fun speedValue(mps: Double, units: UnitSystem): Double =
        mps * if (units == UnitSystem.METRIC) MPS_TO_KMH else MPS_TO_MPH

    /** Metres in the user's distance unit. */
    public fun distanceValue(m: Double, units: UnitSystem): Double = m / if (units == UnitSystem.METRIC) M_PER_KM else M_PER_MI

    /** "64 km/h". */
    public fun speed(mps: Double, units: UnitSystem): String = "${fixed(speedValue(mps, units), 0)} ${speedUnit(units)}"

    /** "9.8 km". */
    public fun distance(m: Double, units: UnitSystem): String = "${fixed(distanceValue(m, units), 1)} ${distanceUnit(units)}"

    /** "184 m" or "604 ft". */
    public fun elevation(m: Double, units: UnitSystem): String =
        if (units == UnitSystem.METRIC) "${fixed(m, 0)} m" else "${fixed(m * FT_PER_M, 0)} ft"

    private fun two(v: Long): String = v.toString().padStart(2, '0')

    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3600L
    private const val TEN = 10L
    private const val MINUS = "−"
    internal const val MPS_TO_KMH = 3.6
    internal const val MPS_TO_MPH = 2.2369362920544
    private const val M_PER_KM = 1000.0
    private const val M_PER_MI = 1609.344
    private const val FT_PER_M = 3.280839895
}
