package com.driveperf.scoring

import com.driveperf.domain.model.DrivingEvent
import com.driveperf.domain.model.EventType
import com.driveperf.domain.scoring.SmoothnessScoreCalculator
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class SmoothnessScoreCalculatorTest {

    private lateinit var calculator: SmoothnessScoreCalculator

    @Before
    fun setUp() {
        calculator = SmoothnessScoreCalculator()
    }

    @Test
    fun `perfect trip returns 100`() {
        val score = calculator.calculate(emptyList())
        assertThat(score).isEqualTo(100f)
    }

    @Test
    fun `cornering event reduces score`() {
        val events = listOf(
            DrivingEvent(tripId = 1, type = EventType.AGGRESSIVE_CORNERING, timestampMs = 1000, severity = 0.5f),
        )
        val score = calculator.calculate(events)
        assertThat(score).isLessThan(100f)
    }

    @Test
    fun `stop-start events reduce score`() {
        val events = listOf(
            DrivingEvent(tripId = 1, type = EventType.STOP_START, timestampMs = 1000, severity = 0.5f),
        )
        val score = calculator.calculate(events)
        assertThat(score).isLessThan(100f)
    }

    @Test
    fun `score never goes below 0`() {
        val manyEvents = List(100) {
            DrivingEvent(tripId = 1, type = EventType.HARD_BRAKING, timestampMs = it.toLong(), severity = 1f)
        }
        val score = calculator.calculate(manyEvents)
        assertThat(score).isAtLeast(0f)
    }

    @Test
    fun `events not relevant to smoothness have no effect`() {
        val events = listOf(
            DrivingEvent(tripId = 1, type = EventType.SPEEDING, timestampMs = 1000, severity = 1f),
            DrivingEvent(tripId = 1, type = EventType.PHONE_DISTRACTION, timestampMs = 2000, severity = 1f),
            DrivingEvent(tripId = 1, type = EventType.NIGHT_DRIVING, timestampMs = 3000, severity = 1f),
        )
        val score = calculator.calculate(events)
        assertThat(score).isEqualTo(100f)
    }
}
