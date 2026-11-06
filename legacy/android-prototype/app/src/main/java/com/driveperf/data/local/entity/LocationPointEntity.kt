package com.driveperf.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "location_points",
    foreignKeys = [
        ForeignKey(
            entity = TripSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("tripId")],
)
data class LocationPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val tripId: Long,
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    /** Altitude in metres, null if unavailable. */
    val altitudeMeters: Double? = null,
    /** Speed in m/s reported by the location provider. */
    val speedMs: Float = 0f,
    /** Horizontal accuracy radius in metres. */
    val accuracyMeters: Float = 0f,
    /** Bearing in degrees (0-360), null if unavailable. */
    val bearing: Float? = null,
)
