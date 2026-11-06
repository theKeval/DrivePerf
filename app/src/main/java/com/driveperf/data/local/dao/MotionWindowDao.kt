package com.driveperf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.driveperf.data.local.entity.MotionWindowEntity

@Dao
interface MotionWindowDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(window: MotionWindowEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(windows: List<MotionWindowEntity>)

    @Query("SELECT * FROM motion_windows WHERE tripId = :tripId ORDER BY timestampMs ASC")
    suspend fun getByTripId(tripId: Long): List<MotionWindowEntity>

    @Query("DELETE FROM motion_windows WHERE tripId = :tripId")
    suspend fun deleteByTripId(tripId: Long)
}
