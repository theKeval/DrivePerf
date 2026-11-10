package com.driveperf.platform

import com.driveperf.platform.ports.LocationSample
import com.driveperf.platform.ports.MotionSample
import com.driveperf.platform.ports.Vec3

/**
 * Synthetic samples only. The coordinates are a made-up straight line in the open ocean: this repository is public and
 * must never hold a real location (ADR-008).
 */
object Fixtures {
    const val NS = 1_000_000_000L

    fun fix(
        tS: Double,
        speedMps: Double? = 10.0,
        hAccM: Double? = 5.0,
        lat: Double = 0.0,
        lng: Double = 0.0,
    ) = LocationSample(
        tMonoNs = (tS * NS).toLong(),
        wallMs = (tS * 1000).toLong(),
        lat = lat,
        lng = lng,
        altM = null,
        hAccM = hAccM,
        vAccM = null,
        speedMps = speedMps,
        speedAccMps = null,
        courseDeg = null,
        courseAccDeg = null,
    )

    fun motion(tS: Double) = MotionSample(
        tMonoNs = (tS * NS).toLong(),
        userAccelG = Vec3(0.0, 0.0, 0.0),
        gravityUp = Vec3(0.0, 0.0, 1.0),
        rotRateDps = Vec3(0.0, 0.0, 0.0),
        attitude = null,
    )
}
