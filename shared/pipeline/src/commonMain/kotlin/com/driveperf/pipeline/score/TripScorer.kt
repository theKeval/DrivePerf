package com.driveperf.pipeline.score

import com.driveperf.domain.score.Contributor
import com.driveperf.domain.score.ContributorId
import com.driveperf.domain.score.Efficiency
import com.driveperf.domain.score.EventLedgerRow
import com.driveperf.domain.score.Ledger
import com.driveperf.domain.score.Score
import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.DriveContext
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.TurnDirection
import com.driveperf.foundation.newId
import com.driveperf.pipeline.Versions
import kotlin.math.abs

/** What the scorer needs to know about the trip besides its R1 signal. */
public data class ScoringContext(
    val tripId: String,
    /** The trip is a `DRIVER` trip whose badge is not Unscorable (§5.1). Otherwise no score is computed. */
    val scorable: Boolean,
    /** The trip qualifies for baselines and efficiency (§5.7, §5.9). */
    val baselineEligible: Boolean,
    val distanceM: Double,
    val routeId: String?,
    val priors: List<PriorTrip>,
    val nowWallMs: Long,
)

/** Events with their domain IDs, and the score (null when not scorable). */
public data class ScoringOutcome(val events: List<DrivingEvent>, val score: Score?, val efficiencyInputs: EfficiencyInputs)

/** The raw efficiency inputs, kept so re-scoring after feedback does not need R1. */
public data class EfficiencyInputs(val stopStartCount: Int, val abruptAccels: Int)

/**
 * Builds the domain [Score] and [DrivingEvent]s from an [R1Analysis] (§4.5, §5.3–§5.9), and re-scores a stored
 * score from its ledger when feedback changes (§5.4). Tips are filled by the tip selector.
 */
internal class TripScorer(private val cfg: ScoringConfig = ScoringConfig()) {
    private val analyser = R1Analyser(cfg)
    private val calculator = SmoothnessCalculator(cfg)

    fun score(a: R1Analysis, ctx: ScoringContext): ScoringOutcome {
        val events = a.events.map { toDomain(a, it, ctx.tripId) }
        val stopStarts = StopMetrics.stops(a.moving, cfg.fs, STOP_START_S).size
        val abrupt = StopMetrics.abruptAccels(a.a3Lon, a.trace::ok, cfg.fs)
        val inputs = EfficiencyInputs(stopStarts, abrupt)
        val smooth = if (ctx.scorable) analyser.score(a) else null
        if (smooth == null) return ScoringOutcome(events, null, inputs)
        val ledger = Ledger(
            a.ledger.base,
            a.events.indices.map { EventLedgerRow(events[it].id, events[it].type, a.ledger.perEvent[it]) },
            a.ledger.jxMps3,
            a.ledger.jyMps3,
        )
        val costs = events.associate { it.id to eventCost(it, cfg) }
        val score = assemble(ctx, a.context, smooth, ledger, events, costs, inputs, a.speedConsistencyCv)
        return ScoringOutcome(events, score, inputs)
    }

    /**
     * §5.4 instant recompute: drops the ledger rows of events marked needed or inaccurate and re-scores, without R1.
     * Noise floors and the badge stay as computed at finalisation.
     */
    @Suppress("LongParameterList")
    fun rescore(
        score: Score,
        events: List<DrivingEvent>,
        feedback: Map<String, EventFeedback>,
        context: DriveContext,
        ctx: ScoringContext,
        inputs: EfficiencyInputs,
    ): Score {
        val ledger = score.ledger ?: return score
        val active = ledger.perEvent.filter { feedback[it.eventId]?.let { f -> f.needed || f.inaccurate != null } != true }
        val total = active.fold(ledger.base) { sum, row -> sum + row.row }
        val wobble = score.contributors.firstOrNull { it.id == ContributorId.CONSISTENCY }?.value
        val activeIds = active.map { it.eventId }.toSet()
        val activeEvents = events.filter { it.id in activeIds }
        val costs = activeEvents.associate { e -> e.id to eventCost(e, cfg) }
        val cv = score.efficiency?.speedConsistencyCv
        return calculator.score(total, wobble, context)
            ?.let { assemble(ctx, context, it, ledger, activeEvents, costs, inputs, cv) }
            ?: insufficientExposure(score, ctx)
    }

    /**
     * Feedback left under 120 s of exposure (§5.5): no Smoothness, so nothing derived from it either. The ledger stays,
     * so undoing the feedback scores the trip again.
     */
    private fun insufficientExposure(score: Score, ctx: ScoringContext): Score = score.copy(
        smoothness = null,
        smoothnessRaw = null,
        baseline = null,
        delta = null,
        basis = null,
        basisRouteId = null,
        calibrating = null,
        contributors = emptyList(),
        efficiency = null,
        tip = null,
        recordState = null,
        computedAt = ctx.nowWallMs,
    )

