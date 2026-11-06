package com.driveperf.scoring

import com.driveperf.domain.model.DrivingEvent
import com.driveperf.domain.model.EventType
import com.driveperf.domain.scoring.EfficiencyScoreCalculator
import com.driveperf.domain.scoring.SafetyScoreCalculator
import com.driveperf.domain.scoring.SmoothnessScoreCalculator
import com.driveperf.domain.scoring.TripQualityScoreCalculator
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class TripQualityScoreCalculatorTest {

    private lateinit var calculator: TripQualityScoreCalculator

    @Before
    fun setUp() {
        calculator = TripQualityScoreCalculator(
            safetyScoreCalculator = SafetyScoreCalculator(),
            smoothnessScoreCalculator = SmoothnessScoreCalculator(),
            efficiencyScoreCalculator = EfficiencyScoreCalculator(),
        )
    }

    @Test
    fun `perfect trip returns 5 stars and 100 quality`() {
        val score = calculator.calculate(
            tripId = 1L,
            events = emptyList(),
            tripDurationMs = 10_000,
            signalConfidence = 100f,
        )
        assertThat(score.tripQualityScore).isEqualTo(100f)
        assertThat(score.starRating).isEqualTo(5f)
    }

    @Test
    fun `low signal confidence reduces quality score`() {
        val highConfidence = calculator.calculate(
            tripId = 1L, events = emptyList(), tripDurationMs = 10_000, signalConfidence = 100f,
        )
        val lowConfidence = calculator.calculate(
            tripId = 1L, events = emptyList(), tripDurationMs = 10_000, signalConfidence = 40f,
        )
        assertThat(lowConfidence.tripQualityScore).isLessThan(highConfidence.tripQualityScore)
    }

    @Test
    fun `quality score never goes below 0`() {
        val manyBadEvents = List(100) {
            DrivingEvent(tripId = 1, type = EventType.HARD_BRAKING, timestampMs = it.toLong(), severity = 1f)
        }
        val score = calculator.calculate(
            tripId = 1L,
            events = manyBadEvents,
            tripDurationMs = 10_000,
            signalConfidence = 10f,
        )
        assertThat(score.tripQualityScore).isAtLeast(0f)
        assertThat(score.starRating).isAtLeast(0f)
    }

    @Test
    fun `breakdown contains explanation for each dimension`() {
        val score = calculator.calculate(
            tripId = 1L, events = emptyList(), tripDurationMs = 10_000,
        )
        assertThat(score.breakdown).containsKey("Safety")
        assertThat(score.breakdown).containsKey("Smoothness")
        assertThat(score.breakdown).containsKey("Efficiency")
    }

    @Test
    fun `low confidence adds signal note to breakdown`() {
        val score = calculator.calculate(
            tripId = 1L, events = emptyList(), tripDurationMs = 10_000, signalConfidence = 50f,
        )
        assertThat(score.breakdown).containsKey("Signal")
    }

    @Test
    fun `trip id is preserved in score`() {
        val score = calculator.calculate(
            tripId = 42L, events = emptyList(), tripDurationMs = 10_000,
        )
        assertThat(score.tripId).isEqualTo(42L)
    }

    @Test
    fun `star rating is in increments of 0-5`() {
        val score = calculator.calculate(
            tripId = 1L, events = emptyList(), tripDurationMs = 10_000,
        )
        assertThat(score.starRating).isIn(
            listOf(0f, 0.5f, 1f, 1.5f, 2f, 2.5f, 3f, 3.5f, 4f, 4.5f, 5f)
        )
    }
}
