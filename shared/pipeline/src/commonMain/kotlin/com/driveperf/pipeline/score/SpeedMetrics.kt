package com.driveperf.pipeline.score

import kotlin.math.sqrt

/** Speed-derived metrics from gated 1 Hz GNSS (§3). Speed never raises a score: wobble uses absolute m/s only. */
internal object SpeedMetrics {
    private const val WINDOW = 21
    private const val HOP = 10
    private const val MIN_SPEED = 5.0
    private const val MAX_DRIFT = 1.5
    private const val MIN_WINDOWS = 3
    private const val MAX_STEP_S = 1.5

    /** Wobble (§3) and the display-only `speedConsistencyCv`, or nulls with fewer than 3 qualifying windows. */
    fun wobble(gnss: List<R1Fix>): Pair<Double?, Double?> {
        val stds = mutableListOf<Double>()
        val cvs = mutableListOf<Double>()
        var i = 0
        while (i + WINDOW <= gnss.size) {
            val w = gnss.subList(i, i + WINDOW)
            val continuous = (1 until WINDOW).all { w[it].tS - w[it - 1].tS <= MAX_STEP_S }
            val speeds = w.map { it.speedMps }
            if (continuous && speeds.min() >= MIN_SPEED && kotlin.math.abs(speeds.last() - speeds.first()) <= MAX_DRIFT) {
                val mean = speeds.sum() / WINDOW
                var ss = 0.0
                for (s in speeds) ss += (s - mean) * (s - mean)
                val std = sqrt(ss / WINDOW)
                stds += std
                cvs += std / mean
            }
            i += HOP
        }
        if (stds.size < MIN_WINDOWS) return null to null
        stds.sort()
        cvs.sort()
        return stds[stds.size / 2] to cvs[cvs.size / 2]
    }
}

/** Stop counting on the moving mask (§3). */
internal object StopMetrics {
    /** Stopped runs of at least [minS], merging runs split by under 3 s of motion; leading stop excluded. */
    fun stops(moving: BooleanArray, fs: Int, minS: Double): List<IntRange> {
        val merged = mutableListOf<IntRange>()
        val mergeGap = MERGE_S * fs
        for (run in MovingMask.stoppedRuns(moving)) {
            val last = merged.lastOrNull()
            if (last != null && run.first - last.last - 1 < mergeGap) merged[merged.lastIndex] = last.first..run.last else merged += run
        }
        return merged.filter { it.first > 0 && it.count() >= minS * fs }
    }

    /** §5.9 abrupt accelerations: runs of `a3_lon ≥ 0.18 g` (exit 0.12 g) lasting at least 1.0 s. */
    fun abruptAccels(a3Lon: DoubleArray, ok: (Int) -> Boolean, fs: Int): Int {
        var count = 0
        var k = 0
        while (k < a3Lon.size) {
            if (a3Lon[k] >= ABRUPT_ENTER && ok(k)) {
                var start = k
                while (start > 0 && a3Lon[start - 1] >= ABRUPT_EXIT && ok(start - 1)) start--
                var end = k
                while (end + 1 < a3Lon.size && a3Lon[end + 1] >= ABRUPT_EXIT && ok(end + 1)) end++
                if ((end - start + 1).toDouble() / fs >= ABRUPT_MIN_S) count++
                k = end + 1
            } else {
                k++
            }
        }
        return count
    }

    private const val MERGE_S = 3.0
    private const val ABRUPT_ENTER = 0.18
    private const val ABRUPT_EXIT = 0.12
    private const val ABRUPT_MIN_S = 1.0
}
