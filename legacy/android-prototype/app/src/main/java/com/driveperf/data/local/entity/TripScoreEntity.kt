package com.driveperf.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trip_scores",
    foreignKeys = [
        ForeignKey(
            entity = TripSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("tripId", unique = true)],
)
data class TripScoreEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val tripId: Long,
    val safetyScore: Float,
    val smoothnessScore: Float,
    val efficiencyScore: Float,
    val tripQualityScore: Float,
    val starRating: Float,
    /** JSON-serialised breakdown map. */
    val breakdownJson: String = "{}",
    val signalConfidence: Float,
    val computedAt: Long = System.currentTimeMillis(),
)
