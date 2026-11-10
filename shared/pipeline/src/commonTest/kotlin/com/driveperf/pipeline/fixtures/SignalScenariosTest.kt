package com.driveperf.pipeline.fixtures

import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.CaptureHeader
import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.capture.WakeKind
import com.driveperf.domain.trip.InterruptionReason
import com.driveperf.domain.trip.Placement
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.QualityReason
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.SegmentReason
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripStatus
import com.driveperf.pipeline.score.QualityBits
import com.driveperf.pipeline.signal.SignalPipeline
import com.driveperf.testing.SyntheticCapture
import com.driveperf.testing.SyntheticCapture.Mount
import com.driveperf.domain.capture.Vec3
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §8.3 further scenarios G-12, G-13, G-14, G-17, G-18, G-19 and G-21 on synthetic R0 captures. */
class SignalScenariosTest {
    private val harness = PipelineHarness()

    private fun capture(streams: SyntheticCapture.Streams, wake: WakeKind = WakeKind.MANUAL, parked: Boolean = false, id: String = "x") =
        CapturedTrip(
            CaptureHeader(
                id, SyntheticCapture.START_MONO_NS, SyntheticCapture.WALL0, 0, "Etc/UTC", wake, null, 50,
                if (parked) DriveFixtures.cityParking else null,
            ),
            streams.fixes, streams.motion, streams.activity, emptyList(), emptyList(),
        )

    /** A short loop with varied longitudinal and lateral excitation, for alignment. */
    private fun loop(mount: Mount, imuHz: Int = 50, seed: Long = 5): SyntheticCapture =
        SyntheticCapture(mount, imuHz = imuHz, seed = seed).apply {
        cruise(3.0)
        repeat(3) {
            speedTo(13.0, 1.6, 1.5)
            cruise(10.0)
            speedTo(8.0, 1.5, 1.5)
            turn(90.0, 9.0)
            speedTo(13.0, 1.4, 1.5)
            cruise(10.0)
            stop(1.8, 1.5, 8.0)
        }
    }

    @Test
    fun g12AlignmentRecoversEveryMount() = runTest {
        val mounts = mapOf(
            "flat" to Mount.FLAT,
            "portrait" to Mount.PORTRAIT_UPRIGHT,
            "tilt30" to Mount.rotated(Mount.PORTRAIT_UPRIGHT, 0.0, 30.0),
            "upsideDown" to Mount(Vec3(0.0, 0.0, -1.0), Vec3(-1.0, 0.0, 0.0), Vec3(0.0, -1.0, 0.0)),
            "yaw180" to Mount.rotated(Mount.FLAT, 180.0, 0.0),
            "yaw37tilt12" to Mount.rotated(Mount.FLAT, 37.0, 12.0),
        )
        for ((name, mount) in mounts) {
            val streams = loop(mount).streams()
            val sig = SignalPipeline().process(capture(streams), null)
            assertTrue(sig.anyEpochConverged, "$name converges")
            var se = 0.0
            var n = 0
            for (k in 0 until sig.trace.size) {
                if (!sig.trace.ok(k) || 2 * k + 1 >= streams.lonTruthG.size) continue
                val lon = (streams.lonTruthG[2 * k] + streams.lonTruthG[2 * k + 1]) / 2
                val lat = (streams.latTruthG[2 * k] + streams.latTruthG[2 * k + 1]) / 2
                se += (sig.trace.lonG[k] - lon) * (sig.trace.lonG[k] - lon) + (sig.trace.latG[k] - lat) * (sig.trace.latG[k] - lat)
                n += 2
            }
            val rms = sqrt(se / n)
            assertTrue(rms <= 0.03, "$name RMS ${'$'}rms g (noise 0.03 g uniform included)")
        }
    }

