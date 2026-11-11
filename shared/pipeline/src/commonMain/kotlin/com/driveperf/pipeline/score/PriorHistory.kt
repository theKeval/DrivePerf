package com.driveperf.pipeline.score

import com.driveperf.domain.repository.ScoredTrip
import com.driveperf.domain.repository.TripHistory
import com.driveperf.domain.trip.DriveContext

/** Reads the prior trips a score is compared with (§5.7), for the finaliser and for re-scoring after feedback. */
internal class PriorHistory(private val history: TripHistory) {
    /** The newest finished trips before [beforeStartedAt], one page. */
    suspend fun recent(beforeStartedAt: Long): List<ScoredTrip> = history.finishedBefore(beforeStartedAt, PAGE)

    /**
     * §5.7 baselines use the last 10 eligible trips of a basis, however many ineligible or other-context trips came
     * in between, so history is read a page at a time until this trip's context, and its route when it has one, have
     * 10 each or history ends.
     */
    suspend fun priorsFor(beforeStartedAt: Long, context: DriveContext, routeId: String?): List<PriorTrip> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<PriorTrip>()
        var cursor = beforeStartedAt
        do {
            val page = history.finishedBefore(cursor, PAGE).filter { seen.add(it.trip.id) }
            out += priors(page)
            // The next page starts at the last startedAt again, so trips sharing it are not skipped.
            cursor = (page.lastOrNull()?.trip?.startedAt ?: cursor) + 1
            val enough = out.count { it.context == context } >= Baselines.DEPTH &&
                (routeId == null || out.count { it.routeId == routeId } >= Baselines.DEPTH)
        } while (page.isNotEmpty() && !enough)
        return out
    }

    private fun priors(history: List<ScoredTrip>): List<PriorTrip> = history.mapNotNull { (trip, score, routeId) ->
        val smoothness = score?.smoothness
        if (smoothness == null || !Baselines.eligible(trip, smoothness)) return@mapNotNull null
        PriorTrip(
            tripId = trip.id,
            startedAt = trip.startedAt,
            context = trip.context ?: return@mapNotNull null,
            routeId = routeId,
            smoothness = smoothness,
            subScores = score.contributors.associate { it.id to it.subScore },
            stopsPerKm = score.efficiency?.stopsPerKm,
            abruptAccelsPerMin = score.efficiency?.abruptAccelsPerMin,
            speedWobbleMps = score.efficiency?.speedWobbleMps,
        )
    }

    private companion object {
        const val PAGE = 200
    }
}
