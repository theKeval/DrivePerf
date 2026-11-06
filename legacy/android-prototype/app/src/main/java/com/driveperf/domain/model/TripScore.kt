package com.driveperf.domain.model

/**
 * Aggregated scores for a completed trip.
 *
 * All scores are in the range [0.0, 100.0] where 100 is best.
 *
 * @param tripId Foreign key to the parent [TripSession].
 * @param safetyScore Penalises speeding, hard braking, harsh acceleration, cornering, and phone use.
 * @param smoothnessScore Penalises jerk, abrupt longitudinal/lateral force changes, and stop-start.
 * @param efficiencyScore Proxy score based on idling time, overspeed time, and repeated aggressive events.
 * @param tripQualityScore Weighted aggregate of the other three scores plus signal-confidence adjustments.
 * @param starRating Convenience 0-5 star rating derived from [tripQualityScore].
 * @param breakdown Human-readable per-category explanations keyed by [EventType] name.
 * @param signalConfidence 0-100 confidence in the data quality for this trip.
 */
data class TripScore(
    val tripId: Long,
    val safetyScore: Float = 100f,
    val smoothnessScore: Float = 100f,
    val efficiencyScore: Float = 100f,
    val tripQualityScore: Float = 100f,
    val starRating: Float = 5f,
    val breakdown: Map<String, String> = emptyMap(),
    val signalConfidence: Float = 100f,
)
