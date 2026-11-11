package com.driveperf.data.local

import com.driveperf.domain.capture.FinalisedTrip
import com.driveperf.domain.route.MatchSource
import com.driveperf.domain.route.PersonalBest
import com.driveperf.domain.route.RecordEntry
import com.driveperf.domain.route.Route
import com.driveperf.domain.route.RouteMatch
import com.driveperf.domain.route.RouteState
import com.driveperf.domain.score.Score
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.testing.InMemoryTripFileStore
import com.driveperf.testing.TestClock
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** pipeline-and-scoring.md §7.4–7.5: a corrected kind removes the route match, and a record it held passes on. */
class CorrectedRouteRecordsTest {
    private val db = openDatabase(JvmDatabaseDriverFactory().create())
    private val io = EmptyCoroutineContext
    private val clock = TestClock()
    private val trips = LocalTripRepository(db, InMemoryTripFileStore(), io)
    private val routes = LocalRouteRepository(db, io)
    private val corrections = LocalTripCorrections(db, trips, io, clock)
    private val route = Route(
        "r1", null, null, RouteState.ROUTE, null, null, 2.0, -27.6, 2.1, -27.5, 200.0, "a", 5_000.0,
        createdAt = 500L, updatedAt = 500L,
    )

    private fun trip(id: String, startedAt: Long) = Trip(
        id = id, status = TripStatus.FINALISED, kind = TripKind.DRIVER, source = TripSource.AUTO, startedAt = startedAt,
        startTzOffsetMin = 0, endedAt = startedAt + 600_000, durationS = 600.0, distanceM = 5_000.0,
        createdAt = startedAt, updatedAt = startedAt,
    )

    private fun score(id: String, smoothness: Int) =
        Score(id, smoothness, null, null, null, null, null, null, emptyList(), null, null, null, null, "smooth-1.0.0", 0L)

    /** Runs of r1 as (id, smoothness, eligible), started in order; [best] is the stored record. */
    private suspend fun runs(vararg runs: Triple<String, Int, Boolean>, best: PersonalBest?) {
        val store = LocalTripStore(db, "device-1", io)
        runs.forEachIndexed { i, (id, smoothness, eligible) ->
            store.saveFinalised(
                FinalisedTrip(
                    trip(id, 1_000L * (i + 1)), score = score(id, smoothness),
                    routeMatch = RouteMatch(id, "r1", 0.9, MatchSource.AUTO, eligible, null),
                    newRoute = route.takeIf { i == 0 }, personalBest = best.takeIf { i == runs.lastIndex },
                ),
            )
        }
    }

    @Test
    fun aRecordHeldByACorrectedTripPassesToTheBestRemainingEligibleRun() = runTest {
        val history = listOf(RecordEntry("a", 70, 1_000L), RecordEntry("bus", 95, 2_000L))
        runs(
            Triple("a", 70, true), Triple("bus", 95, true), Triple("b", 88, true), Triple("c", 90, false),
            Triple("d", 88, true), best = PersonalBest("r1", "bus", 95, 2_000L, history),
        )

        corrections.correct("bus", TripCorrection.TRANSIT)

        assertNull(routes.matchFor("bus"), "a corrected kind removes the match")
        val expected = PersonalBest("r1", "b", 88, 3_000L, listOf(RecordEntry("a", 70, 1_000L), RecordEntry("b", 88, 3_000L)))
        assertEquals(expected, routes.personalBest("r1"), "c is ineligible; b ties d and is earlier")
    }

    @Test
    fun aRecordNotHeldByTheCorrectedTripStays() = runTest {
        val best = PersonalBest("r1", "a", 90, 1_000L, listOf(RecordEntry("a", 90, 1_000L)))
        runs(Triple("a", 90, true), Triple("p", 80, true), best = best)

        corrections.correct("p", TripCorrection.PASSENGER)

        assertNull(routes.matchFor("p"))
        assertEquals(best, routes.personalBest("r1"))
    }

    @Test
    fun aRouteLeftWithNoEligibleRunHasNoRecord() = runTest {
        runs(Triple("walk", 80, true), Triple("x", 85, false), best = PersonalBest("r1", "walk", 80, 1_000L, emptyList()))

        corrections.correct("walk", TripCorrection.WALK)

        assertNull(routes.personalBest("r1"))
        assertEquals(false, routes.matchFor("x")?.eligibleForRecord)
    }

    @Test
    fun correctingBackToADriveLeavesTheRouteAlone() = runTest {
        val best = PersonalBest("r1", "a", 90, 1_000L, listOf(RecordEntry("a", 90, 1_000L)))
        runs(Triple("a", 90, true), best = best)

        corrections.correct("a", TripCorrection.DRIVER)

        assertEquals("r1", routes.matchFor("a")?.routeId)
        assertEquals(best, routes.personalBest("r1"))
    }
}
