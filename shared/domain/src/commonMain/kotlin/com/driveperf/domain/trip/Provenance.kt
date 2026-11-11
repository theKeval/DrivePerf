package com.driveperf.domain.trip

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * How a number was obtained (F-DQ-05, PRD §5). [label] and [meaning] are the legend's words (S-DQ-01 "What the labels
 * mean", components.md §10.2).
 */
public enum class Provenance(public val label: String, public val meaning: String) {
    OBSERVED("Observed", "Measured directly by a sensor or the phone."),
    DERIVED("Derived", "Calculated from measured data, the same way every time."),
    ESTIMATED("Estimated", "Inferred or filled in where data was missing."),
    ;

    /** The lower-case word used in exports (the CSV `provenance` column, S-PRV-04). */
    public val exportName: String get() = name.lowercase()

    public companion object {
        /**
         * Provenance of one preview or R1 track row by its quality code ([PreviewRow.quality]): 0 observed and
         * 3 excluded were measured; 1 is estimated. 2 is a gap with no data, so it has none.
         */
        public fun ofTrackQuality(code: Int): Provenance? = when (code) {
            0, 3 -> OBSERVED
            1 -> ESTIMATED
            else -> null
        }
    }
}

/**
 * Display units with the most decimals each may show: never more than the measurement's accuracy (F-DQ-05 AC3,
 * pipeline-and-scoring.md §3, brand.md "Honest instruments"). [perSi] converts the stored SI value to the unit.
 */
public enum class DisplayUnit(public val symbol: String, public val decimals: Int, public val perSi: Double) {
    /** Distance from metres, to 0.1 km. */
    KILOMETRES("km", 1, 0.001),

    /** Speed from m/s, as an integer. */
    KM_PER_HOUR("km/h", 0, 3.6),

    /** Acceleration in g, to 0.01. */
    G("g", 2, 1.0),

    /** Elevation in metres, as an integer. */
    METRES("m", 0, 1.0),

    /** Durations from seconds, in whole minutes. */
    MINUTES("min", 0, 1.0 / 60),

    /** Short durations (an event) in seconds, to 0.1 s. */
    SECONDS("s", 1, 1.0),

    /** Shares as whole percent. The stored value is already a percentage. */
    PERCENT("%", 0, 1.0),

    /** Sample rates as whole hertz. */
    HERTZ("Hz", 0, 1.0),

    /** Counts and 0–100 scores. */
    WHOLE("", 0, 1.0),
    ;

    /** [si] converted to this unit and rounded half away from zero to [decimals]. */
    public fun round(si: Double): Double {
        val scale = POW10[decimals]
        val v = si * perSi * scale
        return (if (v < 0) -(abs(v).roundToLong()) else v.roundToLong()) / scale
    }

    /** [si] as text at this unit's precision, without the symbol: "12.3", "0.42", "−3". English only (Gate 2). */
    public fun format(si: Double): String {
        val scaled = (abs(si * perSi) * POW10[decimals]).roundToLong()
        val whole = scaled / POW10[decimals].toLong()
        val frac = scaled % POW10[decimals].toLong()
        val sign = if (si * perSi < 0 && scaled != 0L) MINUS else ""
        return if (decimals == 0) "$sign$whole" else "$sign$whole.${frac.toString().padStart(decimals, '0')}"
    }

    private companion object {
        val POW10 = doubleArrayOf(1.0, 10.0, 100.0)

        /** A real minus sign (brand.md). */
        const val MINUS = "−"
    }
}

/**
 * Every user-visible metric of PRD §5 with its provenance and a one-line meaning, shown on long-press or the ⓘ
 * (F-DQ-05 AC1, analytics `provenance_viewed {metric}` uses [key]). [unit] is null for metrics that are not a single
 * number. [partlyEstimated] metrics become Estimated for a trip whose value includes estimated data (an estimated
 * start, gap fills): pass that to [provenance].
 */
