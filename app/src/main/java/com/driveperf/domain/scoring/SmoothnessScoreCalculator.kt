package com.driveperf.domain.scoring

import com.driveperf.domain.model.DrivingEvent
import com.driveperf.domain.model.EventType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Calculates a Smoothness Score [0, 100] for a trip.
 *
 * Penalises:
 * - Hard braking (abrupt longitudinal deceleration)
 * - Harsh acceleration (abrupt longitudinal acceleration)
 * - Aggressive cornering (abrupt lateral force / high jerk)
 * - Excessive stop-start patterns
 */
@Singleton
class SmoothnessScoreCalculator @Inject constructor() {

    companion object {
        private const val BRAKING_PENALTY = 6f
        private const val ACCELERATION_PENALTY = 6f
        private const val CORNERING_PENALTY = 7f
        private const val STOP_START_PENALTY = 4f
    }

    /**
     * @param events All driving events for this trip.
     * @return Smoothness score clamped to [0, 100].
     */
    fun calculate(events: List<DrivingEvent>): Float {
        var penalty = 0f

        for (event in events) {
            penalty += when (event.type) {
                EventType.HARD_BRAKING -> BRAKING_PENALTY * (1f + event.severity)
                EventType.HARSH_ACCELERATION -> ACCELERATION_PENALTY * (1f + event.severity)
                EventType.AGGRESSIVE_CORNERING -> CORNERING_PENALTY * (1f + event.severity)
                EventType.STOP_START -> STOP_START_PENALTY * (1f + event.severity)
                else -> 0f
            }
        }

        return max(0f, 100f - penalty)
    }
}
