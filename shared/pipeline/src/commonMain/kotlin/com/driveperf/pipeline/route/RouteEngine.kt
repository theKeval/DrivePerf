package com.driveperf.pipeline.route

import com.driveperf.domain.repository.RouteHistory
import com.driveperf.domain.repository.ScoredTrip
import com.driveperf.domain.route.IneligibleReason
import com.driveperf.domain.route.MatchSource
import com.driveperf.domain.route.PersonalBest
import com.driveperf.domain.route.RecordEntry
import com.driveperf.domain.route.Route
import com.driveperf.domain.route.RouteMatch
import com.driveperf.domain.route.RouteState
import com.driveperf.domain.score.RecordState
import com.driveperf.domain.trip.PreviewRow
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.SuspectAnswer
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.foundation.newId
import com.driveperf.pipeline.score.Baselines

/** The route outcome of one trip, before the score's record fields are known. */
internal data class RouteOutcome(val route: Route, val similarity: Double, val created: Boolean, val paired: ScoredTrip?)

/** What §7.5 decides once the score exists. */
internal data class RecordOutcome(val match: RouteMatch, val recordState: RecordState, val personalBest: PersonalBest?)

/**
 * §7.4 candidates and promotion, §7.5 personal bests. Reads routes, runs and previews through [RouteHistory].
 */
internal class RouteEngine(private val history: RouteHistory, private val cfg: RouteConfig = RouteConfig()) {
    private val matcher = RouteMatcher(cfg)

    /** §7.1: only driver trips with GNSS coverage ≥ 60 % and no open suspicion seed or join a route. */
    fun eligible(trip: Trip): Boolean {
        val gnss = trip.quality?.gnssCoveragePct ?: 0.0
        val suspectOk = trip.suspect == Suspect.NONE || trip.suspectAnswer == SuspectAnswer.NO
        return trip.kind == TripKind.DRIVER && gnss >= cfg.minGnssPct && suspectOk
    }

    /**
     * Matches [trip] against existing routes (smallest Fréchet; tie oldest `createdAt`, then smallest ID), or pairs it
     * with an unmatched eligible trip from [unmatched] to create a candidate.
     */
    suspend fun match(trip: Trip, preview: List<PreviewRow>, unmatched: List<ScoredTrip>, nowWallMs: Long): RouteOutcome? {
        val side = Side.ofTrip(preview)
        if (!eligible(trip) || side == null) return null
        return matchExisting(side, nowWallMs) ?: pairWithTrip(trip, side, unmatched, nowWallMs)
    }

    private suspend fun matchExisting(side: Side, nowWallMs: Long): RouteOutcome? {
        val scored = history.routes().mapNotNull { route ->
            val ref = history.preview(route.referenceTripId) ?: return@mapNotNull null
            val routeSide = Side(route.startLat, route.startLng, route.endLat, route.endLng, ref)
            if (!matcher.endpointsMatch(side, routeSide)) return@mapNotNull null
            matcher.frechet(side, routeSide)?.let { route to it }
        }
        val best = scored.minWithOrNull(compareBy<Pair<Route, Double>> { it.second }.thenBy { it.first.createdAt }.thenBy { it.first.id })
            ?: return null
        val route = best.first
        val runs = history.runs(route.id, Int.MAX_VALUE).size + 1
        val promoted = if (route.state == RouteState.CANDIDATE && runs >= PROMOTE_AT_RUNS) RouteState.ROUTE else route.state
        val updated = route.copy(
            startLat = runningMean(route.startLat, side.startLat, runs),
            startLng = runningMean(route.startLng, side.startLng, runs),
            endLat = runningMean(route.endLat, side.endLat, runs),
            endLng = runningMean(route.endLng, side.endLng, runs),
            state = promoted,
            updatedAt = nowWallMs,
        )
        return RouteOutcome(updated, matcher.similarity(best.second), created = false, paired = null)
    }

    @Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements") // §7.4 pairing: one guard per rule
    private suspend fun pairWithTrip(trip: Trip, side: Side, unmatched: List<ScoredTrip>, nowWallMs: Long): RouteOutcome? {
        val candidates = unmatched.filter { it.routeId == null && eligible(it.trip) }
            .sortedWith(compareByDescending<ScoredTrip> { it.trip.startedAt }.thenByDescending { it.trip.id })
            .take(PAIRING_LIMIT)
        var best: Triple<ScoredTrip, Side, Double>? = null
        for (other in candidates) {
            val otherSide = history.preview(other.trip.id)?.let { Side.ofTrip(it) } ?: continue
            if (!matcher.endpointsMatch(side, otherSide)) continue
            val f = matcher.frechet(side, otherSide) ?: continue
            if (best == null || f < best.third) best = Triple(other, otherSide, f)
        }
        val (other, otherSide, f) = best ?: return null
        val route = Route(
            id = newId(),
            name = null,
            namePreset = null,
            state = RouteState.CANDIDATE,
            fromLabel = null,
            toLabel = null,
            startLat = (side.startLat + otherSide.startLat) / 2,
            startLng = (side.startLng + otherSide.startLng) / 2,
            endLat = (side.endLat + otherSide.endLat) / 2,
            endLng = (side.endLng + otherSide.endLng) / 2,
            clusterRadiusM = cfg.radiusM,
            referenceTripId = other.trip.id,
            distanceM = ((trip.distanceM ?: 0.0) + (other.trip.distanceM ?: 0.0)) / 2,
            createdAt = nowWallMs,
            updatedAt = nowWallMs,
        )
        return RouteOutcome(route, matcher.similarity(f), created = true, paired = other)
    }

