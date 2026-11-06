package com.driveperf.data.repository

import com.driveperf.domain.model.DrivingEvent
import com.driveperf.domain.model.TripScore
import com.driveperf.domain.model.TripSession
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for trip data.
 * Implementations may be backed by Room, a remote API, or a combination.
 */
interface TripRepository {

    // ── Trip sessions ──────────────────────────────────────────────────────

    /** Persists a new trip and returns its generated ID. */
    suspend fun startTrip(startTimeMs: Long): Long

    /** Marks a trip as active (confirmed in-vehicle). */
    suspend fun activateTrip(tripId: Long)

    /** Ends an active trip, recording its distance. */
    suspend fun endTrip(tripId: Long, endTimeMs: Long, distanceMeters: Double)

    /** Flags the trip as driven by a passenger, effectively discarding it. */
    suspend fun markAsPassenger(tripId: Long)

    /** Permanently deletes all data for a trip. */
    suspend fun deleteTrip(tripId: Long)

    /** Observes all non-discarded trips ordered by start time descending. */
    fun observeAllTrips(): Flow<List<TripSession>>

    /** Observes a single trip by its ID. */
    fun observeTrip(tripId: Long): Flow<TripSession?>

    /** Returns the currently active trip if one exists. */
    suspend fun getActiveTrip(): TripSession?

    // ── Driving events ─────────────────────────────────────────────────────

    /** Records a single driving event. */
    suspend fun saveEvent(event: DrivingEvent)

    /** Records multiple driving events in one transaction. */
    suspend fun saveEvents(events: List<DrivingEvent>)

    /** Observes all driving events for a trip. */
    fun observeEventsForTrip(tripId: Long): Flow<List<DrivingEvent>>

    // ── Scores ─────────────────────────────────────────────────────────────

    /** Persists a computed score for a trip. */
    suspend fun saveScore(score: TripScore)

    /** Observes the score for a specific trip. */
    fun observeScoreForTrip(tripId: Long): Flow<TripScore?>

    // ── Insights ───────────────────────────────────────────────────────────

    /** Observes the average safety score for trips since [fromMs]. */
    fun observeAverageSafetyScore(fromMs: Long): Flow<Float?>

    /** Observes the average trip quality score for trips since [fromMs]. */
    fun observeAverageQualityScore(fromMs: Long): Flow<Float?>

    /** Observes the count of completed trips. */
    fun observeCompletedTripCount(): Flow<Int>
}