    @Suppress("LongParameterList")
    private fun assemble(
        ctx: ScoringContext,
        context: DriveContext,
        smooth: SmoothnessResult,
        ledger: Ledger,
        activeEvents: List<DrivingEvent>,
        costs: Map<String, Double>,
        inputs: EfficiencyInputs,
        cv: Double?,
    ): Score {
        val compare = Baselines.compare(smooth.score, smooth.subScores, context, ctx.routeId, ctx.priors)
        val values = mapOf(
            ContributorId.BRAKING to smooth.rBrake,
            ContributorId.ACCELERATION to smooth.rAccel,
            ContributorId.CORNERING to smooth.rCorner,
            ContributorId.CONSISTENCY to smooth.wobbleMps,
        )
        val contributors = smooth.subScores.map { (id, sub) ->
            val band = compare.bands[id]
            Contributor(
                id = id,
                subScore = sub,
                value = values[id],
                band = band,
                reasonKey = band?.let { "${id.name.lowercase()}.${it.name.lowercase()}" },
                eventIds = eventsFor(id, activeEvents, costs),
            )
        }
        val efficiency: Efficiency = EfficiencyHabits.rate(
            inputs.stopStartCount, ctx.distanceM, inputs.abruptAccels, smooth.movingS, smooth.wobbleMps, cv, context,
            ctx.baselineEligible, ctx.priors,
        )
        return Score(
            tripId = ctx.tripId,
            smoothness = smooth.score,
            smoothnessRaw = smooth.raw,
            baseline = compare.baseline,
            delta = compare.delta,
            basis = compare.basis,
            basisRouteId = compare.basisRouteId,
            calibrating = compare.calibrating,
            contributors = contributors,
            ledger = ledger,
            efficiency = efficiency,
            tip = null,
            recordState = null,
            algorithmVersion = Versions.SCORING,
            computedAt = ctx.nowWallMs,
        )
    }

    /** That channel's active events, by cost descending, tie earliest (§5.7). Consistency has none. */
    private fun eventsFor(id: ContributorId, events: List<DrivingEvent>, costs: Map<String, Double>): List<String> {
        val type = when (id) {
            ContributorId.BRAKING -> EventType.BRAKE
            ContributorId.ACCELERATION -> EventType.ACCEL
            ContributorId.CORNERING -> EventType.CORNER
            ContributorId.CONSISTENCY -> return emptyList()
        }
        return events.filter { it.type == type }
            .sortedWith(compareByDescending<DrivingEvent> { costs[it.id] ?: 0.0 }.thenBy { it.tPeakS })
            .map { it.id }
    }

    /** §4.5: position from the nearest gated fix within 3 s, speeds 1 s before and after. */
    private fun toDomain(a: R1Analysis, e: DetectedEvent, tripId: String): DrivingEvent {
        val fs = cfg.fs.toDouble()
        val tPeak = e.peak / fs
        val nearest = a.trace.gnss.filter { it.lat != null && it.lng != null }.minByOrNull { abs(it.tS - tPeak) }
            ?.takeIf { abs(it.tS - tPeak) <= PIN_MAX_S }
        val before = (e.start - cfg.fs).coerceAtLeast(0)
        val after = (e.end + cfg.fs).coerceAtMost(a.trace.size - 1)
        return DrivingEvent(
            id = newId(),
            tripId = tripId,
            type = e.type,
            tPeakS = tPeak,
            tStartS = e.start / fs,
            tEndS = e.end / fs,
            durationS = (e.end - e.start + 1) / fs,
            peakG = e.peakG,
            direction = if (e.type == EventType.CORNER) (if (e.sign > 0) TurnDirection.RIGHT else TurnDirection.LEFT) else null,
            lat = nearest?.lat,
            lng = nearest?.lng,
            speedBeforeMps = a.v[before],
            speedAfterMps = a.v[after],
            confidence = e.confidence,
            algorithmVersion = Versions.SCORING,
        )
    }

    private companion object {
        const val PIN_MAX_S = 3.0
        const val STOP_START_S = 3.0
    }
}

/** §5.3 event cost of a stored event, recomputed from its type, peak and confidence. */
internal fun eventCost(e: DrivingEvent, cfg: ScoringConfig): Double {
    val enter = when (e.type) {
        EventType.BRAKE -> cfg.brakeEnterG
        EventType.ACCEL -> cfg.accelEnterG
        EventType.CORNER -> cfg.cornerEnterG
    }
    val w = when (e.confidence) {
        Confidence.HIGH -> cfg.weightHigh
        Confidence.MEDIUM -> cfg.weightMedium
        Confidence.LOW -> cfg.weightLow
    }
    return cfg.eventCostBase * w * (1 + minOf(1.0, (e.peakG - enter) / enter))
}
