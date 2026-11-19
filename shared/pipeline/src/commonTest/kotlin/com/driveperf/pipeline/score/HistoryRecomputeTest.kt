package com.driveperf.pipeline.score

import com.driveperf.domain.repository.ScoredTrip
import com.driveperf.domain.score.ContributorId
import com.driveperf.domain.score.Efficiency
import com.driveperf.domain.score.EfficiencyRating
import com.driveperf.domain.score.Score
import com.driveperf.domain.score.ScoreBasis
import com.driveperf.domain.trip.DriveContext
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.pipeline.coach.TipAudit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/** DRIVE-131 and DRIVE-97: §5.7 "Corrections" recomputes a trip and every later one from stored ledgers. */
class HistoryRecomputeTest {
    private val g02 = RecomputeWorld.g02
    private val g03 = RecomputeWorld.g03
    private val g04 = RecomputeWorld.g04

    private fun trip(id: String, startedAt: Long) = RecomputeWorld.trip(id, startedAt)

    private fun World() = RecomputeWorld()

    /** Four city drives: 97, 88, 52, then 97 compared with the median of the first three, 88. */
    private suspend fun fourDrives(): RecomputeWorld = World().apply {
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
    fun aLongHistoryIsReadOnceAndWrittenInOneTransactionFromTheChangedTripOn() = runTest {
        val w = World().longHistory()
        val before = w.trips.mapValues { it.value.score }
        val reads = w.reads
        val saves = w.saves
        val pages = w.historyPages
        w.setKind("t1000", TripKind.PASSENGER)
        val mark = TimeSource.Monotonic.markNow()
        val changed = w.recompute.from(1_000_000L)
        // No wall-clock assertion here: debug Kotlin/Native on a shared simulator is too slow and noisy for one. The
        // JVM checks the 2 s budget (HistoryRecomputeBudgetTest); this line reports the time on every platform.
        println("DRIVE-131 recompute of 1,000 later trips over 1,000 earlier ones: ${mark.elapsedNow()}")

        assertEquals(reads + 1, w.reads, "the later trips are read in one call")
        assertEquals(saves + 1, w.saves, "the changes are written in one call")
        assertTrue(w.historyPages - pages <= 1_000 / 200 + 1, "earlier trips are paged once: ${w.historyPages - pages}")
        assertTrue(changed.all { it.tripId.removePrefix("t").toInt() >= 1_000 }, "nothing before the changed trip is written")
        assertTrue(changed.any { it.tripId == "t1000" })
        assertNull(w.score("t1000").smoothness)
        assertTrue((0 until 1_000).all { w.trips.getValue("t$it").score == before["t$it"] })
    }

    @Test
    fun overlappingCorrectionsEndWithTheLatestOne() = runTest {
        val w = fourDrives()
        val t2 = w.score("t2")
        val t4 = w.score("t4")
        // Correction 1 makes t2 a passenger; its walk is held just before it saves.
        w.setKind("t2", TripKind.PASSENGER)
        w.holdSave = CompletableDeferred()
        val gate = w.holdSave!!
        val first = launch { w.recompute.fromTrip("t2") }
        w.savePending.await()
        // Correction 2 makes it a drive again and starts its own walk while the first is still unsaved.
        w.setKind("t2", TripKind.DRIVER)
        val second = launch { w.recompute.fromTrip("t2") }
        gate.complete(Unit)
        first.join()
        second.join()

        assertEquals(t2.smoothness, w.score("t2").smoothness, "the later correction wins: t2 is a drive")
        assertEquals(t4.baseline, w.score("t4").baseline)
        assertEquals(t4.calibrating, w.score("t4").calibrating)
    }

    @Test
    fun aScoreWrittenDuringAWalkIsNotOverwrittenByIt() = runTest {
        val w = fourDrives()
        w.setKind("t1", TripKind.PASSENGER)
        w.holdSave = CompletableDeferred()
        val gate = w.holdSave!!
        val walk = launch { w.recompute.fromTrip("t1") }
        w.savePending.await()
        // Feedback re-scores t4 while the walk is unsaved; the feedback store's own recompute follows it.
        val rescored = w.score("t4").copy(smoothness = 99, computedAt = 7L)
        w.trips["t4"] = w.trips.getValue("t4").copy(score = rescored)
        val followUp = launch { w.recompute.fromTrip("t4") }
        gate.complete(Unit)
        walk.join()
        followUp.join()

        assertEquals(99, w.score("t4").smoothness, "the walk read the old t4, so it left the new one alone")
        assertNull(w.score("t1").smoothness, "the rest of the walk was written")
        assertEquals(3, w.score("t4").calibrating?.k, "and the follow-up compared t4 without t1")
    }
}
