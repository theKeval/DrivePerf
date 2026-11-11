package com.driveperf.data.local

import com.driveperf.data.local.db.Driving_event
import com.driveperf.data.local.db.Event_feedback
import com.driveperf.data.local.db.Personal_best
import com.driveperf.data.local.db.Route_match
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

// Row ↔ domain mapping. Enums are stored as their constant names; booleans as 0/1; JSON columns via columnJson.



internal fun TripRow.toDomain(spans: List<TripSegment>): Trip = Trip(
    id = id,
    status = TripStatus.valueOf(status),
    kind = enumOr(kind, TripKind.DRIVER),
    source = enumOr(source, TripSource.AUTO),
    sourceDetail = source_detail,
    vehicleId = vehicle_id,
    startedAt = started_at ?: created_at,
    startTzOffsetMin = start_tz_offset_min?.toInt() ?: 0,
    startEstimated = start_estimated != 0L,
    endedAt = ended_at,
    endTzOffsetMin = end_tz_offset_min?.toInt(),
    tzId = tz_id,
    partOfDay = enumOrNull<PartOfDay>(part_of_day),
    fromLabel = from_label,
    toLabel = to_label,
    labelOverride = label_override,
    context = enumOrNull<DriveContext>(context),
    distanceM = distance_m,
    estimatedDistanceM = estimated_distance_m,
    durationS = duration_s,
    movingS = moving_s,
    stoppedS = stopped_s,
    stopCount = stop_count?.toInt(),
    avgMovingSpeedMps = avg_moving_speed_mps,
    maxSpeedMps = max_speed_mps,
    maxSpeedAtS = max_speed_at_s,
    elevGainM = elev_gain_m,
    elevLossM = elev_loss_m,
    elevMinM = elev_min_m,
    elevMaxM = elev_max_m,
    bbox = bbox_json?.let { BboxJson.decode(it) },
    stops = decodeStops(stops_json),
    cleanest = decode<StretchJson>(cleanest_json)?.toDomain(),
    quality = decode<QualityJson>(quality_json)?.toDomain(spans),
    interrupted = decode<InterruptionJson>(interrupted_json)?.toDomain(),
    suspect = Suspect.valueOf(suspect),
    suspectAnswer = suspect_answer?.let { SuspectAnswer.valueOf(it) },
    captureMeta = decode<CaptureMetaJson>(capture_meta_json)?.toDomain(),
    pipelineVersion = pipeline_version,
    rawState = RawState.valueOf(raw_state),
    createdAt = created_at,
    updatedAt = updated_at,
    deletedAt = deleted_at,
)

internal fun Trip.toRow(deviceId: String): TripRow = TripRow(
    id = id,
    status = status.name,
    kind = kind.name,
    source = source.name,
    source_detail = sourceDetail,
    vehicle_id = vehicleId,
    started_at = startedAt,
    start_tz_offset_min = startTzOffsetMin.toLong(),
    start_estimated = startEstimated.toInt(),
    ended_at = endedAt,
    end_tz_offset_min = endTzOffsetMin?.toLong(),
    tz_id = tzId,
    part_of_day = partOfDay?.name,
    from_label = fromLabel,
    to_label = toLabel,
    label_override = labelOverride,
    context = context?.name,
    distance_m = distanceM,
    estimated_distance_m = estimatedDistanceM,
    duration_s = durationS,
    moving_s = movingS,
    stopped_s = stoppedS,
    stop_count = stopCount?.toLong(),
    avg_moving_speed_mps = avgMovingSpeedMps,
    max_speed_mps = maxSpeedMps,
    max_speed_at_s = maxSpeedAtS,
    max_speed_lat = null,
    max_speed_lng = null,
    elev_gain_m = elevGainM,
    elev_loss_m = elevLossM,
    elev_min_m = elevMinM,
    elev_max_m = elevMaxM,
    bbox_json = bbox?.let { BboxJson.encode(it) },
    stops_json = if (stops.isEmpty()) null else columnJson.encodeToString(stops.map(StopJson::of)),
    cleanest_json = cleanest?.let { columnJson.encodeToString(StretchJson.of(it)) },
    quality_json = quality?.let { columnJson.encodeToString(QualityJson.of(it)) },
    interrupted_json = interrupted?.let { columnJson.encodeToString(InterruptionJson.of(it)) },
    capture_meta_json = captureMeta?.let { columnJson.encodeToString(CaptureMetaJson.of(it)) },
    suspect = suspect.name,
    suspect_answer = suspectAnswer?.name,
    pipeline_version = pipelineVersion,
    raw_state = rawState.name,
    preview_path = null,
    schema_version = 1L,
    created_at = createdAt,
    updated_at = updatedAt,
    deleted_at = deletedAt,
    origin_device_id = deviceId,
    field_clock = "{}",
)

