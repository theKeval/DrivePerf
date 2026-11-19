package com.driveperf.pipeline.score

import com.driveperf.domain.capture.PreviewStore
import com.driveperf.domain.repository.ScoredTrip
import com.driveperf.domain.repository.TripHistory
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.score.ContributorId
import com.driveperf.domain.score.Efficiency
import com.driveperf.domain.score.EfficiencyRating
import com.driveperf.domain.score.Score
import com.driveperf.domain.score.ScoreBasis
import com.driveperf.domain.score.ScoreHistoryStore
import com.driveperf.domain.trip.DriveContext
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.Placement
import com.driveperf.domain.trip.PreviewRow
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripQuality
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.tripfile.FileResult
import com.driveperf.pipeline.coach.TipAudit
import com.driveperf.testing.TestClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/** DRIVE-131 and DRIVE-97: §5.7 "Corrections" recomputes a trip and every later one from stored ledgers. */
class HistoryRecomputeTest {
    private val clock = TestClock()

    /** Scored drives from the §8.3 goldens: G-02 is 97, G-03 is 88, G-04 is 52 (events on every channel). */
    private companion object Scored {
        val g02: Scored1 = scored(Goldens.g02)
        val g03: Scored1 = scored(Goldens.g03)
        val g04: Scored1 = scored(Goldens.g04)

        fun scored(trace: R1Trace): Scored1 {
            val out = TripScorer().score(
                R1Analyser().analyse(trace),
                ScoringContext(
                    "x", true, baselineEligible = true, distanceM = 5_000.0, routeId = null, priors = emptyList(), nowWallMs = 1L,
                ),
            )
            return Scored1(assertNotNull(out.score), out.events)
        }
    }

    private data class Scored1(val score: Score, val events: List<DrivingEvent>)

    private fun trip(id: String, startedAt: Long, kind: TripKind = TripKind.DRIVER, context: DriveContext = DriveContext.CITY) = Trip(
        id = id, status = TripStatus.FINALISED, kind = kind, source = TripSource.AUTO, startedAt = startedAt,
        startTzOffsetMin = 0, context = context, distanceM = 5_000.0,
        quality = TripQuality(QualityBadge.GOOD, null, Placement.MOUNTED, 50.0, 95.0, null, null, 600.0, 0.0, 600.0, emptyList()),
        createdAt = startedAt, updatedAt = startedAt,
    )

    /** The trips, their scores and events in memory, and every port the recompute uses over them. */
    private inner class World {
        val trips = linkedMapOf<String, ScoredTrip>()
        val events = mutableMapOf<String, List<DrivingEvent>>()
        val feedback = mutableMapOf<String, Map<String, EventFeedback>>()
        var saves = 0

        fun add(id: String, startedAt: Long, from: Scored1, routeId: String? = null, context: DriveContext = DriveContext.CITY) {
            trips[id] = ScoredTrip(trip(id, startedAt, context = context), from.score.copy(tripId = id), routeId)
            events[id] = from.events
        }

        fun score(id: String): Score = assertNotNull(trips.getValue(id).score)

        fun setKind(id: String, kind: TripKind) {
            val t = trips.getValue(id)
            trips[id] = t.copy(trip = t.trip.copy(kind = kind))
        }

        val store = object : ScoreHistoryStore {
            override suspend fun finishedFrom(startedAt: Long): List<ScoredTrip> = trips.values
                .filter { it.trip.startedAt >= startedAt }
                .sortedWith(compareBy<ScoredTrip> { it.trip.startedAt }.thenBy { it.trip.id })

            override suspend fun saveScores(scores: List<Score>) {
                saves++
                scores.forEach { s -> trips[s.tripId]?.let { trips[s.tripId] = it.copy(score = s) } }
            }
        }

        val history = object : TripHistory {
            override suspend fun finishedBefore(beforeStartedAt: Long, limit: Int): List<ScoredTrip> = trips.values
                .filter { it.trip.startedAt < beforeStartedAt }
                .sortedWith(compareByDescending<ScoredTrip> { it.trip.startedAt }.thenByDescending { it.trip.id })
                .take(limit)
        }

        val repository = object : TripRepository {
            override fun observeTrips(): Flow<List<Trip>> = emptyFlow()

            override suspend fun trip(id: String): Trip? = trips[id]?.trip

            override suspend fun events(tripId: String): List<DrivingEvent> = events[tripId].orEmpty()

            override suspend fun feedback(tripId: String): Map<String, EventFeedback> = feedback[tripId].orEmpty()

            override suspend fun score(tripId: String): Score? = trips[tripId]?.score

            override suspend fun delete(tripId: String): FileResult = FileResult.OK
        }

        val previews = object : PreviewStore {
            override suspend fun write(tripId: String, rows: List<PreviewRow>) = true

            override suspend fun read(tripId: String) = listOf(PreviewRow(0.0, 2.5, -27.5, 10.0, null, null, null, 0))
        }

        val recompute = HistoryRecompute(history, store, repository, clock, previews)
    }

