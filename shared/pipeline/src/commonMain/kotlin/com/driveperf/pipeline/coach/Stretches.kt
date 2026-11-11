package com.driveperf.pipeline.coach

import com.driveperf.domain.score.TipCategory
import com.driveperf.domain.trip.Stretch
import com.driveperf.pipeline.score.R1Analysis

/** §6.2 jerk window and §6.3 cleanest stretch, from the R1 analysis. */
internal object Stretches {
    private const val WINDOW_S = 5
    private const val MIN_CLEAN_S = 30.0
    private const val CLEAN_FRACTION = 0.5
    private const val EVENT_MARGIN = 25

    /** The 5 s window with the most excess jerk (tie earliest), or null when there is none. */
    @Suppress("ReturnCount")
    fun jerkWindow(a: R1Analysis): JerkWindow? {
        val fs = a.trace.fs
        val n = a.trace.size
        val w = WINDOW_S * fs
        if (n < w) return null
        val lon = DoubleArray(n + 1)
        val lat = DoubleArray(n + 1)
        for (k in 0 until n) {
            lon[k + 1] = lon[k] + a.jerk.excessLon[k]
            lat[k + 1] = lat[k] + a.jerk.excessLat[k]
        }
        var best = -1
        var bestSum = 0.0
        for (s in 0..n - w) {
            val sum = lon[s + w] - lon[s] + lat[s + w] - lat[s]
            if (sum > bestSum) {
                bestSum = sum
                best = s
            }
        }
        if (best < 0) return null
        val lonPart = lon[best + w] - lon[best]
        val category = if (lonPart >= lat[best + w] - lat[best]) {
            val brakeShare = (best until best + w).count { a.a15Lon[it] < 0 && a.jerk.excessLon[it] > 0 }
            val accelShare = (best until best + w).count { a.a15Lon[it] >= 0 && a.jerk.excessLon[it] > 0 }
            if (brakeShare >= accelShare) TipCategory.BRAKING else TipCategory.ACCELERATION
        } else {
            TipCategory.CORNERING
        }
        return JerkWindow(category, best.toDouble() / fs, (best + w).toDouble() / fs)
    }

    /** §6.3: the longest run of ok, moving samples outside event windows with no jerk above half the band; ≥ 30 s. */
    @Suppress("CyclomaticComplexMethod") // §6.3 conditions, all required per sample
    fun cleanest(a: R1Analysis): Stretch? {
        val fs = a.trace.fs
        val n = a.trace.size
        val inEvent = BooleanArray(n)
        for (e in a.events) for (k in maxOf(0, e.start - EVENT_MARGIN)..minOf(n - 1, e.end + EVENT_MARGIN)) inEvent[k] = true
        fun clean(k: Int): Boolean = a.trace.ok(k) && a.moving[k] && !inEvent[k] &&
            a.jerk.excessLon[k] <= CLEAN_FRACTION * a.jerk.bandLon[k] && a.jerk.excessLat[k] <= CLEAN_FRACTION * a.jerk.jyMps3
        var bestStart = -1
        var bestLen = 0
        var k = 0
        while (k < n) {
            if (!clean(k)) {
                k++
            } else {
                val start = k
                while (k < n && clean(k)) k++
                if (k - start > bestLen) {
                    bestLen = k - start
                    bestStart = start
                }
            }
        }
        if (bestStart < 0 || bestLen < MIN_CLEAN_S * fs) return null
        var distance = 0.0
        for (j in bestStart until bestStart + bestLen) distance += a.v[j] / fs
        return Stretch(distance, bestStart.toDouble() / fs, (bestStart + bestLen).toDouble() / fs)
    }
}
