package com.driveperf.pipeline.score

import com.driveperf.domain.capture.PreviewStore
import com.driveperf.domain.feedback.RescoreRequest
import com.driveperf.domain.repository.ScoredTrip
import com.driveperf.domain.repository.TripHistory
import com.driveperf.domain.score.ContributorId
import com.driveperf.domain.score.RecordState
import com.driveperf.domain.trip.DriveContext
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.Placement
import com.driveperf.domain.trip.PreviewRow
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripQuality
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class FeedbackRescorerTest {
    private val out = TripScorer().score(
        R1Analyser().analyse(Goldens.g04),
        ScoringContext("t1", true, baselineEligible = true, distanceM = 5_000.0, routeId = null, priors = emptyList(), nowWallMs = 1L),
    )
    private val score = assertNotNull(out.score).copy(recordState = RecordState(true, 70, "older"))
    private val brake = out.events.first { it.type == EventType.BRAKE }
    private val trip = Trip(
        id = "t1", status = TripStatus.FINALISED, kind = TripKind.DRIVER, source = TripSource.AUTO,
        startedAt = 10_000L, startTzOffsetMin = 0, context = DriveContext.CITY, distanceM = 5_000.0,
        quality = TripQuality(QualityBadge.GOOD, null, Placement.MOUNTED, 50.0, 95.0, null, null, 600.0, 0.0, 600.0, emptyList()),
        createdAt = 1L, updatedAt = 1L,
    )
    private val history = object : TripHistory {
        override suspend fun finishedBefore(beforeStartedAt: Long, limit: Int): List<ScoredTrip> = emptyList()
    }
    private val previews = object : PreviewStore {
        override suspend fun write(tripId: String, rows: List<PreviewRow>) = true

        override suspend fun read(tripId: String) = listOf(PreviewRow(0.0, 2.5, -27.5, 10.0, null, null, null, 0))
    }

    private fun request(feedback: Map<String, EventFeedback>, s: com.driveperf.domain.score.Score = score) =
        RescoreRequest(trip, s, out.events, feedback, null, 99L)

    @Test
    fun markingABrakeNeededRescoresAndKeepsTheRecordState() = runTest {
        val rescorer = FeedbackRescorer(history, previews)
        val needed = mapOf(brake.id to EventFeedback(brake.id, "t1", needed = true, inaccurate = null, at = 2L))
        val rescored = rescorer.rescore(request(needed))
        assertEquals(54, rescored.smoothness, "G-08: the same result as the scorer's own re-score")
        assertFalse(brake.id in rescored.contributors.first { it.id == ContributorId.BRAKING }.eventIds)
        val tip = assertNotNull(rescored.tip, "a scorable trip always gets one tip")
        assertFalse(tip.eventId == brake.id, "F-SCO-05 AC2: never about the excluded event")
        assertEquals(score.recordState, rescored.recordState)
        assertEquals(99L, rescored.computedAt)
        assertEquals(52, rescorer.rescore(request(emptyMap())).smoothness, "Count it again restores the original")
    }

    @Test
    fun aTripWithoutContextOrLedgerIsLeftAlone() = runTest {
        val rescorer = FeedbackRescorer(history)
        val noContext = RescoreRequest(trip.copy(context = null), score, out.events, emptyMap(), null, 1L)
        assertSame(score, rescorer.rescore(noContext))
        val noLedger = score.copy(ledger = null)
        assertSame(noLedger, rescorer.rescore(request(emptyMap(), noLedger)))
    }

    @Test
    fun feedbackUnderTheExposureFloorClearsTheScoreWithoutATip() = runTest {
        val ledger = assertNotNull(score.ledger)
        val thin = score.copy(
            ledger = ledger.copy(
                base = ledger.base.copy(movingS = 100.0),
                perEvent = ledger.perEvent.mapIndexed { i, e -> e.copy(row = e.row.copy(movingS = if (i == 0) 30.0 else 0.0)) },
            ),
        )
        val first = ledger.perEvent.first().eventId
        val rescored = FeedbackRescorer(history).rescore(
            request(mapOf(first to EventFeedback(first, "t1", needed = true, inaccurate = null, at = 2L)), thin),
        )
        assertNull(rescored.smoothness)
        assertNull(rescored.tip)
    }

    @Test
    fun efficiencyComesBackExactlyAfterSeveralFeedbackChanges() = runTest {
        val rescorer = FeedbackRescorer(history)
        val counted = score.copy(ledger = score.ledger!!.copy(stopStartCount = 4, abruptAccelCount = 10))
        val baseline = rescorer.rescore(request(emptyMap(), counted))
        val needed = mapOf(brake.id to EventFeedback(brake.id, "t1", needed = true, inaccurate = null, at = 2L))
        val marked = rescorer.rescore(request(needed, counted))
        assertEquals(10, marked.ledger?.abruptAccelCount, "the finalisation counts ride along on the ledger")
        val undone = rescorer.rescore(request(emptyMap(), marked))
        assertEquals(baseline.efficiency, undone.efficiency, "Count it again from a re-scored score restores efficiency")
        assertEquals(4, undone.efficiency?.stopStartCount)
    }

    @Test
    fun theFinalisationCountsAreOnTheLedger() {
        assertEquals(out.efficiencyInputs.stopStartCount, out.score?.ledger?.stopStartCount)
        assertEquals(out.efficiencyInputs.abruptAccels, out.score?.ledger?.abruptAccelCount)
    }
}
