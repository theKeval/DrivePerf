package com.driveperf.pipeline

import com.driveperf.domain.capture.FinalisedTrip
import com.driveperf.domain.capture.GeoPoint
import com.driveperf.domain.capture.Parking
import com.driveperf.domain.capture.WakeKind
import com.driveperf.domain.repository.RouteHistory
import com.driveperf.domain.repository.ScoredTrip
import com.driveperf.domain.repository.TripHistory
import com.driveperf.domain.trip.CaptureMeta
import com.driveperf.domain.trip.DriveContext
import com.driveperf.domain.trip.Interruption
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripQuality
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.tripfile.AnalysisFix
import com.driveperf.domain.tripfile.AnalysisTrace
import com.driveperf.foundation.newId
import com.driveperf.pipeline.capture.FinaliseRequest
import com.driveperf.pipeline.capture.FinaliseResult
import com.driveperf.pipeline.capture.TripFinaliser
import com.driveperf.pipeline.capture.TripSplit
import com.driveperf.pipeline.coach.Stretches
import com.driveperf.pipeline.coach.TipInputs
import com.driveperf.pipeline.coach.TipSelector
import com.driveperf.pipeline.route.PreviewTrack
import com.driveperf.pipeline.route.RouteEngine
import com.driveperf.pipeline.score.Baselines
import com.driveperf.pipeline.score.PriorHistory
import com.driveperf.pipeline.score.R1Analyser
import com.driveperf.pipeline.score.R1Trace
import com.driveperf.pipeline.score.ScoringConfig
import com.driveperf.pipeline.score.ScoringContext
import com.driveperf.pipeline.score.TripScorer
import com.driveperf.pipeline.score.eventCost
import com.driveperf.pipeline.signal.Badge
import com.driveperf.pipeline.signal.BadgeInputs
import com.driveperf.pipeline.signal.SignalConfig
import com.driveperf.pipeline.signal.SignalPipeline
import com.driveperf.pipeline.signal.SignalResult
import com.driveperf.pipeline.signal.Spans
import com.driveperf.pipeline.signal.SuspectRules
import com.driveperf.pipeline.signal.TripMetrics

/**
 * The `pipe-1.0.0` batch pipeline at trip end (§0.2): R0 → gate → phone movement → alignment → R1 → metrics →
 * events → ledger → badge → Smoothness → baseline → efficiency, committed as one [FinalisedTrip] that carries its R1
 * trace. A trip longer than 12 h is split at its longest stop of more than 30 min and each part finalised on its own
 * (§1.6).
 */
