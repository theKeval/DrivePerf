package com.driveperf.domain.repository

import com.driveperf.domain.route.PersonalBest
import com.driveperf.domain.route.Route
import com.driveperf.domain.route.RouteMatch
import com.driveperf.domain.score.Score
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.tripfile.FileResult
import kotlinx.coroutines.flow.Flow

/**
 * Read side of the trip store (ADR-002 section 3). Implemented in Kotlin by `:shared:data:local` on SQLDelight, so
 * it may use `suspend` and `Flow`. The device database is the source of truth (data-model.md P1).
 */
public interface TripRepository {
    /** Finalised and interrupted trips, newest first (`startedAt` desc, `id` desc). Never recording or discarded. */
    public fun observeTrips(): Flow<List<Trip>>

    /** One trip, or null when it does not exist or is a tombstone. */
    public suspend fun trip(id: String): Trip?

    /** A trip's events, by `tPeakS`. */
    public suspend fun events(tripId: String): List<DrivingEvent>

    /** A trip's feedback, by event ID. */
    public suspend fun feedback(tripId: String): Map<String, EventFeedback>

    /** A trip's current score, or null. */
    public suspend fun score(tripId: String): Score?

    /**
     * Deletes a trip's files, then its rows (F-PRV-04). When the files cannot be deleted the trip is kept and the
     * file failure returned, so no raw location or motion data is left without a trip; the user can try again.
     */
    public suspend fun delete(tripId: String): FileResult
}

/** Read side of routes and records. */
public interface RouteRepository {
    /** Live routes and candidates, newest first. */
    public fun observeRoutes(): Flow<List<Route>>

    /** One route, or null. */
    public suspend fun route(id: String): Route?

    /** A trip's match, or null. */
    public suspend fun matchFor(tripId: String): RouteMatch?

    /** The current record of a route, or null. */
    public suspend fun personalBest(routeId: String): PersonalBest?
}
