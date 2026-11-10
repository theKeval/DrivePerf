package com.driveperf.platform.capture

import com.driveperf.platform.ports.LocationSample

/**
 * Measures how complete GNSS delivery was **while the car was moving** (spike 1 change 4). iOS and Android both slow
 * the fix rate when stopped, so a whole-trip percentage punishes the 4-minute wait after parking; the spike saw 35.8%
 * overall on a trip that had 100% while moving.
 *
 * An interval between two consecutive fixes counts as moving when either end is at or above [movingSpeedMps]. A moving
 * interval longer than [gapAfterS] is a gap. Not thread-safe.
 */
class MovingGapTracker(
    private val movingSpeedMps: Double = DEFAULT_MOVING_SPEED_MPS,
    private val gapAfterS: Double = DEFAULT_GAP_AFTER_S,
) {
    private var previous: LocationSample? = null
    private var movingS = 0.0
    private var gapS = 0.0

    fun onFix(sample: LocationSample) {
        val prev = previous
        previous = sample
        if (prev == null) return
        val dtS = (sample.tMonoNs - prev.tMonoNs) / NS_PER_S
        if (dtS <= 0.0) return
        val moving = (prev.speedMps ?: 0.0) >= movingSpeedMps || (sample.speedMps ?: 0.0) >= movingSpeedMps
        if (!moving) return
        movingS += dtS
        if (dtS > gapAfterS) gapS += dtS
    }

    /** Seconds spent moving between fixes. */
    val movingSeconds: Double get() = movingS

    /** Seconds of moving time with no fix for longer than the gap threshold. */
    val gapSeconds: Double get() = gapS

    /** Delivery while moving in percent, or null when the trip never moved. */
    val deliveryPercent: Int?
        get() = if (movingS <= 0.0) null else (100.0 * (movingS - gapS) / movingS).toInt()

    private companion object {
        const val DEFAULT_MOVING_SPEED_MPS = 2.0 // about 7 km/h, the spike's "stopped" cut-off
        const val DEFAULT_GAP_AFTER_S = 3.0 // pipeline-and-scoring.md section 2.3: continuous while gaps are <= 3 s
        const val NS_PER_S = 1e9
    }
}
