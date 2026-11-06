package com.driveperf.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trip_sessions")
data class TripSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val startTimeMs: Long,
    val endTimeMs: Long? = null,
    val distanceMeters: Double = 0.0,
    /** One of: DETECTING, ACTIVE, COMPLETED, DISCARDED */
    val state: String = "DETECTING",
    val isPassenger: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
