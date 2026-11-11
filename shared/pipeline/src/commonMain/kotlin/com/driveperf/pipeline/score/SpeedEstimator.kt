package com.driveperf.pipeline.score

import com.driveperf.foundation.Units

/**
 * The internal speed estimate `v(t)` (§2.3): a two-state Kalman filter (speed, accelerometer bias) at the R1 rate
 * over longitudinal acceleration, with gated GNSS speed as the measurement. Only `+ − × ÷` (§0.5 rule 2). Never
 * shown to users; it feeds the moving mask, stops and event speeds.
 */
internal object SpeedEstimator {
    private const val P0_V = 4.0
    private const val P0_B = 0.01
    private const val Q_V = 0.04
    private const val Q_B = 4e-6
    private const val R_MIN = 0.09
    private const val STALE_FIX_S = 3.0

    /**
     * @param still optional IMU-stillness mask: during a GNSS gap, a still sample with the last fix more than 3 s
     *   old sets `v = 0`.
     */
    fun estimate(trace: R1Trace, still: BooleanArray? = null): DoubleArray {
        val n = trace.size
        val dt = 1.0 / trace.fs
        val fixes = trace.gnss
        val out = DoubleArray(n)
        var v = fixes.firstOrNull()?.speedMps ?: 0.0
        var b = 0.0
        var p00 = P0_V
        var p01 = 0.0
        var p10 = 0.0
        var p11 = P0_B
        var next = 0
        var lastFixT = Double.NEGATIVE_INFINITY
        for (k in 0 until n) {
            val t = k * dt
            v += (trace.lonG[k] * Units.G - b) * dt
            val n00 = p00 - dt * p10 - dt * (p01 - dt * p11) + Q_V * dt
            val n01 = p01 - dt * p11
            val n10 = p10 - dt * p11
            val n11 = p11 + Q_B * dt
            p00 = n00
            p01 = n01
            p10 = n10
            p11 = n11
            while (next < fixes.size && fixes[next].tS <= t) {
                val fix = fixes[next++]
                val acc = fix.speedAccMps
                val r = if (acc == null) R_MIN else maxOf(acc * acc, R_MIN)
                val y = fix.speedMps - v
                val s = p00 + r
                val k0 = p00 / s
                val k1 = p10 / s
                v += k0 * y
                b += k1 * y
                val q00 = (1 - k0) * p00
                val q01 = (1 - k0) * p01
                val q10 = p10 - k1 * p00
                val q11 = p11 - k1 * p01
                p00 = q00
                p01 = q01
                p10 = q10
                p11 = q11
                lastFixT = fix.tS
            }
            if (still != null && still[k] && t - lastFixT > STALE_FIX_S) v = 0.0
            v = maxOf(v, 0.0)
            out[k] = v
        }
        return out
    }
}

/** The moving flag (§3): moving at `v ≥ 1.0 m/s`, stopped below `0.5 m/s`, starting stopped. */
internal object MovingMask {
    fun of(v: DoubleArray, cfg: ScoringConfig): BooleanArray {
        val out = BooleanArray(v.size)
        var moving = false
        for (k in v.indices) {
            moving = if (moving) v[k] >= cfg.movingOffMps else v[k] >= cfg.movingOnMps
            out[k] = moving
        }
        return out
    }

    /** Runs of `false` as `[start, endExclusive)` sample ranges. */
    fun stoppedRuns(moving: BooleanArray): List<IntRange> {
        val runs = mutableListOf<IntRange>()
        var k = 0
        while (k < moving.size) {
            if (moving[k]) {
                k++
            } else {
                val start = k
                while (k < moving.size && !moving[k]) k++
                runs += start until k
            }
        }
        return runs
    }
}
