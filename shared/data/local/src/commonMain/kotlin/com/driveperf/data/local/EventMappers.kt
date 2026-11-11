package com.driveperf.data.local

import com.driveperf.data.local.db.Driving_event
import com.driveperf.data.local.db.Event_feedback
import com.driveperf.data.local.db.Personal_best
import com.driveperf.data.local.db.Route_match
import com.driveperf.data.local.db.Trip_file
import com.driveperf.data.local.db.Trip_segment
import com.driveperf.domain.route.IneligibleReason
import com.driveperf.domain.route.MatchSource
import com.driveperf.domain.route.PersonalBest
import com.driveperf.domain.route.Route
import com.driveperf.domain.route.RouteMatch
import com.driveperf.domain.route.RoutePreset
import com.driveperf.domain.route.RouteState
import com.driveperf.domain.score.Calibrating
import com.driveperf.domain.score.Score
import com.driveperf.domain.score.ScoreBasis
import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.DriveContext
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.tripfile.TripFileRef
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.InaccurateReason
import com.driveperf.domain.trip.PartOfDay
import com.driveperf.domain.trip.RawState
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.SegmentReason
import com.driveperf.domain.trip.Stop
import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.SuspectAnswer
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripSegment
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.trip.TurnDirection
import com.driveperf.data.local.db.Route as RouteRow
import com.driveperf.data.local.db.Score as ScoreRow
import com.driveperf.data.local.db.Trip as TripRow

internal fun Trip_segment.toDomain(): TripSegment =
    TripSegment(SegmentKind.valueOf(kind), t0_s, t1_s, reason?.let { SegmentReason.valueOf(it) }, label_key)

internal fun TripSegment.toRow(tripId: String, seq: Int): Trip_segment =
    Trip_segment(tripId, seq.toLong(), kind.name, t0S, t1S, reason?.name, labelKey)

internal fun Driving_event.toDomain(): DrivingEvent = DrivingEvent(
    id, trip_id, EventType.valueOf(type), t_peak_s, t_start_s, t_end_s, duration_s, peak_g,
    direction?.let { TurnDirection.valueOf(it) }, lat, lng, speed_before_mps, speed_after_mps,
    Confidence.valueOf(confidence), algorithm_version,
)

internal fun DrivingEvent.toRow(): Driving_event = Driving_event(
    id, tripId, type.name, tPeakS, tStartS, tEndS, durationS, peakG, direction?.name, lat, lng, null, null,
    speedBeforeMps, speedAfterMps, confidence.name, algorithmVersion,
)

internal fun Event_feedback.toDomain(): EventFeedback =
    EventFeedback(event_id, trip_id, needed != 0L, inaccurate?.let { InaccurateReason.valueOf(it) }, at)

internal fun EventFeedback.toRow(): Event_feedback =
    Event_feedback(eventId, tripId, needed.toInt(), inaccurate?.name, at)


/** A `trip_file` row (data-model.md §1.17). Not uploaded yet, and R0 has no expiry (pipeline-and-scoring.md §0.3). */
internal fun TripFileRef.toRow(tripId: String): Trip_file =
    Trip_file(tripId, kind.name, formatVersion.toLong(), sha256, sizeBytes, localPath, null, null, null)
