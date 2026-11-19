package com.driveperf.domain.score

import com.driveperf.domain.route.RoutePreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScoreReaderTest {
    @Test
    fun scoreBucketsFollowTheAnalyticsBoundaries() {
        val buckets = listOf(0, 49, 50, 59, 60, 69, 70, 79, 80, 89, 90, 100).map(::scoreBucket)
        assertEquals(
            listOf("lt_50", "lt_50", "50_59", "50_59", "60_69", "60_69", "70_79", "70_79", "80_89", "80_89", "90_100", "90_100"),
            buckets,
        )
    }

    @Test
    fun theEmptyLinesHaveNoTripsAndNoCars() {
        assertTrue(ScoreLines.EMPTY.lines.isEmpty())
        assertEquals(0, ScoreLines.EMPTY.carCount)
    }

    @Test
    fun linesAndDetailsKeepWhatTheyAreGiven() {
        val route = RouteName("r1", null, RoutePreset.COMMUTE)
        val line = ScoreLine("t1", 85, 2, null, newRecord = true, route = route, car = "GTI")
        val detail = TripScoreDetail("t1", null, emptyList(), emptyMap(), route, null, "GTI", listOf(80, 85))
        assertEquals(route, line.route)
        assertEquals(listOf(80, 85), detail.runs)
        assertEquals(ScoreLines(mapOf("t1" to line), 2).lines["t1"], line)
    }
}
