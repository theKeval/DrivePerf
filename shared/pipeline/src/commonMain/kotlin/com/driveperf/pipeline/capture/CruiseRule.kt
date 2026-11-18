package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.MotionSample
import com.driveperf.pipeline.signal.V3
import com.driveperf.pipeline.signal.v3
import kotlin.math.abs

internal data class Cruise(
    val fixes: List<LocationSample>,
    val lastCourseDeg: Double?,
    val steady: Boolean,
    val accel: CruiseAcceleration? = null,
)

internal data class CruiseAcceleration(val tNs: Long, val horizontalG: V3)

/** Steady cruise (F-CAP-16 AC2–3, P-10) uses the trailing GNSS window and filtered horizontal acceleration. */
internal class CruiseRule(private val cfg: CaptureConfig) {
    fun next(c: Cruise?, fix: LocationSample): Cruise? {
        val speed = fix.speedMps?.takeIf { it.isFinite() && it >= cfg.cruiseMinSpeedMps } ?: return null
        val previous = c?.takeIf { fix.tMonoNs > it.fixes.last().tMonoNs && !turning(it, fix) }
        val fixes = previous?.fixes.orEmpty()
        val holdNs = secondsNs(cfg.cruiseHoldS)
        // Keep one fix at or before the cutoff to prove that the window spans the full hold time.
        val cutoff = fixes.indexOfLast { it.tMonoNs <= fix.tMonoNs - holdNs }
        val outOfBand = fixes.indexOfLast { abs(requireNotNull(it.speedMps) - speed) > cfg.cruiseSpeedBandMps }
        val window = fixes.drop(maxOf(cutoff, outOfBand + 1)) + fix
        val steady = fix.tMonoNs - window.first().tMonoNs >= holdNs
        return Cruise(window, fix.courseDeg ?: previous?.lastCourseDeg, steady, previous?.accel)
    }

    fun afterMotion(cruise: Cruise?, samples: List<MotionSample>): Cruise? {
        var filtered = cruise?.accel
        if (cruise != null) {
            for (sample in samples) {
                filtered = filter(filtered, sample)
                if (filtered.horizontalG.norm() >= cfg.cruiseExitAccelG) return null
            }
        }
        return cruise?.copy(accel = filtered)
    }

    private fun filter(previous: CruiseAcceleration?, sample: MotionSample): CruiseAcceleration {
        val dtS = previous?.let { (sample.tMonoNs - it.tNs) / NS_PER_S } ?: 0.0
        return when {
            previous == null || dtS > cfg.cruiseAccelMaxGapS -> CruiseAcceleration(sample.tMonoNs, V3.ZERO)
            dtS <= 0.0 -> previous
            else -> {
                val up = sample.gravityUp.v3().unit()
                val accel = sample.userAccelG.v3()
                val horizontalG = accel - up * (accel dot up)
                val weight = dtS / (cfg.cruiseAccelTimeConstantS + dtS)
                val mean = previous.horizontalG + (horizontalG - previous.horizontalG) * weight
                CruiseAcceleration(sample.tMonoNs, mean)
            }
        }
    }

    /** Heading change per second above the limit, measured across the shorter arc. */
    private fun turning(c: Cruise, fix: LocationSample): Boolean {
        val from = c.lastCourseDeg
        val to = fix.courseDeg
        if (from == null || to == null) return false
        val raw = abs(to - from) % FULL_TURN_DEG
        val deg = if (raw > HALF_TURN_DEG) FULL_TURN_DEG - raw else raw
        return deg / ((fix.tMonoNs - c.fixes.last().tMonoNs) / NS_PER_S) > cfg.cruiseMaxTurnDps
    }

    private companion object {
        const val NS_PER_S = 1e9
        const val FULL_TURN_DEG = 360.0
        const val HALF_TURN_DEG = 180.0
    }
}
