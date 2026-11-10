package com.driveperf.domain.repository

import com.driveperf.domain.route.PersonalBest
import com.driveperf.domain.route.Route
import com.driveperf.domain.route.RouteMatch
import com.driveperf.domain.score.Score
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.Trip
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

    /** Deletes a trip, its rows and its files (F-PRV-04). */
    public suspend fun delete(tripId: String)
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

/** A finished trip with what the scorer needs from it: its score and its route, if matched. */
public data class ScoredTrip(val trip: com.driveperf.domain.trip.Trip, val score: Score?, val routeId: String?)

/**
 * History the finaliser reads for baselines, efficiency and route matching (§5.7, §5.9, §7). Implemented in
 * `:shared:data:local`; not a platform port.
 */
public interface TripHistory {
    /** Finalised trips that started before [beforeStartedAt], newest first (`startedAt` desc, `id` desc), at most [limit]. */
    public suspend fun finishedBefore(beforeStartedAt: Long, limit: Int): List<ScoredTrip>
}

/** What route matching reads (§7). Implemented in `:shared:data:local`; not a platform port. */
public interface RouteHistory {
    /** Live routes and candidates (not merged, not deleted). */
    public suspend fun routes(): List<Route>

    /** Prior runs of a route, newest first, at most [limit]. */
    public suspend fun runs(routeId: String, limit: Int): List<ScoredTrip>

    /** The route's current record, or null. */
    public suspend fun personalBest(routeId: String): PersonalBest?

    /** A trip's preview track, or null when it is gone. */
    public suspend fun preview(tripId: String): List<com.driveperf.domain.trip.PreviewRow>?
}