public enum class Metric(
    public val key: String,
    private val base: Provenance,
    public val meaning: String,
    public val unit: DisplayUnit?,
    public val partlyEstimated: Boolean = false,
) {
    START(
        "start", Provenance.OBSERVED, "When the car started moving. Estimated when filled in from motion history.", null, true,
    ),
    END("end", Provenance.OBSERVED, "When the car stopped for the last time.", null),
    DURATION("duration", Provenance.DERIVED, "End time minus start time.", DisplayUnit.MINUTES),
    MOVING_TIME("movingTime", Provenance.DERIVED, "Time spent above walking pace.", DisplayUnit.MINUTES),
    STOPPED_TIME("stoppedTime", Provenance.DERIVED, "Time spent stopped or crawling.", DisplayUnit.MINUTES),
    DISTANCE(
        "distance", Provenance.DERIVED,
        "Added up from accurate GPS fixes. Estimated stretches are counted and marked.", DisplayUnit.KILOMETRES, true,
    ),
    ESTIMATED_DISTANCE(
        "estimatedDistance", Provenance.ESTIMATED, "The part of the distance filled in where GPS was missing.",
        DisplayUnit.KILOMETRES,
    ),
    ROUTE_SHAPE(
        "routeShape", Provenance.OBSERVED, "Your GPS track, cleaned and simplified. Estimated parts are dashed.", null, true,
    ),
    CURRENT_SPEED(
        "currentSpeed", Provenance.OBSERVED, "GPS speed at this moment, from accurate fixes only.", DisplayUnit.KM_PER_HOUR,
    ),
    MAX_SPEED("maxSpeed", Provenance.DERIVED, "The highest steady GPS speed, with outliers rejected.", DisplayUnit.KM_PER_HOUR),
    AVG_MOVING_SPEED("avgMovingSpeed", Provenance.DERIVED, "Distance divided by moving time.", DisplayUnit.KM_PER_HOUR),
    STOPS("stops", Provenance.DERIVED, "Times the car stood still for 10 seconds or more.", DisplayUnit.WHOLE),
    ELEVATION("elevation", Provenance.OBSERVED, "GPS altitude, smoothed.", DisplayUnit.METRES),
    ELEVATION_GAIN_LOSS(
        "elevationGainLoss", Provenance.DERIVED, "Climb and descent, ignoring changes under 5 m.",
        DisplayUnit.METRES,
    ),
    LONG_G(
        "longG", Provenance.DERIVED, "Speeding up and slowing down, from the motion sensors turned to face the car.",
        DisplayUnit.G,
    ),
    LAT_G(
        "latG", Provenance.DERIVED, "Sideways force in turns, from the motion sensors turned to face the car.",
        DisplayUnit.G,
    ),
    GG("gg", Provenance.DERIVED, "Every moment's braking, acceleration and cornering force on one chart.", DisplayUnit.G),
    EVENT_PEAK_G("eventPeakG", Provenance.DERIVED, "The strongest filtered force during the event.", DisplayUnit.G),
    EVENT_DURATION("eventDuration", Provenance.DERIVED, "How long the force stayed above the threshold.", DisplayUnit.SECONDS),
    EVENTS(
        "events", Provenance.DERIVED, "Hard braking, acceleration and cornering, found by fixed thresholds.",
        DisplayUnit.WHOLE,
    ),
    EVENT_CONFIDENCE(
        "eventConfidence", Provenance.ESTIMATED, "How sure we are, from data quality and how clearly the threshold was crossed.",
        null,
    ),
    SMOOTHNESS(
        "smoothness", Provenance.DERIVED, "How gentle and steady your inputs were, 0–100. Speed never raises it.",
        DisplayUnit.WHOLE,
    ),
    SMOOTHNESS_DELTA("smoothnessDelta", Provenance.DERIVED, "This drive's Smoothness minus your usual.", DisplayUnit.WHOLE),
    BASELINE("baseline", Provenance.DERIVED, "Your usual: a steady average of your recent scored drives.", DisplayUnit.WHOLE),
    CONTRIBUTORS(
        "contributors", Provenance.DERIVED, "Braking, acceleration, cornering and consistency compared with your usual.",
        null,
    ),
    EFFICIENCY(
        "efficiency", Provenance.ESTIMATED, "Habits linked to efficiency, compared with your usual. Never fuel or energy used.",
        null,
    ),
    TIP("tip", Provenance.DERIVED, "Picked by fixed rules from this drive's evidence.", null),
    CLEANEST_STRETCH(
        "cleanestStretch", Provenance.DERIVED, "The longest stretch without an abrupt input.",
        DisplayUnit.KILOMETRES,
    ),
    QUALITY_BADGE(
        "qualityBadge", Provenance.DERIVED, "From GPS coverage, sensor rate, phone alignment and what was left out.",
        null,
    ),
    IMU_RATE("imuRate", Provenance.OBSERVED, "Motion samples received per second.", DisplayUnit.HERTZ),
    GNSS_COVERAGE("gnssCoverage", Provenance.DERIVED, "Share of moving time with an accurate GPS fix.", DisplayUnit.PERCENT),
    EXCLUDED_TIME("excludedTime", Provenance.DERIVED, "Minutes left out of the score, and why.", DisplayUnit.MINUTES),
    ESTIMATED_START(
        "estimatedStart", Provenance.ESTIMATED, "From where you parked to the first accurate GPS fix.",
        DisplayUnit.KILOMETRES,
    ),
    ROUTE_RECORD("routeRecord", Provenance.DERIVED, "Your highest eligible Smoothness on this route.", DisplayUnit.WHOLE),
    WHERE_YOU_GAINED("whereYouGained", Provenance.DERIVED, "Each part of the route compared with your record run.", null),
    WEEKLY_AVERAGE("weeklyAverage", Provenance.DERIVED, "The mean of your scored drives in the period.", DisplayUnit.WHOLE),
    CHANGE_CLAIM(
        "changeClaim", Provenance.DERIVED, "Shown only when the change passes a statistical check.",
        DisplayUnit.PERCENT,
    ),
    FOCUS_PROGRESS("focusProgress", Provenance.DERIVED, "Drives that improved on your focus habit.", DisplayUnit.WHOLE),
    WRAPPED_TOTALS("wrappedTotals", Provenance.DERIVED, "Totals over your driver trips.", null),
    WRAPPED_COMPARISON("wrappedComparison", Provenance.ESTIMATED, "Compared with a vetted list of reference distances.", null),
    ARCHETYPE("archetype", Provenance.ESTIMATED, "Picked by fixed rules from the period's drives.", null),
    PART_OF_DAY("partOfDay", Provenance.DERIVED, "The local time the drive started.", null),
    BATTERY_USED("batteryUsed", Provenance.OBSERVED, "The phone's battery level change over the drive.", DisplayUnit.PERCENT),
    PREVIEW_DAYS_LEFT("previewDaysLeft", Provenance.OBSERVED, "Counted from when you first opened the app.", DisplayUnit.WHOLE),
    PRICES("prices", Provenance.OBSERVED, "As your app store shows them.", null),
    ;

    /** The metric's provenance; Estimated when [includesEstimate] and the metric can be partly estimated. */
    public fun provenance(includesEstimate: Boolean = false): Provenance =
        if (includesEstimate && partlyEstimated) Provenance.ESTIMATED else base

    public companion object {
        /** The metric with [key], or null. */
        public fun byKey(key: String): Metric? = entries.firstOrNull { it.key == key }
    }
}
