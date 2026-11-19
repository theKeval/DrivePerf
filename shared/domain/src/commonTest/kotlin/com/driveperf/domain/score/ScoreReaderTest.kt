package com.driveperf.domain.score

import com.driveperf.domain.route.RoutePreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScoreReaderTest {
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
