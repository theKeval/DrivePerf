package com.driveperf.presentation.score

import com.driveperf.domain.route.RoutePreset
import com.driveperf.domain.score.RouteName
import com.driveperf.domain.trip.DriveContext
import kotlin.math.abs

/**
 * The comparison words (components.md §3, §4; DRIVE-77, DRIVE-82): every comparison is with the user's own route or
 * drives of the same kind, never with anyone else (F-SCO-01 AC3).
 */
public object ComparisonCopy {
    /** A real minus sign, never a hyphen (brand.md). */
    private const val MINUS = "\u2212"

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

    /** "Calibrating your baseline · 2 of 3. We compare each drive …" (S-SCO-01 calibrating). */
    public fun calibratingLine(k: Int, of: Int): String =
        "${calibratingText(k, of)}. We compare each drive with your own usual once we’ve seen three, and make no trend " +
            "claims before then."
}
