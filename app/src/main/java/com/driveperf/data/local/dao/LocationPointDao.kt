package com.driveperf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.driveperf.data.local.entity.LocationPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationPointDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: LocationPointEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<LocationPointEntity>)

    @Query("SELECT * FROM location_points WHERE tripId = :tripId ORDER BY timestampMs ASC")
    suspend fun getByTripId(tripId: Long): List<LocationPointEntity>

    @Query("SELECT * FROM location_points WHERE tripId = :tripId ORDER BY timestampMs ASC")
    fun observeByTripId(tripId: Long): Flow<List<LocationPointEntity>>

    @Query("SELECT COUNT(*) FROM location_points WHERE tripId = :tripId")
    suspend fun countForTrip(tripId: Long): Int

    @Query("DELETE FROM location_points WHERE tripId = :tripId")
    suspend fun deleteByTripId(tripId: Long)
}
