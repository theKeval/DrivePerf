package com.driveperf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.driveperf.data.local.entity.DrivingEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DrivingEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: DrivingEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<DrivingEventEntity>)

    @Query("SELECT * FROM driving_events WHERE tripId = :tripId ORDER BY timestampMs ASC")
    suspend fun getByTripId(tripId: Long): List<DrivingEventEntity>

    @Query("SELECT * FROM driving_events WHERE tripId = :tripId ORDER BY timestampMs ASC")
    fun observeByTripId(tripId: Long): Flow<List<DrivingEventEntity>>

    @Query("SELECT COUNT(*) FROM driving_events WHERE tripId = :tripId AND type = :type")
    suspend fun countByType(tripId: Long, type: String): Int

    @Query("DELETE FROM driving_events WHERE tripId = :tripId")
    suspend fun deleteByTripId(tripId: Long)
}
