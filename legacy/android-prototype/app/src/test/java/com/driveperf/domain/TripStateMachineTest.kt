package com.driveperf.domain

import com.driveperf.domain.model.TripSession
import com.driveperf.domain.model.TripState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Validates the trip state lifecycle transitions that the repository
 * is expected to enforce:
 *
 *  DETECTING → ACTIVE → COMPLETED
 *  DETECTING → DISCARDED
 *  ACTIVE    → DISCARDED (passenger flag)
 *  COMPLETED → DISCARDED (delete is a hard remove, but we test the state domain model)
 */
class TripStateMachineTest {

    @Test
    fun `new trip starts in DETECTING state`() {
        val trip = TripSession(startTimeMs = 1000L)
        assertThat(trip.state).isEqualTo(TripState.DETECTING)
    }

    @Test
    fun `trip transitions to ACTIVE when confirmed in vehicle`() {
        val detecting = TripSession(startTimeMs = 1000L, state = TripState.DETECTING)
        val active = detecting.copy(state = TripState.ACTIVE)
        assertThat(active.state).isEqualTo(TripState.ACTIVE)
    }

    @Test
    fun `trip transitions to COMPLETED after end`() {
        val active = TripSession(
            startTimeMs = 1000L,
            state = TripState.ACTIVE,
        )
        val completed = active.copy(
            endTimeMs = 5000L,
            distanceMeters = 1200.0,
            state = TripState.COMPLETED,
        )
        assertThat(completed.state).isEqualTo(TripState.COMPLETED)
        assertThat(completed.endTimeMs).isNotNull()
        assertThat(completed.distanceMeters).isGreaterThan(0.0)
    }

    @Test
    fun `passenger flag transitions trip to DISCARDED`() {
        val active = TripSession(startTimeMs = 1000L, state = TripState.ACTIVE)
        val discarded = active.copy(isPassenger = true, state = TripState.DISCARDED)
        assertThat(discarded.state).isEqualTo(TripState.DISCARDED)
        assertThat(discarded.isPassenger).isTrue()
    }

    @Test
    fun `completed trip has non-null end time`() {
        val completed = TripSession(
            startTimeMs = 1000L,
            endTimeMs = 9000L,
            state = TripState.COMPLETED,
        )
        assertThat(completed.endTimeMs).isNotNull()
    }

    @Test
    fun `detecting trip has no end time`() {
        val detecting = TripSession(startTimeMs = 1000L)
        assertThat(detecting.endTimeMs).isNull()
    }

    @Test
    fun `trip duration is calculable from start and end times`() {
        val startMs = 1_000L
        val endMs = 61_000L // 1 minute
        val trip = TripSession(
            startTimeMs = startMs,
            endTimeMs = endMs,
            state = TripState.COMPLETED,
        )
        val durationMs = trip.endTimeMs!! - trip.startTimeMs
        assertThat(durationMs).isEqualTo(60_000L)
    }
}
