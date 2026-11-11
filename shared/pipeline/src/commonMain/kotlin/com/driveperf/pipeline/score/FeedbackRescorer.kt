package com.driveperf.pipeline.score

import com.driveperf.domain.capture.PreviewStore
import com.driveperf.domain.feedback.RescoreRequest
import com.driveperf.domain.feedback.TripRescorer
import com.driveperf.domain.repository.TripHistory
import com.driveperf.domain.score.Ledger
import com.driveperf.domain.score.Score
import com.driveperf.domain.trip.DriveContext
import com.driveperf.pipeline.coach.TipInputs
import com.driveperf.pipeline.coach.TipSelector
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * §5.4 instant re-score after "Was it needed?" or "Not accurate?": drops the excluded events' ledger rows, compares
 * with the same prior trips as at finalisation, and re-chooses the tip from the stored events (never an excluded one,
 * F-SCO-05 AC2). Needs no R1; the tip's place comes from the preview track. The route record state is kept as it was
 * (the D4 cap on needed marks belongs to the record check, F-RTE-03).
 */
public class FeedbackRescorer(
    history: TripHistory,
    private val previews: PreviewStore? = null,
    private val cfg: ScoringConfig = ScoringConfig(),
) : TripRescorer {
    private val priorHistory = PriorHistory(history)
    private val scorer = TripScorer(cfg)

    override suspend fun rescore(request: RescoreRequest): Score {
        val context = request.trip.context
        val ledger = request.score.ledger
        return if (context == null || ledger == null) request.score else rescore(request, context, ledger)
    }

    private suspend fun rescore(request: RescoreRequest, context: DriveContext, ledger: Ledger): Score {
        val trip = request.trip
        val score = request.score
        val routeId = request.routeId
        val inputs = originalInputs(score, ledger)
        val ctx = ScoringContext(
            tripId = trip.id,
            scorable = true,
            baselineEligible = Baselines.eligible(trip, 0) &&
                (trip.quality?.gnssCoveragePct ?: 0.0) >= GNSS_ELIGIBLE_PCT,
            distanceM = trip.distanceM ?: 0.0,
            routeId = routeId,
            priors = priorHistory.priorsFor(trip.startedAt, context, routeId),
            nowWallMs = request.nowWallMs,
        )
        val rescored = scorer.rescore(score, request.events, request.feedback, context, ctx, inputs)
        return if (rescored.smoothness == null) rescored else withTip(request, rescored, context, inputs)
    }

    /** §6.2 tip re-selection from the stored events; the record state stays as it was. */
    private suspend fun withTip(request: RescoreRequest, rescored: Score, context: DriveContext, inputs: EfficiencyInputs): Score {
        val trip = request.trip
        val preview = previews?.read(trip.id).orEmpty()
        val recent = priorHistory.recent(trip.startedAt)
        val tip = TipSelector.select(
            TipInputs(
                score = rescored,
                events = request.events,
                feedback = request.feedback,
                context = context,
                stopStartCount = inputs.stopStartCount,
                recentTipIds = recent.mapNotNull { it.score?.tip?.tipId }.take(RECENT_TIPS),
                jerkWindow = null,
                cleanestMidS = trip.cleanest?.let { (it.t0S + it.t1S) / 2 },
                positionAt = { t -> preview.minByOrNull { abs(it.tS - t) }?.let { it.lat to it.lng } },
                eventCost = { eventCost(it, cfg) },
            ),
        )
        return rescored.copy(tip = tip, recordState = request.score.recordState)
    }

    /**
     * The efficiency inputs measured at finalisation. They are kept on the ledger; a row stored before that falls back
     * to reconstructing them from its efficiency, which is exact only before the first re-score.
     */
    private fun originalInputs(score: Score, ledger: Ledger): EfficiencyInputs {
        val stopStarts = ledger.stopStartCount ?: score.efficiency?.stopStartCount ?: 0
        val abrupt = ledger.abruptAccelCount ?: run {
            val fullMovingS = ledger.perEvent.fold(ledger.base) { sum, e -> sum + e.row }.movingS
            ((score.efficiency?.abruptAccelsPerMin ?: 0.0) * fullMovingS / SECONDS_PER_MINUTE).roundToInt()
        }
        return EfficiencyInputs(stopStarts, abrupt)
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60.0
        const val GNSS_ELIGIBLE_PCT = 60.0
        const val RECENT_TIPS = 2
    }
}