    @Test
    fun g13ALiftedPhoneIsExcludedAndStartsANewEpoch() = runTest {
        val c = loop(Mount.PORTRAIT_UPRIGHT)
        c.speedTo(13.0, 1.6, 1.5)
        c.cruise(5.0)
        c.phoneMoved(40.0, 2.0)
        c.cruise(5.0)
        c.speedTo(8.0, 1.5, 1.5)
        c.turn(-90.0, 9.0)
        repeat(2) {
            c.speedTo(13.0, 1.6, 1.5)
            c.cruise(10.0)
            c.turn(90.0, 9.0)
            c.stop(1.8, 1.5, 6.0)
        }
        val r = harness.trip(capture(c.streams()))
        val q = assertNotNull(r.trip.quality)
        val moved = q.spans.filter { it.reason == SegmentReason.PHONE_MOVED }
        assertEquals(1, moved.size)
        assertEquals(Placement.CUPHOLDER, q.placement)
        val span = moved.single()
        assertTrue(r.events.none { it.tPeakS >= span.t0S && it.tPeakS < span.t1S }, "no events inside the span")
    }

    @Test
    fun g14AndG21MostlyExcludedIsUnscorableAnd10HzIsExcluded() = runTest {
        val r = harness.trip(capture(loop(Mount.FLAT, imuHz = 10).streams()))
        val q = assertNotNull(r.trip.quality)
        assertEquals(QualityBadge.UNSCORABLE, q.badge)
        assertEquals(QualityReason.LOW_SCORED_SHARE, q.reason)
        assertTrue(q.spans.any { it.reason == SegmentReason.LOW_IMU_RATE })
        assertNull(r.score)
    }

    @Test
    fun g21TheSameDriveDecimatedTo25HzScoresWithinOnePoint() = runTest {
        for (fixture in DriveFixtures.all) {
            val s = fixture.streams
            val decimated = SyntheticCapture.Streams(
                s.fixes, s.motion.filterIndexed { i, _ -> i % 2 == 0 }, s.activity, s.lonTruthG, s.latTruthG,
            )
            val at50 = harness.trip(capture(s)).score?.smoothness
            val at25 = harness.trip(capture(decimated)).score?.smoothness
            assertNotNull(at50)
            assertNotNull(at25)
            assertTrue(abs(at50 - at25) <= 1, "${fixture.id}: 50 Hz $at50 vs 25 Hz $at25")
        }
    }

    @Test
    fun g21ASeparately25HzDriveStillScores() = runTest {
        val at50 = harness.trip(capture(loop(Mount.FLAT, imuHz = 50).streams())).score?.smoothness
        val at25 = harness.trip(capture(loop(Mount.FLAT, imuHz = 25).streams())).score?.smoothness
        assertNotNull(at50)
        assertNotNull(at25)
        assertTrue(abs(at50 - at25) <= 3, "different noise draws: 50 Hz $at50 vs 25 Hz $at25")
    }

    @Test
    fun g17EventsInATunnelAreListedButNotPinned() = runTest {
        val c = loop(Mount.FLAT)
        c.speedTo(14.0, 1.6, 1.5)
        c.cruise(20.0)
        c.tunnel(60.0) {
            cruise(20.0)
            speedTo(6.0, 4.0, 8.0)
            speedTo(14.0, 1.5, 1.5)
            cruise(20.0)
        }
        c.cruise(20.0)
        c.stop(1.5, 1.2, 5.0)
        val r = harness.trip(capture(c.streams()))
        val gap = r.trip.quality!!.spans.single { it.kind == SegmentKind.GAP }
        assertEquals(SegmentReason.TUNNEL, gap.reason)
        val inTunnel = r.events.filter { it.tPeakS > gap.t0S + 3 && it.tPeakS < gap.t1S - 3 }
        assertTrue(inTunnel.isNotEmpty(), "the hard brake in the tunnel is detected")
        assertTrue(inTunnel.all { it.lat == null && it.lng == null })
        assertNotNull(r.score)
    }

