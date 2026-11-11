package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.CaptureMark
import com.driveperf.domain.capture.CaptureMarkType
import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.capture.RecordedAccelSample
import com.driveperf.domain.capture.Vec3
import com.driveperf.domain.trip.Stop
import com.driveperf.pipeline.capture.CaptureFixtures.fix
import com.driveperf.pipeline.capture.CaptureFixtures.header
import com.driveperf.pipeline.capture.CaptureFixtures.motionRun
import com.driveperf.pipeline.capture.CaptureFixtures.ns
import com.driveperf.pipeline.capture.CaptureFixtures.wallAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripSplitTest {
    @Test
    fun theLongestStopOverTheMinimumIsChosenEarliestOnATie() {
        val short = Stop(100.0, 1800.0, null, null)
        assertNull(TripSplit.stopFor(listOf(short), 1800.0), "exactly 30 min is not more than 30 min")
        val a = Stop(500.0, 2400.0, null, null)
        val b = Stop(9000.0, 3600.0, null, null)
        val c = Stop(20000.0, 3600.0, null, null)
        assertEquals(b, TripSplit.stopFor(listOf(c, short, a, b), 1800.0))
        assertNull(TripSplit.stopFor(emptyList(), 1800.0))
    }

    @Test
    fun aCaptureIsCutAroundTheStop() {
        val capture = CapturedTrip(
            header(0.0, id = "whole"),
            fixes = (0..100).map { fix(it * 10.0, 10.0, it * 100.0) },
            motion = motionRun(0.0, 1000.0, noisy = true),
            activity = listOf(CaptureFixtures.activity(5.0, com.driveperf.domain.capture.ActivityType.AUTOMOTIVE)),
            recordedAccel = listOf(RecordedAccelSample(ns(-5.0), Vec3(0.0, 0.0, 1.0))),
            marks = listOf(CaptureMark(ns(1.0), CaptureMarkType.CONFIRMED), CaptureMark(ns(900.0), CaptureMarkType.GNSS_LOST)),
        )
        // t = 0 of the trip is 2 s after the header (an estimated start shifts it); the stop is 300–700 s of trip time.
        val cut = TripSplit.split(capture, ns(2.0), Stop(298.0, 400.0, null, null), "second")

        assertEquals(ns(300.0), cut.firstEndNs)
        assertEquals("whole", cut.first.header.tripId)
        assertTrue(cut.first.fixes.all { it.tMonoNs < ns(300.0) })
        assertEquals(30, cut.first.fixes.size)
        assertEquals(1, cut.first.recordedAccel.size, "the history before t = 0 stays with the first part")
        assertEquals(listOf(CaptureMarkType.CONFIRMED), cut.first.marks.map { it.type })

        val h = cut.second.header
        assertEquals("second", h.tripId)
        assertEquals(ns(700.0), h.startMonoNs)
        assertEquals(wallAt(700.0), h.startWallMs)
        assertEquals(capture.header.tzId, h.tzId)
        val parked = assertNotNull(h.parking, "the second part starts where the first parked")
        assertEquals(cut.first.fixes.last().lat, parked.point.lat)
        assertEquals(cut.first.fixes.last().wallMs, parked.wallMs)
        assertTrue(cut.second.fixes.all { it.tMonoNs >= ns(700.0) })
        assertEquals(31, cut.second.fixes.size)
        assertTrue(cut.second.recordedAccel.isEmpty())
        assertTrue(cut.second.activity.isEmpty())
        assertEquals(listOf(CaptureMarkType.GNSS_LOST), cut.second.marks.map { it.type })
        assertEquals(capture.motion.size, cut.first.motion.size + cut.second.motion.size + 400 * 50)
    }

    @Test
    fun batchesCoverTheCaptureInMinuteWindows() {
        val capture = CapturedTrip(
            header(0.0),
            fixes = (0 until 150).map { fix(it.toDouble(), 10.0) },
            motion = motionRun(0.0, 150.0, noisy = false),
            activity = emptyList(),
            recordedAccel = listOf(RecordedAccelSample(ns(-5.0), Vec3(0.0, 0.0, 1.0))),
            marks = emptyList(),
        )
        val batches = capture.batches()
        assertEquals(3, batches.size)
        assertEquals(capture.fixes, batches.flatMap { it.fixes })
        assertEquals(capture.motion, batches.flatMap { it.motion })
        assertEquals(1, batches.first().recordedAccel.size)
        assertTrue(batches.drop(1).all { it.recordedAccel.isEmpty() })
        val empty = capture.copy(fixes = emptyList(), motion = emptyList(), recordedAccel = emptyList())
        assertEquals(1, empty.batches().size)
    }
}
