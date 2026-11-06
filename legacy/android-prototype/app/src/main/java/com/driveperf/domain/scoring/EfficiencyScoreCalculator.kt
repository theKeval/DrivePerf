package com.driveperf.domain.scoring

import com.driveperf.domain.model.DrivingEvent
import com.driveperf.domain.model.EventType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Calculates a proxy Efficiency Score [0, 100] for a trip.
 *
 * This is a **behavioural proxy** – it does not measure actual fuel/energy consumption.
 * It penalises driving patterns known to waste energy:
 * - Idling events
 * - Speeding (aerodynamic drag increases dramatically above 100 km/h)
 * - Repeated harsh acceleration (accelerating hard then braking wastes kinetic energy)
 * - Repeated hard braking (kinetic energy converted to heat)
 * - Excessive stop-start sequences
 */
@Singleton
class EfficiencyScoreCalculator @Inject constructor() {

    companion object {
        private const val IDLING_PENALTY = 5f
        private const val SPEEDING_PENALTY = 8f
        private const val ACCELERATION_PENALTY = 6f
        private const val BRAKING_PENALTY = 4f
        private const val STOP_START_PENALTY = 3f
    }

    /**
     * @param events All driving events for this trip.
     * @return Efficiency score clamped to [0, 100].
     */
    fun calculate(events: List<DrivingEvent>): Float {
        var penalty = 0f

        for (event in events) {
            penalty += when (event.type) {
                EventType.IDLING -> IDLING_PENALTY * (1f + event.severity)
                EventType.SPEEDING -> SPEEDING_PENALTY * (1f + event.severity)
                EventType.HARSH_ACCELERATION -> ACCELERATION_PENALTY * (1f + event.severity)
                EventType.HARD_BRAKING -> BRAKING_PENALTY * (1f + event.severity)
                EventType.STOP_START -> STOP_START_PENALTY * (1f + event.severity)
                else -> 0f
            }
        }

        return max(0f, 100f - penalty)
    }
}
