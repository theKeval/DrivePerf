package com.driveperf.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "driving_events",
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
data class DrivingEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val tripId: Long,
    /** Matches [com.driveperf.domain.model.EventType] name. */
    val type: String,
    val timestampMs: Long,
    val durationMs: Long = 0L,
    /** Severity in [0.0, 1.0]. */
    val severity: Float = 0f,
    val details: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
)
