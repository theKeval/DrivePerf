package com.driveperf.domain.model

/**
 * A driving event detected within a trip.
 *
 * @param id Unique identifier.
 * @param tripId Foreign key to the parent [TripSession].
 * @param type The category of driving behaviour.
 * @param timestampMs When the event occurred.
 * @param durationMs How long the event lasted (0 for instantaneous events).
 * @param severity A value in [0.0, 1.0] indicating how severe the event was.
 * @param details Human-readable explanation shown to the user.
 * @param latitude Approximate latitude where the event occurred.
 * @param longitude Approximate longitude where the event occurred.
 */
data class DrivingEvent(
    val id: Long = 0L,
    val tripId: Long,
    val type: EventType,
    val timestampMs: Long,
    val durationMs: Long = 0L,
    val severity: Float = 0f,
    val details: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
)