    @Test
    fun g18ALongGnssLossIsALabelledGapWithNoDistance() = runTest {
        val c = loop(Mount.FLAT)
        c.speedTo(14.0, 1.6, 1.5)
        c.tunnel(200.0)
        c.stop(1.5, 1.2, 5.0)
        val r = harness.trip(capture(c.streams()))
        val gap = r.trip.quality!!.spans.single { it.kind == SegmentKind.GAP }
        assertEquals(SegmentReason.NO_GNSS, gap.reason)
        assertEquals("gap.labelled", gap.labelKey)
        assertEquals(0.0, r.trip.estimatedDistanceM!!, 0.01)
    }

    @Test
    fun g19AnEstimatedStartIsFlaggedAndNeverScored() = runTest {
        val streams = DriveFixtures.city.streams
        val withHistory = SyntheticCapture.Streams(
            streams.fixes, streams.motion,
            streams.activity + SyntheticCapture(seed = 1).apply { activity(ActivityType.AUTOMOTIVE, -40.0) }.streams().activity,
            streams.lonTruthG, streams.latTruthG,
        )
        val r = harness.trip(capture(withHistory, WakeKind.PARKED_EXIT, parked = true), manual = false)
        val q = assertNotNull(r.trip.quality)
        assertTrue(r.trip.startEstimated)
        assertEquals(SyntheticCapture.WALL0 - 40_000, r.trip.startedAt)
        val span = q.spans.single { it.kind == SegmentKind.ESTIMATED }
        assertEquals(SegmentReason.ESTIMATED_START, span.reason)
        assertEquals(0.0, span.t0S)
        assertEquals(150.0, q.estimatedStartM!!, 1.0)
        assertTrue(r.events.none { it.tPeakS < span.t1S })
    }

    @Test
    fun aRunStartingAtTheHistoryWindowEdgeIsNotAMotionStart() = runTest {
        val streams = DriveFixtures.city.streams
        val edge = SyntheticCapture(seed = 1).apply { activity(ActivityType.AUTOMOTIVE, -900.0) }.streams().activity
        val r = harness.trip(
            capture(
                SyntheticCapture.Streams(streams.fixes, streams.motion, edge, streams.lonTruthG, streams.latTruthG),
                WakeKind.PARKED_EXIT,
                parked = true,
            ),
            manual = false,
        )
        assertTrue(!r.trip.startEstimated, "Spike 1 change 3: the window edge is not the motion start")
    }

    @Test
    fun shortTripsFollowSection16() = runTest {
        val short = SyntheticCapture().apply { speedTo(8.0, 1.5, 1.5); cruise(30.0); stop(1.5, 1.2, 5.0) }.streams()
        assertNull(harness.run(capture(short, WakeKind.PARKED_EXIT), manual = false).trip, "auto < 500 m is discarded")
        val kept = harness.trip(capture(short), manual = true)
        assertNull(kept.score, "a manual short trip is kept without a score")
        val interrupted = harness.run(
            capture(short, WakeKind.PARKED_EXIT), manual = false, interruption = InterruptionReason.APP_KILLED,
        ).trip
        assertEquals(TripStatus.INTERRUPTED, interrupted?.trip?.status)
    }

    @Test
    fun passengersAreNeverScored() = runTest {
        val r = harness.trip(DriveFixtures.city.capture(), kind = TripKind.PASSENGER)
        assertNull(r.score)
        assertEquals(TripKind.PASSENGER, r.trip.kind)
    }

    @Test
    fun noImuOrNoFixesIsUnscorable() = runTest {
        val s = DriveFixtures.highway.streams
        val noImu = harness.trip(capture(SyntheticCapture.Streams(s.fixes, emptyList(), s.activity, s.lonTruthG, s.latTruthG)))
        assertEquals(QualityReason.NO_IMU, noImu.trip.quality?.reason)
        val sig = SignalPipeline().process(capture(s), null)
        assertTrue((0 until sig.trace.size).count { sig.trace.quality[it] and QualityBits.IMU_GAP != 0 } < 5)
    }
}
