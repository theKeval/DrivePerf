package com.driveperf.pipeline.signal

import com.driveperf.foundation.Units
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/** §2.4 phone-moved detection, IMU stillness and placement on the 25 Hz device-frame signal. */
internal class PhoneMovement(private val cfg: SignalConfig) {
    /** Half-open sample ranges excluded as `PHONE_MOVED`. Each ends one mount epoch and starts the next. */
    fun spans(sig: DeviceSignal, gnss: List<GatedFix>): List<IntRange> {
        val hits = mutableListOf<Int>()
        hits += offAxisHits(sig)
        hits += tiltHits(sig)
        hits += shockHits(sig, gnss)
        if (hits.isEmpty()) return emptyList()
        hits.sort()
        val merge = (cfg.hitMergeS * cfg.fs).toInt()
        val groups = mutableListOf(hits.first()..hits.first())
        for (h in hits.drop(1)) {
            val last = groups.last()
            if (h - last.last <= merge) groups[groups.lastIndex] = last.first..h else groups += h..h
        }
        val before = (cfg.spanBeforeS * cfg.fs).toInt()
        val after = (cfg.spanAfterS * cfg.fs).toInt()
        return groups.map { maxOf(0, it.first - before) until minOf(sig.size, it.last + after) }
    }

    /** Hit A: rotation out of the vertical of at least 45 °/s held for 0.25 s. */
    private fun offAxisHits(sig: DeviceSignal): List<Int> {
        val hold = (cfg.offAxisHoldS * cfg.fs).toInt()
        val hits = mutableListOf<Int>()
        var run = 0
        for (k in 0 until sig.size) {
            val u = sig.gravity[k].unit()
            val w = sig.gyro[k]
            val perp = w - u * (w dot u)
            run = if (perp.norm() >= cfg.offAxisDps) run + 1 else 0
            if (run >= hold) hits += k
        }
        return hits
    }

    /** Hit B: gravity direction at least 20° from its mean over `[t − 5, t − 1]` s. */
    private fun tiltHits(sig: DeviceSignal): List<Int> {
        val fs = cfg.fs
        val hits = mutableListOf<Int>()
        val prefix = Array(sig.size + 1) { V3.ZERO }
        for (k in 0 until sig.size) prefix[k + 1] = prefix[k] + sig.gravity[k]
        val from = TILT_FROM_S * fs
        val to = TILT_TO_S * fs
        for (k in from until sig.size) {
            val mean = (prefix[k - to + 1] - prefix[k - from]).unit()
            val u = sig.gravity[k].unit()
            if (mean.norm() > 0 && u.norm() > 0 && angleDeg(mean, u) >= cfg.tiltDeg) hits += k
        }
        return hits
    }

    /** Hit C: a shock of at least 1.2 g while GNSS says the car changed speed by under 0.25 g within ±1 s. */
    private fun shockHits(sig: DeviceSignal, gnss: List<GatedFix>): List<Int> {
        val hits = mutableListOf<Int>()
        for (k in 0 until sig.size) {
            if (sig.accel[k].norm() < cfg.shockG) continue
            val t = k.toDouble() / cfg.fs
            val near = gnss.filter { abs(it.tS - t) <= 1.0 + 1e-9 }
            val dvdt = if (near.size >= 2) {
                abs(near.last().speedMps - near.first().speedMps) / (near.last().tS - near.first().tS) / Units.G
            } else {
                0.0
            }
            if (dvdt < cfg.shockGnssG) hits += k
        }
        return hits
    }

    /** IMU stillness over 2 s windows (§2.4): std of `|userAccel|` < 0.008 g and `|ω|` < 1 °/s. */
    fun still(sig: DeviceSignal): BooleanArray {
        val w = (cfg.stillWindowS * cfg.fs).toInt()
        val out = BooleanArray(sig.size)
        var start = 0
        while (start < sig.size) {
            val end = minOf(sig.size, start + w)
            var sum = 0.0
            var sumSq = 0.0
            var maxGyro = 0.0
            for (k in start until end) {
                val a = sig.accel[k].norm()
                sum += a
                sumSq += a * a
                maxGyro = maxOf(maxGyro, sig.gyro[k].norm())
            }
            val count = end - start
            val mean = sum / count
            val std = sqrt(maxOf(0.0, sumSq / count - mean * mean))
            val isStill = std < cfg.stillAccelStdG && maxGyro < cfg.stillGyroDps
            for (k in start until end) out[k] = isStill
            start = end
        }
        return out
    }

    /**
     * Standard deviation of the gravity direction, degrees, against its mean. Samples with no gravity (resampling
     * gaps leave zero vectors) have no direction and are skipped.
     */
    fun gravitySpreadDeg(sig: DeviceSignal, range: IntRange): Double {
        val valid = range.filter { sig.gravity[it].norm() > 0.0 }
        if (valid.isEmpty()) return 0.0
        var mean = V3.ZERO
        for (k in valid) mean += sig.gravity[k].unit()
        val m = mean.unit()
        var ss = 0.0
        for (k in valid) {
            val a = angleDeg(m, sig.gravity[k].unit())
            ss += a * a
        }
        return sqrt(ss / valid.size)
    }

    private companion object {
        const val TILT_FROM_S = 5
        const val TILT_TO_S = 1
        const val RAD_TO_DEG = 180.0 / PI

        /** Angle between two directions; 0 when either has none, so a zero vector never yields NaN. */
        fun angleDeg(a: V3, b: V3): Double {
            val norms = a.norm() * b.norm()
            return if (norms == 0.0) 0.0 else acos(((a dot b) / norms).coerceIn(-1.0, 1.0)) * RAD_TO_DEG
        }
    }
}
