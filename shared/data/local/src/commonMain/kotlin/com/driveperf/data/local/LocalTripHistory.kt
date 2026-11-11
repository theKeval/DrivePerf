package com.driveperf.data.local

import com.driveperf.data.local.db.DrivePerfDatabase
import com.driveperf.domain.repository.ScoredTrip
import com.driveperf.domain.repository.TripHistory
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** [TripHistory] on SQLDelight: finished trips with their score and route match, newest first. */
public class LocalTripHistory(private val db: DrivePerfDatabase, private val io: CoroutineContext) : TripHistory {
    override suspend fun finishedBefore(beforeStartedAt: Long, limit: Int): List<ScoredTrip> = withContext(io) {
        val trips = db.tripQueries
        trips.finishedBefore(beforeStartedAt, limit.toLong()).executeAsList().map { row ->
            ScoredTrip(
                trip = row.toDomain(trips.segmentsForTrip(row.id).executeAsList().map { it.toDomain() }),
                score = trips.scoreForTrip(row.id).executeAsOneOrNull()?.toDomain(),
                routeId = db.routeQueries.matchForTrip(row.id).executeAsOneOrNull()?.route_id,
            )
        }
    }
}
