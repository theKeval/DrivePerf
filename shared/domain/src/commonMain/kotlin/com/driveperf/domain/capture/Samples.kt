package com.driveperf.domain.capture

/** A 3-vector in the normalised device frame (pipeline-and-scoring.md §2.1). */
public data class Vec3(val x: Double, val y: Double, val z: Double)

/** A unit quaternion (attitude). */
public data class Quat(val w: Double, val x: Double, val y: Double, val z: Double)

/** A WGS 84 position in degrees. */
public data class GeoPoint(val lat: Double, val lng: Double)

/**
 * One GNSS fix, as platform adapters deliver it (pipeline-and-scoring.md §2.1). Units are SI and named in the field.
 *
 * @property tMonoNs when the fix was taken, on the monotonic clock of [com.driveperf.foundation.Clock]. iOS converts
 *   `CLLocation.timestamp` with `nowMono − (nowWall − fix.timestamp)` at delivery; Android uses
 *   `elapsedRealtimeNanos` (§2.2).
 * @property wallMs the fix's UTC time, display only.
 * @property hAccM horizontal accuracy (radius, 68 %). Null when the platform does not report it.
 * @property speedMps Doppler speed. Null when unavailable (iOS reports −1: the adapter maps it to null).
 * @property courseDeg course over ground, clockwise from true north, 0–360.
 */
public data class LocationSample(
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
)

/**
 * One device-motion sample in the normalised device frame (pipeline-and-scoring.md §2.1): right-handed, `x` towards
 * the right edge, `y` towards the top edge, `z` out of the screen.
 *
 * @property userAccelG the device's own acceleration without gravity, in g. Positive `x` means the device is
 *   accelerating towards its right edge. Android adapters divide m/s² by [com.driveperf.foundation.Units.G].
 * @property gravityUp unit vector pointing away from the Earth.
 * @property rotRateDps rotation rate in degrees per second, positive counter-clockwise about each axis. Both
 *   platforms report rad/s, which the adapter converts.
 * @property attitude orientation, when the platform provides it.
 */
public data class MotionSample(
    val tMonoNs: Long,
    val userAccelG: Vec3,
    val gravityUp: Vec3,
    val rotRateDps: Vec3,
    val attitude: Quat?,
)

/**
 * Raw accelerometer history (gravity included), in g, from iOS `CMSensorRecorder` for the estimated start
 * (pipeline-and-scoring.md §2.8). Kept in R0 only: per decision D6 it is never scored.
 */
public data class RecordedAccelSample(val tMonoNs: Long, val accelG: Vec3)

/** The motion activity the platform classifier reports. */
public enum class ActivityType { STATIONARY, WALKING, RUNNING, CYCLING, AUTOMOTIVE, UNKNOWN }

/**
 * Classifier confidence. iOS maps `low/medium/high` directly. Android activity transitions carry no confidence, so
 * an `IN_VEHICLE` enter maps to [MEDIUM] (pipeline-and-scoring.md §1.3).
 */
public enum class ActivityConfidence { LOW, MEDIUM, HIGH }

/** One activity classification, live or from history. [tMonoNs] is when the activity started. */
public data class ActivitySample(
    val tMonoNs: Long,
    val wallMs: Long,
    val type: ActivityType,
    val confidence: ActivityConfidence,
)

/** What the motion hardware can do. A device with no gyroscope records capability `NO_GYRO` (§2.1). */
public data class MotionCapability(val hasGyroscope: Boolean, val maxRateHz: Int)

/** Thermal pressure as the OS reports it, mapped to the iOS names. */
public enum class ThermalState { NOMINAL, FAIR, SERIOUS, CRITICAL }

/** Power conditions that change the IMU rate and end trips (pipeline-and-scoring.md §2.7, EC-BAT). */
public data class PowerStatus(
    val batteryPct: Int?,
    val isCharging: Boolean,
    val lowPowerMode: Boolean,
    val thermal: ThermalState,
)
