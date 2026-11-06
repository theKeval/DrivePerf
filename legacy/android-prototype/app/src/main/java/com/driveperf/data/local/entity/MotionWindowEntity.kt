package com.driveperf.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A window of motion sensor data sampled during a trip.
 *
 * Each window represents a short time interval (e.g. 1 second) during which
 * accelerometer and gyroscope readings are summarised.
 */
@Entity(
    tableName = "motion_windows",
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
data class MotionWindowEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val tripId: Long,
    val timestampMs: Long,
    /** Duration of this window in milliseconds. */
    val durationMs: Long,
    // Accelerometer (m/s²)
    val accelXMean: Float,
    val accelYMean: Float,
    val accelZMean: Float,
    val accelMagnitudePeak: Float,
    // Gyroscope (rad/s)
    val gyroXMean: Float,
    val gyroYMean: Float,
    val gyroZMean: Float,
    val gyroMagnitudePeak: Float,
    // Jerk proxy (Δaccel / Δtime, m/s³)
    val jerkMagnitudePeak: Float = 0f,
)
