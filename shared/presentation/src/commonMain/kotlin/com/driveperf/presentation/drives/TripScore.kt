package com.driveperf.presentation.drives

import com.driveperf.domain.route.IneligibleReason
import com.driveperf.domain.score.Contributor
import com.driveperf.domain.score.ScoreBasis
import com.driveperf.domain.score.ScoreLine
import com.driveperf.domain.score.TripScoreDetail
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.presentation.score.BasisOptionUi
import com.driveperf.presentation.score.BreakdownUi
import com.driveperf.presentation.score.ComparisonTone
import com.driveperf.presentation.score.ComparisonUi
import com.driveperf.presentation.score.ContributorUi
import com.driveperf.presentation.score.EventCountUi
import com.driveperf.presentation.score.EventRowUi
import com.driveperf.presentation.score.ScoreBadgeUi
import com.driveperf.presentation.score.ScoreCopy
import com.driveperf.presentation.score.ScoreUi
import com.driveperf.presentation.score.TipCopy
import com.driveperf.presentation.score.TipUi
import kotlin.math.roundToInt

/** A trip whose score is shown: a driver trip nobody marked otherwise (F-RPL-07 AC4, EC-PAS). */
internal val Trip.scoresShown: Boolean get() = kind == TripKind.DRIVER && (correction == null || correction == com.driveperf.domain.trip.TripCorrection.DRIVER)

private const val MS_PER_S = 1_000L
private const val RUNS_FOR_TREND = 3
private const val G_HUNDREDTHS = 100.0
private const val TENTHS = 10.0

/**
 * Builds the score surfaces of one trip (S-TRP-01 score card, events and tip; S-SCO-01) from the reader's
 * [TripScoreDetail]. [firstScoredEver] is the trip with the user's first score: its calibrating chip says "Your baseline".
 */
