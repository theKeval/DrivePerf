package com.driveperf.presentation.drives

import com.driveperf.domain.route.IneligibleReason
import com.driveperf.domain.score.Contributor
import com.driveperf.domain.score.Score
import com.driveperf.domain.score.ScoreBasis
import com.driveperf.domain.score.ScoreLine
import com.driveperf.domain.score.TripScoreDetail
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.domain.trip.TripKind
import com.driveperf.presentation.score.ComparisonTone
import com.driveperf.presentation.score.ComparisonUi
import com.driveperf.presentation.score.ComparisonCopy
import com.driveperf.presentation.score.ContributorUi
import com.driveperf.presentation.score.EventCountUi
import com.driveperf.presentation.score.EventRowUi
import com.driveperf.presentation.score.ScoreBadgeUi
import com.driveperf.presentation.score.ScoreCopy
import com.driveperf.presentation.score.ScoreUi
import com.driveperf.presentation.score.TipCopy
import com.driveperf.presentation.score.TipUi
import kotlin.math.abs
import kotlin.math.roundToInt

/** A trip whose score is shown: a driver trip nobody marked otherwise (F-RPL-07 AC4, EC-PAS). */
internal val Trip.scoresShown: Boolean
    get() = kind == TripKind.DRIVER && (correction == null || correction == TripCorrection.DRIVER)

private const val MS_PER_S = 1_000L

private const val G_HUNDREDTHS = 100.0
private const val TENTHS = 10.0
private const val HUNDRED = 100
private const val TEN = 10

/**
 * Builds the score surfaces of one trip (S-TRP-01 score card, events and tip; S-SCO-01) and its card badge from the
 * reader's data. [startedAtOf] finds a trip's start, for the date of the route record a drive beat.
 */
internal class TripScore(
    private val units: UnitSystem,
    private val format: DrivesFormatter,
    private val startedAtOf: (String) -> Long?,
) {
    /** The comparison chip of a scored trip: "Your baseline" on the first scored drive ever, then k of 3, then the delta. */
    private fun comparison(score: Score, routeName: String?, trip: Trip, firstScoredEver: Boolean): ComparisonUi? {
        val calibrating = score.calibrating
        val delta = score.delta
        return when {
            calibrating != null -> ComparisonUi(
                ComparisonTone.CALIBRATING,
                "",
                if (calibrating.k <= 1 && firstScoredEver) {
                    ComparisonCopy.FIRST_DRIVE
                } else {
                    ComparisonCopy.calibratingText(calibrating.k, calibrating.of)
                },
                calibrating.k,
                calibrating.of,
            )
            delta == null -> null
            else -> ComparisonUi(
                tone = tone(delta),
                glyph = when {
                    delta > 0 -> "▲"
                    delta < 0 -> "▼"
                    else -> "●"
                },
                text = ComparisonCopy.comparisonText(delta, score.basis == ScoreBasis.ROUTE, routeName, trip.context),
            )
        }
    }

    private fun tone(delta: Int?): ComparisonTone = when {
        delta == null -> ComparisonTone.CALIBRATING
        delta > 0 -> ComparisonTone.BETTER
        delta < 0 -> ComparisonTone.LOWER
        else -> ComparisonTone.SAME
    }

    /**
     * The trip card's badge (components.md §12); null for a trip whose score is hidden, and before analysis (only a
     * finished analysis can say "Not scored").
     */
    fun badge(trip: Trip, line: ScoreLine?): ScoreBadgeUi? {
        val smoothness = line?.smoothness
        val delta = line?.delta?.takeIf { line.calibrating == null }
        return when {
            !trip.scoresShown -> null
            smoothness == null -> trip.quality?.let { ScoreBadgeUi(null, null, ComparisonTone.SAME, notScoredText = ScoreCopy.NOT_SCORED) }
            else -> ScoreBadgeUi(smoothness.toString(), delta?.let(ComparisonCopy::signed), tone(delta), line.newRecord)
        }
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
        val routeName = ComparisonCopy.routeName(detail.route)
        val comparison = comparison(score, routeName, trip, firstScoredEver)
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
                score.delta != null -> append(ComparisonCopy.spokenComparison(score.delta ?: 0, onRoute, routeName, trip.context))
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
            counts = EventType.entries.map { type -> EventCountUi(type, ScoreCopy.eventCountLabel(type),
                detail.events.count { it.type == type }) },
            rows = detail.events.sortedWith(compareBy({ it.tPeakS }, { it.id })).map { event(trip, it, detail) },
            emptyTitle = ScoreCopy.NO_EVENTS_TITLE,
            emptyBody = ScoreCopy.NO_EVENTS_BODY,
            note = ScoreCopy.EVENTS_UNSCORED.takeIf { unscored },
        )
    }

    /** The trip's tip card, when it has a tip with copy (none on unscored or passenger trips). */
    fun tip(detail: TripScoreDetail): TipUi? {
        val tip = detail.score?.takeIf { it.smoothness != null }?.tip
        val text = tip?.let { TipCopy.text(it.tipId) }
        // "See the moment" only for an event that is still listed and not reported "Not accurate?" (DRIVE-102).
        val eventId = tip?.eventId?.takeIf { id -> detail.events.any { it.id == id } && detail.feedback[id]?.inaccurate == null }
        return if (tip == null || text == null) null else TipUi(tip.tipId, tip.category, ScoreCopy.TIP_KICKER, text.title,
            text.nextTime, text.why, eventId)
    }

    /** "1.2 km with no abrupt inputs." (the positive card first, F-SCO-06 AC2; no road names in v1). */
    fun cleanest(trip: Trip, detail: TripScoreDetail): String? =
        trip.cleanest?.takeIf { detail.score?.smoothness != null }?.let { "${distanceText(it.distanceM, units)} with no abrupt inputs." }

    private companion object {
        /** "0.42" */
        fun gText(g: Double): String {
            val h = (abs(g) * G_HUNDREDTHS).roundToInt()
            return "${h / HUNDRED}.${(h % HUNDRED).toString().padStart(2, '0')}"
        }

        /** "1.4" */
        fun tenths(s: Double): String {
            val t = (s * TENTHS).roundToInt()
            return "${t / TEN}.${t % TEN}"
        }
    }
}
