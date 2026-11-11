package com.driveperf.domain.trip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DRIVE-170 (S-DQ-01 rows) and DRIVE-167 (gaps and estimated segments for drawing). */
class QualityDetailTest {
    private fun quality(placement: Placement = Placement.MOUNTED, vararg spans: TripSegment) = TripQuality(
        QualityBadge.USABLE, null, placement, 50.0, 97.0, 40.0, 429.0, 1_900.0, 80.0, 1_980.0, spans.toList(),
    )

    @Test
    fun phoneStateComesFromMovementThenPlacement() {
        assertEquals(PhoneState.MOUNTED, quality().phoneState)
        assertEquals(PhoneState.LOOSE, quality(Placement.CUPHOLDER).phoneState)
        assertEquals(PhoneState.LOOSE, quality(Placement.POCKET).phoneState)
        assertEquals(PhoneState.UNKNOWN, quality(Placement.UNKNOWN).phoneState)
        val moved = quality(Placement.MOUNTED, TripSegment(SegmentKind.EXCLUDED, 10.0, 70.0, SegmentReason.PHONE_MOVED))
        assertEquals(PhoneState.MOVED, moved.phoneState)
    }

    @Test
    fun leftOutTimeIsGroupedByReasonLongestFirstWithOverlapsCountedOnce() {
        val q = quality(
            Placement.MOUNTED,
            TripSegment(SegmentKind.EXCLUDED, 100.0, 160.0, SegmentReason.PHONE_MOVED),
            TripSegment(SegmentKind.EXCLUDED, 150.0, 200.0, SegmentReason.PHONE_MOVED),
            TripSegment(SegmentKind.EXCLUDED, 0.0, 40.0, SegmentReason.NOT_ALIGNED),
            TripSegment(SegmentKind.INTERRUPTED, 500.0, 520.0, null),
            TripSegment(SegmentKind.ESTIMATED, 0.0, 49.0, SegmentReason.ESTIMATED_START),
            TripSegment(SegmentKind.GAP, 300.0, 340.0, SegmentReason.NO_GNSS),
            TripSegment(SegmentKind.EXCLUDED, 600.0, 600.0, SegmentReason.LOW_IMU_RATE),
        )
        assertEquals(
            listOf(
                LeftOut(SegmentReason.PHONE_MOVED, 100.0),
                LeftOut(SegmentReason.NOT_ALIGNED, 40.0),
                LeftOut(SegmentReason.APP_KILLED, 20.0),
            ),
            q.leftOut(),
        )
    }

    @Test
    fun aRecoveredTripsAppKilledGapIsLeftOutTimeLikeItsOverlay() {
        // What Spans.fromMarks writes for a kill and relaunch (§1.7): a GAP with reason APP_KILLED, not INTERRUPTED.
        val q = quality(
            Placement.MOUNTED,
            TripSegment(SegmentKind.GAP, 900.0, 1_200.0, SegmentReason.APP_KILLED),
            TripSegment(SegmentKind.GAP, 300.0, 340.0, SegmentReason.TUNNEL, LONG_GAP_LABEL),
        )
        assertEquals(listOf(LeftOut(SegmentReason.APP_KILLED, 300.0)), q.leftOut())
        assertEquals(OverlayStyle.INTERRUPTED, q.overlays().single { it.reason == SegmentReason.APP_KILLED }.style)
    }

    @Test
    fun overlaysTellShortGapsFromLabelledGapsAndKeepInterruptionsAsGaps() {
        val q = quality(
            Placement.MOUNTED,
            TripSegment(SegmentKind.GAP, 1_899.0, 1_940.0, SegmentReason.TUNNEL),
            TripSegment(SegmentKind.ESTIMATED, 0.0, 49.0, SegmentReason.ESTIMATED_START),
            TripSegment(SegmentKind.GAP, 400.0, 640.0, SegmentReason.NO_GNSS, LONG_GAP_LABEL),
            TripSegment(SegmentKind.GAP, 900.0, 1_200.0, SegmentReason.APP_KILLED),
            TripSegment(SegmentKind.EXCLUDED, 100.0, 160.0, SegmentReason.PHONE_MOVED),
            TripSegment(SegmentKind.PAUSED, 1_300.0, 1_360.0, SegmentReason.USER_PAUSE),
            TripSegment(SegmentKind.INTERRUPTED, 1_500.0, 1_510.0, null),
        )
        val overlays = q.overlays()
        assertEquals(
            listOf(
                OverlayStyle.ESTIMATED_START, OverlayStyle.EXCLUDED, OverlayStyle.LONG_GAP, OverlayStyle.INTERRUPTED,
                OverlayStyle.PAUSED, OverlayStyle.INTERRUPTED, OverlayStyle.SHORT_GAP,
            ),
            overlays.map { it.style },
        )
        val byStyle = overlays.associateBy { it.style }
        assertEquals(Provenance.ESTIMATED, byStyle.getValue(OverlayStyle.ESTIMATED_START).provenance)
        assertEquals(Provenance.ESTIMATED, byStyle.getValue(OverlayStyle.SHORT_GAP).provenance)
        assertEquals(Provenance.OBSERVED, byStyle.getValue(OverlayStyle.EXCLUDED).provenance)
        assertNull(byStyle.getValue(OverlayStyle.LONG_GAP).provenance)
        assertNull(byStyle.getValue(OverlayStyle.INTERRUPTED).provenance)
        assertNull(byStyle.getValue(OverlayStyle.PAUSED).provenance)
        assertTrue(byStyle.getValue(OverlayStyle.INTERRUPTED).notScored)
        assertTrue(byStyle.getValue(OverlayStyle.ESTIMATED_START).notScored)
        assertFalse(byStyle.getValue(OverlayStyle.SHORT_GAP).notScored)
        assertFalse(byStyle.getValue(OverlayStyle.LONG_GAP).notScored)
    }
}
