package com.driveperf.scoring

import com.driveperf.domain.model.DrivingEvent
import com.driveperf.domain.model.EventType
import com.driveperf.domain.scoring.SafetyScoreCalculator
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class SafetyScoreCalculatorTest {

    private lateinit var calculator: SafetyScoreCalculator

    @Before
    fun setUp() {
        calculator = SafetyScoreCalculator()
    }

    @Test
    fun `perfect trip with no events returns 100`() {
        val score = calculator.calculate(emptyList(), tripDurationMs = 10_000)
        assertThat(score).isEqualTo(100f)
    }

    @Test
    fun `score is reduced by hard braking event`() {
        val events = listOf(
            DrivingEvent(tripId = 1, type = EventType.HARD_BRAKING, timestampMs = 1000, severity = 0f),
        )
        val score = calculator.calculate(events, tripDurationMs = 10_000)
        assertThat(score).isLessThan(100f)
    }

    @Test
    fun `score is reduced by harsh acceleration event`() {
        val events = listOf(
            DrivingEvent(tripId = 1, type = EventType.HARSH_ACCELERATION, timestampMs = 1000, severity = 0.5f),
        )
        val score = calculator.calculate(events, tripDurationMs = 10_000)
        assertThat(score).isLessThan(100f)
    }

    @Test
    fun `score is reduced by speeding event`() {
        val events = listOf(
            DrivingEvent(tripId = 1, type = EventType.SPEEDING, timestampMs = 1000, severity = 0.5f),
        )
        val score = calculator.calculate(events, tripDurationMs = 10_000)
        assertThat(score).isLessThan(100f)
    }

    @Test
    fun `phone distraction applies highest penalty`() {
        val distractionEvents = listOf(
            DrivingEvent(tripId = 1, type = EventType.PHONE_DISTRACTION, timestampMs = 1000, severity = 0.5f),
        )
        val brakingEvents = listOf(
            DrivingEvent(tripId = 1, type = EventType.HARD_BRAKING, timestampMs = 1000, severity = 0.5f),
        )
        val distractionScore = calculator.calculate(distractionEvents, tripDurationMs = 10_000)
        val brakingScore = calculator.calculate(brakingEvents, tripDurationMs = 10_000)
        assertThat(distractionScore).isLessThan(brakingScore)
    }

    @Test
    fun `night driving multiplier further reduces score`() {
        val events = listOf(
            DrivingEvent(tripId = 1, type = EventType.SPEEDING, timestampMs = 1000, severity = 0.3f),
        )
        val dayScore = calculator.calculate(events, tripDurationMs = 10_000, isNight = false)
        val nightScore = calculator.calculate(events, tripDurationMs = 10_000, isNight = true)
        assertThat(nightScore).isLessThan(dayScore)
    }

    @Test
    fun `long trip multiplier further reduces score`() {
        val events = listOf(
            DrivingEvent(tripId = 1, type = EventType.SPEEDING, timestampMs = 1000, severity = 0.3f),
        )
        val shortScore = calculator.calculate(events, tripDurationMs = 10_000)
        val longScore = calculator.calculate(events, tripDurationMs = 2 * 60 * 60_000L) // 2 hours
        assertThat(longScore).isLessThan(shortScore)
    }

    @Test
    fun `score never goes below 0`() {
        val manyEvents = List(50) {
            DrivingEvent(tripId = 1, type = EventType.HARD_BRAKING, timestampMs = it.toLong(), severity = 1f)
        }
        val score = calculator.calculate(manyEvents, tripDurationMs = 10_000, isNight = true)
        assertThat(score).isAtLeast(0f)
    }

    @Test
    fun `severity scales the penalty`() {
        val lowSeverity = listOf(
            DrivingEvent(tripId = 1, type = EventType.HARD_BRAKING, timestampMs = 1000, severity = 0f),
        )
        val highSeverity = listOf(
            DrivingEvent(tripId = 1, type = EventType.HARD_BRAKING, timestampMs = 1000, severity = 1f),
        )
        val lowScore = calculator.calculate(lowSeverity, tripDurationMs = 10_000)
        val highScore = calculator.calculate(highSeverity, tripDurationMs = 10_000)
        assertThat(highScore).isLessThan(lowScore)
    }

    @Test
    fun `irrelevant event types do not affect safety score`() {
        val events = listOf(
            DrivingEvent(tripId = 1, type = EventType.IDLING, timestampMs = 1000, severity = 1f),
            DrivingEvent(tripId = 1, type = EventType.STOP_START, timestampMs = 2000, severity = 1f),
        )
        val score = calculator.calculate(events, tripDurationMs = 10_000)
        assertThat(score).isEqualTo(100f)
    }
}
