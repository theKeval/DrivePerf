package com.driveperf.pipeline.score

import com.driveperf.domain.capture.PreviewStore
import com.driveperf.domain.feedback.RescoreRequest
import com.driveperf.domain.repository.ScoredTrip
import com.driveperf.domain.repository.TripHistory
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.score.Score
import com.driveperf.domain.score.ScoreHistoryStore
import com.driveperf.domain.score.TripRecompute
import com.driveperf.domain.trip.DriveContext
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.foundation.Clock

/**
 * §5.7 "Corrections" (F-HIS-02 AC1, F-SCO-05): after a trip's kind, an event's feedback or a deletion changed, walks
 * that trip and every later one in `(startedAt, id)` order and re-derives each stored score from its ledger, without
 * R1, writing only the scores that changed in one transaction.
 *
 * - A trip that is no longer a drive keeps its ledger and sub-scores but loses its Smoothness, comparison and tip
 *   (§5.8: `kind ≠ DRIVER` has no score), so it leaves every later baseline.
 * - A drive whose Smoothness was withheld that way is re-scored from its ledger and feedback, tip included (§6.2), as
 *   "Was it needed?" re-scores.
 * - Every other drive keeps its Smoothness, sub-scores, events and tip, and is compared again with its priors as they
 *   are now: baseline, delta, basis, calibrating, contributor bands and the efficiency rating (§5.7, §5.9).
 *
 * Earlier trips are read once and later ones are compared in memory, so a long history stays inside the 2 s budget.
 * The result depends only on the stored trips, and no score's [Score.algorithmVersion] changes (F-SCO-04 AC3).
 */
public class HistoryRecompute(
    private val history: TripHistory,
    private val store: ScoreHistoryStore,
    private val trips: TripRepository,
    private val clock: Clock,
    private val previews: PreviewStore? = null,
    private val cfg: ScoringConfig = ScoringConfig(),
) : TripRecompute {
    override suspend fun fromTrip(tripId: String) {
        trips.trip(tripId)?.let { from(it.startedAt) }
    }

    /** Recomputes every finished trip that started at or after [startedAt]. Returns the scores it changed. */
    public suspend fun from(startedAt: Long): List<Score> {
        val later = store.finishedFrom(startedAt).toMutableList()
        if (later.isEmpty()) return emptyList()
        val view = RecomputeHistory(history, startedAt, later)
        val walk = Walk(PriorHistory(view), FeedbackRescorer(view, previews, cfg), clock.wallMs())
        val changed = mutableListOf<Score>()
        for (i in later.indices) {
            val item = later[i]
            val before = item.score ?: continue
            val after = walk.recompute(item, before)
            if (after != before) {
                later[i] = item.copy(score = after)
                changed += after
            }
        }
        if (changed.isNotEmpty()) store.saveScores(changed)
        return changed
    }

    /** One recompute pass: priors and the re-scorer read the history as this pass has updated it so far. */
    private inner class Walk(val priors: PriorHistory, val rescorer: FeedbackRescorer, val now: Long) {
        suspend fun recompute(item: ScoredTrip, score: Score): Score {
            val trip = item.trip
            val context = trip.context
            return when {
                trip.kind != TripKind.DRIVER -> if (score.smoothness == null) score else withheld(score)
                score.smoothness == null -> restored(item, score)
                context == null -> score
                else -> recompare(trip, score, context, priors.priorsFor(trip.startedAt, context, item.routeId), item.routeId)
            }
        }

        /** §5.8: no Smoothness, comparison or tip; the ledger, sub-scores and efficiency measures stay for an undo. */
        private fun withheld(score: Score): Score = score.copy(
            smoothness = null,
            smoothnessRaw = null,
            baseline = null,
            delta = null,
            basis = null,
            basisRouteId = null,
            calibrating = null,
            contributors = score.contributors.map { it.copy(band = null, reasonKey = null) },
            efficiency = score.efficiency?.copy(rating = null),
            tip = null,
            recordState = null,
            computedAt = now,
        )

        /** A drive again: the §5.4 ledger re-score, tip included. Under 120 s of exposure it stays without a score. */
        private suspend fun restored(item: ScoredTrip, score: Score): Score {
            val trip = item.trip
            if (score.ledger == null || trip.quality?.badge == QualityBadge.UNSCORABLE) return score
            val request = RescoreRequest(trip, score, trips.events(trip.id), trips.feedback(trip.id), item.routeId, now)
            return rescorer.rescore(request).takeIf { it.smoothness != null } ?: score
        }

        private fun recompare(trip: Trip, score: Score, context: DriveContext, priors: List<PriorTrip>, routeId: String?): Score {
            val smoothness = score.smoothness ?: return score
            val compare = Baselines.compare(smoothness, score.contributors.associate { it.id to it.subScore }, context, routeId, priors)
            val next = score.copy(
                baseline = compare.baseline,
                delta = compare.delta,
                basis = compare.basis,
                basisRouteId = compare.basisRouteId,
                calibrating = compare.calibrating,
                contributors = score.contributors.map { c ->
                    val band = compare.bands[c.id]
                    c.copy(band = band, reasonKey = reasonKey(c.id, band))
                },
                efficiency = score.efficiency?.let { EfficiencyHabits.rerate(it, context, Baselines.ratable(trip), priors) },
            )
            return if (next == score) score else next.copy(computedAt = now)
        }
    }
}

/**
 * The history one recompute pass sees: [later] (oldest first, started at or after [from], updated as the pass goes)
 * over [base] for everything earlier. Earlier trips are read from [base] once, a page at a time, however many later
 * trips ask for them.
 */
internal class RecomputeHistory(
    private val base: TripHistory,
    private val from: Long,
    private val later: List<ScoredTrip>,
) : TripHistory {
    private val earlier = mutableListOf<ScoredTrip>()
    private val seen = mutableSetOf<String>()
    private var exhausted = false

    override suspend fun finishedBefore(beforeStartedAt: Long, limit: Int): List<ScoredTrip> {
        val recent = later.asReversed().filter { it.trip.startedAt < beforeStartedAt }.take(limit)
        if (recent.size >= limit) return recent
        return recent + older(minOf(beforeStartedAt, from), limit - recent.size)
    }

    /** The newest [count] earlier trips that started before [before], loading pages of [base] as needed. */
    private suspend fun older(before: Long, count: Int): List<ScoredTrip> {
        while (!exhausted && earlier.count { it.trip.startedAt < before } < count) load()
        return earlier.filter { it.trip.startedAt < before }.take(count)
    }

    private suspend fun load() {
        // The next page starts at the last startedAt again, so trips sharing it are not skipped; `seen` drops repeats.
        val cursor = earlier.lastOrNull()?.trip?.startedAt?.plus(1) ?: from
        val page = base.finishedBefore(cursor, PAGE).filter { it.trip.startedAt < from && seen.add(it.trip.id) }
        if (page.isEmpty()) exhausted = true else earlier += page
    }

    private companion object {
        const val PAGE = 200
    }
}