    /** Four city drives: 97, 88, 52, then 97 compared with the median of the first three, 88. */
    private suspend fun fourDrives(): World = World().apply {
        add("t1", 1_000L, g02)
        add("t2", 2_000L, g03)
        add("t3", 3_000L, g04)
        add("t4", 4_000L, g02)
        recompute.from(0L)
    }

    @Test
    fun aFullWalkGivesEachDriveItsBaseline() = runTest {
        val w = fourDrives()
        assertEquals(1, w.score("t1").calibrating?.k)
        assertEquals(3, w.score("t3").calibrating?.k)
        val t4 = w.score("t4")
        assertNull(t4.calibrating)
        assertEquals(ScoreBasis.CONTEXT, t4.basis)
        assertEquals(88, t4.baseline)
        assertEquals(97 - 88, t4.delta)
        assertTrue(t4.contributors.all { it.band != null && it.reasonKey != null }, "bands once a baseline exists")
    }

    @Test
    fun recomputingAgainWritesNothing() = runTest {
        val w = fourDrives()
        val saves = w.saves
        assertEquals(emptyList(), w.recompute.from(0L), "deterministic: an unchanged history changes no score")
        assertEquals(saves, w.saves)
    }

    @Test
    fun aTripCorrectedToPassengerLeavesItsScoreAndLaterBaselines() = runTest {
        val w = fourDrives()
        val before = w.score("t1")
        w.setKind("t1", TripKind.PASSENGER)
        w.recompute.fromTrip("t1")

        val t1 = w.score("t1")
        assertNull(t1.smoothness, "§5.8: kind ≠ DRIVER has no score")
        assertNull(t1.tip, "F-SCO-06 AC3: and no tip")
        assertNull(t1.calibrating)
        assertEquals(before.ledger, t1.ledger, "the ledger stays, so the trip can be a drive again")
        assertEquals(before.algorithmVersion, t1.algorithmVersion)
        val t4 = w.score("t4")
        assertNull(t4.baseline, "two prior drives are not a baseline")
        assertEquals(3, t4.calibrating?.k)
        assertTrue(TipAudit.violations(w.trips.getValue("t1").trip, t1, w.events.getValue("t1"), emptyMap()).isEmpty())
    }

    @Test
    fun aTripCorrectedBackToADriveScoresAsBefore() = runTest {
        val w = fourDrives()
        val t1 = w.score("t1")
        val t4 = w.score("t4")
        w.setKind("t1", TripKind.PASSENGER)
        w.recompute.fromTrip("t1")
        w.setKind("t1", TripKind.DRIVER)
        w.recompute.fromTrip("t1")

        val back = w.score("t1")
        assertEquals(t1.smoothness, back.smoothness)
        t1.contributors.zip(back.contributors).forEach { (a, b) -> assertEquals(a.subScore, b.subScore, 1e-9, "${a.id}") }
        assertNotNull(back.tip, "a drive again gets its one tip")
        assertEquals(t4.copy(computedAt = 0L), w.score("t4").copy(computedAt = 0L), "later baselines are as they were")
    }

    @Test
    fun aDeletedTripLeavesLaterBaselines() = runTest {
        val w = fourDrives()
        w.trips.remove("t2")
        w.recompute.from(2_000L)
        assertNull(w.score("t4").baseline)
        assertEquals(3, w.score("t4").calibrating?.k)
    }

    @Test
    fun feedbackOnAnEarlierTripMovesLaterBaselines() = runTest {
        // 52, 52, 88, then 97 against their median, 52. "Was it needed?" on a brake of t1 lifts it to 54 (G-08).
        val w = World().apply {
            add("t1", 1_000L, g04)
            add("t2", 2_000L, g04)
            add("t3", 3_000L, g03)
            add("t4", 4_000L, g02)
            recompute.from(0L)
        }
        assertEquals(52, w.score("t4").baseline)
        val brake = w.events.getValue("t1").first { it.type == com.driveperf.domain.trip.EventType.BRAKE }
        w.feedback["t1"] = mapOf(brake.id to EventFeedback(brake.id, "t1", needed = true, inaccurate = null, at = 2L))
        val rescored = FeedbackRescorer(w.history, w.previews).rescore(
            com.driveperf.domain.feedback.RescoreRequest(
                w.trips.getValue("t1").trip, w.score("t1"), w.events.getValue("t1"), w.feedback.getValue("t1"), null, 5L,
            ),
        )
        w.trips["t1"] = w.trips.getValue("t1").copy(score = rescored)
        w.recompute.fromTrip("t1")

        assertEquals(54, w.score("t1").smoothness)
        assertEquals(54, w.score("t4").baseline, "the later trip compares with the re-scored one")
        assertEquals(97 - 54, w.score("t4").delta)
    }

