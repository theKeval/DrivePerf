package com.driveperf.testing

import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.MotionSample
import com.driveperf.domain.capture.Vec3
import com.driveperf.foundation.Units
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sin

/**
 * A synthetic R0 capture (pipeline-and-scoring.md §8.2, `source: synthetic`): a vehicle driven by a small DSL, seen
 * by a phone mounted at an arbitrary rotation, with 50 Hz device motion and 1 Hz GNSS in the ocean test box
 * (latitude 0–5°, longitude −30 to −25°). No real location data is involved.
 *
 * Vehicle frame: forward, right, up. Device frame per §2.1. Yaw rate is positive turning right.
 */
@Suppress("TooManyFunctions") // the drive vocabulary
public class SyntheticCapture(
    /** The phone's mount: vehicle forward, right and up expressed in device axes (orthonormal). */
    private val mount: Mount = Mount.PORTRAIT_UPRIGHT,
    startLat: Double = 2.5,
    startLng: Double = -27.5,
    private val imuHz: Int = 50,
    /**
     * Road vibration amplitude while moving, g: deterministic, band-limited (a new value every 0.1 s), so it reads the
     * same at 25 and 50 Hz. Parked: a tenth of it.
     */
    private val noiseG: Double = 0.04,
    seed: Long = 1L,
) {
    /** A phone mount as the vehicle axes seen in device coordinates. */
    public data class Mount(val forward: Vec3, val right: Vec3, val up: Vec3) {
        public companion object {
            /** Upright in a dash mount, screen facing the driver: forward is −z, right is +x, up is +y. */
            public val PORTRAIT_UPRIGHT: Mount = Mount(Vec3(0.0, 0.0, -1.0), Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0))

            /** Lying flat, top edge forward. */
            public val FLAT: Mount = Mount(Vec3(0.0, 1.0, 0.0), Vec3(1.0, 0.0, 0.0), Vec3(0.0, 0.0, 1.0))

            /** [base] rotated about the vehicle's up axis by [yawDeg] and tilted back by [tiltDeg] about its right axis. */
            public fun rotated(base: Mount, yawDeg: Double, tiltDeg: Double): Mount {
                val y = yawDeg * DEG
                val t = tiltDeg * DEG
                fun combine(a: Vec3, ca: Double, b: Vec3, cb: Double) = Vec3(a.x * ca + b.x * cb, a.y * ca + b.y * cb, a.z * ca + b.z * cb)
                val f1 = combine(base.forward, cos(y), base.right, -sin(y))
                val r1 = combine(base.forward, sin(y), base.right, cos(y))
                val f2 = combine(f1, cos(t), base.up, sin(t))
                val u2 = combine(base.up, cos(t), f1, -sin(t))
                return Mount(f2, r1, u2)
            }
        }
    }

    private val dt = 1.0 / imuHz
    private var t = 0.0
    private var v = 0.0
    private var headingDeg = 0.0
    private var lat = startLat
    private var lng = startLng
    private var rng = seed
    private var gnssOn = true
    private var stepIndex = 0L
    private var vibration = Vec3(0.0, 0.0, 0.0)
    private val motion = mutableListOf<MotionSample>()
    private val fixes = mutableListOf<LocationSample>()
    private val activity = mutableListOf<ActivitySample>()
    private val lonTruth = mutableListOf<Double>()
    private val latTruth = mutableListOf<Double>()
    private var phoneOverride: ((Double) -> Mount)? = null

    /** Seconds elapsed. */
    public val seconds: Double get() = t

    /** Current speed, m/s. */
    public val speed: Double get() = v

    /** One IMU step with longitudinal acceleration [aMps2] and yaw rate [yawDps] (right positive). */
    public fun step(aMps2: Double, yawDps: Double) {
        v = maxOf(0.0, v + aMps2 * dt)
        headingDeg = (headingDeg + yawDps * dt + FULL) % FULL
        val north = v * dt * cos(headingDeg * DEG)
        val east = v * dt * sin(headingDeg * DEG)
        lat += north / M_PER_DEG
        lng += east / (M_PER_DEG * cos(lat * DEG))
        val lonG = aMps2 / Units.G
        val latG = v * yawDps * DEG / Units.G
        lonTruth += lonG
        latTruth += latG
        val m = phoneOverride?.invoke(t) ?: mount
        if (stepIndex % (imuHz / VIBRATION_HZ).coerceAtLeast(1) == 0L) {
            val amp = if (v > MOVING_MPS) noiseG else noiseG * PARKED_NOISE
            vibration = Vec3(noise(amp), noise(amp), noise(amp))
        }
        val accel = add(add(scale(m.forward, lonG), scale(m.right, latG)), vibration)
        val gyro = scale(m.up, -yawDps)
        motion += MotionSample(ns(t), round4(accel), round4(m.up), round3(gyro), null)
        if (stepIndex % imuHz == 0L && gnssOn) fixes += fix()
        stepIndex++
        t = stepIndex.toDouble() / imuHz
    }

    private fun fix(): LocationSample = LocationSample(
        tMonoNs = ns(t),
        wallMs = WALL0 + (t * MS).toLong(),
        lat = r7(lat),
        lng = r7(lng),
        altM = 10.0,
        hAccM = 4.0,
        vAccM = 3.0,
        speedMps = r2(v),
        speedAccMps = 0.3,
        courseDeg = if (v > 1.0) r1(headingDeg) else null,
        courseAccDeg = if (v > 1.0) 3.0 else null,
    )

    /** Holds speed for [s] seconds. */
    public fun cruise(s: Double) {
        repeat(steps(s)) { step(0.0, 0.0) }
    }

    /** Changes speed to [target] with peak acceleration [peakMps2] and jerk [jerk] m/s³. */
    public fun speedTo(target: Double, peakMps2: Double, jerk: Double) {
        val d = target - v
        val s = sign(d)
        val ramp = peakMps2 / jerk
        val rampDv = peakMps2 * ramp
        val (peak, hold) = if (abs(d) < rampDv) kotlin.math.sqrt(abs(d) * jerk) to 0.0 else peakMps2 to (abs(d) - rampDv) / peakMps2
        val r = steps(peak / jerk)
        for (i in 0 until r) step(s * peak * (i + HALF) / r, 0.0)
        repeat(steps(hold)) { step(s * peak, 0.0) }
        for (i in 0 until r) step(s * peak * (r - i - HALF) / r, 0.0)
        if (target == 0.0) v = 0.0
    }

    /** Turns by [angleDeg] (right positive) over [s] seconds with a smooth yaw-rate profile. */
    public fun turn(angleDeg: Double, s: Double) {
        val n = steps(s)
        for (i in 0 until n) {
            val shape = sin(PI * (i + HALF) / n)
            step(0.0, angleDeg * PI / 2 / s * shape)
        }
    }

    /** Stops with [peakMps2] braking and waits [dwell] seconds. */
    public fun stop(peakMps2: Double, jerk: Double, dwell: Double) {
        speedTo(0.0, peakMps2, jerk)
        cruise(dwell)
    }

    /** Drives [s] seconds with no GNSS (a tunnel). */
    public fun tunnel(s: Double, body: SyntheticCapture.() -> Unit = { cruise(s) }) {
        gnssOn = false
        body()
        gnssOn = true
    }

    /** The phone is picked up: its mount rotates by [deg] about the vehicle right axis over [s] seconds, then stays. */
    public fun phoneMoved(deg: Double, s: Double) {
        val start = t
        val from = mount
        phoneOverride = { now -> Mount.rotated(from, 0.0, deg * minOf(1.0, (now - start) / s)) }
        cruise(s)
    }

    /** Adds an activity classification at the current time, or [atS] seconds (negative: before the wake). */
    public fun activity(type: ActivityType, atS: Double = t, confidence: ActivityConfidence = ActivityConfidence.HIGH) {
        activity += ActivitySample(ns(atS), WALL0 + (atS * MS).toLong(), type, confidence)
    }

    /** The captured streams. Times are monotonic from [START_MONO_NS]; wall time from [WALL0]. */
    public fun streams(): Streams = Streams(
        fixes.toList(), motion.toList(), activity.sortedBy { it.tMonoNs }, lonTruth.toDoubleArray(), latTruth.toDoubleArray(),
    )

    /** Generated R0 streams, plus the noise-free vehicle-frame truth per IMU step (for alignment tests). */
    public class Streams(
        public val fixes: List<LocationSample>,
        public val motion: List<MotionSample>,
        public val activity: List<ActivitySample>,
        public val lonTruthG: DoubleArray,
        public val latTruthG: DoubleArray,
    ) {
        /** The same streams without the fixes in `[fromS, toS)` (a GNSS outage). */
        public fun withoutFixes(fromS: Double, toS: Double): Streams = Streams(
            fixes.filter { val t = (it.tMonoNs - START_MONO_NS) / 1e9; t < fromS || t >= toS },
            motion, activity, lonTruthG, latTruthG,
        )
    }

    private fun steps(s: Double): Int = round(s * imuHz).toInt()

    private fun noise(amplitude: Double): Double {
        rng = (rng * LCG_A + LCG_C) and LCG_MASK
        return ((rng ushr NOISE_SHIFT).toDouble() / NOISE_RANGE - HALF) * 2 * amplitude
    }

    public companion object {
        /** Monotonic time of `t = 0` in synthetic captures. */
        public const val START_MONO_NS: Long = 1_000_000_000_000L

        /** Wall time of `t = 0`: 2026-11-10T00:00:00Z, arbitrary and fixed. */
        public const val WALL0: Long = 1_794_268_800_000L

        private const val DEG = PI / 180.0
        private const val FULL = 360.0
        private const val HALF = 0.5
        private const val MS = 1000.0
        private const val M_PER_DEG = 111_195.0
        private const val LCG_A = 6364136223846793005L
        private const val LCG_C = 1442695040888963407L
        private const val LCG_MASK = Long.MAX_VALUE
        private const val NOISE_SHIFT = 31
        private const val NOISE_RANGE = 4294967296.0
        private const val MOVING_MPS = 0.5
        private const val PARKED_NOISE = 0.1
        private const val VIBRATION_HZ = 10

        private fun ns(s: Double): Long = START_MONO_NS + round(s * 1e9).toLong()

        private fun r7(x: Double) = round(x * 1e7) / 1e7

        private fun r2(x: Double) = round(x * 100) / 100

        private fun r1(x: Double) = round(x * 10) / 10

        private fun scale(v: Vec3, s: Double) = Vec3(v.x * s, v.y * s, v.z * s)

        /** Rounded to the fixture CSV precision, so writing and parsing a fixture is exact. */
        private fun round4(v: Vec3) = Vec3(r(v.x, 1e4), r(v.y, 1e4), r(v.z, 1e4))

        private fun round3(v: Vec3) = Vec3(r(v.x, 1e3), r(v.y, 1e3), r(v.z, 1e3))

        /** Rounds to 1/[scale]; `+ 0.0` turns −0.0 into 0.0 so text round-trips exactly. */
        private fun r(x: Double, scale: Double) = round(x * scale) / scale + 0.0

        private fun add(a: Vec3, b: Vec3) = Vec3(a.x + b.x, a.y + b.y, a.z + b.z)
    }
}
