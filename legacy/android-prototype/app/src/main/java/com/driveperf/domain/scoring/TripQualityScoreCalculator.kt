package com.driveperf.domain.scoring

import com.driveperf.domain.model.DrivingEvent
import com.driveperf.domain.model.TripScore
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Computes the overall [TripScore] by combining the three sub-scores and applying
 * signal-confidence adjustments.
 *
 * Trip Quality Score weights:
 *   Safety     40 %
 *   Smoothness 30 %
 *   Efficiency 30 %
 *
 * Signal confidence below 70 reduces the quality score proportionally
 * to indicate that the data may be incomplete.
 */
@Singleton
class TripQualityScoreCalculator @Inject constructor(
    private val safetyScoreCalculator: SafetyScoreCalculator,
    private val smoothnessScoreCalculator: SmoothnessScoreCalculator,
    private val efficiencyScoreCalculator: EfficiencyScoreCalculator,
) {

    companion object {
        private const val SAFETY_WEIGHT = 0.40f
        private const val SMOOTHNESS_WEIGHT = 0.30f
        private const val EFFICIENCY_WEIGHT = 0.30f

        private const val LOW_CONFIDENCE_THRESHOLD = 70f
        private const val MAX_STARS = 5f
    }

    /**
     * @param tripId           ID of the trip being scored.
     * @param events           All driving events detected during the trip.
     * @param tripDurationMs   Total trip duration in milliseconds.
     * @param isNight          Whether the trip occurred primarily at night.
     * @param signalConfidence 0–100 GPS/sensor data quality confidence.
     * @return Fully populated [TripScore].
     */
    fun calculate(
        tripId: Long,
        events: List<DrivingEvent>,
        tripDurationMs: Long,
        isNight: Boolean = false,
        signalConfidence: Float = 100f,
    ): TripScore {
        val safety = safetyScoreCalculator.calculate(events, tripDurationMs, isNight)
        val smoothness = smoothnessScoreCalculator.calculate(events)
        val efficiency = efficiencyScoreCalculator.calculate(events)

        var quality =
            safety * SAFETY_WEIGHT +
            smoothness * SMOOTHNESS_WEIGHT +
            efficiency * EFFICIENCY_WEIGHT

        // Penalise low-confidence trips slightly
        if (signalConfidence < LOW_CONFIDENCE_THRESHOLD) {
            val confidenceFactor = signalConfidence / LOW_CONFIDENCE_THRESHOLD
            quality *= confidenceFactor
        }

        quality = max(0f, quality)

        val starRating = (quality / 100f * MAX_STARS * 2).roundToInt() / 2f // 0.5-star steps

        val breakdown = buildBreakdown(safety, smoothness, efficiency, signalConfidence)

        return TripScore(
            tripId = tripId,
            safetyScore = safety,
            smoothnessScore = smoothness,
            efficiencyScore = efficiency,
            tripQualityScore = quality,
            starRating = starRating,
            breakdown = breakdown,
            signalConfidence = signalConfidence,
        )
    }

    private fun buildBreakdown(
        safety: Float,
        smoothness: Float,
        efficiency: Float,
        confidence: Float,
    ): Map<String, String> = buildMap {
        put("Safety", formatScoreExplanation("Safety", safety,
            "Considers speeding, hard braking, harsh acceleration, cornering, and phone use."))
        put("Smoothness", formatScoreExplanation("Smoothness", smoothness,
            "Considers jerk, abrupt acceleration/braking, cornering harshness, and stop-start."))
        put("Efficiency", formatScoreExplanation("Efficiency", efficiency,
            "Proxy based on idling time, overspeed duration, and aggressive acceleration/braking patterns."))
        if (confidence < LOW_CONFIDENCE_THRESHOLD) {
            put("Signal", "GPS/sensor confidence was ${confidence.toInt()}% — score may be less accurate.")
        }
    }

    private fun formatScoreExplanation(label: String, score: Float, detail: String): String =
        "$label score: ${score.toInt()}/100. $detail"
}
