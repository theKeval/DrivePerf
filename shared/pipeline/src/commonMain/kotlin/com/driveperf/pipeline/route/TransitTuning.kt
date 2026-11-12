package com.driveperf.pipeline.route

import com.driveperf.domain.repository.RouteHistory
import com.driveperf.domain.repository.ScoredTrip
import com.driveperf.domain.trip.PreviewRow
import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.SuspectAnswer
import com.driveperf.domain.trip.Trip
import com.driveperf.pipeline.signal.SignalConfig

/**
 * F-CAP-04 AC4, the only per-user tuning (§1.8): once the user has answered "No" to "Was this a bus or train?" on
 * [SignalConfig.transitDeclinedToSuppress] earlier trips along the same path, a new trip on that path is not asked.
 * "Same path" is the route test (§7.2 endpoints, §7.3 Fréchet), run on previews because a suspected trip never gets a
 * route match.
 */
internal class TransitTuning(
    private val history: RouteHistory,
    private val cfg: SignalConfig,
    routeConfig: RouteConfig = RouteConfig(),
) {
    private val matcher = RouteMatcher(routeConfig)

    /** True when [trip]'s transit question should not be asked. [recent] is finished history, newest first. */
    suspend fun declinedHere(trip: Trip, preview: List<PreviewRow>, recent: List<ScoredTrip>): Boolean {
        if (trip.suspect != Suspect.TRANSIT) return false
        val declined = recent.take(cfg.transitDeclinedLookback).map { it.trip }
            .filter { it.suspect == Suspect.TRANSIT && it.suspectAnswer == SuspectAnswer.NO }
        return declined.size >= cfg.transitDeclinedToSuppress &&
            samePathCount(preview, declined) >= cfg.transitDeclinedToSuppress
    }

    /** How many of [others] follow the same path as [preview], by the test that puts two trips on one route. */
    suspend fun samePathCount(preview: List<PreviewRow>, others: List<Trip>): Int {
        val side = Side.ofTrip(preview) ?: return 0
        return others.count { other ->
            val otherSide = history.preview(other.id)?.let { Side.ofTrip(it) }
            otherSide != null && matcher.endpointsMatch(side, otherSide) && matcher.frechet(side, otherSide) != null
        }
    }
}
