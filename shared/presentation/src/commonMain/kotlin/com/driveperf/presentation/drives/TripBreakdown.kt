package com.driveperf.presentation.drives

import com.driveperf.domain.score.Score
import com.driveperf.domain.score.ScoreBasis
import com.driveperf.domain.score.TripScoreDetail
import com.driveperf.domain.trip.Trip
import com.driveperf.presentation.score.BasisOptionUi
import com.driveperf.presentation.score.BreakdownUi
import com.driveperf.presentation.score.ComparisonCopy
import com.driveperf.presentation.score.ScoreCopy

private const val RUNS_FOR_TREND = 3
private const val THIS_ROUTE = "this route"

/**
 * S-SCO-01 Smoothness breakdown beyond its hero (DRIVE-87, DRIVE-82): the basis sentence, "Compared with", the route's
 * last runs, and the explanation of an unscored drive. The hero is [TripScore.score].
 */
internal object TripBreakdown {
    /** S-SCO-01 beyond the hero. */
    fun build(trip: Trip, detail: TripScoreDetail): BreakdownUi {
        val score = detail.score?.takeIf { it.smoothness != null }
        val routeName = ComparisonCopy.routeName(detail.route)
        val runs = detail.runs.takeIf { score != null && it.size >= RUNS_FOR_TREND }.orEmpty()
        return BreakdownUi(
            basisLine = score?.let { basisLine(it, routeName, trip) }.orEmpty(),
            options = score?.takeIf { it.calibrating == null }?.let { options(it, detail, routeName, trip) }.orEmpty(),
            optionsFooter = ScoreCopy.COMPARISON_FOOTER,
            runsTitle = if (runs.isEmpty()) null else "Your last ${runs.size} runs of ${routeName ?: THIS_ROUTE}",
            runs = runs,
            runsSummary = if (runs.isEmpty()) null else "Smoothness on your last ${runs.size} runs: ${runs.joinToString(", ")}.",
            contributorsFooter = ScoreCopy.CONTRIBUTORS_FOOTER,
            notScoredText = if (score == null) notScoredText(trip) else null,
            notScoredFooter = ScoreCopy.NOT_SCORED_FOOTER.takeIf { score == null },
        )
    }

    /** "Compared with your usual on The Commute: 83." or the calibrating explanation. */
    private fun basisLine(score: Score, routeName: String?, trip: Trip): String {
        val calibrating = score.calibrating
        val context = ComparisonCopy.contextWord(trip.context)
        return when {
            calibrating != null -> ComparisonCopy.calibratingLine(calibrating.k, calibrating.of)
            score.basis == ScoreBasis.ROUTE -> "Compared with your usual on ${routeName ?: THIS_ROUTE}: ${score.baseline}."
            else -> "Compared with your usual ${context?.let { "$it drives" } ?: "drives"}: ${score.baseline}."
        }
    }

    /** "Compared with": the route's runs and the usual drives of the same kind, the used one selected. */
    private fun options(score: Score, detail: TripScoreDetail, routeName: String?, trip: Trip): List<BasisOptionUi> {
        val usualKind = ComparisonCopy.contextWord(trip.context)?.let { "Your usual $it drives" } ?: "Your usual drives"
        val usual = "usual ${score.baseline}"
        if (score.basis == ScoreBasis.ROUTE) {
            return listOf(
                BasisOptionUi("Your runs of ${routeName ?: THIS_ROUTE}", "This drive matched a route, so it’s compared on that route",
                    true, usual),
                BasisOptionUi(usualKind, "Used when a drive isn’t on a route", false, null),
            )
        }
        val onNewRoute = detail.route != null
        return listOf(
            BasisOptionUi(
                usualKind,
                if (onNewRoute) "Its route doesn’t have three earlier runs yet" else "This drive didn’t match a route",
                true,
                usual,
            ),
            BasisOptionUi(
                routeName ?: "A route",
                if (onNewRoute) "From its fourth run, it’s compared on its route" else "Drive the same trip twice and it becomes a route",
                false,
                null,
            ),
        )
    }

    private fun notScoredText(trip: Trip): String =
        "${trip.quality?.let { TripCopy.reasonText(it.reason) } ?: "Not enough good data"}. When we can’t tell the phone’s " +
            "movement from the car’s, any score would be a guess."
}
