package com.driveperf.data.local

import com.driveperf.data.local.db.TripQueries
import com.driveperf.data.local.db.Trip as TripRow

/**
 * Writes a trip row in place. `INSERT OR REPLACE` would delete the old row first and cascade to its feedback and file
 * rows, so this inserts when absent and otherwise updates.
 */
internal fun TripQueries.upsertTrip(r: TripRow) {
    insertTripIfAbsent(r)
    updateTrip(
        status = r.status, kind = r.kind, source = r.source, source_detail = r.source_detail, vehicle_id = r.vehicle_id,
        started_at = r.started_at, start_tz_offset_min = r.start_tz_offset_min, start_estimated = r.start_estimated,
        ended_at = r.ended_at, end_tz_offset_min = r.end_tz_offset_min, tz_id = r.tz_id, part_of_day = r.part_of_day,
        from_label = r.from_label, to_label = r.to_label, label_override = r.label_override, context = r.context,
        distance_m = r.distance_m, estimated_distance_m = r.estimated_distance_m, duration_s = r.duration_s, moving_s = r.moving_s,
        stopped_s = r.stopped_s, stop_count = r.stop_count, avg_moving_speed_mps = r.avg_moving_speed_mps,
        max_speed_mps = r.max_speed_mps, max_speed_at_s = r.max_speed_at_s, max_speed_lat = r.max_speed_lat,
        max_speed_lng = r.max_speed_lng, elev_gain_m = r.elev_gain_m, elev_loss_m = r.elev_loss_m, elev_min_m = r.elev_min_m,
        elev_max_m = r.elev_max_m, bbox_json = r.bbox_json, stops_json = r.stops_json, cleanest_json = r.cleanest_json,
        quality_json = r.quality_json, interrupted_json = r.interrupted_json, capture_meta_json = r.capture_meta_json,
        suspect = r.suspect, suspect_answer = r.suspect_answer, pipeline_version = r.pipeline_version, raw_state = r.raw_state,
        preview_path = r.preview_path, schema_version = r.schema_version, created_at = r.created_at, updated_at = r.updated_at,
        deleted_at = r.deleted_at, origin_device_id = r.origin_device_id, field_clock = r.field_clock, correction = r.correction,
        corrected_at = r.corrected_at, id = r.id,
    )
}
