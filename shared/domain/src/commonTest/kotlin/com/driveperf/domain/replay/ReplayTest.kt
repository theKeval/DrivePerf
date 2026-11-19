package com.driveperf.domain.replay

import com.driveperf.domain.replay.ReplayFixtures.STEP_DEG
import com.driveperf.domain.replay.ReplayFixtures.northbound
import com.driveperf.domain.replay.ReplayFixtures.parking
import com.driveperf.domain.replay.ReplayFixtures.span
import com.driveperf.domain.replay.ReplayFixtures.trip
import com.driveperf.domain.trip.BoundingBox
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.SegmentReason
import com.driveperf.domain.trip.TripSource
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReplayTest {
    @Suppress("LongParameterList") // one knob per input the tests vary
    private fun build(
        fixes: List<ReplayPoint>,
        spans: List<com.driveperf.domain.trip.TripSegment> = emptyList(),
        parkingSpot: ReplayPoint? = null,
        durationS: Double? = null,
        source: TripSource = TripSource.AUTO,
        summaryOnly: Boolean = false,
    ): Replay = assertNotNull(ReplayBuilder.build(trip(spans, durationS, source = source), parkingSpot, fixes, summaryOnly))

    private fun assertNear(expected: Double, actual: Double?, tolerance: Double = 1e-9) {
        assertNotNull(actual)
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected, got $actual")
    }

    @Test
    fun anEstimatedStartIsADashedLineFromTheParkingSpotToTheFirstFix() {
        val replay = build(
            northbound(60, 180),
            spans = listOf(span(SegmentKind.ESTIMATED, 0.0, 60.0, SegmentReason.ESTIMATED_START)),
            parkingSpot = parking,
            durationS = 180.0,
        )
        assertEquals(listOf(ReplaySpan(SegmentStyle.ESTIMATED, 0.0, 60.0)), replay.spans)
        val segments = replay.segments(16)
        assertEquals(listOf(SegmentStyle.ESTIMATED, SegmentStyle.OBSERVED), segments.map { it.style })
        assertEquals(listOf(parking, replay.track[1]), segments[0].points)
        assertEquals(segments[0].points.last(), segments[1].points.first(), "the line stays continuous")

        val halfway = replay.frameAt(30.0)
        assertEquals(SegmentStyle.ESTIMATED, halfway.style)
        assertNear((parking.lat + 60 * STEP_DEG) / 2, halfway.lat)
        assertNull(halfway.speedMps, "the estimated start has no measured speed")
        assertNotNull(halfway.bearingDeg)
        assertEquals("Estimated start", replay.spanAt(30.0)?.label)
        assertNull(replay.spanAt(90.0))
        assertEquals(SegmentStyle.OBSERVED, replay.frameAt(90.0).style)
    }

    @Test
    fun theBoundingBoxGrowsToTheParkingSpot() {
        val stored = BoundingBox(60 * STEP_DEG, 0.0, 180 * STEP_DEG, 0.0)
        val replay = assertNotNull(
            ReplayBuilder.build(
                trip(listOf(span(SegmentKind.ESTIMATED, 0.0, 60.0)), 180.0, bbox = stored),
                parking,
                northbound(60, 180),
                summaryOnly = false,
            ),
        )
        assertEquals(BoundingBox(parking.lat, parking.lng, 180 * STEP_DEG, 0.0), replay.bbox)
    }

    @Test
    fun aTunnelIsABrokenStretchTheMarkerJumps() {
        val fixes = northbound(0, 100) + northbound(140, 200)
        val replay = build(fixes, spans = listOf(span(SegmentKind.GAP, 100.0, 140.0, SegmentReason.TUNNEL)), durationS = 200.0)
        assertEquals(listOf(ReplaySpan(SegmentStyle.NO_GPS, 100.0, 140.0)), replay.spans)
        val segments = replay.segments(16)
        assertEquals(listOf(SegmentStyle.OBSERVED, SegmentStyle.NO_GPS, SegmentStyle.OBSERVED), segments.map { it.style })
        assertEquals(listOf(100.0, 140.0), segments[1].points.map { it.tS })

        val inside = replay.frameAt(120.0)
        assertEquals(SegmentStyle.NO_GPS, inside.style)
        assertNear(100 * STEP_DEG, inside.lat)
        assertNull(inside.speedMps)
        assertNull(inside.bearingDeg)
        assertEquals("No GPS", replay.spanAt(120.0)?.label)

        val after = replay.frameAt(140.0)
        assertEquals(SegmentStyle.OBSERVED, after.style)
        assertNear(140 * STEP_DEG, after.lat)
        assertNear(0.0, after.bearingDeg, 1e-6)
    }

    @Test
    fun aLongWaitBetweenFixesIsAGapEvenWithoutASpanButAShortOneIsNot() {
        val replay = build(northbound(0, 50) + northbound(58, 80) + northbound(95, 120), durationS = 120.0)
        assertEquals(listOf(ReplaySpan(SegmentStyle.NO_GPS, 80.0, 95.0)), replay.spans)
        assertEquals(SegmentStyle.OBSERVED, replay.frameAt(54.0).style, "8 s is bridged")
        assertNear(54 * STEP_DEG, replay.frameAt(54.0).lat, 1e-12)
    }

    @Test
    fun anExcludedSpanIsItsOwnSegmentWithSharedBoundaries() {
        val replay = build(
            northbound(0, 120),
            spans = listOf(
                span(SegmentKind.EXCLUDED, 40.0, 60.0, SegmentReason.PHONE_MOVED),
                span(SegmentKind.EXCLUDED, 80.0, 90.0, SegmentReason.NOT_ALIGNED),
            ),
            durationS = 120.0,
        )
        assertEquals(
            listOf(
                ReplaySpan(SegmentStyle.EXCLUDED, 40.0, 60.0, SegmentReason.PHONE_MOVED),
                ReplaySpan(SegmentStyle.EXCLUDED, 80.0, 90.0, SegmentReason.NOT_ALIGNED),
            ),
            replay.spans,
        )
        val segments = replay.segments(16)
        assertEquals(
            listOf(SegmentStyle.OBSERVED, SegmentStyle.EXCLUDED, SegmentStyle.OBSERVED, SegmentStyle.EXCLUDED, SegmentStyle.OBSERVED),
            segments.map { it.style },
        )
        segments.zipWithNext().forEach { (a, b) -> assertEquals(a.points.last(), b.points.first()) }
        assertEquals("Phone moved — not scored", replay.spanAt(50.0)?.label)
        assertEquals("Not scored", replay.spanAt(85.0)?.label)
        assertEquals(SegmentStyle.EXCLUDED, replay.frameAt(50.0).style)
        assertEquals(11.0, replay.frameAt(50.0).speedMps, "excluded time is still measured")
    }

    @Test
    fun aManualTripWithoutAnEstimateIsAllObserved() {
        val replay = build(northbound(2, 100), parkingSpot = parking, durationS = 100.0, source = TripSource.MANUAL)
        assertTrue(replay.spans.isEmpty())
        assertEquals(2.0, replay.track.first().tS, "no estimate, so the parking spot is not drawn")
        assertEquals(listOf(SegmentStyle.OBSERVED), replay.segments(16).map { it.style })
        val start = replay.frameAt(0.0)
        assertNear(2 * STEP_DEG, start.lat)
        assertEquals(SegmentStyle.OBSERVED, start.style)
    }

    @Test
    fun waitsBeforeTheFirstFixAndAfterTheLastAreGaps() {
        val replay = build(northbound(30, 100), durationS = 160.0)
        assertEquals(
            listOf(ReplaySpan(SegmentStyle.NO_GPS, 0.0, 30.0), ReplaySpan(SegmentStyle.NO_GPS, 100.0, 160.0)),
            replay.spans,
        )
        val before = replay.frameAt(10.0)
        assertEquals(SegmentStyle.NO_GPS, before.style)
        assertNull(before.speedMps)
        assertNull(before.bearingDeg)
        val end = replay.frameAt(160.0)
        assertEquals(SegmentStyle.NO_GPS, end.style)
        assertNear(100 * STEP_DEG, end.lat)
        assertEquals("No GPS", replay.spanAt(160.0)?.label, "the last instant belongs to the span ending at the trip's end")
    }

    @Test
    fun theMarkerFrameIsClampedToTheTrip() {
        val replay = build(northbound(0, 60), durationS = 60.0)
        assertEquals(0.0, replay.frameAt(-5.0).tS)
        assertEquals(60.0, replay.frameAt(1e9).tS)
        assertEquals(0.0, replay.frameAt(Double.NaN).tS)
        val last = replay.frameAt(60.0)
        assertNear(60 * STEP_DEG, last.lat)
        assertEquals(11.0, last.speedMps)
        assertNear(0.0, last.bearingDeg, 1e-6)
    }

    @Test
    fun speedIsInterpolatedAndBearingFollowsTheRoad() {
        val fixes = listOf(
            ReplayPoint(0.0, 0.0, 0.0, 0.0),
            ReplayPoint(1.0, 0.0, 0.0, 0.0), // still: no bearing of its own
            ReplayPoint(2.0, 0.0, STEP_DEG, 10.0), // east
            ReplayPoint(3.0, 0.0, STEP_DEG, 10.0), // still again: keeps east
            ReplayPoint(4.0, 0.0, STEP_DEG, null),
        )
        val replay = build(fixes, durationS = 4.0)
        assertNear(90.0, replay.frameAt(0.5).bearingDeg, 1e-6) // faces the way it will leave
        assertNear(5.0, replay.frameAt(1.5).speedMps)
        assertNear(90.0, replay.frameAt(2.5).bearingDeg, 1e-6)
        assertNull(replay.frameAt(3.5).speedMps, "no speed to interpolate towards")
    }

    @Test
    fun aSinglePointReplayStillDrawsAndPlays() {
        val replay = build(listOf(ReplayPoint(0.0, 1.0, 2.0, null)), durationS = 0.0)
        assertEquals(listOf(ReplaySegment(SegmentStyle.OBSERVED, replay.track)), replay.segments(10))
        assertEquals(1.0, replay.frameAt(3.0).lat)
        assertNull(replay.frameAt(0.0).bearingDeg)
        assertEquals(1.0, replay.playbackRate(PlaybackSpeed.AUTO), "a zero-length trip plays at 1×")
    }

    @Test
    fun playbackRates() {
        val replay = build(northbound(0, 1800), durationS = 1800.0)
        assertEquals(30.0, replay.playbackRate(PlaybackSpeed.AUTO), "the whole trip in 60 s")
        assertEquals(1.0, replay.playbackRate(PlaybackSpeed.X1))
        assertEquals(10.0, replay.playbackRate(PlaybackSpeed.X10))
        assertEquals(30.0, replay.playbackRate(PlaybackSpeed.X30))
    }

    @Test
    fun theDurationCoversTheLastFixWhenTheTripHasNone() {
        val replay = build(northbound(0, 90))
        assertEquals(90.0, replay.durationS)
        assertEquals(1.5, replay.playbackRate(PlaybackSpeed.AUTO))
    }

    @Test
    fun aSummaryTrackOnlyCountsWaitsLongComparedWithItsRowSpacing() {
        // Preview rows every 12 s, with one 40 s wait: 2.5 × 12 = 30 s is the gap threshold.
        val rows = (0..10).map { ReplayPoint(it * 12.0, it * STEP_DEG, 0.0, 11.0) } +
            (0..5).map { ReplayPoint(160.0 + it * 12.0, (20 + it) * STEP_DEG, 0.0, 11.0) }
        val replay = build(rows, durationS = 220.0, summaryOnly = true)
        assertTrue(replay.summaryOnly)
        assertEquals(listOf(ReplaySpan(SegmentStyle.NO_GPS, 120.0, 160.0)), replay.spans)
    }

    @Test
    fun aShortGapBetweenSparseRowsIsNeitherDrawnSolidNorInterpolated() {
        // Codex P2 on #76: summary rows at 108 s and 132 s around a recorded GAP at 110-114 s.
        val rows = (0..9).map { ReplayPoint(it * 12.0, it * STEP_DEG, 0.0, 11.0) } +
            (11..15).map { ReplayPoint(it * 12.0, it * STEP_DEG, 0.0, 11.0) }
        val replay = build(
            rows,
            spans = listOf(span(SegmentKind.GAP, 110.0, 114.0, SegmentReason.NO_GNSS), span(SegmentKind.EXCLUDED, 40.0, 42.0)),
            durationS = 180.0,
            summaryOnly = true,
        )
        val segments = replay.segments(16)
        assertEquals(
            listOf(SegmentStyle.OBSERVED, SegmentStyle.EXCLUDED, SegmentStyle.OBSERVED, SegmentStyle.NO_GPS, SegmentStyle.OBSERVED),
            segments.map { it.style },
        )
        assertEquals(listOf(108.0, 132.0), segments[3].points.map { it.tS }, "the stretch holding the gap is broken")
        assertEquals(listOf(36.0, 48.0), segments[1].points.map { it.tS }, "a short excluded span is not drawn solid")
        for (t in listOf(109.0, 112.0, 125.0)) {
            val frame = replay.frameAt(t)
            assertEquals(SegmentStyle.NO_GPS, frame.style, "t=$t")
            assertNear(9 * STEP_DEG, frame.lat)
            assertNull(frame.speedMps)
        }
        assertEquals("No GPS", replay.spanAt(112.0)?.label)
    }

    @Test
    fun theEstimatedStartNeverReportsTheFirstFixSpeed() {
        // Codex P2 on #76: an estimate whose preview has no parking row starts the track at the first fix.
        val replay = build(northbound(60, 120), spans = listOf(span(SegmentKind.ESTIMATED, 0.0, 60.0)), durationS = 120.0)
        val early = replay.frameAt(10.0)
        assertEquals(SegmentStyle.ESTIMATED, early.style)
        assertNull(early.speedMps)
        assertNear(60 * STEP_DEG, early.lat)
        assertEquals(11.0, replay.frameAt(60.0).speedMps, "measured from the first fix on")
    }

    @Test
    fun overlappingSpansKeepTheStrongerStyleAndEqualNeighboursMerge() {
        val resolved = ReplayBuilder.resolve(
            listOf(
                ReplaySpan(SegmentStyle.EXCLUDED, 0.0, 20.0, SegmentReason.PHONE_MOVED),
                ReplaySpan(SegmentStyle.ESTIMATED, 0.0, 10.0),
                ReplaySpan(SegmentStyle.NO_GPS, 5.0, 12.0),
                ReplaySpan(SegmentStyle.EXCLUDED, 20.0, 30.0, SegmentReason.PHONE_MOVED),
                ReplaySpan(SegmentStyle.EXCLUDED, 30.0, 40.0, SegmentReason.IMU_GAP),
                ReplaySpan(SegmentStyle.NO_GPS, 50.0, 50.0),
                ReplaySpan(SegmentStyle.NO_GPS, 90.0, 120.0),
            ),
            duration = 100.0,
        )
        assertEquals(
            listOf(
                ReplaySpan(SegmentStyle.ESTIMATED, 0.0, 10.0),
                ReplaySpan(SegmentStyle.NO_GPS, 10.0, 12.0),
                ReplaySpan(SegmentStyle.EXCLUDED, 12.0, 30.0, SegmentReason.PHONE_MOVED),
                ReplaySpan(SegmentStyle.EXCLUDED, 30.0, 40.0, SegmentReason.IMU_GAP),
                ReplaySpan(SegmentStyle.NO_GPS, 90.0, 100.0),
            ),
            resolved,
        )
    }

    @Test
    fun labelsFollowTheStyle() {
        assertNull(ReplaySpan(SegmentStyle.OBSERVED, 0.0, 1.0).label)
        assertEquals(ReplaySpan.LABEL_PHONE_MOVED, ReplaySpan(SegmentStyle.EXCLUDED, 0.0, 1.0).label, "reason unknown")
        assertEquals(ReplaySpan.LABEL_NOT_SCORED, ReplaySpan(SegmentStyle.EXCLUDED, 0.0, 1.0, SegmentReason.LOW_IMU_RATE).label)
    }

    @Test
    fun noPositionsMeansNoReplay() {
        assertNull(ReplayBuilder.build(trip(durationS = 60.0), null, emptyList(), summaryOnly = false))
    }

    @Test
    fun anEstimatedStartWithoutFixesIsTheParkingSpotThenNoGps() {
        val replay = assertNotNull(
            ReplayBuilder.build(trip(listOf(span(SegmentKind.ESTIMATED, 0.0, 30.0)), 90.0), parking, emptyList(), false),
        )
        assertEquals(listOf(parking), replay.track)
        assertEquals(
            listOf(ReplaySpan(SegmentStyle.ESTIMATED, 0.0, 30.0), ReplaySpan(SegmentStyle.NO_GPS, 30.0, 90.0)),
            replay.spans,
        )
    }

    @Test
    fun zoomBucketsForMapKitRegions() {
        assertEquals(0, Replay.zoomBucketFor(360.0, 256.0))
        assertEquals(15, Replay.zoomBucketFor(0.01, 400.0))
        assertEquals(Replay.MAX_ZOOM, Replay.zoomBucketFor(0.0, 400.0))
        assertEquals(Replay.MAX_ZOOM, Replay.zoomBucketFor(1e-9, 400.0))
        assertEquals(0, Replay.zoomBucketFor(1e6, 400.0))
    }
}