internal class TripScore(
    private val units: UnitSystem,
    private val format: DrivesFormatter,
    private val startedAtOf: (String) -> Long?,
) {
    /** The comparison chip for a scored, shown trip; null without one. */
    fun comparison(
        smoothness: Int?,
        delta: Int?,
        calibrating: com.driveperf.domain.score.Calibrating?,
        onRoute: Boolean,
        routeName: String?,
        trip: Trip,
        firstScoredEver: Boolean,
    ): ComparisonUi? {
        if (smoothness == null) return null
        return when {
            calibrating != null && calibrating.k <= 1 && firstScoredEver ->
                ComparisonUi(ComparisonTone.CALIBRATING, "", ScoreCopy.FIRST_DRIVE, calibrating.k, calibrating.of)
            calibrating != null ->
                ComparisonUi(ComparisonTone.CALIBRATING, "", ScoreCopy.calibratingText(calibrating.k, calibrating.of), calibrating.k, calibrating.of)
            delta == null -> null
            else -> ComparisonUi(
                tone = when {
                    delta > 0 -> ComparisonTone.BETTER
                    delta < 0 -> ComparisonTone.LOWER
                    else -> ComparisonTone.SAME
                },
                glyph = when {
                    delta > 0 -> "▲"
                    delta < 0 -> "▼"
                    else -> "●"
                },
                text = ScoreCopy.comparisonText(delta, onRoute, routeName, trip.context),
            )
        }
    }

    /** The trip card's badge (components.md §12); null for a trip whose score is hidden. */
    fun badge(trip: Trip, line: ScoreLine?): ScoreBadgeUi? {
        if (!trip.scoresShown) return null
        val smoothness = line?.smoothness
        if (smoothness == null) {
            // Only a finished analysis can say "Not scored"; before it the card shows nothing.
            return if (trip.quality == null) null else ScoreBadgeUi(null, null, ComparisonTone.SAME, notScoredText = ScoreCopy.NOT_SCORED)
        }
        val delta = line.delta.takeIf { line.calibrating == null }
        return ScoreBadgeUi(
            score = smoothness.toString(),
            delta = delta?.let(ScoreCopy::signed),
            tone = when {
                delta == null -> ComparisonTone.CALIBRATING
                delta > 0 -> ComparisonTone.BETTER
                delta < 0 -> ComparisonTone.LOWER
                else -> ComparisonTone.SAME
            },
            newRecord = line.newRecord,
        )
    }

    /** "Smoothness 85, 2 above your usual." for the card's accessibility label; null without a score. */
    fun spokenBadge(trip: Trip, line: ScoreLine?): String? {
        val smoothness = line?.smoothness?.takeIf { trip.scoresShown } ?: return null
        val delta = line.delta.takeIf { line.calibrating == null }
        val comparison = when {
            line.calibrating != null -> ", calibrating"
            delta == null || delta == 0 -> ", right on your usual"
            delta > 0 -> ", $delta above your usual"
            else -> ", ${-delta} below your usual"
        }
        return "Smoothness $smoothness$comparison${if (line.newRecord) ". New route record" else ""}"
    }

    /** The S-TRP-01 score card and the S-SCO-01 hero. */
    fun score(trip: Trip, detail: TripScoreDetail, firstScoredEver: Boolean): ScoreUi {
        val score = detail.score
        val smoothness = score?.smoothness
        if (smoothness == null) {
            val reason = trip.quality?.let { "${TripCopy.reasonText(it.reason)}." }
            return ScoreUi(
                smoothness = null, baseline = null, comparison = null, recordLine = null, basedOn = null, paceNote = null,
                notScoredReason = reason ?: "Not enough good data.",
                contributors = emptyList(),
                accessibilityLabel = "Smoothness, not scored. ${reason.orEmpty()}".trim(),
            )
        }
        val onRoute = score.basis == ScoreBasis.ROUTE
        val routeName = ScoreCopy.routeName(detail.route)
        val comparison = comparison(smoothness, score.delta, score.calibrating, onRoute, routeName, trip, firstScoredEver)
        val record = score.recordState?.takeIf { it.isNew }
        val recordLine = record?.let { r ->
            val previous = r.previousScore ?: return@let "New route record"
            val date = r.previousTripId?.let(startedAtOf)?.let { " from ${format.dayOfMonth(it)} ${format.month(it)}" }.orEmpty()
            "New route record · beat $previous$date"
        }
        val quality = trip.quality
        val basedOn = quality?.takeIf { wholeMinutes(it.scoredS) < wholeMinutes(it.totalS) }?.let {
            "Score based on ${wholeMinutes(it.scoredS)} of ${wholeMinutes(it.totalS)} min"
        }
        val spoken = buildString {
            append("Smoothness $smoothness. ")
            when {
                score.calibrating != null -> append("${comparison?.text}.")
                score.delta != null -> append(ScoreCopy.spokenComparison(score.delta ?: 0, onRoute, routeName, trip.context))
            }
            recordLine?.let { append(" $it.") }
            basedOn?.let { append(" $it.") }
            if (detail.recordIneligible == IneligibleReason.PACE_BAND) append(" ${ScoreCopy.UNUSUAL_PACE}.")
        }
        return ScoreUi(
            smoothness = smoothness,
            baseline = score.baseline.takeIf { score.calibrating == null },
            comparison = comparison,
            recordLine = recordLine,
            basedOn = basedOn,
            paceNote = ScoreCopy.UNUSUAL_PACE.takeIf { detail.recordIneligible == IneligibleReason.PACE_BAND },
            notScoredReason = null,
            contributors = score.contributors.map { contributor(trip, it, detail) },
            accessibilityLabel = spoken.trim(),
        )
    }

    private fun contributor(trip: Trip, c: Contributor, detail: TripScoreDetail): ContributorUi {
        val byId = detail.events.associateBy { it.id }
        val events = c.eventIds.mapNotNull(byId::get).map { event(trip, it, detail) }
        val label = ScoreCopy.contributorLabel(c.id)
        // A withheld row keeps its reason keys null; so does calibrating. No band then (F-SCO-02 AC1).
        val band = c.band
        val reason = ScoreCopy.reason(c.id, c.reasonKey, c.eventIds.size)
        return ContributorUi(
            id = c.id,
            label = label,
            band = band,
            bandGlyph = band?.let(ScoreCopy::bandGlyph),
            bandText = band?.let(ScoreCopy::bandText),
            reason = reason,
            events = events,
            accessibilityLabel = listOfNotNull(label, band?.let { ScoreCopy.bandText(it).lowercase() }).joinToString(", ") +
                ". $reason." + if (events.isNotEmpty()) " Show events." else "",
        )
    }

    /** One event row: "Hard braking · 8:14 a.m. · 0.42 g · 1.4 s". */
    fun event(trip: Trip, e: DrivingEvent, detail: TripScoreDetail): EventRowUi {
        val label = ScoreCopy.eventLabel(e.type)
        val time = format.time(trip.startedAt + (e.tPeakS * MS_PER_S).toLong())
        val g = gText(e.peakG)
        val seconds = tenths(e.durationS)
        val feedback = detail.feedback[e.id]
        val needed = feedback?.needed == true
        val excluded = feedback?.inaccurate != null
        val low = e.confidence == Confidence.LOW
        val tag = when {
            needed -> "Needed"
            excluded -> "Not counted"
            low -> "Low confidence"
            else -> null
        }
        return EventRowUi(
            id = e.id,
            type = e.type,
            label = label,
            time = time,
            meta = "$g g · $seconds s",
            needed = needed,
            excluded = excluded,
            lowConfidence = low,
            tag = tag,
            accessibilityLabel = "$label at $time. Peak $g g over $seconds seconds." + (tag?.let { " $it." } ?: ""),
        )
    }

    /** "Events · 3": counts by type and the rows, by time. */
    fun events(trip: Trip, detail: TripScoreDetail): TripEventsUi {
        val unscored = trip.quality?.badge == QualityBadge.UNSCORABLE
        return TripEventsUi(
            title = "Events · ${detail.events.size}",
            counts = EventType.entries.map { type -> EventCountUi(type, ScoreCopy.eventCountLabel(type), detail.events.count { it.type == type }) },
            rows = detail.events.sortedWith(compareBy({ it.tPeakS }, { it.id })).map { event(trip, it, detail) },
            emptyTitle = ScoreCopy.NO_EVENTS_TITLE,
            emptyBody = ScoreCopy.NO_EVENTS_BODY,
            note = ScoreCopy.EVENTS_UNSCORED.takeIf { unscored },
        )
    }

    /** The trip's tip card, when it has a tip with copy (none on unscored or passenger trips). */
    fun tip(detail: TripScoreDetail): TipUi? {
        val tip = detail.score?.takeIf { it.smoothness != null }?.tip ?: return null
        val text = TipCopy.text(tip.tipId) ?: return null
        return TipUi(tip.tipId, tip.category, ScoreCopy.TIP_KICKER, text.title, text.nextTime, text.why)
    }

    /** "1.2 km with no abrupt inputs." (the positive card first, F-SCO-06 AC2; no road names in v1). */
    fun cleanest(trip: Trip, detail: TripScoreDetail): String? {
        if (detail.score?.smoothness == null) return null
        val stretch = trip.cleanest ?: return null
        return "${distanceText(stretch.distanceM, units)} with no abrupt inputs."
    }

    /** S-SCO-01 beyond the hero. */
    fun breakdown(trip: Trip, detail: TripScoreDetail): BreakdownUi {
        val score = detail.score
        val smoothness = score?.smoothness
        val routeName = ScoreCopy.routeName(detail.route)
        val context = ScoreCopy.contextWord(trip.context)
        val usualKind = context?.let { "Your usual $it drives" } ?: "Your usual drives"
        val calibrating = score?.calibrating
        val basisLine = when {
            smoothness == null -> ""
            calibrating != null -> ScoreCopy.calibratingLine(calibrating.k, calibrating.of)
            score.basis == ScoreBasis.ROUTE -> "Compared with your usual on ${routeName ?: "this route"}: ${score.baseline}."
            else -> "Compared with your usual ${context?.let { "$it drives" } ?: "drives"}: ${score.baseline}."
        }
        val options = if (smoothness == null || calibrating != null) {
            emptyList()
        } else if (score.basis == ScoreBasis.ROUTE) {
            listOf(
                BasisOptionUi("Your runs of ${routeName ?: "this route"}", "This drive matched a route, so it’s compared on that route", true, "usual ${score.baseline}"),
                BasisOptionUi(usualKind, "Used when a drive isn’t on a route", false, null),
            )
        } else {
            val onNewRoute = detail.route != null
            listOf(
                BasisOptionUi(
                    usualKind,
                    if (onNewRoute) "Its route doesn’t have three earlier runs yet" else "This drive didn’t match a route",
                    true,
                    "usual ${score.baseline}",
                ),
                BasisOptionUi(
                    routeName ?: "A route",
                    if (onNewRoute) "From its fourth run, it’s compared on its route" else "Drive the same trip twice and it becomes a route",
                    false,
                    null,
                ),
            )
        }
        val runs = detail.runs.takeIf { smoothness != null && it.size >= RUNS_FOR_TREND }.orEmpty()
        return BreakdownUi(
            basisLine = basisLine,
            options = options,
            optionsFooter = ScoreCopy.COMPARISON_FOOTER,
            runsTitle = if (runs.isEmpty()) null else "Your last ${runs.size} runs of ${routeName ?: "this route"}",
            runs = runs,
            runsSummary = if (runs.isEmpty()) null else "Smoothness on your last ${runs.size} runs: ${runs.joinToString(", ")}.",
            contributorsFooter = ScoreCopy.CONTRIBUTORS_FOOTER,
            notScoredText = if (smoothness == null) {
                "${trip.quality?.let { TripCopy.reasonText(it.reason) } ?: "Not enough good data"}. When we can’t tell the " +
                    "phone’s movement from the car’s, any score would be a guess."
            } else {
                null
            },
            notScoredFooter = ScoreCopy.NOT_SCORED_FOOTER.takeIf { smoothness == null },
        )
    }

    private companion object {
        /** "0.42" */
        fun gText(g: Double): String {
            val h = (kotlin.math.abs(g) * G_HUNDREDTHS).roundToInt()
            return "${h / 100}.${(h % 100).toString().padStart(2, '0')}"
        }

        /** "1.4" */
        fun tenths(s: Double): String {
            val t = (s * TENTHS).roundToInt()
            return "${t / 10}.${t % 10}"
        }
    }
}
