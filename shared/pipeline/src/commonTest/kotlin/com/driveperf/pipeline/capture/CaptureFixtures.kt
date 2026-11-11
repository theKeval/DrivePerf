package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.CaptureHeader
import com.driveperf.domain.capture.GeoPoint
import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.MotionSample
import com.driveperf.domain.capture.Parking
import com.driveperf.domain.capture.Vec3
import com.driveperf.domain.capture.WakeKind

/** Synthetic capture inputs. Positions sit in the ocean test box (§8.2): latitude 0–5°, longitude −30 to −25°. */
internal object CaptureFixtures {
    const val LAT0 = 2.0
    const val LNG0 = -27.5
    const val WALL0 = 1_794_268_800_000L
    private const val M_PER_DEG_LAT = 111_195.0

    fun ns(s: Double): Long = (s * 1e9).toLong()

    fun wallAt(s: Double): Long = WALL0 + (s * 1000).toLong()

    /** A fix at [tS] seconds, [northM] metres north of the origin. */
    fun fix(tS: Double, speed: Double?, northM: Double = 0.0, hAcc: Double? = 5.0): LocationSample = LocationSample(
        tMonoNs = ns(tS), wallMs = wallAt(tS), lat = LAT0 + northM / M_PER_DEG_LAT, lng = LNG0, altM = 10.0,
        hAccM = hAcc, vAccM = 3.0, speedMps = speed, speedAccMps = 0.3, courseDeg = 0.0, courseAccDeg = 5.0,
    )

    fun activity(tS: Double, type: ActivityType, confidence: ActivityConfidence = ActivityConfidence.MEDIUM): ActivitySample =
        ActivitySample(ns(tS), wallAt(tS), type, confidence)

    fun motion(tS: Double, accel: Double = 0.0, gyro: Double = 0.0): MotionSample =
        MotionSample(ns(tS), Vec3(accel, 0.0, 0.0), Vec3(0.0, 0.0, 1.0), Vec3(0.0, 0.0, gyro), null)

    /** [seconds] of 50 Hz motion starting at [fromS]; [noisy] alternates the acceleration so the IMU is not still. */
    fun motionRun(fromS: Double, seconds: Double, noisy: Boolean, accel: Double = 0.05): List<MotionSample> =
        (0 until (seconds * 50).toInt()).map { i ->
            val a = if (noisy && i % 2 == 0) accel else 0.0
            motion(fromS + i * 0.02, accel = a)
        }

    val parking: Parking = Parking(GeoPoint(LAT0, LNG0), WALL0 - 3_600_000L)

    fun header(
        startS: Double,
        kind: WakeKind = WakeKind.PARKED_EXIT,
        parking: Parking? = this.parking,
        id: String = "trip-1",
    ): CaptureHeader =
        CaptureHeader(id, ns(startS), wallAt(startS), -300, "Etc/GMT+5", kind, null, 50, parking)
}