public class PipelineFinaliser(
    private val history: TripHistory,
    routeHistory: RouteHistory? = null,
    private val signalConfig: SignalConfig = SignalConfig(),
    private val scoringConfig: ScoringConfig = ScoringConfig(),
) : TripFinaliser {
    private val routes = routeHistory?.let { RouteEngine(it) }
    private val signal = SignalPipeline(signalConfig)
    private val analyser = R1Analyser(scoringConfig)
    private val scorer = TripScorer(scoringConfig)
    private val metrics = TripMetrics(signalConfig)
    private val spans = Spans(signalConfig)
    private val badge = Badge(signalConfig)
    private val suspects = SuspectRules()

    override suspend fun finalise(request: FinaliseRequest): FinaliseResult = finaliseSplitting(request, history, 0)

    /**
     * Finalises [request]; when the result is longer than 12 h and has a stop of more than 30 min, splits the capture
     * there and finalises each part, so a part may split again. [depth] > 0 marks a part, whose R0 is rewritten.
     */
    private suspend fun finaliseSplitting(request: FinaliseRequest, past: TripHistory, depth: Int): FinaliseResult {
        val whole = finaliseOne(request, past)
        val trip = whole.result.trip
        val stop = trip?.trip?.takeIf { (it.durationS ?: 0.0) > signalConfig.splitAfterS && depth < MAX_SPLIT_DEPTH }
            ?.let { TripSplit.stopFor(it.stops, signalConfig.splitStopMinS) }
        if (stop == null) {
            val parts = whole.result.trips.map { it.copy(splitCapture = request.capture) }
            return if (depth == 0) whole.result else whole.result.copy(trips = parts)
        }
        val cut = TripSplit.split(request.capture, whole.t0Ns, stop, newId())
        val first = finaliseSplitting(
            request.copy(capture = cut.first, endNs = cut.firstEndNs, interruption = null), past, depth + 1,
        )
        val second = finaliseSplitting(request.copy(capture = cut.second), PendingHistory(past, first.trips), depth + 1)
        return FinaliseResult(first.trips + second.trips, second.parking ?: first.parking)
    }

    private class Finalised(val result: FinaliseResult, val t0Ns: Long)

    @Suppress("LongMethod", "CyclomaticComplexMethod") // the §0.2 sequence, in order
    private suspend fun finaliseOne(request: FinaliseRequest, past: TripHistory): Finalised {
        val priorHistory = PriorHistory(past)
        val capture = request.capture
        val h = capture.header
        val sig = signal.process(capture, request.endNs)
        var analysis = analyser.analyse(sig.trace, limited = false, still = sig.still)
        val estimatedEnd = sig.estimate?.let { sig.fixes.firstOrNull()?.tS ?: 0.0 }
        val gaps = spans.gnssGaps(sig.fixes, sig.still)
        val allSpans = (sig.fixedSpans + gaps.spans + spans.excluded(sig.trace.quality, analysis.moving, estimatedEnd))
            .sortedWith(compareBy({ it.t0S }, { it.kind.name }))
        val m = metrics.compute(sig.fixes, analysis.moving)
        val distance = m.gatedDistanceM + (sig.estimate?.distanceM ?: 0.0) + gaps.chordM
        val end = sig.fixes.lastOrNull()?.let { Parking(GeoPoint(it.lat, it.lng), sig.startWallMs + (it.tS * MS).toLong()) }
        val short = distance < signalConfig.shortTripM || sig.endS < signalConfig.shortTripS
        val manual = request.manual || h.wakeKind == WakeKind.MANUAL
        if (short && !manual && request.interruption == null) return Finalised(FinaliseResult(null, end), sig.t0Ns)
        val b = badge.evaluate(
            BadgeInputs(
                sig.endS, allSpans, m.gnssCoveragePct, sig.imuDelivery, analysis.total().movingS, sig.hasImu,
                sig.fixes.isNotEmpty(), sig.anyEpochConverged, sig.noGyro,
            ),
        )
        if (b.badge == QualityBadge.LIMITED) analysis = analyser.analyse(sig.trace, limited = true, still = sig.still)
        val suspect = suspects.classify(
            capture.activity, sig.t0Ns, analysis.moving, scoringConfig.fs, sig.fixes, m.stops, sig.trace.latG, distance,
        )
        val quality = TripQuality(
            badge = b.badge,
            reason = b.reason,
            placement = sig.placement(signalConfig),
            imuHz = sig.imuMedianHz,
            gnssCoveragePct = m.gnssCoveragePct,
            alignmentSettledS = sig.alignmentSettledS,
            estimatedStartM = sig.estimate?.distanceM,
            scoredS = b.scoredS,
            excludedS = b.excludedS,
            totalS = sig.endS,
            spans = allSpans,
        )
        val cleanest = Stretches.cleanest(analysis)
        val draft = trip(request, sig, quality, distance, m, suspect, analysis.context).copy(cleanest = cleanest)
        val scorable = request.kind == TripKind.DRIVER && b.badge != QualityBadge.UNSCORABLE && !short
        val recent = priorHistory.recent(draft.startedAt)
        val preview = PreviewTrack.build(
            sig.trace.gnss, analysis.a3Lon, analysis.a3Lat, scoringConfig.fs, allSpans,
            sig.estimate?.let { h.parking?.point?.let { p -> p.lat to p.lng } },
            analysis.events.map { it.peak.toDouble() / scoringConfig.fs },
        )
        val engine = routes
        val route = engine?.match(draft, preview, recent, request.nowWallMs)
        val priors = priorHistory.priorsFor(draft.startedAt, analysis.context, route?.route?.id)
        val outcome = scorer.score(
            analysis,
            ScoringContext(
                tripId = h.tripId,
                scorable = scorable,
                baselineEligible = Baselines.eligible(draft, 0) && m.gnssCoveragePct >= GNSS_ELIGIBLE_PCT,
                distanceM = distance,
                routeId = route?.route?.id,
                priors = priors,
                nowWallMs = request.nowWallMs,
            ),
        )
        val record = if (engine != null && route != null) engine.record(draft, outcome.score?.smoothness, 0, route) else null
        val paired = if (engine != null && route != null) engine.pairedMatch(route) else null
        val tip = outcome.score?.let { score ->
            TipSelector.select(
                TipInputs(
                    score = score,
                    events = outcome.events,
                    feedback = emptyMap(),
                    context = analysis.context,
                    stopStartCount = outcome.efficiencyInputs.stopStartCount,
                    recentTipIds = recent.mapNotNull { it.score?.tip?.tipId }.take(RECENT_TIPS),
                    jerkWindow = Stretches.jerkWindow(analysis),
                    cleanestMidS = cleanest?.let { (it.t0S + it.t1S) / 2 },
                    positionAt = { t -> nearestFix(sig, t) },
                    eventCost = { eventCost(it, scoringConfig) },
                ),
            )
        }
        val score = outcome.score?.copy(tip = tip, recordState = record?.recordState)
        val finalised = FinalisedTrip(
            trip = draft,
            events = outcome.events,
            score = score,
            routeMatch = record?.match,
            newRoute = route?.route,
            extraMatches = listOfNotNull(paired),
            personalBest = record?.personalBest,
            preview = preview,
            analysis = sig.trace.toAnalysis(),
        )
        return Finalised(FinaliseResult(finalised, end), sig.t0Ns)
    }

    private fun R1Trace.toAnalysis(): AnalysisTrace = AnalysisTrace(
        fs, lonG, latG, vertG, yawDps, quality, gnss.map { AnalysisFix(it.tS, it.speedMps, it.lat, it.lng, it.speedAccMps) },
    )

    private fun nearestFix(sig: SignalResult, t: Double): Pair<Double, Double>? =
        sig.fixes.minByOrNull { kotlin.math.abs(it.tS - t) }?.let { it.lat to it.lng }

    @Suppress("LongParameterList")
    private fun trip(
        r: FinaliseRequest,
        sig: SignalResult,
        quality: TripQuality,
        distance: Double,
        m: com.driveperf.pipeline.signal.Metrics,
        suspect: com.driveperf.domain.trip.Suspect,
        context: DriveContext,
    ): Trip {
        val h = r.capture.header
        val estimatedM = (sig.estimate?.distanceM ?: 0.0) + (distance - m.gatedDistanceM - (sig.estimate?.distanceM ?: 0.0))
        return Trip(
            id = h.tripId,
            status = if (r.interruption != null) TripStatus.INTERRUPTED else TripStatus.FINALISED,
            kind = r.kind,
            source = if (h.wakeKind == WakeKind.MANUAL || r.manual) TripSource.MANUAL else TripSource.AUTO,
            sourceDetail = h.sourceDetail,
            startedAt = sig.startWallMs,
            startTzOffsetMin = h.tzOffsetMin,
            startEstimated = sig.estimate != null,
            endedAt = sig.startWallMs + (sig.endS * MS).toLong(),
            endTzOffsetMin = h.tzOffsetMin,
            tzId = h.tzId,
            partOfDay = metrics.partOfDay(sig.startWallMs, h.tzOffsetMin),
            context = context,
            distanceM = distance,
            estimatedDistanceM = estimatedM,
            durationS = sig.endS,
            movingS = m.movingS,
            stoppedS = m.stoppedS,
            stopCount = m.stops.size,
            avgMovingSpeedMps = m.avgMovingSpeedMps,
            maxSpeedMps = m.maxSpeedMps,
            maxSpeedAtS = m.maxSpeedAtS,
            elevGainM = m.elevGainM,
            elevLossM = m.elevLossM,
            elevMinM = m.elevMinM,
            elevMaxM = m.elevMaxM,
            bbox = m.bbox,
            stops = m.stops,
            quality = quality,
            interrupted = r.interruption?.let { Interruption(sig.endS, it) },
            suspect = suspect,
            captureMeta = CaptureMeta(h.wakeKind, null, sig.imuDelivery * PERCENT, null, null),
            pipelineVersion = Versions.PIPELINE,
            createdAt = h.startWallMs,
            updatedAt = r.nowWallMs,
        )
    }

    /** History plus parts of the same split that are not committed yet, so a later part compares with earlier ones. */
    private class PendingHistory(private val base: TripHistory, private val pending: List<FinalisedTrip>) : TripHistory {
        override suspend fun finishedBefore(beforeStartedAt: Long, limit: Int): List<ScoredTrip> =
            (pending.filter { it.trip.startedAt < beforeStartedAt }.map { ScoredTrip(it.trip, it.score, it.routeMatch?.routeId) } +
                base.finishedBefore(beforeStartedAt, limit))
                .sortedWith(compareByDescending<ScoredTrip> { it.trip.startedAt }.thenByDescending { it.trip.id })
                .take(limit)
    }

    private companion object {
        const val MS = 1000.0
        const val PERCENT = 100.0
        const val MAX_SPLIT_DEPTH = 8
        const val GNSS_ELIGIBLE_PCT = 60.0
        const val RECENT_TIPS = 2
    }
}
