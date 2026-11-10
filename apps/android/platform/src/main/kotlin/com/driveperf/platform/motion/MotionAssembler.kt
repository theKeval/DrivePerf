package com.driveperf.platform.motion

import com.driveperf.platform.ports.MotionSample
import com.driveperf.platform.ports.Quat
import com.driveperf.platform.ports.Vec3
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * Combines the separate Android sensor streams into [MotionSample]s in the normalised device frame, and converts units:
 * m/s² to g, rad/s to °/s (pipeline-and-scoring.md section 2.1).
 *
 * **Sign conventions** (to be confirmed by the on-device shake/tilt/spin conformance test, which is mandatory):
 * `TYPE_LINEAR_ACCELERATION` is already positive towards the device's right edge when it accelerates that way, so it
 * is only scaled; `TYPE_GRAVITY` points away from Earth at rest (a phone lying face-up reads +9.8 on z), which is
 * exactly `gravityUp`, so it is only normalised; `TYPE_GYROSCOPE` is counter-clockwise positive.
 *
 * **Thread safety:** the assembler holds mutable state and is not thread-safe. [SensorMotionSource] calls it only from
 * its single sensor handler thread (spike 1 change 6: callbacks must not touch state from another thread), and
 * hands finished samples across threads through a channel.
 *
 * A sample is emitted each time linear acceleration arrives, using the latest gravity, gyro and attitude readings, once
 * gravity and gyro have been seen.
 */
internal class MotionAssembler {
    private var gravity: Vec3? = null
    private var gyroDps: Vec3? = null
    private var attitude: Quat? = null

    fun onGravity(x: Double, y: Double, z: Double) {
        val norm = sqrt(x * x + y * y + z * z)
        if (norm > MIN_GRAVITY) gravity = Vec3(x / norm, y / norm, z / norm)
    }

    fun onGyro(xRadS: Double, yRadS: Double, zRadS: Double) {
        gyroDps = Vec3(xRadS * RAD_TO_DEG, yRadS * RAD_TO_DEG, zRadS * RAD_TO_DEG)
    }

    /** [w] is absent on devices that report only the vector part of the rotation vector. */
    fun onRotation(x: Double, y: Double, z: Double, w: Double?) {
        val wValue = w ?: sqrt(maxOf(0.0, 1.0 - x * x - y * y - z * z))
        attitude = Quat(wValue, x, y, z)
    }

    /** Returns the finished sample, or null while gravity or gyro has not arrived yet. */
    fun onLinearAcceleration(tNs: Long, xMs2: Double, yMs2: Double, zMs2: Double): MotionSample? {
        val up = gravity
        val rot = gyroDps
        return if (up == null || rot == null) {
            null
        } else {
            MotionSample(
                tMonoNs = tNs,
                userAccelG = Vec3(xMs2 / STANDARD_GRAVITY, yMs2 / STANDARD_GRAVITY, zMs2 / STANDARD_GRAVITY),
                gravityUp = up,
                rotRateDps = rot,
                attitude = attitude,
            )
        }
    }

    companion object {
        const val STANDARD_GRAVITY = 9.80665
        const val RAD_TO_DEG = 180.0 / PI
        private const val MIN_GRAVITY = 1e-6
    }
}
