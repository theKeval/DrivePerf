package com.driveperf.data.repository

import com.driveperf.data.local.dao.DrivingEventDao
import com.driveperf.data.local.dao.LocationPointDao
import com.driveperf.data.local.dao.MotionWindowDao
import com.driveperf.data.local.dao.TripScoreDao
import com.driveperf.data.local.dao.TripSessionDao
import com.driveperf.data.local.entity.DrivingEventEntity
import com.driveperf.data.local.entity.TripScoreEntity
import com.driveperf.data.local.entity.TripSessionEntity
import com.driveperf.domain.model.DrivingEvent
import com.driveperf.domain.model.EventType
import com.driveperf.domain.model.TripScore
import com.driveperf.domain.model.TripSession
import com.driveperf.domain.model.TripState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepositoryImpl @Inject constructor(
    private val tripSessionDao: TripSessionDao,
    private val locationPointDao: LocationPointDao,
    private val motionWindowDao: MotionWindowDao,
    private val drivingEventDao: DrivingEventDao,
    private val tripScoreDao: TripScoreDao,
) : TripRepository {

    // ── Trip sessions ──────────────────────────────────────────────────────

    override suspend fun startTrip(startTimeMs: Long): Long =
        tripSessionDao.insert(TripSessionEntity(startTimeMs = startTimeMs))

    override suspend fun activateTrip(tripId: Long) =
        tripSessionDao.updateState(tripId, TripState.ACTIVE.name)

    override suspend fun endTrip(tripId: Long, endTimeMs: Long, distanceMeters: Double) {
        tripSessionDao.updateEndAndDistance(tripId, endTimeMs, distanceMeters)
        tripSessionDao.updateState(tripId, TripState.COMPLETED.name)
    }

    override suspend fun markAsPassenger(tripId: Long) {
        tripSessionDao.updatePassengerFlag(tripId, true)
        tripSessionDao.updateState(tripId, TripState.DISCARDED.name)
    }

    override suspend fun deleteTrip(tripId: Long) =
        tripSessionDao.deleteById(tripId)

    override fun observeAllTrips(): Flow<List<TripSession>> =
        tripSessionDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeTrip(tripId: Long): Flow<TripSession?> =
        tripSessionDao.observeById(tripId).map { it?.toDomain() }

    override suspend fun getActiveTrip(): TripSession? =
        tripSessionDao.getActiveTrip()?.toDomain()

    // ── Driving events ─────────────────────────────────────────────────────

    override suspend fun saveEvent(event: DrivingEvent) {
        drivingEventDao.insert(event.toEntity())
    }

    override suspend fun saveEvents(events: List<DrivingEvent>) {
        drivingEventDao.insertAll(events.map { it.toEntity() })
    }

    override fun observeEventsForTrip(tripId: Long): Flow<List<DrivingEvent>> =
        drivingEventDao.observeByTripId(tripId).map { entities ->
            entities.map { it.toDomain() }
        }

    // ── Scores ─────────────────────────────────────────────────────────────

    override suspend fun saveScore(score: TripScore) {
        tripScoreDao.insert(score.toEntity())
    }

    override fun observeScoreForTrip(tripId: Long): Flow<TripScore?> =
        tripScoreDao.observeByTripId(tripId).map { it?.toDomain() }

    // ── Insights ───────────────────────────────────────────────────────────

    override fun observeAverageSafetyScore(fromMs: Long): Flow<Float?> =
        tripScoreDao.observeAverageSafetyScoreSince(fromMs)

    override fun observeAverageQualityScore(fromMs: Long): Flow<Float?> =
        tripScoreDao.observeAverageQualityScoreSince(fromMs)

    override fun observeCompletedTripCount(): Flow<Int> =
        tripSessionDao.observeCompletedTripCount()

    // ── Mappers ───────────────────────────────────────────────────────────

    private fun TripSessionEntity.toDomain() = TripSession(
        id = id,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        distanceMeters = distanceMeters,
        state = TripState.valueOf(state),
        isPassenger = isPassenger,
    )

    private fun DrivingEvent.toEntity() = DrivingEventEntity(
        id = id,
        tripId = tripId,
        type = type.name,
        timestampMs = timestampMs,
        durationMs = durationMs,
        severity = severity,
        details = details,
        latitude = latitude,
        longitude = longitude,
    )

    private fun DrivingEventEntity.toDomain() = DrivingEvent(
        id = id,
        tripId = tripId,
        type = EventType.valueOf(type),
        timestampMs = timestampMs,
        durationMs = durationMs,
        severity = severity,
        details = details,
        latitude = latitude,
        longitude = longitude,
    )

    private fun TripScore.toEntity() = TripScoreEntity(
        tripId = tripId,
        safetyScore = safetyScore,
        smoothnessScore = smoothnessScore,
        efficiencyScore = efficiencyScore,
        tripQualityScore = tripQualityScore,
        starRating = starRating,
        breakdownJson = breakdownToJson(breakdown),
        signalConfidence = signalConfidence,
    )

    private fun TripScoreEntity.toDomain() = TripScore(
        tripId = tripId,
        safetyScore = safetyScore,
        smoothnessScore = smoothnessScore,
        efficiencyScore = efficiencyScore,
        tripQualityScore = tripQualityScore,
        starRating = starRating,
        breakdown = parseBreakdownJson(breakdownJson),
        signalConfidence = signalConfidence,
    )

    /** Serialises the breakdown map to JSON using Android's built-in JSONObject. */
    private fun breakdownToJson(breakdown: Map<String, String>): String {
        val obj = org.json.JSONObject()
        breakdown.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    /** Deserialises the breakdown JSON using Android's built-in JSONObject. */
    private fun parseBreakdownJson(json: String): Map<String, String> {
        if (json == "{}" || json.isBlank()) return emptyMap()
        return try {
            val obj = org.json.JSONObject(json)
            buildMap {
                obj.keys().forEach { key -> put(key, obj.getString(key)) }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
