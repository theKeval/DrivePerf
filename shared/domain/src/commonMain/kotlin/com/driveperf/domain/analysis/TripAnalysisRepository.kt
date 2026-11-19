package com.driveperf.domain.analysis

import com.driveperf.domain.capture.PreviewStore
import com.driveperf.domain.replay.EventMarker
import com.driveperf.domain.replay.EventMarkers
import com.driveperf.domain.replay.Replay
import com.driveperf.domain.replay.ReplayBuilder
import com.driveperf.domain.replay.ReplayRepository
import com.driveperf.domain.replay.ReplaySpan
import com.driveperf.domain.replay.SegmentStyle
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.score.Score
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.RawState
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.tripfile.AnalysisFileStore

/**
 * Everything S-ANL-01 and S-ANL-02 draw for one finished trip. [replay] is the map (route, marker, quality spans); it
 * is null only when the trip has no position at all, and then the map card says so.
 */
public class TripAnalysis(
    public val trip: Trip,
    public val replay: Replay?,
    public val data: AnalysisData,
    public val markers: List<EventMarker>,
    public val events: List<DrivingEvent>,
    public val score: Score?,
)

/** Analysis of one trip, or null when the trip is missing, still recording or discarded. */
public fun interface AnalysisRepository {
    public suspend fun analysis(tripId: String): TripAnalysis?
}

/**
 * [AnalysisRepository] over what a finalised trip stores: the trip row, its R1 file ([AnalysisFileStore]), its preview
 * track, its Replay (map and quality spans) and its events with the user's feedback.
 */
public class TripAnalysisRepository(
    private val trips: TripRepository,
    private val replays: ReplayRepository,
    private val analysisFiles: AnalysisFileStore,
    private val previews: PreviewStore,
) : AnalysisRepository {
    override suspend fun analysis(tripId: String): TripAnalysis? {
        val trip = trips.trip(tripId)?.takeIf { it.status == TripStatus.FINALISED || it.status == TripStatus.INTERRUPTED }
            ?: return null
        val replay = replays.replay(tripId)
        val trace = if (trip.rawState == RawState.SUMMARY_ONLY) null else analysisFiles.read(tripId)
        val preview = previews.read(tripId).orEmpty()
        val duration = maxOf(trip.durationS ?: 0.0, replay?.durationS ?: 0.0, trace?.let { it.size.toDouble() / it.rateHz } ?: 0.0)
        val data = AnalysisDataBuilder.build(duration, trip.distanceM, trace, preview, replay?.spans ?: qualitySpans(trip, duration))
        // Events reported "Not accurate?" leave the ticks, the map, the G-G and its peaks, as they leave Replay's pins.
        val feedback = trips.feedback(tripId)
        val events = trips.events(tripId).filter { feedback[it.id]?.inaccurate == null }
        val markers = EventMarkers.build(events, feedback, trip.quality?.spans.orEmpty())
        return TripAnalysis(trip, replay, data, markers, events, trips.score(tripId))
    }

    /**
     * The trip's own quality spans, styled and resolved as Replay does them. Used when there is no Replay (a trip with
     * motion data but no position), so the hatching, the span chips and the G-G exclusion banner still show.
     */
    private fun qualitySpans(trip: Trip, duration: Double): List<ReplaySpan> {
        val raw = trip.quality?.spans.orEmpty().mapNotNull { s ->
            val style = when (s.kind) {
                SegmentKind.ESTIMATED -> SegmentStyle.ESTIMATED
                SegmentKind.GAP, SegmentKind.INTERRUPTED -> SegmentStyle.NO_GPS
                SegmentKind.EXCLUDED -> SegmentStyle.EXCLUDED
                SegmentKind.PAUSED -> null
            }
            style?.let { st ->
                val reason = s.reason.takeIf { st == SegmentStyle.EXCLUDED }
                ReplaySpan(st, s.t0S.coerceIn(0.0, duration), s.t1S.coerceIn(0.0, duration), reason)
            }
        }
        return ReplayBuilder.resolve(raw, duration)
    }
}
