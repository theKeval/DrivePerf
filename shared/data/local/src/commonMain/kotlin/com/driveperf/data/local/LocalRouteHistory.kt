package com.driveperf.data.local

import com.driveperf.data.local.db.DrivePerfDatabase
import com.driveperf.domain.capture.PreviewStore
import com.driveperf.domain.repository.RouteHistory
import com.driveperf.domain.repository.ScoredTrip
import com.driveperf.domain.route.PersonalBest
import com.driveperf.domain.route.Route
import com.driveperf.domain.trip.PreviewRow
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** [RouteHistory] on SQLDelight and the preview files. */
public class LocalRouteHistory(
    private val db: DrivePerfDatabase,
    private val previews: PreviewStore,
    private val io: CoroutineContext,
) : RouteHistory {
    override suspend fun routes(): List<Route> = withContext(io) {
        db.routeQueries.liveRoutes().executeAsList().map { it.toDomain() }
    }

    override suspend fun runs(routeId: String, limit: Int): List<ScoredTrip> = withContext(io) {
        val trips = db.tripQueries
        db.routeQueries.matchesForRoute(routeId).executeAsList()
            .mapNotNull { match -> trips.tripById(match.trip_id).executeAsOneOrNull() }
            .sortedWith(compareByDescending<com.driveperf.data.local.db.Trip> { it.started_at }.thenByDescending { it.id })
            .take(limit)
            .map { row ->
                ScoredTrip(
                    row.toDomain(trips.segmentsForTrip(row.id).executeAsList().map { it.toDomain() }),
                    trips.scoreForTrip(row.id).executeAsOneOrNull()?.toDomain(),
                    routeId,
                )
            }
    }

    override suspend fun personalBest(routeId: String): PersonalBest? = withContext(io) {
        db.routeQueries.personalBest(routeId).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun preview(tripId: String): List<PreviewRow>? = previews.read(tripId)
}
