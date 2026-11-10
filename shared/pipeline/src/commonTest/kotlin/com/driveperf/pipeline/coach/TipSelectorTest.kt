package com.driveperf.pipeline.coach

import com.driveperf.domain.score.Contributor
import com.driveperf.domain.score.ContributorId
import com.driveperf.domain.score.Score
import com.driveperf.domain.score.TipCategory
import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.DriveContext
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.EventType
import com.driveperf.pipeline.score.Goldens
import com.driveperf.pipeline.score.R1Analyser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TipSelectorTest {
    private fun score(brake: Double = 0.0, accel: Double = 0.0, corner: Double = 0.0, consistency: Double = 100.0) = Score(
        "t", 80, 80.0, null, null, null, null, null,
        listOf(
            Contributor(ContributorId.BRAKING, 90.0, brake, null, null, emptyList()),
            Contributor(ContributorId.ACCELERATION, 90.0, accel, null, null, emptyList()),
            Contributor(ContributorId.CORNERING, 90.0, corner, null, null, emptyList()),
            Contributor(ContributorId.CONSISTENCY, consistency, 1.0, null, null, emptyList()),
        ),
        null, null, null, null, "smooth-1.0.0", 0L,
    )

    @Suppress("LongParameterList")
    private fun event(
        id: String,
        type: EventType,
        t: Double,
        start: Double = t - 1,
        end: Double = t + 1,
        before: Double = 10.0,
        after: Double = 5.0,
        lat: Double? = 2.0,
        confidence: Confidence = Confidence.HIGH,
        peak: Double = 0.4,
    ) = DrivingEvent(
        id, "t", type, t, start, end, end - start, peak, null, lat, lat?.let { -27.5 }, before, after, confidence, "smooth-1.0.0",
    )

    @Suppress("LongParameterList")
    private fun inputs(
        score: Score = score(),
        events: List<DrivingEvent> = emptyList(),
        context: DriveContext = DriveContext.CITY,
        recent: List<String> = emptyList(),
        window: JerkWindow? = null,
        feedback: Map<String, EventFeedback> = emptyMap(),
        stopStarts: Int = 0,
    ) = TipInputs(score, events, feedback, context, stopStarts, recent, window, 30.0, { 2.5 to -27.25 }, { it.peakG })

    @Test
    fun theCostliestCategoryWithACandidateIsChosen() {
        val events = listOf(event("b", EventType.BRAKE, 10.0), event("c", EventType.CORNER, 20.0))
        val tip = TipSelector.select(inputs(score(brake = 1.0, corner = 3.0, accel = 9.0), events))
        assertEquals(TipCategory.CORNERING, tip.category, "accel costs most but has no candidate")
        assertEquals("c", tip.eventId)
        assertEquals("p:2.0000,-27.5000", tip.placeKey)
    }

    @Test
    fun brakingRules() {
        val toStop = event("b", EventType.BRAKE, 10.0, after = 0.2)
        assertEquals("brake_light", TipSelector.select(inputs(score(brake = 2.0), listOf(toStop))).tipId)
        val lateEase = event("b", EventType.BRAKE, 10.0, start = 8.0, end = 11.0)
        assertEquals("brake_ease_end", TipSelector.select(inputs(score(brake = 2.0), listOf(lateEase))).tipId)
        val early = event("b", EventType.BRAKE, 8.5, start = 8.0, end = 11.0)
        assertEquals("brake_longer_ramp", TipSelector.select(inputs(score(brake = 2.0), listOf(early))).tipId)
    }

    @Test
    fun accelerationAndCorneringRules() {
        val pullAway = event("a", EventType.ACCEL, 5.0, before = 0.5)
        assertEquals("accel_first_second", TipSelector.select(inputs(score(accel = 2.0), listOf(pullAway))).tipId)
        val merge = event("a", EventType.ACCEL, 5.0)
        assertEquals("accel_merge_build", TipSelector.select(inputs(score(accel = 2.0), listOf(merge), DriveContext.HIGHWAY)).tipId)
        assertEquals("accel_progressive", TipSelector.select(inputs(score(accel = 2.0), listOf(merge))).tipId)
        val brake = event("b", EventType.BRAKE, 8.0, start = 7.0, end = 8.5, confidence = Confidence.LOW)
        val corner = event("c", EventType.CORNER, 10.5, start = 10.0, end = 13.0)
        assertEquals("corner_brake_before", TipSelector.select(inputs(score(corner = 2.0), listOf(brake, corner))).tipId)
        assertEquals("corner_smooth_entry", TipSelector.select(inputs(score(corner = 2.0), listOf(corner))).tipId)
        val lateCorner = event("c", EventType.CORNER, 12.5, start = 10.0, end = 13.0)
        assertEquals("corner_unwind_exit", TipSelector.select(inputs(score(corner = 2.0), listOf(lateCorner))).tipId)
    }

    @Test
    fun withoutEventsTheJerkWindowOrAnticipationDecides() {
        val window = JerkWindow(TipCategory.CONSISTENCY, 10.0, 15.0)
        val consistency = TipSelector.select(inputs(window = window, context = DriveContext.HIGHWAY))
        assertEquals("consist_hold_steady", consistency.tipId)
        assertNull(consistency.eventId)
        assertEquals("p:2.5000,-27.2500", consistency.placeKey)
        assertEquals("consist_longer_look", TipSelector.select(inputs(window = window)).tipId)
        assertEquals("anticip_coast_red", TipSelector.select(inputs(stopStarts = 4)).tipId)
        assertEquals("anticip_leave_gap", TipSelector.select(inputs()).tipId)
        assertEquals("anticip_read_ahead", TipSelector.select(inputs(context = DriveContext.MIXED)).tipId)
    }

    @Test
    fun recentTipsAreSkippedUnlessNoneRemain() {
        val e = event("b", EventType.BRAKE, 10.0, after = 0.2)
        val tip = TipSelector.select(inputs(score(brake = 2.0), listOf(e), recent = listOf("brake_light")))
        assertEquals("brake_ease_end", tip.tipId)
        val all = TipSelector.LIBRARY.getValue(TipCategory.BRAKING)
        assertEquals("brake_light", TipSelector.select(inputs(score(brake = 2.0), listOf(e), recent = all)).tipId)
    }

    @Test
    fun neededInaccurateAndLowEventsAreNeverAnchors() {
        val needed = event("n", EventType.BRAKE, 10.0)
        val low = event("l", EventType.ACCEL, 20.0, confidence = Confidence.LOW)
        val fb = mapOf("n" to EventFeedback("n", "t", needed = true, inaccurate = null, at = 0L))
        val tip = TipSelector.select(inputs(score(brake = 5.0), listOf(needed, low), feedback = fb))
        assertEquals(TipCategory.ANTICIPATION, tip.category)
        assertNull(tip.eventId)
    }

    @Test
    fun unpinnedEventsAnchorTheEarliestAndNegativeCoordinatesFormat() {
        val a = event("a", EventType.BRAKE, 30.0, lat = null)
        val b = event("b", EventType.BRAKE, 10.0, lat = null)
        val tip = TipSelector.select(inputs(score(brake = 2.0), listOf(a, b)))
        assertEquals("b", tip.eventId)
        assertNull(tip.placeKey)
    }

    @Test
    fun stretchesComeFromTheAnalysis() {
        val gentle = R1Analyser().analyse(Goldens.g01)
        val clean = assertNotNull(Stretches.cleanest(gentle))
        assertTrue(clean.t1S - clean.t0S >= 30.0)
        assertTrue(clean.distanceM > 0)
        assertNull(Stretches.jerkWindow(gentle), "G-01 has no excess jerk")
        val brisk = assertNotNull(Stretches.jerkWindow(R1Analyser().analyse(Goldens.g03)))
        assertEquals(5.0, brisk.t1S - brisk.t0S, 1e-9)
        assertTrue(brisk.category in setOf(TipCategory.BRAKING, TipCategory.ACCELERATION, TipCategory.CORNERING))
        assertNull(Stretches.cleanest(R1Analyser().analyse(Goldens.g04)), "rough driving has no clean 30 s")
    }
}
