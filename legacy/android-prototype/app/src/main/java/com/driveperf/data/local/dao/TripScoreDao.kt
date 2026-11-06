package com.driveperf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.driveperf.data.local.entity.TripScoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripScoreDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(score: TripScoreEntity): Long

    @Update
    suspend fun update(score: TripScoreEntity)

    @Query("SELECT * FROM trip_scores WHERE tripId = :tripId")
    suspend fun getByTripId(tripId: Long): TripScoreEntity?

    @Query("SELECT * FROM trip_scores WHERE tripId = :tripId")
    fun observeByTripId(tripId: Long): Flow<TripScoreEntity?>

    @Query("SELECT AVG(safetyScore) FROM trip_scores WHERE tripId IN (SELECT id FROM trip_sessions WHERE startTimeMs >= :fromMs AND state = 'COMPLETED')")
    fun observeAverageSafetyScoreSince(fromMs: Long): Flow<Float?>

    @Query("SELECT AVG(tripQualityScore) FROM trip_scores WHERE tripId IN (SELECT id FROM trip_sessions WHERE startTimeMs >= :fromMs AND state = 'COMPLETED')")
    fun observeAverageQualityScoreSince(fromMs: Long): Flow<Float?>

    @Query("DELETE FROM trip_scores WHERE tripId = :tripId")
    suspend fun deleteByTripId(tripId: Long)
}
