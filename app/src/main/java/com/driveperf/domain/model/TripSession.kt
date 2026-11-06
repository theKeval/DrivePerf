package com.driveperf.domain.model

/** State of a trip session. */
enum class TripState {
    DETECTING,   // Activity Recognition running, not yet confirmed in-vehicle
    ACTIVE,      // Trip is being recorded
    COMPLETED,   // Trip ended and scored
    DISCARDED,   // User marked as not driver / deleted
}

/**
 * Domain model representing a single driving trip.
 *
 * @param id Unique identifier for this trip.
 * @param startTimeMs Epoch millis when the trip started.
 * @param endTimeMs Epoch millis when the trip ended (null if still active).
 * @param distanceMeters Total distance in metres.
 * @param state Current state of this trip.
 * @param isPassenger Whether the user flagged themselves as a passenger.
 * @param score Scores computed after the trip ends.
 */
data class TripSession(
    val id: Long = 0L,
    val startTimeMs: Long,
    val endTimeMs: Long? = null,
    val distanceMeters: Double = 0.0,
    val state: TripState = TripState.DETECTING,
    val isPassenger: Boolean = false,
    val score: TripScore? = null,
)
