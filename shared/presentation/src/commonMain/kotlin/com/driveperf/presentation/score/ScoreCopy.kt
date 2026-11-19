package com.driveperf.presentation.score

import com.driveperf.domain.route.RoutePreset
import com.driveperf.domain.score.Band
import com.driveperf.domain.score.ContributorId
import com.driveperf.domain.score.RouteName
import com.driveperf.domain.trip.DriveContext
import com.driveperf.domain.trip.EventType
import kotlin.math.abs

/**
 * The score surfaces' fixed words (docs/design DRIVE-77, DRIVE-82, DRIVE-87; components.md §3, §4, §18). Reasons never
 * name a street or place: v1 has no geocoder (coordinator decision 2), so they use the design's fallback without one.
 */
public object ScoreCopy {
    /** A real minus sign, never a hyphen (brand.md). */
    private const val MINUS = "−"

    public fun contributorLabel(id: ContributorId): String = when (id) {
        ContributorId.BRAKING -> "Braking"
        ContributorId.ACCELERATION -> "Acceleration"
        ContributorId.CORNERING -> "Cornering"
        ContributorId.CONSISTENCY -> "Consistency"
    }

    /** "Smoother than usual" / "Usual" / "Rougher than usual". Rougher is neutral grey, never red. */
    public fun bandText(band: Band): String = when (band) {
        Band.SMOOTHER -> "Smoother than usual"
        Band.USUAL -> "Usual"
        Band.ROUGHER -> "Rougher than usual"
    }

    public fun bandGlyph(band: Band): String = when (band) {
        Band.SMOOTHER -> "▲"
        Band.USUAL -> "●"
        Band.ROUGHER -> "▼"
    }

    /**
     * The contributor's one-line reason for [reasonKey] (`"braking.rougher"`, pipeline `TripScorer`), with [events] the
     * channel's counted events. Braking, acceleration and cornering count their events in every band (the band chip
     * says how that compares); consistency has no events, so its reason follows its band. While calibrating there is
     * no band, so no key.
     */
    public fun reason(id: ContributorId, reasonKey: String?, events: Int): String = when (reasonKey) {
        "consistency.smoother" -> "Steadier speed than usual"
        "consistency.usual" -> "Your usual rhythm"
        "consistency.rougher" -> "More speed changes than usual"
        else -> counted(id, events)
    }

    /** "2 abrupt stops", "1 sharp start", "No hard corners this drive". */
    private fun counted(id: ContributorId, events: Int): String {
        val (one, many, none) = when (id) {
            ContributorId.BRAKING -> Triple("abrupt stop", "abrupt stops", "No abrupt stops this drive")
            ContributorId.ACCELERATION -> Triple("sharp start", "sharp starts", "No sharp starts this drive")
            ContributorId.CORNERING -> Triple("hard corner", "hard corners", "No hard corners this drive")
            ContributorId.CONSISTENCY -> return "How steady your speed was"
        }
        return when (events) {
            0 -> none
            1 -> "1 $one"
            else -> "$events $many"
        }
    }

    /** "Hard braking" */
    public fun eventLabel(type: EventType): String = when (type) {
        EventType.BRAKE -> "Hard braking"
        EventType.ACCEL -> "Hard acceleration"
        EventType.CORNER -> "Hard cornering"
    }

    /** The count chip's word: "Braking". */
    public fun eventCountLabel(type: EventType): String = when (type) {
        EventType.BRAKE -> "Braking"
        EventType.ACCEL -> "Acceleration"
        EventType.CORNER -> "Cornering"
    }

    /** "+2", "−3", "0". */
    public fun signed(n: Int): String = when {
        n > 0 -> "+$n"
        n < 0 -> "$MINUS${abs(n)}"
        else -> "0"
    }

    /** The route's name: its own, else its preset's, else null for an unnamed route. */
    public fun routeName(route: RouteName?): String? = route?.name?.takeIf { it.isNotBlank() } ?: route?.preset?.let(::presetName)

    public fun presetName(preset: RoutePreset): String? = when (preset) {
        RoutePreset.COMMUTE -> "The Commute"
        RoutePreset.GYM -> "Gym"
        RoutePreset.SCHOOL_RUN -> "School run"
        RoutePreset.CUSTOM -> null
    }

    /** "city", "highway", "mixed"; null without a context. */
    public fun contextWord(context: DriveContext?): String? = when (context) {
        DriveContext.CITY -> "city"
        DriveContext.HIGHWAY -> "highway"
        DriveContext.MIXED -> "mixed"
        null -> null
    }

    /** "on The Commute", "on this route", "city drives" or "drives": what the usual is of. */
    internal fun basisPhrase(onRoute: Boolean, routeName: String?, context: DriveContext?): String = if (onRoute) {
        "on ${routeName ?: "this route"}"
    } else {
        contextWord(context)?.let { "$it drives" } ?: "drives"
    }

    /** The chip: "+2 vs your usual on The Commute", "−3 vs your usual city drives", "Right on your usual". */
    public fun comparisonText(delta: Int, onRoute: Boolean, routeName: String?, context: DriveContext?): String =
        if (delta == 0) "Right on your usual" else "${signed(delta)} vs your usual ${basisPhrase(onRoute, routeName, context)}"

    /** "Calibrating your baseline · 2 of 3" */
    public fun calibratingText(k: Int, of: Int): String = "Calibrating your baseline · $k of $of"

    /** The first scorable drive ever (pipeline-and-scoring.md §5.7, components.md §3 "First drive"). */
    public const val FIRST_DRIVE: String = "Your baseline. Every drive from here is compared to you, not to anyone else."

    /** "2 above your usual on The Commute." for the ring's accessibility label. */
    internal fun spokenComparison(delta: Int, onRoute: Boolean, routeName: String?, context: DriveContext?): String {
        if (delta == 0) return "Right on your usual."
        val way = if (delta > 0) "above" else "below"
        return "${abs(delta)} $way your usual ${basisPhrase(onRoute, routeName, context)}."
    }

    public const val NOT_SCORED: String = "Not scored"

    /** F-SCO-07 AC2: a run at an unusual pace for its route sets no record. */
    public const val UNUSUAL_PACE: String = "Unusual pace — not counted for records"

    public const val TIP_KICKER: String = "One thing for next time"

    public const val NO_EVENTS_TITLE: String = "No abrupt inputs on this drive."
    public const val NO_EVENTS_BODY: String = "Nothing crossed the braking, acceleration or cornering thresholds."
    public const val EVENTS_UNSCORED: String = "Events aren’t detected when the phone moves around freely."

    public const val COMPARISON_FOOTER: String = "Every comparison is against your own drives. Never anyone else’s."
    public const val CONTRIBUTORS_FOOTER: String =
        "Each contributor is compared with your usual. Tap one with events to see the evidence."
    public const val NOT_SCORED_FOOTER: String = "Unscored drives don’t count toward your baseline, routes or weekly average."

    /** "Calibrating your baseline · 2 of 3. We compare each drive …" (S-SCO-01 calibrating). */
    public fun calibratingLine(k: Int, of: Int): String =
        "${calibratingText(k, of)}. We compare each drive with your own usual once we’ve seen three, and make no trend " +
            "claims before then."
}
