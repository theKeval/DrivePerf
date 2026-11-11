package com.driveperf.pipeline.signal

import com.driveperf.foundation.Units
import kotlin.math.sqrt

/**
 * §2.2 GNSS lag: the shift, −1.5…+1.5 s in 40 ms steps, that best correlates 1 Hz IMU longitudinal acceleration with
 * GNSS `dv/dt` over the first 300 s. Used only if the correlation is ≥ 0.6; ties go to the smallest |lag|.
 */
internal class GnssLag(private val cfg: SignalConfig) {
    /** @param lonG vehicle-frame longitudinal per R1 sample, g, before the lag is known. */
    fun estimateS(lonG: DoubleArray, ok: (Int) -> Boolean, gnss: List<GatedFix>): Double {
        val fixes = gnss.filter { it.tS <= cfg.lagWindowS }
        if (fixes.size < MIN_POINTS) return 0.0
        val dvdt = (1 until fixes.size - 1).map { i ->
            fixes[i].tS to (fixes[i + 1].speedMps - fixes[i - 1].speedMps) / (fixes[i + 1].tS - fixes[i - 1].tS) / Units.G
        }
        var best = 0.0
        var bestCorr = Double.NEGATIVE_INFINITY
        val steps = cfg.lagMaxMs / cfg.lagStepMs
        for (s in -steps..steps) {
            val lag = s * cfg.lagStepMs / MS
            val corr = correlation(dvdt, lonG, ok, lag) ?: continue
            val better = corr > bestCorr + EPS || (corr > bestCorr - EPS && kotlin.math.abs(lag) < kotlin.math.abs(best))
            if (better) {
                bestCorr = corr
                best = lag
            }
        }
        return if (bestCorr >= cfg.lagMinCorrelation) best else 0.0
    }

    private fun correlation(dvdt: List<Pair<Double, Double>>, lon: DoubleArray, ok: (Int) -> Boolean, lagS: Double): Double? {
        val xs = mutableListOf<Double>()
        val ys = mutableListOf<Double>()
        for ((t, g) in dvdt) {
            val from = ((t - HALF + lagS) * cfg.fs).toInt()
            val to = ((t + HALF + lagS) * cfg.fs).toInt()
            if (from < 0 || to > lon.size) continue
            var sum = 0.0
            var n = 0
            for (k in from until to) if (ok(k)) { sum += lon[k]; n++ }
            if (n > 0) {
                xs += sum / n
                ys += g
            }
        }
        if (xs.size < MIN_POINTS) return null
        val mx = xs.sum() / xs.size
        val my = ys.sum() / ys.size
        var sxy = 0.0
        var sxx = 0.0
        var syy = 0.0
        for (i in xs.indices) {
            sxy += (xs[i] - mx) * (ys[i] - my)
            sxx += (xs[i] - mx) * (xs[i] - mx)
            syy += (ys[i] - my) * (ys[i] - my)
        }
        return if (sxx > 0 && syy > 0) sxy / sqrt(sxx * syy) else null
    }

    private companion object {
        const val MS = 1000.0
        const val HALF = 0.5
        const val MIN_POINTS = 10
        const val EPS = 1e-12
    }
}
