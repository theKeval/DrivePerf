package com.driveperf.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.driveperf.data.local.db.DrivePerfDatabase
import com.driveperf.domain.repository.RouteRepository
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.route.PersonalBest
import com.driveperf.domain.route.Route
import com.driveperf.domain.route.RouteMatch
import com.driveperf.domain.score.Score
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.tripfile.FileResult
import com.driveperf.domain.tripfile.TripFileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** [TripRepository] on SQLDelight. Deleting a trip also deletes its files through [files]. */
public class LocalTripRepository(
    private val db: DrivePerfDatabase,
    private val files: TripFileStore,
    private val io: CoroutineContext,
) : TripRepository {
    private val q get() = db.tripQueries

    override fun observeTrips(): Flow<List<Trip>> =
        q.listedTrips().asFlow().mapToList(io).map { rows -> rows.map { it.toDomain(spans(it.id)) } }

    override suspend fun trip(id: String): Trip? = withContext(io) {
        q.tripById(id).executeAsOneOrNull()?.toDomain(spans(id))
    }

    override suspend fun events(tripId: String): List<DrivingEvent> = withContext(io) {
        q.eventsForTrip(tripId).executeAsList().map { it.toDomain() }
    }

    override suspend fun feedback(tripId: String): Map<String, EventFeedback> = withContext(io) {
        q.feedbackForTrip(tripId).executeAsList().map { it.toDomain() }.associateBy { it.eventId }
    }

    override suspend fun score(tripId: String): Score? = withContext(io) {
        q.scoreForTrip(tripId).executeAsOneOrNull()?.toDomain()
    }

    /** Files first: a failure keeps the row, so the files are never orphaned. */
    override suspend fun delete(tripId: String): FileResult = withContext(io) {
        files.deleteTrip(tripId).also { if (it == FileResult.OK) q.deleteTrip(tripId) }
    }

    private fun spans(tripId: String) = q.segmentsForTrip(tripId).executeAsList().map { it.toDomain() }
}

/** [RouteRepository] on SQLDelight. */
public class LocalRouteRepository(private val db: DrivePerfDatabase, private val io: CoroutineContext) : RouteRepository {
    private val q get() = db.routeQueries

    override fun observeRoutes(): Flow<List<Route>> =
        q.liveRoutes().asFlow().mapToList(io).map { rows -> rows.map { it.toDomain() } }

    override suspend fun route(id: String): Route? = withContext(io) { q.routeById(id).executeAsOneOrNull()?.toDomain() }

    override suspend fun matchFor(tripId: String): RouteMatch? = withContext(io) {
        q.matchForTrip(tripId).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun personalBest(routeId: String): PersonalBest? = withContext(io) {
        q.personalBest(routeId).executeAsOneOrNull()?.toDomain()
    }
}
