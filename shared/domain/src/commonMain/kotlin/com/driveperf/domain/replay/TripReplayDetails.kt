package com.driveperf.domain.replay

import com.driveperf.domain.capture.PreviewStore
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.trip.Trip

/**
 * [ReplayDetailsRepository] over what a finished trip already stores: its events and the user's feedback, its stops,
 * its max speed and the preview track's elevation (data-model.md §1.7, §1.9, §2.5). Nothing is recomputed from R1.
 *
 * - **Events:** one marker per event ([EventMarkers.build]). An event the user reported as inaccurate (F-RPL-04 AC4)
 *   is left out entirely, pin and tick, because the user said it did not happen. A "Needed" event stays, hollow.
 * - **Max speed:** the trip's own, which the pipeline takes from gated fixes only (F-RPL-02 AC5, F-DQ-03). It is
 *   pinned where the replay's marker is at that moment; a trip with no stored time falls back to the fastest
 *   measured point of the route.
 * - **Elevation:** only when the preview track has at least two altitudes.
 */
public class TripReplayDetails(
    private val trips: TripRepository,
    private val previews: PreviewStore,
) : ReplayDetailsRepository {
    override suspend fun details(tripId: String, replay: Replay): ReplayDetails? {
        val trip = trips.trip(tripId) ?: return null
        val feedback = trips.feedback(tripId)
        val events = trips.events(tripId).filter { feedback[it.id]?.inaccurate == null }
        val markers = EventMarkers.build(events, feedback, trip.quality?.spans.orEmpty())
        return ReplayDetails(
            markers = markers,
            events = events.associateBy { it.id },
            maxSpeed = maxSpeed(trip, replay),
            stops = trip.stops.sortedBy { it.atS }.mapIndexed { i, s -> StopPin(i + 1, s.atS, s.durationS, s.lat, s.lng) },
            elevation = elevation(trip),
            distance = ReplayDistance(replay.track, trip.distanceM),
            startedAtMs = trip.startedAt,
        )
    }

    private suspend fun elevation(trip: Trip): ElevationProfile? {
        val points = previews.read(trip.id).orEmpty().mapNotNull { row -> row.elevM?.let { ElevationPoint(row.tS, it) } }
        if (points.size < 2) return null
        return ElevationProfile(points.sortedBy { it.tS }, trip.elevGainM ?: 0.0, trip.elevLossM ?: 0.0)
    }

    private fun maxSpeed(trip: Trip, replay: Replay): MaxSpeedPin? = storedMaxSpeed(trip, replay) ?: fastestPoint(trip, replay)

    /** The trip's own max speed, where the marker is at that moment; null without a place for it. */
    private fun storedMaxSpeed(trip: Trip, replay: Replay): MaxSpeedPin? {
        val speed = trip.maxSpeedMps?.takeIf { it > 0 }
        val at = trip.maxSpeedAtS
        val pin = if (speed != null && at != null) replay.frameAt(at).let { MaxSpeedPin(at, speed, it.lat, it.lng) to it.style } else null
        return pin?.takeIf { it.second != SegmentStyle.NO_GPS }?.first
    }

    /** The fastest measured point of the route, carrying the trip's own number when it has one. */
    private fun fastestPoint(trip: Trip, replay: Replay): MaxSpeedPin? {
        val speed = trip.maxSpeedMps
        val fastest = replay.track
            .filter { p -> p.speedMps != null && replay.spanAt(p.tS)?.style.let { it == null || it == SegmentStyle.EXCLUDED } }
            .maxByOrNull { it.speedMps ?: 0.0 }
        val top = fastest?.speedMps?.takeIf { it > 0 } ?: return null
        return MaxSpeedPin(fastest.tS, speed ?: top, fastest.lat, fastest.lng)
    }
}
