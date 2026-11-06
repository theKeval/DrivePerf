package com.driveperf.domain.scoring

import com.driveperf.domain.model.DrivingEvent
import com.driveperf.domain.model.EventType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Calculates a Safety Score [0, 100] for a trip.
 *
 * Penalties are applied for:
 * - Hard braking events
 * - Harsh acceleration events
 * - Aggressive cornering events
 * - Speeding events (weighted by severity)
 * - Phone distraction events (screen use penalised most)
 *
 * Night driving and long-trip context act as multipliers (raise exposure risk)
 * rather than direct score deductions.
 */
@Singleton
class SafetyScoreCalculator @Inject constructor() {

    companion object {
        private const val BRAKING_PENALTY = 8f
        private const val ACCELERATION_PENALTY = 7f
        private const val CORNERING_PENALTY = 5f
        private const val SPEEDING_BASE_PENALTY = 10f
        private const val DISTRACTION_PENALTY = 12f
        private const val NIGHT_MULTIPLIER = 1.15f
        private const val LONG_TRIP_MULTIPLIER = 1.10f
        private const val LONG_TRIP_THRESHOLD_MS = 60L * 60_000L // 1 hour
    }

    /**
     * @param events All driving events for this trip.
     * @param tripDurationMs Total trip duration in milliseconds.
     * @param isNight Whether the trip occurred primarily at night.
     * @return Safety score clamped to [0, 100].
     */
    fun calculate(
        events: List<DrivingEvent>,
        tripDurationMs: Long,
        isNight: Boolean = false,
    ): Float {
        var penalty = 0f

        for (event in events) {
            penalty += when (event.type) {
                EventType.HARD_BRAKING -> BRAKING_PENALTY * (1f + event.severity)
                EventType.HARSH_ACCELERATION -> ACCELERATION_PENALTY * (1f + event.severity)
                EventType.AGGRESSIVE_CORNERING -> CORNERING_PENALTY * (1f + event.severity)
                EventType.SPEEDING -> SPEEDING_BASE_PENALTY * (1f + event.severity)
                EventType.PHONE_DISTRACTION -> DISTRACTION_PENALTY * (1f + event.severity)
                else -> 0f
            }
        }

        // Context multipliers increase penalty exposure, not score directly
        if (isNight) penalty *= NIGHT_MULTIPLIER
        if (tripDurationMs > LONG_TRIP_THRESHOLD_MS) penalty *= LONG_TRIP_MULTIPLIER

        return max(0f, 100f - penalty)
    }
}
