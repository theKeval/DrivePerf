package com.driveperf.pipeline.fixtures

import com.driveperf.domain.capture.CaptureHeader
import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.capture.WakeKind
import com.driveperf.domain.repository.ScoredTrip
import com.driveperf.domain.route.RouteState
import com.driveperf.domain.score.ScoreBasis
import com.driveperf.pipeline.route.FakeRouteHistory
import com.driveperf.testing.SyntheticCapture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The same city loop driven repeatedly: route candidate, promotion, route baseline and records (§5.7, §7). */
class RepeatDriveTest {
    private fun run(n: Int): CapturedTrip {
        val s = DriveFixtures.city.streams
        val shift = n * DAY_MS
        return CapturedTrip(
            CaptureHeader(
                "run-$n", SyntheticCapture.START_MONO_NS, SyntheticCapture.WALL0 + shift, -300, "Etc/GMT+5",
                WakeKind.MANUAL, null, 50, null,
            ),
            s.fixes.map { it.copy(wallMs = it.wallMs + shift) }, s.motion, s.activity, emptyList(), emptyList(),
        )
    }

    @Test
    fun repeatedRunsBuildARouteWithABaselineAndARecord() = runTest {
        val routes = FakeRouteHistory()
        val history = mutableListOf<ScoredTrip>()
        val results = (0 until 5).map { n ->
            val result = assertNotNull(PipelineHarness(history.toList(), routes).run(run(n)).trip)
            history += ScoredTrip(result.trip, result.score, result.routeMatch?.routeId)
            routes.previews[result.trip.id] = result.preview
            result.newRoute?.let { r -> routes.routes.removeAll { it.id == r.id }; routes.routes += r }
            for (m in result.extraMatches) {
                val i = history.indexOfFirst { it.trip.id == m.tripId }
                if (i >= 0) history[i] = history[i].copy(routeId = m.routeId)
            }
            result.routeMatch?.let { m ->
                val earlier = history.filter { it.routeId == m.routeId && it.trip.id != result.trip.id }
                routes.runs[m.routeId] = earlier + ScoredTrip(result.trip, result.score, m.routeId)
            }
            result.personalBest?.let { routes.bests[it.routeId] = it }
            result
        }
        assertNull(results[0].routeMatch, "a single trip is not a route")
        val second = results[1]
        assertEquals(RouteState.CANDIDATE, second.newRoute?.state)
        assertEquals("run-0", second.newRoute?.referenceTripId)
        assertEquals("run-0", second.extraMatches.single().tripId)
        assertEquals(RouteState.ROUTE, results[2].newRoute?.state, "the third run promotes the candidate")
        val routeId = second.newRoute!!.id
        assertTrue(results.drop(1).all { it.routeMatch?.routeId == routeId })
        assertEquals(ScoreBasis.ROUTE, results[4].score?.basis, "with 3 prior runs the route is the basis")
        assertEquals(results[3].score?.smoothness, results[4].score?.baseline)
        assertEquals(0, results[4].score?.delta)
        assertTrue(results.drop(1).none { it.score?.recordState?.isNew == true }, "identical runs never beat the first record")
        assertEquals("run-0", routes.bests[routeId]?.tripId)
    }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
