package com.driveperf.platform.ports

/** A 3-vector. Units depend on the field that carries it. */
data class Vec3(val x: Double, val y: Double, val z: Double)

/** A unit quaternion, scalar first. */
data class Quat(val w: Double, val x: Double, val y: Double, val z: Double)

/**
 * One GNSS fix in the form pipeline-and-scoring.md section 2.1 asks adapters to deliver. Time is the monotonic
 * clock (`elapsedRealtimeNanos` on Android); [wallMs] is for display only.
 *
 * `toString` is redacted on purpose: coordinates must never reach a log or a crash report (ADR-007).
 */
data class LocationSample(
    val tMonoNs: Long,
    val wallMs: Long,
    val lat: Double,
    val lng: Double,
    val altM: Double?,
    val hAccM: Double?,
    val vAccM: Double?,
    val speedMps: Double?,
    val speedAccMps: Double?,
    val courseDeg: Double?,
    val courseAccDeg: Double?,
) {
    override fun toString(): String = "LocationSample(tMonoNs=$tMonoNs, redacted)"
}

/**
 * One IMU sample in the normalised device frame (right-handed, x to the right edge, y to the top edge, z out of the
 * screen). [userAccelG] is the device's own acceleration in g, [gravityUp] a unit vector pointing away from Earth,
 * [rotRateDps] positive counter-clockwise about each axis.
 */
data class MotionSample(
    val tMonoNs: Long,
    val userAccelG: Vec3,
    val gravityUp: Vec3,
    val rotRateDps: Vec3,
    val attitude: Quat?,
) {
    override fun toString(): String = "MotionSample(tMonoNs=$tMonoNs, redacted)"
}

enum class ActivityKind { VEHICLE, BICYCLE, WALKING, RUNNING, STILL }

enum class TransitionKind { ENTER, EXIT }

/** An OS activity-recognition transition, timestamped on the monotonic clock. Never trusted without motion and GNSS. */
data class ActivityEvent(val tMonoNs: Long, val kind: ActivityKind, val transition: TransitionKind)

/** What the motion hardware can do. A device without a gyroscope records `NO_GYRO` (section 2.1). */
data class MotionCapabilities(val hasGyro: Boolean)
