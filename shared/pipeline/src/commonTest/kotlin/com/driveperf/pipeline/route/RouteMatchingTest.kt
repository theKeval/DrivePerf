package com.driveperf.pipeline.route

import com.driveperf.domain.repository.ScoredTrip
import com.driveperf.domain.route.IneligibleReason
import com.driveperf.domain.route.PersonalBest
import com.driveperf.domain.route.RecordEntry
import com.driveperf.domain.route.Route
import com.driveperf.domain.route.RouteState
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.TripKind
import com.driveperf.pipeline.route.RouteTestData.lPath
import com.driveperf.pipeline.route.RouteTestData.score
import com.driveperf.pipeline.route.RouteTestData.trip
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteMatchingTest {
    private val matcher = RouteMatcher()
    private val reference = Side.ofTrip(lPath())!!

    private fun route(id: String = "r1", createdAt: Long = 1L, state: RouteState = RouteState.ROUTE, ref: String = "ref") = Route(
        id, null, null, state, null, null, reference.startLat, reference.startLng, reference.endLat, reference.endLng,
        200.0, ref, 3000.0, createdAt = createdAt, updatedAt = createdAt,
    )

    @Test
    fun g20StartLatencyMatchesReverseAndAParallelRoadDoNot() {
        val late = Side.ofTrip(lPath(skipFirstM = 300.0))!!
        assertTrue(matcher.endpointsMatch(late, reference), "the first-kilometre allowance absorbs start latency")
        val f = assertNotNull(matcher.frechet(late, reference))
        assertTrue(matcher.similarity(f) > 0.0)

        val reverse = Side.ofTrip(lPath(reverse = true))!!
        assertFalse(matcher.endpointsMatch(reverse, reference), "A→B never matches B→A")

        val parallel = Side.ofTrip(lPath(offsetEast = 300.0))!!
        assertNull(matcher.frechet(parallel, reference), "a parallel road 300 m away")
    }

    @Test
    fun aMuchShorterPathFailsTheLengthRatio() {
        val short = Side.ofTrip(lPath().take(150))!!
        assertNull(matcher.frechet(short, reference))
        assertEquals(1.0, matcher.similarity(0.0))
        assertEquals(0.0, matcher.similarity(400.0))
    }

    @Test
    fun geometryBasics() {
        val a = listOf(XY(0.0, 0.0), XY(0.0, 100.0))
        assertEquals(100.0, RouteGeometry.length(a), 1e-9)
        assertEquals(5, RouteGeometry.resample(a, 25.0).size)
        assertEquals(10.0, RouteGeometry.frechet(a, listOf(XY(10.0, 0.0), XY(10.0, 100.0))), 1e-9)
        assertEquals(Double.POSITIVE_INFINITY, RouteGeometry.frechet(emptyList(), a))
        assertEquals(1, RouteGeometry.resample(listOf(XY(0.0, 0.0)), 10.0).size)
        assertTrue(RouteGeometry.firstMetres(emptyList(), 10.0).isEmpty())
    }

    @Test
    fun aTripMatchesTheClosestRouteAndPromotesACandidate() = runTest {
        val history = FakeRouteHistory(
            routes = mutableListOf(
                route("r2", createdAt = 5L, state = RouteState.CANDIDATE),
                route("r1", createdAt = 1L, state = RouteState.CANDIDATE),
            ),
            previews = mutableMapOf("ref" to lPath()),
            runs = mutableMapOf(
                "r1" to listOf(ScoredTrip(trip("p1", 1L), score("p1", 80), "r1"), ScoredTrip(trip("p2", 2L), score("p2", 81), "r1")),
            ),
        )
        val engine = RouteEngine(history)
        val outcome = assertNotNull(engine.match(trip("t", 100L), lPath(), emptyList(), 100L))
        assertEquals("r1", outcome.route.id, "tie on Fréchet goes to the oldest route")
        assertEquals(RouteState.ROUTE, outcome.route.state, "three runs promote a candidate")
        assertFalse(outcome.created)
    }

    @Test
    fun twoUnmatchedTripsOnTheSamePathCreateACandidate() = runTest {
        val older = ScoredTrip(trip("old", 1L), score("old", 88), null)
        val history = FakeRouteHistory(previews = mutableMapOf("old" to lPath()))
        val engine = RouteEngine(history)
        val outcome = assertNotNull(engine.match(trip("new", 100L), lPath(skipFirstM = 100.0), listOf(older), 100L))
        assertTrue(outcome.created)
        assertEquals(RouteState.CANDIDATE, outcome.route.state)
        assertEquals("old", outcome.route.referenceTripId)
        val record = engine.record(trip("new", 100L), 85, 0, outcome)
        assertFalse(record.recordState.isNew, "85 does not beat the older run's 88")
        assertEquals("old", record.personalBest?.tripId)
        val paired = assertNotNull(engine.pairedMatch(outcome))
        assertEquals("old", paired.tripId)
        assertTrue(paired.eligibleForRecord)
    }

    @Test
    fun thePairedRunsEligibilityDoesNotDependOnWhoHoldsTheRecord() = runTest {
        val older = ScoredTrip(trip("old", 1L), score("old", 70), null)
        val engine = RouteEngine(FakeRouteHistory(previews = mutableMapOf("old" to lPath())))
        val outcome = assertNotNull(engine.match(trip("new", 100L), lPath(), listOf(older), 100L))
        val record = engine.record(trip("new", 100L), 85, 0, outcome)
        assertEquals("new", record.personalBest?.tripId, "85 beats the older run's 70")
        assertEquals(listOf("old", "new"), record.personalBest?.history?.map { it.tripId })
        val paired = assertNotNull(engine.pairedMatch(outcome))
        assertTrue(paired.eligibleForRecord)
        assertNull(paired.ineligibleReason)

        val suspect = older.copy(trip = older.trip.copy(suspect = Suspect.TRANSIT))
        val unanswered = RouteOutcome(outcome.route, outcome.similarity, created = true, paired = suspect)
        val match = assertNotNull(engine.pairedMatch(unanswered))
        assertFalse(match.eligibleForRecord)
        assertEquals(IneligibleReason.QUALITY, match.ineligibleReason)
        assertNull(engine.record(trip("new", 100L), 60, 0, unanswered).personalBest?.history?.firstOrNull { it.tripId == "old" })
    }

    @Test
    fun theEndpointIsTheLastGatedFixEvenInsideAnExclusion() {
        val rows = lPath()
        val tail = rows.size - 20
        val excludedEnd = rows.mapIndexed { i, r -> if (i >= tail) r.copy(quality = 3) else r }
        assertEquals(rows.last().lat, Side.ofTrip(excludedEnd)!!.endLat)
        val allExcluded = Side.ofTrip(rows.map { it.copy(quality = 3) })
        assertEquals(rows.last().lng, assertNotNull(allExcluded).endLng)
        assertEquals(rows[rows.size - 2].lat, Side.ofTrip(rows.dropLast(1) + rows.last().copy(quality = 1))!!.endLat)
    }

    @Test
    fun ineligibleTripsNeverJoinARoute() = runTest {
        val history = FakeRouteHistory(routes = mutableListOf(route()), previews = mutableMapOf("ref" to lPath()))
        val engine = RouteEngine(history)
        assertNull(engine.match(trip("t", 1L, kind = TripKind.PASSENGER), lPath(), emptyList(), 1L))
        assertNull(engine.match(trip("t", 1L).copy(suspect = Suspect.TRANSIT), lPath(), emptyList(), 1L))
        assertNull(engine.match(trip("t", 1L), emptyList(), emptyList(), 1L))
        assertNull(engine.match(trip("t", 1L), lPath(offsetEast = 3000.0), emptyList(), 1L))
    }

    @Test
    fun g11UnusualPaceSetsNoRecord() = runTest {
        val runs = (1..4).map { ScoredTrip(trip("p$it", it.toLong(), movingS = 300.0), score("p$it", 80), "r1") }
        val history = FakeRouteHistory(
            routes = mutableListOf(route()),
            previews = mutableMapOf("ref" to lPath()),
            runs = mutableMapOf("r1" to runs),
            bests = mutableMapOf("r1" to PersonalBest("r1", "p1", 80, 1L, listOf(RecordEntry("p1", 80, 1L)))),
        )
        val engine = RouteEngine(history)
        for (moving in listOf(0.6 * 300, 1.6 * 300)) {
            val t = trip("fast-or-slow", 100L, movingS = moving)
            val outcome = assertNotNull(engine.match(t, lPath(), emptyList(), 100L))
            val record = engine.record(t, 99, 0, outcome)
            assertEquals(IneligibleReason.PACE_BAND, record.match.ineligibleReason)
            assertFalse(record.recordState.isNew)
            assertEquals("p1", record.personalBest?.tripId)
        }
        val usual = trip("usual", 100L, movingS = 310.0)
        val record = engine.record(usual, 99, 0, assertNotNull(engine.match(usual, lPath(), emptyList(), 100L)))
        assertTrue(record.recordState.isNew)
        assertEquals(80, record.recordState.previousScore)
        assertEquals(2, record.personalBest?.history?.size)
        val tie = engine.record(usual, 80, 0, assertNotNull(engine.match(usual, lPath(), emptyList(), 100L)))
        assertFalse(tie.recordState.isNew, "ties stay with the earlier trip")
    }

    @Test
    fun recordEligibilityIsCheckedInOrder() = runTest {
        val history = FakeRouteHistory(routes = mutableListOf(route()), previews = mutableMapOf("ref" to lPath()))
        val engine = RouteEngine(history)
        val outcome = assertNotNull(engine.match(trip("t", 1L), lPath(), emptyList(), 1L))
        val passenger = engine.record(trip("t", 1L, kind = TripKind.PASSENGER), 90, 0, outcome)
        assertEquals(IneligibleReason.PASSENGER, passenger.match.ineligibleReason)
        val limited = engine.record(trip("t", 1L, badge = QualityBadge.LIMITED), 90, 0, outcome)
        assertEquals(IneligibleReason.QUALITY, limited.match.ineligibleReason)
        assertEquals(IneligibleReason.QUALITY, engine.record(trip("t", 1L), null, 0, outcome).match.ineligibleReason)
        assertEquals(IneligibleReason.EXCLUSIONS, engine.record(trip("t", 1L), 90, 3, outcome).match.ineligibleReason)
        val first = engine.record(trip("t", 1L), 90, 2, outcome)
        assertNull(first.match.ineligibleReason, "two needed marks are allowed (D4)")
        assertTrue(first.recordState.isNew, "the first eligible run sets the record")
    }
}