    @Test
    fun aRescoreNeverChangesTheAlgorithmVersion() = runTest {
        val w = World().apply {
            add("t1", 1_000L, g02)
            add("t2", 2_000L, g03)
            add("t3", 3_000L, g04)
            add("t4", 4_000L, g02)
        }
        w.trips.keys.toList().forEach { id ->
            val t = w.trips.getValue(id)
            w.trips[id] = t.copy(score = t.score?.copy(algorithmVersion = "smooth-0.9.0"))
        }
        w.recompute.from(0L)
        w.setKind("t1", TripKind.PASSENGER)
        w.recompute.fromTrip("t1")
        w.setKind("t1", TripKind.DRIVER)
        w.recompute.fromTrip("t1")
        assertEquals(setOf("smooth-0.9.0"), w.trips.values.map { it.score?.algorithmVersion }.toSet())
    }

    @Test
    fun anUnknownTripChangesNothing() = runTest {
        val w = fourDrives()
        val saves = w.saves
        w.recompute.fromTrip("nope")
        assertEquals(emptyList(), w.recompute.from(10_000L), "no trips from there on")
        assertEquals(saves, w.saves)
    }

    @Test
    fun aDriveWithoutContextOrScoreIsLeftAlone() = runTest {
        val w = fourDrives()
        w.trips["t5"] = ScoredTrip(trip("t5", 5_000L).copy(context = null), g02.score.copy(tripId = "t5"), null)
        w.trips["t6"] = ScoredTrip(trip("t6", 6_000L), null, null)
        val t5 = w.score("t5")
        w.recompute.from(5_000L)
        assertEquals(t5, w.score("t5"))
        assertNull(w.trips.getValue("t6").score)
    }

    @Test
    fun aDriveWithoutSmoothnessStaysUnscoredWhenItsLedgerCannotScore() = runTest {
        val w = fourDrives()
        val ledger = assertNotNull(w.score("t4").ledger)
        val thinLedger = ledger.copy(base = ledger.base.copy(movingS = 1.0), perEvent = emptyList())
        val thin = w.score("t4").copy(smoothness = null, ledger = thinLedger)
        w.trips["t4"] = w.trips.getValue("t4").copy(score = thin)
        w.recompute.from(4_000L)
        assertEquals(thin, w.score("t4"), "under 120 s of exposure: nothing to restore, nothing written")
        val noLedger = thin.copy(ledger = null)
        w.trips["t4"] = w.trips.getValue("t4").copy(score = noLedger)
        w.recompute.from(4_000L)
        assertEquals(noLedger, w.score("t4"))
    }

    @Test
    fun theRouteBasisUsesRunsBeforeTheRecomputeStartedAndTheEfficiencyRatingFollows() = runTest {
        val w = World()
        // Five route runs well before the corrected trip, behind more than a page of other drives in another context.
        (1..5).forEach { w.add("r$it", it * 10L, g03, routeId = "route-a") }
        (1..250).forEach { w.add("h$it", 1_000L + it, g02, context = DriveContext.HIGHWAY) }
        w.add("x", 5_000L, g02)
        w.add("y", 6_000L, g04, routeId = "route-a")
        val withEfficiency = { id: String, stopsPerKm: Double ->
            val s = w.score(id)
            w.trips[id] = w.trips.getValue(id).copy(score = s.copy(efficiency = Efficiency(null, stopsPerKm, 1.0, 0.5, null, 4)))
        }
        (1..5).forEach { withEfficiency("r$it", 0.5) }
        withEfficiency("y", 2.0)
        w.recompute.from(5_000L)

        val y = w.score("y")
        assertEquals(ScoreBasis.ROUTE, y.basis)
        assertEquals("route-a", y.basisRouteId)
        assertEquals(88, y.baseline)
        assertEquals(EfficiencyRating.WORSE, y.efficiency?.rating, "rated against the same priors (§5.9)")
        assertEquals(
            com.driveperf.domain.score.Band.ROUGHER,
            y.contributors.first { it.id == ContributorId.BRAKING }.band,
        )
    }

    @Test
    fun aLongHistoryRecomputesWellInsideTheTwoSecondBudget() = runTest {
        val w = World()
        val contexts = DriveContext.entries
        (0 until 2_000).forEach { i ->
            val from = listOf(g02, g03, g04)[i % 3]
            w.add("t$i", i * 1_000L, from, routeId = if (i % 7 == 0) "route-${i % 5}" else null, context = contexts[i % 3])
        }
        w.recompute.from(0L)
        w.setKind("t1000", TripKind.PASSENGER)
        val mark = TimeSource.Monotonic.markNow()
        w.recompute.fromTrip("t1000")
        val elapsed = mark.elapsedNow()
        assertTrue(elapsed.inWholeMilliseconds < 2_000, "1,000 later trips over 1,000 earlier ones took $elapsed")
        assertNull(w.score("t1000").smoothness)
    }
}
