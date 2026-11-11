package com.driveperf.data.local

import com.driveperf.data.local.db.DrivePerfDatabase
import com.driveperf.domain.route.PersonalBest
import com.driveperf.domain.route.RecordEntry

/**
 * pipeline-and-scoring.md §7.4: "A corrected kind removes the match." A trip corrected away from a drive leaves its
 * route; when it held the record (§7.5: only drives are eligible), the record passes to the route's best remaining
 * eligible run, or the route has no record. Call inside the correction's transaction.
 */
internal fun DrivePerfDatabase.removeFromRoute(tripId: String, now: Long) {
    val q = routeQueries
    val match = q.matchForTrip(tripId).executeAsOneOrNull() ?: return
    q.deleteMatch(tripId)
    val record = q.personalBest(match.route_id).executeAsOneOrNull()?.toDomain()
    if (record?.tripId == tripId) passOn(record, now)
}

/** Gives [record], whose holder has just left the route, to the best remaining eligible run, or removes it. */
private fun DrivePerfDatabase.passOn(record: PersonalBest, now: Long) {
    val q = routeQueries
    val next = q.bestEligibleRun(record.routeId).executeAsOneOrNull()
    val smoothness = next?.smoothness?.toInt()
    if (next == null || smoothness == null) {
        q.deletePersonalBest(record.routeId)
    } else {
        val setAt = next.started_at ?: 0L
        val kept = record.history.filter { it.tripId != record.tripId }
        val history = if (kept.lastOrNull()?.tripId == next.trip_id) kept else kept + RecordEntry(next.trip_id, smoothness, setAt)
        q.upsertPersonalBest(PersonalBest(record.routeId, next.trip_id, smoothness, setAt, history).toRow(now))
    }
}
