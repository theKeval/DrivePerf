package com.driveperf.domain.replay

import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.InaccurateReason
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.SegmentReason
import com.driveperf.domain.trip.TripSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventMarkersTest {
    private fun event(id: String, peak: Double, type: EventType = EventType.BRAKE, lat: Double? = 43.0, lng: Double? = -79.0) =
        DrivingEvent(
            id = id, tripId = "t", type = type, tPeakS = peak, tStartS = peak - 1.0, tEndS = peak + 1.0, durationS = 2.0,
            peakG = 0.4, direction = null, lat = lat, lng = lng, speedBeforeMps = null, speedAfterMps = null,
            confidence = Confidence.HIGH, algorithmVersion = "v1",
        )

    private fun feedback(id: String, needed: Boolean, inaccurate: InaccurateReason? = null) =
        EventFeedback(eventId = id, tripId = "t", needed = needed, inaccurate = inaccurate, at = 0L)

    @Test
    fun `markers carry the event's type and times, ordered by peak then ID`() {
        val markers = EventMarkers.build(
            listOf(event("c", 30.0, EventType.CORNER), event("b", 10.0), event("a", 10.0, EventType.ACCEL)),
            emptyMap(),
            emptyList(),
        )
        assertEquals(listOf("a", "b", "c"), markers.map { it.eventId })
        assertEquals(
            EventMarker("c", EventType.CORNER, 29.0, 30.0, 31.0, 43.0, -79.0, needed = false, pinned = true),
            markers.last(),
        )
    }

    @Test
    fun `needed comes from the feedback and an inaccurate flag alone is not needed`() {
        val markers = EventMarkers.build(
            listOf(event("a", 1.0), event("b", 2.0), event("c", 3.0)),
            mapOf("a" to feedback("a", needed = true), "b" to feedback("b", needed = false, InaccurateReason.POTHOLE)),
            emptyList(),
        )
        assertEquals(listOf(true, false, false), markers.map { it.needed })
        assertTrue(markers.all { it.pinned }, "feedback never removes a pin")
    }

    @Test
    fun `no pin without a position (EC-GPS)`() {
        val markers = EventMarkers.build(
            listOf(event("a", 1.0, lat = null), event("b", 2.0, lng = null), event("c", 3.0, lat = null, lng = null)),
            emptyMap(),
            emptyList(),
        )
        assertTrue(markers.none { it.pinned })
        assertEquals(3, markers.size, "still listed")
    }

    @Test
    fun `no pin when the peak is inside an estimated or excluded span, start inclusive and end exclusive`() {
        val spans = listOf(
            TripSegment(SegmentKind.ESTIMATED, 0.0, 10.0, null),
            TripSegment(SegmentKind.GAP, 20.0, 30.0, SegmentReason.TUNNEL),
            TripSegment(SegmentKind.EXCLUDED, 40.0, 50.0, SegmentReason.PHONE_MOVED),
        )
        val markers = EventMarkers.build(
            listOf(event("a", 5.0), event("b", 10.0), event("c", 20.0), event("d", 29.9), event("e", 45.0), event("f", 50.0)),
            emptyMap(),
            spans,
        )
        assertEquals(listOf(false, true, false, false, false, true), markers.map { it.pinned })
    }

    @Test
    fun `no events make no markers`() {
        assertFalse(EventMarkers.build(emptyList(), emptyMap(), emptyList()).any())
    }
}
