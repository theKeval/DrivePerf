package com.driveperf.pipeline.score

import com.driveperf.domain.score.ContributorId
import com.driveperf.domain.score.LedgerRow
import com.driveperf.domain.trip.DriveContext
import kotlin.math.floor

/** Contributor measures and sub-scores of one trip (§5.3, §5.6). */
internal data class SmoothnessResult(
    val movingS: Double,
    val rBrake: Double,
    val rAccel: Double,
    val rCorner: Double,
    val wobbleMps: Double?,
    val subScores: Map<ContributorId, Double>,
    val raw: Double,
    val score: Int,
)

/** §5.5–§5.6: context factor, piecewise-linear curves, weights and half-up rounding. */
internal class SmoothnessCalculator(private val cfg: ScoringConfig) {
    /** §5.5: share of gated fixes at ≥ 22.2 m/s among those at ≥ 1.0 m/s. */
    fun context(gnss: List<R1Fix>): DriveContext {
        val moving = gnss.count { it.speedMps >= cfg.movingOnMps }
        val fast = gnss.count { it.speedMps >= cfg.fastMps }
        val h = if (moving == 0) 0.0 else fast.toDouble() / moving
        return when {
            h >= cfg.highwayFrom -> DriveContext.HIGHWAY
            h >= cfg.mixedFrom -> DriveContext.MIXED
            else -> DriveContext.CITY
        }
    }

    fun factor(context: DriveContext): Double = when (context) {
        DriveContext.CITY -> cfg.cityFactor
        DriveContext.MIXED -> cfg.mixedFactor
        DriveContext.HIGHWAY -> cfg.highwayFactor
    }

    /** Scores the summed ledger. Null when `T < 120 s` (§5.6 exposure rule). */
    fun score(total: LedgerRow, wobbleMps: Double?, context: DriveContext): SmoothnessResult? {
        val t = total.movingS
        if (t < cfg.minExposureS) return null
        val c = factor(context)
        val r = listOf(total.brake, total.accel, total.corner).map { SECONDS_PER_MINUTE * it / t }
        val subs = linkedMapOf(
            ContributorId.BRAKING to eventCurve(r[0] / c),
            ContributorId.ACCELERATION to eventCurve(r[1] / c),
            ContributorId.CORNERING to eventCurve(r[2] / c),
        )
        if (wobbleMps != null) subs[ContributorId.CONSISTENCY] = interpolate(wobbleMps / c, WOBBLE_X, WOBBLE_Y)
        var weighted = 0.0
        var weights = 0.0
        for ((id, sub) in subs) {
            weighted += WEIGHTS.getValue(id) * sub
            weights += WEIGHTS.getValue(id)
        }
        val raw = weighted / weights
        return SmoothnessResult(t, r[0], r[1], r[2], wobbleMps, subs, raw, floor(raw + HALF).toInt())
    }

    /** The R-curve for Braking, Acceleration and Cornering. */
    fun eventCurve(x: Double): Double = interpolate(x, EVENT_X, EVENT_Y)

    private fun interpolate(x: Double, xs: DoubleArray, ys: DoubleArray): Double {
        val i = xs.indexOfFirst { x <= it }
        return when {
            i == 0 -> ys.first()
            i < 0 -> ys.last()
            else -> ys[i - 1] + (x - xs[i - 1]) / (xs[i] - xs[i - 1]) * (ys[i] - ys[i - 1])
        }
    }

    private companion object {
        const val HALF = 0.5
        const val SECONDS_PER_MINUTE = 60.0
        val EVENT_X = doubleArrayOf(0.0, 1.0, 3.0, 6.0, 12.0, 25.0)
        val EVENT_Y = doubleArrayOf(100.0, 92.0, 78.0, 60.0, 35.0, 0.0)
        val WOBBLE_X = doubleArrayOf(0.3, 0.8, 1.5, 3.0, 5.0)
        val WOBBLE_Y = doubleArrayOf(100.0, 85.0, 60.0, 30.0, 0.0)
        val WEIGHTS = mapOf(
            ContributorId.BRAKING to 0.35,
            ContributorId.ACCELERATION to 0.25,
            ContributorId.CORNERING to 0.25,
            ContributorId.CONSISTENCY to 0.15,
        )
    }
}
