package com.driveperf.feature.stats

import com.driveperf.domain.model.TripSession
import com.driveperf.domain.model.TripState
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import org.junit.Test

class StatsModelsTest {

    private val zoneId = ZoneId.of("UTC")
    private val now = Instant.parse("2026-07-12T10:00:00Z")

    @Test
    fun buildStatsSnapshot_calculatesWeightedAverageSpeedFromTotals() {
        val trips = listOf(
            completedTrip(
                id = 1L,
                startMs = Instant.parse("2026-07-10T10:00:00Z").toEpochMilli(),
                endMs = Instant.parse("2026-07-10T11:00:00Z").toEpochMilli(),
                distanceMeters = 60_000.0,
            ),
            completedTrip(
                id = 2L,
                startMs = Instant.parse("2026-07-11T09:00:00Z").toEpochMilli(),
                endMs = Instant.parse("2026-07-11T11:00:00Z").toEpochMilli(),
                distanceMeters = 40_000.0,
            ),
        )

        val snapshot = buildStatsSnapshot(trips = trips, now = now, zoneId = zoneId)

        assertThat(snapshot.summary.totalDistanceMeters).isEqualTo(100_000.0)
        assertThat(snapshot.summary.totalDurationMs).isEqualTo(10_800_000L)
        assertThat(snapshot.summary.averageSpeedMetersPerHour).isWithin(0.01).of(33_333.33)
    }

    @Test
    fun buildStatsSnapshot_excludesInvalidTripsAndUsesDeterministicTiesForRecords() {
        val sharedStart = Instant.parse("2026-07-08T08:00:00Z").toEpochMilli()
        val trips = listOf(
            completedTrip(id = 2L, startMs = sharedStart, endMs = sharedStart + 3_600_000L, distanceMeters = 80_000.0),
            completedTrip(id = 1L, startMs = sharedStart, endMs = sharedStart + 3_600_000L, distanceMeters = 80_000.0),
            completedTrip(id = 3L, startMs = sharedStart + 120_000L, endMs = sharedStart + 120_000L, distanceMeters = 100_000.0),
        )

        val snapshot = buildStatsSnapshot(trips = trips, now = now, zoneId = zoneId)

        assertThat(snapshot.summary.totalTrips).isEqualTo(2)
        assertThat(snapshot.longestTripRecord?.tripId).isEqualTo(1L)
        assertThat(snapshot.fastestTripRecord?.tripId).isEqualTo(1L)
        assertThat(snapshot.hasPartialData).isTrue()
    }

    @Test
    fun buildStatsSnapshot_allInvalidTripsReportsNoTripsState() {
        val start = Instant.parse("2026-07-08T08:00:00Z").toEpochMilli()
        val trips = listOf(
            completedTrip(id = 1L, startMs = start, endMs = start, distanceMeters = 1_000.0),
            completedTrip(id = 2L, startMs = start + 60_000L, endMs = start + 60_000L, distanceMeters = 2_000.0),
        )

        val snapshot = buildStatsSnapshot(trips = trips, now = now, zoneId = zoneId)

        assertThat(snapshot.hasAnyTrips).isFalse()
        assertThat(snapshot.summary.totalTrips).isEqualTo(0)
        assertThat(snapshot.hasPartialData).isTrue()
    }

    @Test
    fun buildStatsSnapshot_buildsSevenChartBarsIncludingZeroDistanceDaysEndingToday() {
        val trips = listOf(
            completedTrip(
                id = 1L,
                startMs = Instant.parse("2026-07-12T01:00:00Z").toEpochMilli(),
                endMs = Instant.parse("2026-07-12T02:00:00Z").toEpochMilli(),
                distanceMeters = 10_000.0,
            ),
        )

        val snapshot = buildStatsSnapshot(trips = trips, now = now, zoneId = zoneId)

        assertThat(snapshot.chartBars).hasSize(7)
        assertThat(snapshot.chartBars.last().isToday).isTrue()
        assertThat(snapshot.chartBars.last().distanceMeters).isEqualTo(10_000.0)
        assertThat(snapshot.chartBars.first().distanceMeters).isEqualTo(0.0)
    }

    private fun completedTrip(
        id: Long,
        startMs: Long,
        endMs: Long,
        distanceMeters: Double,
    ) = TripSession(
        id = id,
        startTimeMs = startMs,
        endTimeMs = endMs,
        distanceMeters = distanceMeters,
        state = TripState.COMPLETED,
    )
}
