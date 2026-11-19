package com.driveperf.data.local

import com.driveperf.domain.capture.FinalisedTrip
import com.driveperf.domain.route.MatchSource
import com.driveperf.domain.route.PersonalBest
import com.driveperf.domain.route.RecordEntry
import com.driveperf.domain.route.Route
import com.driveperf.domain.route.RouteMatch
import com.driveperf.domain.route.RouteState
import com.driveperf.domain.score.Score
import com.driveperf.domain.score.ScoreChange
import com.driveperf.domain.score.TripRecompute
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.Placement
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.SuspectAnswer
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripQuality
import com.driveperf.domain.trip.TripSegment
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.tripfile.FileResult
import com.driveperf.domain.tripfile.TripFileStore
import com.driveperf.testing.InMemoryTripFileStore
import com.driveperf.testing.TestClock
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** DRIVE-131: what the §5.7 recompute reads and writes, and the three changes that start it. */
class RecomputeStoreTest {
    private val db = openDatabase(JvmDatabaseDriverFactory().create())
    private val io = EmptyCoroutineContext
    private val clock = TestClock()
    private val files = InMemoryTripFileStore()
    private val inner = LocalTripRepository(db, files, io)
    private val routes = LocalRouteRepository(db, io)
    private val store = LocalScoreHistoryStore(db, io)

    /** Every trip a recompute was started from, in order. */
    private val recomputed = mutableListOf<String>()
    private val recompute = TripRecompute { recomputed += it }

    private val route = Route(
        "r1", null, null, RouteState.ROUTE, null, null, 2.0, -27.6, 2.1, -27.5, 200.0, "a", 5_000.0,
        createdAt = 500L, updatedAt = 500L,
    )
    private val span = TripSegment(SegmentKind.GAP, 10.0, 20.0, null)

    private fun trip(id: String, startedAt: Long, status: TripStatus = TripStatus.FINALISED, suspect: Suspect = Suspect.NONE) = Trip(
        id = id, status = status, kind = TripKind.DRIVER, source = TripSource.AUTO, startedAt = startedAt,
        startTzOffsetMin = 0, endedAt = startedAt + 600_000, durationS = 600.0, distanceM = 5_000.0, suspect = suspect,
        quality = TripQuality(QualityBadge.GOOD, null, Placement.MOUNTED, 50.0, 95.0, null, null, 600.0, 0.0, 600.0, listOf(span)),
        createdAt = startedAt, updatedAt = startedAt,
    )

    private fun score(id: String, smoothness: Int?) =
        Score(id, smoothness, null, null, null, null, null, null, emptyList(), null, null, null, null, "smooth-1.0.0", 0L)

    private suspend fun save(t: Trip, smoothness: Int? = 80, onRoute: Boolean = false, best: PersonalBest? = null) {
        LocalTripStore(db, "device-1", io).saveFinalised(
            FinalisedTrip(
                t, score = smoothness?.let { score(t.id, it) },
                routeMatch = if (onRoute) RouteMatch(t.id, "r1", 0.9, MatchSource.AUTO, true, null) else null,
                newRoute = route.takeIf { onRoute && routes.route("r1") == null },
                personalBest = best,
            ),
        )
    }

    @Test
    fun theWalkReadsFinishedLiveTripsFromAStartOldestFirstWithScoresRoutesAndSpans() = runTest {
        save(trip("before", 500L))
        save(trip("b", 2_000L), onRoute = true)
        save(trip("a", 2_000L), smoothness = null)
        save(trip("c", 3_000L))

        val walk = store.finishedFrom(1_000L)

        assertEquals(listOf("a", "b", "c"), walk.map { it.trip.id }, "by (startedAt, id)")
        assertNull(walk[0].score)
        assertEquals(80, walk[1].score?.smoothness)
        assertEquals("r1", walk[1].routeId)
        assertNull(walk[2].routeId)
        assertEquals(listOf(span), walk[2].trip.quality?.spans)
    }

