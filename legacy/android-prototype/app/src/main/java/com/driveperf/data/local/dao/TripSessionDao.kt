package com.driveperf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.driveperf.data.local.entity.TripSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trip: TripSessionEntity): Long

    @Update
    suspend fun update(trip: TripSessionEntity)

    @Query("SELECT * FROM trip_sessions WHERE id = :id")
    suspend fun getById(id: Long): TripSessionEntity?

    @Query("SELECT * FROM trip_sessions WHERE id = :id")
    fun observeById(id: Long): Flow<TripSessionEntity?>

    @Query("SELECT * FROM trip_sessions WHERE state != 'DISCARDED' ORDER BY startTimeMs DESC")
    fun observeAll(): Flow<List<TripSessionEntity>>

    @Query("SELECT * FROM trip_sessions WHERE state = 'ACTIVE' LIMIT 1")
    suspend fun getActiveTrip(): TripSessionEntity?

    @Query("UPDATE trip_sessions SET state = :state WHERE id = :id")
    suspend fun updateState(id: Long, state: String)

    @Query("UPDATE trip_sessions SET endTimeMs = :endTimeMs, distanceMeters = :distanceMeters WHERE id = :id")
    suspend fun updateEndAndDistance(id: Long, endTimeMs: Long, distanceMeters: Double)

    @Query("UPDATE trip_sessions SET isPassenger = :isPassenger WHERE id = :id")
    suspend fun updatePassengerFlag(id: Long, isPassenger: Boolean)

    @Query("DELETE FROM trip_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM trip_sessions WHERE state = 'COMPLETED'")
    fun observeCompletedTripCount(): Flow<Int>

    @Query("""
        SELECT * FROM trip_sessions
        WHERE state = 'COMPLETED'
          AND startTimeMs >= :fromMs
        ORDER BY startTimeMs DESC
    """)
    fun observeCompletedSince(fromMs: Long): Flow<List<TripSessionEntity>>
}
