package com.driveperf.pipeline.fixtures

import com.driveperf.domain.trip.DriveContext
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.Placement
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.SegmentReason
import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.TripStatus
import com.driveperf.pipeline.Versions
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The three DRIVE-62 drive fixtures through the whole `pipe-1.0.0` pipeline. Expected values were produced by this
 * pipeline when the fixtures were created; they guard against regressions, not real-drive accuracy (A19 does that).
 */
class DriveFixturesTest {
    private val harness = PipelineHarness()

    @Test
    fun cityDrive() = runTest {
        val r = harness.trip(DriveFixtures.city.capture())
        val t = r.trip
        val q = assertNotNull(t.quality)
        assertEquals(TripStatus.FINALISED, t.status)
        assertEquals(DriveContext.CITY, t.context)
        assertEquals(QualityBadge.GOOD, q.badge)
        assertEquals(Placement.MOUNTED, q.placement)
        assertTrue(q.spans.isEmpty())
        assertEquals(328.04, t.durationS!!, 0.05)
        assertEquals(2611.8, t.distanceM!!, 1.0)
        assertEquals(3, t.stopCount)
        assertEquals(13.95, t.maxSpeedMps!!, 0.05)
        assertEquals(Suspect.NONE, t.suspect)
        assertEquals(Versions.PIPELINE, t.pipelineVersion)
        val score = assertNotNull(r.score)
        assertEquals(92, score.smoothness)
        assertEquals(listOf(EventType.CORNER, EventType.ACCEL, EventType.BRAKE), r.events.map { it.type })
        assertTrue(r.events.all { it.lat != null }, "every event is pinned with GNSS present")
        val tip = assertNotNull(score.tip)
        assertEquals(com.driveperf.domain.score.TipCategory.BRAKING, tip.category)
        assertEquals("brake_light", tip.tipId, "the hard stop is the costliest braking event")
        assertEquals(r.events.single { it.type == EventType.BRAKE }.id, tip.eventId)
        assertTrue(r.preview.size in 2..400)
    }

    @Test
    fun highwayDrive() = runTest {
        val r = harness.trip(DriveFixtures.highway.capture())
        assertEquals(DriveContext.HIGHWAY, r.trip.context)
        assertEquals(QualityBadge.GOOD, r.trip.quality?.badge)
        assertEquals(8227.4, r.trip.distanceM!!, 1.0)
        assertEquals(30.0, r.trip.maxSpeedMps!!, 0.05)
        assertEquals(100, r.score?.smoothness)
        assertTrue(r.events.isEmpty())
        assertNotNull(r.trip.cleanest, "a smooth highway drive has a clean stretch for the Story opener")
    }

    @Test
    fun mixedDriveWithATunnelIsScoredThroughTheGap() = runTest {
        val r = harness.trip(DriveFixtures.mixedTunnel.capture())
        val q = assertNotNull(r.trip.quality)
        assertEquals(DriveContext.MIXED, r.trip.context)
        assertEquals(QualityBadge.USABLE, q.badge, "a 60 s tunnel takes GNSS coverage over moving time below 85 %")
        assertEquals(com.driveperf.domain.trip.QualityReason.LOW_GNSS, q.reason)
        val tunnel = q.spans.single()
        assertEquals(SegmentKind.GAP, tunnel.kind)
        assertEquals(SegmentReason.TUNNEL, tunnel.reason)
        assertEquals(61.0, tunnel.t1S - tunnel.t0S, 0.01)
        assertEquals(1443.9, r.trip.estimatedDistanceM!!, 1.0, "the tunnel chord counts as estimated distance")
        assertEquals(4679.0, r.trip.distanceM!!, 1.0)
        assertEquals(99, r.score?.smoothness)
        assertEquals(q.totalS, q.scoredS, "a tunnel with good IMU is scored (EC-TUN)")
    }

    @Test
    fun theSameCaptureFinalisesIdenticallyTwice() = runTest {
        val a = harness.trip(DriveFixtures.city.capture())
        val b = harness.trip(DriveFixtures.city.capture())
        assertEquals(a.trip, b.trip)
        assertEquals(a.score?.smoothnessRaw, b.score?.smoothnessRaw)
        assertEquals(a.events.map { it.tPeakS to it.peakG }, b.events.map { it.tPeakS to it.peakG })
        assertEquals(a.score?.tip?.tipId, b.score?.tip?.tipId)
        assertEquals(a.preview, b.preview)
    }
}