    @Test
    fun changesAreWrittenOnlyWhileTheStoredScoreIsWhatTheWalkRead() = runTest {
        save(trip("a", 1_000L))
        save(trip("b", 2_000L))
        save(trip("c", 3_000L))
        val read = store.finishedFrom(0L).associate { it.trip.id to assertNotNull(it.score) }
        // Feedback re-scores b after the walk read it.
        LocalEventFeedbackStore(db, io).save(EventFeedback("e1", "b", needed = true, inaccurate = null, at = 2L), score("b", 95))

        val written = store.saveScores(
            listOf(
                ScoreChange(read.getValue("a"), score("a", 90)),
                ScoreChange(read.getValue("b"), score("b", 91)),
                ScoreChange(score("gone", 80), score("gone", 92)),
            ),
        )

        assertEquals(listOf("a"), written.map { it.tripId })
        assertEquals(90, inner.score("a")?.smoothness)
        assertEquals(95, inner.score("b")?.smoothness, "the newer feedback score is kept")
        assertEquals(80, inner.score("c")?.smoothness, "untouched")
        assertNull(inner.score("gone"))
    }

    @Test
    fun deletingATripPassesItsRecordOnAndRecomputesFromTheNextTrip() = runTest {
        val best = PersonalBest("r1", "a", 95, 1_000L, listOf(RecordEntry("a", 95, 1_000L)))
        save(trip("a", 1_000L), smoothness = 95, onRoute = true)
        save(trip("b", 2_000L), smoothness = 88, onRoute = true, best = best)
        save(trip("c", 3_000L))
        val trips = RecomputingTripRepository(inner, db, files, io, clock, recompute)

        assertEquals(FileResult.OK, trips.delete("a"))

        assertNull(trips.trip("a"))
        assertNull(trips.score("a"), "the score goes with the trip")
        assertEquals("b", routes.personalBest("r1")?.tripId, "the record passes to the best remaining run")
        assertEquals(listOf("b"), recomputed, "every later trip is recomputed")
    }

    @Test
    fun deletingTheLastTripRecomputesNothing() = runTest {
        save(trip("a", 1_000L))
        val trips = RecomputingTripRepository(inner, db, files, io, clock, recompute)
        assertEquals(FileResult.OK, trips.delete("a"))
        assertEquals(FileResult.OK, trips.delete("unknown"))
        assertEquals(emptyList(), recomputed)
    }

    @Test
    fun aTripWhoseFilesCannotBeDeletedIsKeptWhole() = runTest {
        val best = PersonalBest("r1", "a", 95, 1_000L, listOf(RecordEntry("a", 95, 1_000L)))
        save(trip("a", 1_000L), smoothness = 95, onRoute = true, best = best)
        save(trip("b", 2_000L))
        val failing = object : TripFileStore by files {
            override fun deleteTrip(tripId: String): FileResult = FileResult.IO_ERROR
        }
        val trips = RecomputingTripRepository(inner, db, failing, io, clock, recompute)

        assertEquals(FileResult.IO_ERROR, trips.delete("a"))

        assertEquals(95, trips.score("a")?.smoothness)
        assertEquals("r1", routes.matchFor("a")?.routeId)
        assertEquals(best, routes.personalBest("r1"))
        assertEquals(emptyList(), recomputed)
    }

    @Test
    fun correctionsRecomputeFromTheCorrectedTripOnceCommitted() = runTest {
        save(trip("a", 1_000L))
        save(trip("bus", 2_000L, suspect = Suspect.TRANSIT))
        val corrections = LocalTripCorrections(db, inner, io, clock, recompute)

        corrections.correct("a", TripCorrection.PASSENGER)
        corrections.answerSuspect("bus", SuspectAnswer.NO)
        corrections.correct("missing", TripCorrection.PASSENGER)
        corrections.answerSuspect("a", SuspectAnswer.YES)

        assertEquals(listOf("a", "bus"), recomputed, "only committed changes recompute")
        assertEquals(TripKind.PASSENGER, inner.trip("a")?.kind)
    }

    @Test
    fun feedbackWithANewScoreRecomputesFromItsTrip() = runTest {
        save(trip("a", 1_000L))
        val feedback = LocalEventFeedbackStore(db, io, recompute)

        feedback.save(EventFeedback("e1", "a", needed = true, inaccurate = null, at = 2L), score("a", 99))
        feedback.save(EventFeedback("e2", "a", needed = true, inaccurate = null, at = 3L), null)

        assertEquals(99, inner.score("a")?.smoothness)
        assertEquals(listOf("a"), recomputed, "no new score, nothing to recompute")
    }
}
