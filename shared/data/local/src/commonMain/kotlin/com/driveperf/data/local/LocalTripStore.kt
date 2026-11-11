package com.driveperf.data.local

import com.driveperf.data.local.db.DrivePerfDatabase
import com.driveperf.domain.capture.FinalisedTrip
import com.driveperf.domain.capture.GeoPoint
import com.driveperf.domain.capture.Parking
import com.driveperf.domain.capture.TripStore
import com.driveperf.domain.trip.Trip
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** [TripStore] on SQLDelight. [deviceId] stamps `origin_device_id` (data-model.md §1.2). */
public class LocalTripStore(
    private val db: DrivePerfDatabase,
    private val deviceId: String,
    private val io: CoroutineContext,
) : TripStore {
    private val trips get() = db.tripQueries
    private val routes get() = db.routeQueries

    override suspend fun insertRecording(trip: Trip): Unit = withContext(io) {
        trips.upsertTrip(trip.toRow(deviceId))
    }

    override suspend fun updateRecording(trip: Trip): Unit = withContext(io) {
        trips.upsertTrip(trip.toRow(deviceId))
    }

    override suspend fun recordingTrip(): Trip? = withContext(io) {
        trips.recordingTrip().executeAsOneOrNull()?.toDomain(emptyList())
    }

    override suspend fun deleteNow(tripId: String): Unit = withContext(io) {
        trips.deleteTrip(tripId)
    }

    override suspend fun lastParking(): Parking? = withContext(io) {
        val row = trips.parking().executeAsOneOrNull()
        val lat = row?.parked_lat
        val lng = row?.parked_lng
        val at = row?.parked_at
        if (lat == null || lng == null || at == null) null else Parking(GeoPoint(lat, lng), at)
    }

    override suspend fun setLastParking(parking: Parking): Unit = withContext(io) {
        trips.setParking(parking.point.lat, parking.point.lng, parking.wallMs)
    }

    /** One transaction for the trip and everything derived from it (data-model.md §2.3 step 2). */
    override suspend fun saveFinalised(result: FinalisedTrip): Unit = withContext(io) {
        val trip = result.trip
        db.transaction {
            trips.upsertTrip(trip.toRow(deviceId))
            trips.deleteSegments(trip.id)
            trip.quality?.spans.orEmpty().forEachIndexed { seq, span -> trips.insertSegment(span.toRow(trip.id, seq)) }
            trips.deleteEvents(trip.id)
            result.events.forEach { trips.insertEvent(it.toRow()) }
            val score = result.score
            if (score == null) trips.deleteScore(trip.id) else trips.upsertScore(score.toRow())
            result.newRoute?.let { routes.upsertRoute(it.toRow(deviceId)) }
            val match = result.routeMatch
            if (match == null) routes.deleteMatch(trip.id) else routes.upsertMatch(match.toRow())
            result.extraMatches.forEach { routes.upsertMatch(it.toRow()) }
            result.personalBest?.let { routes.upsertPersonalBest(it.toRow(trip.updatedAt)) }
            result.files.forEach { trips.upsertTripFile(it.toRow(trip.id)) }
        }
    }
}
