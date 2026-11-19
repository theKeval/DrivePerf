package com.driveperf.pipeline.coach

import com.driveperf.domain.capture.PreviewStore
import com.driveperf.domain.feedback.RescoreRequest
import com.driveperf.domain.repository.ScoredTrip
import com.driveperf.domain.repository.TripHistory
import com.driveperf.domain.score.Tip
import com.driveperf.domain.score.TipCategory
import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.DriveContext
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.InaccurateReason
import com.driveperf.domain.trip.Placement
import com.driveperf.domain.trip.PreviewRow
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripQuality
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.pipeline.coach.TipAudit.Violation
import com.driveperf.pipeline.fixtures.DriveFixtures
import com.driveperf.pipeline.fixtures.PipelineHarness
import com.driveperf.pipeline.score.FeedbackRescorer
import com.driveperf.pipeline.score.Goldens
import com.driveperf.pipeline.score.R1Analyser
import com.driveperf.pipeline.score.ScoringContext
import com.driveperf.pipeline.score.TripScorer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DRIVE-102: no tip ever comes from an excluded event or an unscorable trip, whatever writes the score. */
class TipAuditTest {
    private val trip = Trip(
        id = "t1", status = TripStatus.FINALISED, kind = TripKind.DRIVER, source = TripSource.AUTO,
        startedAt = 10_000L, startTzOffsetMin = 0, context = DriveContext.CITY, distanceM = 5_000.0,
        quality = TripQuality(QualityBadge.GOOD, null, Placement.MOUNTED, 50.0, 95.0, null, null, 600.0, 0.0, 600.0, emptyList()),
        createdAt = 1L, updatedAt = 1L,
    )
    private val out = TripScorer().score(
        R1Analyser().analyse(Goldens.g04),
        ScoringContext("t1", true, baselineEligible = true, distanceM = 5_000.0, routeId = null, priors = emptyList(), nowWallMs = 1L),
    )
    private val score = assertNotNull(out.score)
    private val history = object : TripHistory {
        override suspend fun finishedBefore(beforeStartedAt: Long, limit: Int): List<ScoredTrip> = emptyList()
    }
    private val previews = object : PreviewStore {
        override suspend fun write(tripId: String, rows: List<PreviewRow>) = true

        override suspend fun read(tripId: String) = listOf(PreviewRow(0.0, 2.5, -27.5, 10.0, null, null, null, 0))
    }

    @Test
    fun everyFixtureTripPassesTheAuditAsADriveAndAsAPassenger() = runTest {
        val harness = PipelineHarness()
        for (f in DriveFixtures.all) {
            val drive = harness.trip(f.capture())
            assertNotNull(drive.score?.tip, "${f.id}: a scorable trip gets exactly one tip")
            assertEquals(emptyList(), TipAudit.violations(drive.trip, drive.score, drive.events, emptyMap()), f.id)
            val passenger = harness.trip(f.capture(), kind = TripKind.PASSENGER)
            assertNull(passenger.score?.tip, "${f.id}: no tip for a passenger trip")
            assertEquals(emptyList(), TipAudit.violations(passenger.trip, passenger.score, passenger.events, emptyMap()))
        }
    }

    @Test
    fun excludingAnyEventNeverLeavesItAsTheTipsAnchor() = runTest {
        val rescorer = FeedbackRescorer(history, previews)
        for (event in out.events) {
            for (answer in listOf(
                EventFeedback(event.id, "t1", needed = true, inaccurate = null, at = 2L),
                EventFeedback(event.id, "t1", needed = false, inaccurate = InaccurateReason.PHONE_MOVED, at = 2L),
            )) {
                val feedback = mapOf(event.id to answer)
                val rescored = rescorer.rescore(RescoreRequest(trip, score, out.events, feedback, null, 3L))
                assertEquals(emptyList(), TipAudit.violations(trip, rescored, out.events, feedback), "${event.id} $answer")
            }
        }
        // Every event excluded at once: the tip falls back to a span, never an event.
        val all = out.events.associate { it.id to EventFeedback(it.id, "t1", needed = true, inaccurate = null, at = 2L) }
        val rescored = rescorer.rescore(RescoreRequest(trip, score, out.events, all, null, 3L))
        assertNull(rescored.tip?.eventId)
        assertEquals(emptyList(), TipAudit.violations(trip, rescored, out.events, all))
    }

    @Test
    fun theAuditCatchesEachBrokenRule() {
        val brake = out.events.first()
        val good = score.copy(tip = Tip("brake_light", TipCategory.BRAKING, brake.id, null))
        assertEquals(emptyList(), TipAudit.violations(trip, good, out.events, emptyMap()))
        assertEquals(emptyList(), TipAudit.violations(trip, score.copy(tip = null), out.events, emptyMap()))
        assertEquals(emptyList(), TipAudit.violations(trip, null, out.events, emptyMap()))

        val onTrip = listOf(Violation.TIP_ON_UNSCORABLE_TRIP)
        assertEquals(onTrip, TipAudit.violations(trip.copy(kind = TripKind.PASSENGER), good, out.events, emptyMap()))
        val unscorable = trip.copy(quality = trip.quality?.copy(badge = QualityBadge.UNSCORABLE))
        assertEquals(onTrip, TipAudit.violations(unscorable, good, out.events, emptyMap()))
        assertEquals(onTrip, TipAudit.violations(trip, good.copy(smoothness = null), out.events, emptyMap()))

        val needed = mapOf(brake.id to EventFeedback(brake.id, "t1", needed = true, inaccurate = null, at = 2L))
        assertEquals(listOf(Violation.EXCLUDED_EVENT), TipAudit.violations(trip, good, out.events, needed))
        val inaccurate = mapOf(brake.id to EventFeedback(brake.id, "t1", needed = false, inaccurate = InaccurateReason.OTHER, at = 2L))
        assertEquals(listOf(Violation.EXCLUDED_EVENT), TipAudit.violations(trip, good, out.events, inaccurate))
        val counted = mapOf(brake.id to EventFeedback(brake.id, "t1", needed = false, inaccurate = null, at = 2L))
        assertEquals(emptyList(), TipAudit.violations(trip, good, out.events, counted), "\"Count it again\" is active")

        val low = out.events.map { if (it.id == brake.id) it.copy(confidence = Confidence.LOW) else it }
        assertEquals(listOf(Violation.LOW_CONFIDENCE_EVENT), TipAudit.violations(trip, good, low, emptyMap()))
        assertEquals(listOf(Violation.UNKNOWN_EVENT), TipAudit.violations(trip, good, emptyList(), emptyMap()))
        val wrongCategory = good.copy(tip = Tip("brake_light", TipCategory.CORNERING, null, null))
        assertEquals(listOf(Violation.NOT_IN_LIBRARY), TipAudit.violations(trip, wrongCategory, out.events, emptyMap()))
    }

    @Test
    fun theLaunchLibraryHasAtLeastTwelveTipsInEveryCategory() {
        assertTrue(TipSelector.LIBRARY.values.sumOf { it.size } >= 12, "F-SCO-06 AC4: at least 12 tips")
        assertEquals(TipCategory.entries.toSet(), TipSelector.LIBRARY.filterValues { it.isNotEmpty() }.keys)
        val ids = TipSelector.LIBRARY.values.flatten()
        assertEquals(ids.size, ids.toSet().size, "tip IDs are unique")
    }
}
