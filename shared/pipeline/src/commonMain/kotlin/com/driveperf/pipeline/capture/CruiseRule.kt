package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.LocationSample
import kotlin.math.abs

/**
 * Steady cruise (F-CAP-16 AC2–3, P-10): fixes at or above [CaptureConfig.cruiseMinSpeedMps], within
 * [CaptureConfig.cruiseSpeedBandMps] and turning no faster than [CaptureConfig.cruiseMaxTurnDps], for
 * [CaptureConfig.cruiseHoldS]. The reducer ends it on any manoeuvre the IMU sees.
 */
internal class CruiseRule(private val cfg: CaptureConfig) {
    /** The cruise after [fix]: kept while in band and turning gently, restarted when not, null below cruise speed. */
    fun next(c: Cruise?, fix: LocationSample): Cruise? {
        val speed = fix.speedMps ?: 0.0
        val kept = c?.takeIf {
            fix.tMonoNs > it.lastNs && !turning(it, fix) &&
                maxOf(it.maxMps, speed) - minOf(it.minMps, speed) <= cfg.cruiseSpeedBandMps
        }
        return when {
            speed < cfg.cruiseMinSpeedMps -> null
            kept == null -> Cruise(fix.tMonoNs, speed, speed, fix.courseDeg, fix.tMonoNs)
            else -> kept.copy(
                minMps = minOf(kept.minMps, speed),
                maxMps = maxOf(kept.maxMps, speed),
                lastCourseDeg = fix.courseDeg ?: kept.lastCourseDeg,
                lastNs = fix.tMonoNs,
                steady = fix.tMonoNs - kept.sinceNs >= secondsNs(cfg.cruiseHoldS),
            )
        }
    }

    /** Heading change per second above the limit; the shorter way round, so 359° to 1° is 2°. */
    private fun turning(c: Cruise, fix: LocationSample): Boolean {
        val from = c.lastCourseDeg
        val to = fix.courseDeg
        if (from == null || to == null) return false
        val raw = abs(to - from) % FULL_TURN_DEG
        val deg = if (raw > HALF_TURN_DEG) FULL_TURN_DEG - raw else raw
        return deg / ((fix.tMonoNs - c.lastNs) / NS_PER_S) > cfg.cruiseMaxTurnDps
    }

    private companion object {
        const val NS_PER_S = 1e9
        const val FULL_TURN_DEG = 360.0
        const val HALF_TURN_DEG = 180.0
    }
}
