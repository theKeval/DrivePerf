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

internal fun ScoreRow.toDomain(): Score {
    val column = columnJson.decodeFromString<ContributorsColumn>(contributors_json)
    return Score(
        tripId = trip_id,
        smoothness = smoothness?.toInt(),
        smoothnessRaw = column.smoothnessRaw,
        baseline = baseline?.toInt(),
        delta = delta?.toInt(),
        basis = basis?.let { ScoreBasis.valueOf(it) },
        basisRouteId = basis_route_id,
        calibrating = calibrating_k?.let { Calibrating(it.toInt(), calibrating_of?.toInt() ?: CALIBRATION_TRIPS) },
        contributors = column.contributors.map(ContributorJson::toDomain),
        ledger = column.ledger?.toDomain(),
        efficiency = efficiency_json?.let { columnJson.decodeFromString<EfficiencyJson>(it).toDomain() },
        tip = tip_json?.let { columnJson.decodeFromString<TipJson>(it).toDomain() },
        recordState = record_state_json?.let { columnJson.decodeFromString<RecordStateJson>(it).toDomain() },
        algorithmVersion = algorithm_version,
        computedAt = computed_at,
    )
}

internal fun Score.toRow(): ScoreRow = ScoreRow(
    trip_id = tripId,
    smoothness = smoothness?.toLong(),
    baseline = baseline?.toLong(),
    delta = delta?.toLong(),
    basis = basis?.name,
    basis_route_id = basisRouteId,
    calibrating_k = calibrating?.k?.toLong(),
    calibrating_of = calibrating?.of?.toLong(),
    contributors_json = columnJson.encodeToString(
        ContributorsColumn(contributors.map(ContributorJson::of), ledger?.let(LedgerJson::of), smoothnessRaw),
    ),
    efficiency_json = efficiency?.let { columnJson.encodeToString(EfficiencyJson.of(it)) },
    tip_json = tip?.let { columnJson.encodeToString(TipJson.of(it)) },
    record_state_json = recordState?.let { columnJson.encodeToString(RecordStateJson.of(it)) },
    algorithm_version = algorithmVersion,
    computed_at = computedAt,
)

internal fun RouteRow.toDomain(): Route = Route(
    id = id,
    name = name,
    namePreset = name_preset?.let { RoutePreset.valueOf(it) },
    state = state?.let { RouteState.valueOf(it) } ?: RouteState.CANDIDATE,
    fromLabel = from_label,
    toLabel = to_label,
    startLat = start_lat ?: 0.0,
    startLng = start_lng ?: 0.0,
    endLat = end_lat ?: 0.0,
    endLng = end_lng ?: 0.0,
    clusterRadiusM = cluster_radius_m ?: 0.0,
    referenceTripId = reference_trip_id.orEmpty(),
    distanceM = distance_m ?: 0.0,
    linkedRouteId = linked_route_id,
    isActive = is_active != 0L,
    mergedIntoRouteId = merged_into_route_id,
    createdAt = created_at,
    updatedAt = updated_at,
    deletedAt = deleted_at,
)

internal fun Route.toRow(deviceId: String): RouteRow = RouteRow(
    id, name, namePreset?.name, state.name, fromLabel, toLabel, startLat, startLng, endLat, endLng, clusterRadiusM,
    referenceTripId, distanceM, linkedRouteId, isActive.toInt(), mergedIntoRouteId, null, 1L, createdAt, updatedAt,
    deletedAt, deviceId, "{}",
)

internal fun Route_match.toDomain(): RouteMatch = RouteMatch(
    trip_id, route_id, similarity, MatchSource.valueOf(source), eligible_for_record != 0L,
    ineligible_reason?.let { IneligibleReason.valueOf(it) },
)

internal fun RouteMatch.toRow(): Route_match =
    Route_match(tripId, routeId, similarity, source.name, eligibleForRecord.toInt(), ineligibleReason?.name)

internal fun Personal_best.toDomain(): PersonalBest = PersonalBest(
    route_id, trip_id, smoothness.toInt(), set_at,
    columnJson.decodeFromString<List<RecordEntryJson>>(history_json).map(RecordEntryJson::toDomain),
)

internal fun PersonalBest.toRow(computedAt: Long): Personal_best = Personal_best(
    routeId, tripId, smoothness.toLong(), setAt, columnJson.encodeToString(history.map(RecordEntryJson::of)), computedAt,
)

private const val CALIBRATION_TRIPS = 3
