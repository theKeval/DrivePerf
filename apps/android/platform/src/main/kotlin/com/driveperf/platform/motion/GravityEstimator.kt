package com.driveperf.platform.motion

import kotlin.math.PI
import kotlin.math.sqrt

/**
 * Separates gravity from raw acceleration with a first-order low-pass at 0.3 Hz. Used only on devices without a
 * gyroscope (capability `NO_GYRO`, pipeline-and-scoring.md section 2.1), where Android's fused gravity sensor is not
 * available. Not thread-safe: call from the sensor thread only.
 */
internal class GravityEstimator(cutoffHz: Double = CUTOFF_HZ) {
    private val timeConstantS = 1.0 / (2.0 * PI * cutoffHz)
    private var gx = 0.0
    private var gy = 0.0
    private var gz = 0.0
    private var lastNs = Long.MIN_VALUE

    /** Feeds a raw accelerometer reading (m/s², includes gravity) and returns the current gravity estimate (m/s²). */
    fun update(tNs: Long, ax: Double, ay: Double, az: Double): DoubleArray {
        if (lastNs == Long.MIN_VALUE) {
            gx = ax
            gy = ay
            gz = az
        } else {
            val dt = (tNs - lastNs) / NS_PER_S
            val alpha = dt / (timeConstantS + dt)
            gx += alpha * (ax - gx)
            gy += alpha * (ay - gy)
            gz += alpha * (az - gz)
        }
        lastNs = tNs
        return doubleArrayOf(gx, gy, gz)
    }

    fun magnitude(): Double = sqrt(gx * gx + gy * gy + gz * gz)

    private companion object {
        const val CUTOFF_HZ = 0.3
        const val NS_PER_S = 1e9
    }
}
