package com.driveperf.domain.capture

import kotlin.math.PI
import kotlin.math.sqrt

/**
 * Splits the accelerometer's specific force into gravity and the device's own acceleration for the §2.1 normalised
 * frame (pipeline-and-scoring.md), with a gyro-aided complementary filter.
 *
 * The gravity estimate is carried forward by the gyroscope, so it follows the phone when it is turned, and pulled
 * towards the measured specific force with a long time constant, so a car braking or accelerating for several
 * seconds stays in the user acceleration instead of tilting gravity. Android's fused `TYPE_GRAVITY` does the opposite
 * on some devices (DRIVE-160: on a OnePlus 7T it absorbed most of every sustained manoeuvre within about a second,
 * which removed the signal phone-to-vehicle alignment needs).
 *
 * Until [timeConstantS] of data has been seen the estimate is the running mean, so a trip that starts mid-manoeuvre
 * settles quickly. Not thread-safe: feed it from one thread, in time order.
 *
 * @param timeConstantS how slowly gravity follows the specific force, seconds.
 */
public class GravityTracker(private val timeConstantS: Double = DEFAULT_TIME_CONSTANT_S) {
    private var gx = 0.0
    private var gy = 0.0
    private var gz = 0.0
    private var lastNs = Long.MIN_VALUE
    private var seenS = 0.0
    private var count = 0

    /** Gravity and user acceleration for one sample, both in g. */
    public data class Split(val gravityUp: Vec3, val userAccelG: Vec3)

    /**
     * Feeds one sample and returns the split at its time.
     *
     * @param specificForceG the accelerometer reading in g, gravity included, pointing away from the Earth at rest
     *   (an Android reading in m/s² divided by [com.driveperf.foundation.Units.G]).
     * @param rotRateDps the rotation rate in °/s, counter-clockwise positive; zero when there is no gyroscope.
     */
    public fun update(tNs: Long, specificForceG: Vec3, rotRateDps: Vec3): Split {
        val f = specificForceG
        if (lastNs == Long.MIN_VALUE) {
            gx = f.x
            gy = f.y
            gz = f.z
            count = 1
        } else {
            val dt = ((tNs - lastNs) / NS).coerceAtLeast(0.0)
            if (dt <= MAX_GYRO_STEP_S) rotate(rotRateDps, dt)
            seenS += dt
            count++
            val alpha = if (seenS < timeConstantS) 1.0 / count else dt / (timeConstantS + dt)
            gx += alpha * (f.x - gx)
            gy += alpha * (f.y - gy)
            gz += alpha * (f.z - gz)
        }
        lastNs = tNs
        val norm = sqrt(gx * gx + gy * gy + gz * gz)
        val up = if (norm > 0.0) Vec3(gx / norm, gy / norm, gz / norm) else Vec3(0.0, 0.0, 0.0)
        return Split(up, Vec3(f.x - gx, f.y - gy, f.z - gz))
    }

    /** A fixed world vector seen from a turning device turns the other way: `dg/dt = −ω × g`. */
    private fun rotate(w: Vec3, dt: Double) {
        val wx = w.x * DEG_TO_RAD * dt
        val wy = w.y * DEG_TO_RAD * dt
        val wz = w.z * DEG_TO_RAD * dt
        val cx = wy * gz - wz * gy
        val cy = wz * gx - wx * gz
        val cz = wx * gy - wy * gx
        val before = sqrt(gx * gx + gy * gy + gz * gz)
        gx -= cx
        gy -= cy
        gz -= cz
        val after = sqrt(gx * gx + gy * gy + gz * gz)
        if (after > 0.0) {
            val keep = before / after
            gx *= keep
            gy *= keep
            gz *= keep
        }
    }

    public companion object {
        /** Seconds; long against a manoeuvre (2–10 s), short against gyroscope drift on a calibrated sensor. */
        public const val DEFAULT_TIME_CONSTANT_S: Double = 30.0
        private const val MAX_GYRO_STEP_S = 0.5
        private const val NS = 1e9
        private const val DEG_TO_RAD = PI / 180.0
    }
}