    /**
     * §7.5: eligibility in order (PASSENGER, QUALITY, EXCLUSIONS, PACE_BAND), then a strictly higher Smoothness sets
     * the record. Records hold Smoothness only.
     */
    suspend fun record(trip: Trip, smoothness: Int?, neededCount: Int, outcome: RouteOutcome): RecordOutcome {
        val routeId = outcome.route.id
        val runs = if (outcome.created) emptyList() else history.runs(routeId, PACE_DEPTH)
        val reason = ineligibility(trip, smoothness, neededCount, runs)
        val current = if (outcome.created) pairedRecord(outcome) else history.personalBest(routeId)
        val score = smoothness.takeIf { reason == null }
        val isNew = score != null && (current == null || score > current.smoothness)
        val best = if (isNew) {
            val entry = RecordEntry(trip.id, score, trip.startedAt)
            PersonalBest(routeId, trip.id, score, trip.startedAt, current?.history.orEmpty() + entry)
        } else {
            current
        }
        return RecordOutcome(
            RouteMatch(trip.id, routeId, outcome.similarity, MatchSource.AUTO, reason == null, reason),
            RecordState(isNew, current?.smoothness, current?.tripId),
            best,
        )
    }

    /**
     * §7.5 eligibility, checks in the order the spec stores the first failure; null when eligible. It depends only on
     * the run itself and the route's earlier runs, never on which run holds the record.
     */
    private fun ineligibility(trip: Trip, smoothness: Int?, neededCount: Int, runs: List<ScoredTrip>): IneligibleReason? {
        val badge = trip.quality?.badge
        val qualityOk = (badge == QualityBadge.GOOD || badge == QualityBadge.USABLE) && smoothness != null &&
            (trip.suspect == Suspect.NONE || trip.suspectAnswer == SuspectAnswer.NO)
        return when {
            trip.kind != TripKind.DRIVER -> IneligibleReason.PASSENGER
            !qualityOk -> IneligibleReason.QUALITY
            neededCount > MAX_NEEDED -> IneligibleReason.EXCLUSIONS
            !paceBandPasses(trip.movingS ?: 0.0, runs) -> IneligibleReason.PACE_BAND
            else -> null
        }
    }

    /**
     * A new candidate's first record is its older run, when that run is eligible on its own inputs. It is the route's
     * first run, so the pace band passes; its feedback is not in history, so it counts no needed events, as a trip
     * does at finalisation.
     */
    private fun pairedRecord(outcome: RouteOutcome): PersonalBest? {
        val other = outcome.paired ?: return null
        val smoothness = other.score?.smoothness
        return if (ineligibility(other.trip, smoothness, 0, emptyList()) == null && smoothness != null) {
            val entry = RecordEntry(other.trip.id, smoothness, other.trip.startedAt)
            PersonalBest(outcome.route.id, other.trip.id, smoothness, other.trip.startedAt, listOf(entry))
        } else {
            null
        }
    }

    /** The match written for the paired older trip of a new candidate (§7.4), judged as in [pairedRecord]. */
    fun pairedMatch(outcome: RouteOutcome): RouteMatch? = outcome.paired?.let { other ->
        val reason = ineligibility(other.trip, other.score?.smoothness, 0, emptyList())
        RouteMatch(other.trip.id, outcome.route.id, outcome.similarity, MatchSource.AUTO, reason == null, reason)
    }

    /** §7.5 pace band: moving time within 0.75–1.50× the median of the last 10 prior runs; passes with < 3 runs. */
    private fun paceBandPasses(movingS: Double, runs: List<ScoredTrip>): Boolean {
        val times = runs.take(PACE_DEPTH).mapNotNull { it.trip.movingS }
        val m = Baselines.median(times)
        return times.size < MIN_RUNS || m == null || movingS in PACE_LOW * m..PACE_HIGH * m
    }

    private fun runningMean(mean: Double, x: Double, n: Int): Double = mean + (x - mean) / n

    private companion object {
        const val PROMOTE_AT_RUNS = 3
        const val PAIRING_LIMIT = 200
        const val PACE_DEPTH = 10
        const val MIN_RUNS = 3
        const val PACE_LOW = 0.75
        const val PACE_HIGH = 1.50
        const val MAX_NEEDED = 2
    }
}
