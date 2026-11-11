package com.driveperf.platform.capture

import com.driveperf.domain.capture.LocationSample
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Display-only live figures (distance, fix count) for the recording notification and the in-drive screen.
 *
 * INTERIM (DRIVE-72): the shared core computes the real distance and everything scored. This exists so the shell can
 * show something honest before the core's recorder is wired in, and it follows the spike's rules: fixes with accuracy
 * worse than 30 m are ignored for distance, and steps under 5 m are dropped as jitter. Not thread-safe.
 */
class LiveTripStats {
    private var last: LocationSample? = null
    var distanceM: Double = 0.0
        private set
    var fixCount: Int = 0
        private set

    fun onFix(sample: LocationSample) {
        fixCount++
        val accuracy = sample.hAccM
        if (accuracy != null && accuracy <= MAX_ACCURACY_M) anchorTo(sample)
    }

    /**
     * The anchor only moves once a step has passed the jitter threshold, so slow movement (under 18 km/h at one fix a
     * second, every step is below 5 m) still adds up instead of being dropped fix by fix.
     */
    private fun anchorTo(sample: LocationSample) {
        val anchor = last
        if (anchor == null) {
            last = sample
        } else {
            val step = haversineM(anchor.lat, anchor.lng, sample.lat, sample.lng)
            if (step >= MIN_STEP_M) {
                distanceM += step
                last = sample
            }
        }
    }

    companion object {
        private const val MAX_ACCURACY_M = 30.0
        private const val MIN_STEP_M = 5.0
        private const val EARTH_RADIUS_M = 6_371_000.0

        /** Great-circle distance between two points in metres. */
        fun haversineM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
            return 2 * EARTH_RADIUS_M * asin(sqrt(a))
        }
    }
}
