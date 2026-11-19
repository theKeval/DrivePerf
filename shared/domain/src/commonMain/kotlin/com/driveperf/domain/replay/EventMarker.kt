package com.driveperf.domain.replay

import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.TripSegment

/**
 * One detected event as Replay and Analysis draw it: a map pin, a timeline tick and an Analysis marker at the same
 * times (F-RPL-03 AC3). Times are seconds from the trip start.
 *
 * @property needed the user marked it "Needed" (F-SCO-05): drawn hollow with a "Needed" label, not counted (AC4).
 * @property pinned it gets a map pin: it has a position and its peak is in measured time (AC5, EC-GPS). An unpinned
 *   event is still listed and still ticks the timeline.
 */
public data class EventMarker(
    val eventId: String,
    val type: EventType,
    val tStartS: Double,
    val tPeakS: Double,
    val tEndS: Double,
    val lat: Double?,
    val lng: Double?,
    val needed: Boolean,
    val pinned: Boolean,
)

/** Builds [EventMarker]s from the stored events, the user's feedback and the trip's quality spans. */
public object EventMarkers {
    /**
     * One marker per event in [events], ordered by peak time (ties by ID). [feedback] is keyed by event ID. [spans] are
     * the trip's quality spans (`TripQuality.spans`): estimated, GPS-gap, excluded, paused and interrupted time, each
     * half-open `[t0S, t1S)`. An event whose peak falls in any of them gets no pin.
     */
    public fun build(
        events: List<DrivingEvent>,
        feedback: Map<String, EventFeedback>,
        spans: List<TripSegment>,
    ): List<EventMarker> = events
        .sortedWith(compareBy<DrivingEvent> { it.tPeakS }.thenBy { it.id })
        .map { e ->
            val inSpan = spans.any { e.tPeakS >= it.t0S && e.tPeakS < it.t1S }
            EventMarker(
                eventId = e.id,
                type = e.type,
                tStartS = e.tStartS,
                tPeakS = e.tPeakS,
                tEndS = e.tEndS,
                lat = e.lat,
                lng = e.lng,
                needed = feedback[e.id]?.needed == true,
                pinned = e.lat != null && e.lng != null && !inSpan,
            )
        }
}
