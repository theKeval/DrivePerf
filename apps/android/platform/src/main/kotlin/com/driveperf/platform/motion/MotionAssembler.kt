package com.driveperf.platform.motion

import com.driveperf.domain.capture.GravityTracker
import com.driveperf.domain.capture.MotionSample
import com.driveperf.domain.capture.Quat
import com.driveperf.domain.capture.Vec3
import com.driveperf.foundation.Units
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * Combines the raw accelerometer and gyroscope streams into [MotionSample]s in the normalised device frame
 * (pipeline-and-scoring.md section 2.1): m/s² to g, rad/s to °/s, gravity split out by [GravityTracker].
 *
 * **Why not `TYPE_GRAVITY` / `TYPE_LINEAR_ACCELERATION`** (DRIVE-160): on a OnePlus 7T the fused gravity followed every
 * sustained braking or acceleration within about a second, so linear acceleration lost the low-frequency part of the
 * car's motion and phone-to-vehicle alignment never converged on any drive. The tracker keeps gravity on the gyroscope
 * and lets it follow the accelerometer only with a 30 s time constant, which matches Core Motion on iPhone drives.
 *
 * **Time alignment:** with batching (`maxReportLatencyUs`) the sensor hub delivers each sensor's events in bursts, so
 * the latest gyroscope reading can be half a second older than an accelerometer event (DRIVE-160 found gyro values
 * repeated for up to 2 s). Accelerometer events are therefore held until a gyroscope event at or after their time has
 * arrived, and the rotation rate is interpolated at the accelerometer timestamp. An event waits at most [maxWaitNs];
 * after that the nearest gyroscope reading is used.
 *
 * **Sign conventions** (to be confirmed by the on-device shake/tilt/spin conformance test, which is mandatory):
 * `TYPE_ACCELEROMETER` reads the specific force, +9.8 on z for a phone lying face up, which is the tracker's input
 * convention; `TYPE_GYROSCOPE` is counter-clockwise positive.
 *
 * **Thread safety:** the assembler holds mutable state and is not thread-safe. [SensorMotionSource] calls it only from
 * its single sensor handler thread (spike 1 change 6: callbacks must not touch state from another thread), and
 * hands finished samples across threads through a channel.
 */
internal class MotionAssembler(
    private val tracker: GravityTracker = GravityTracker(),
    private val maxWaitNs: Long = MAX_WAIT_NS,
) {
    private class Reading(val tNs: Long, val x: Double, val y: Double, val z: Double)

    private val pendingAccel = ArrayDeque<Reading>()
    private val gyro = ArrayDeque<Reading>()
    private var attitude: Quat? = null
    private var lastAccelNs = Long.MIN_VALUE

    /** Returns the accelerometer samples this gyroscope reading releases, in time order. */
    fun onGyro(tNs: Long, xRadS: Double, yRadS: Double, zRadS: Double): List<MotionSample> {
        if (gyro.isEmpty() || tNs > gyro.last().tNs) gyro.addLast(Reading(tNs, xRadS * RAD_TO_DEG, yRadS * RAD_TO_DEG, zRadS * RAD_TO_DEG))
        return release()
    }

    /** [w] is absent on devices that report only the vector part of the rotation vector. */
    fun onRotation(x: Double, y: Double, z: Double, w: Double?) {
        val wValue = w ?: sqrt(maxOf(0.0, 1.0 - x * x - y * y - z * z))
        attitude = Quat(wValue, x, y, z)
    }

    /** Queues an accelerometer reading (m/s², gravity included) and returns the samples now complete. */
    fun onAccelerometer(tNs: Long, xMs2: Double, yMs2: Double, zMs2: Double): List<MotionSample> {
        if (tNs <= lastAccelNs) return emptyList()
        lastAccelNs = tNs
        pendingAccel.addLast(Reading(tNs, xMs2, yMs2, zMs2))
        return release()
    }

    private fun release(): List<MotionSample> {
        val out = mutableListOf<MotionSample>()
        while (pendingAccel.isNotEmpty()) {
            val a = pendingAccel.first()
            val covered = gyro.isNotEmpty() && gyro.last().tNs >= a.tNs
            val timedOut = gyro.isNotEmpty() && pendingAccel.last().tNs - a.tNs > maxWaitNs
            if (!covered && !timedOut) break
            pendingAccel.removeFirst()
            out += sample(a, rateAt(a.tNs))
        }
        // Keep one gyroscope reading at or before the oldest accelerometer event still waiting, for interpolation.
        val oldest = pendingAccel.firstOrNull()?.tNs ?: gyro.lastOrNull()?.tNs
        while (oldest != null && gyro.size > 1 && gyro[1].tNs <= oldest) gyro.removeFirst()
        return out
    }

    private fun rateAt(tNs: Long): Vec3 {
        val after = gyro.indexOfFirst { it.tNs >= tNs }
        return when {
            after == -1 -> gyro.last().vec()
            after == 0 -> gyro.first().vec()
            else -> {
                val p = gyro[after - 1]
                val n = gyro[after]
                val f = (tNs - p.tNs).toDouble() / (n.tNs - p.tNs)
                Vec3(p.x + (n.x - p.x) * f, p.y + (n.y - p.y) * f, p.z + (n.z - p.z) * f)
            }
        }
    }

    private fun sample(a: Reading, rotRateDps: Vec3): MotionSample {
        val force = Vec3(a.x / Units.G, a.y / Units.G, a.z / Units.G)
        val split = tracker.update(a.tNs, force, rotRateDps)
        return MotionSample(a.tNs, split.userAccelG, split.gravityUp, rotRateDps, attitude)
    }

    private fun Reading.vec() = Vec3(x, y, z)

    companion object {
        const val RAD_TO_DEG = 180.0 / PI

        /** Longer than the 500 ms batching latency [SensorMotionSource] requests, so a normal flush never times out. */
        const val MAX_WAIT_NS = 1_000_000_000L
    }
}
