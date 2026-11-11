package com.driveperf.pipeline.signal

import com.driveperf.foundation.Units
import com.driveperf.pipeline.score.Baselines
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** The phone-to-vehicle rotation of one mount epoch (§2.5). */
internal data class EpochAlignment(
    val epoch: IntRange,
    val converged: Boolean,
    val eF: V3,
    val eL: V3,
    val eU: V3,
    val biasLon: Double,
    val biasLat: Double,
    /** Earliest `t` from which the running estimate meets step 6 and stays within 5° of the final φ. */
    val settledS: Double?,
)

/**
 * §2.5 alignment: one constant rotation per mount epoch, estimated in batch from GNSS longitudinal and centripetal
 * acceleration and applied retroactively. The GNSS speed sign resolves the 180° ambiguity.
 */
internal class Alignment(private val cfg: SignalConfig) {
    private data class Pair1Hz(val tS: Double, val p1: Double, val p2: Double, val l: Double, val c: Double)

    fun align(sig: DeviceSignal, epoch: IntRange, gnss: List<GatedFix>, lagS: Double): EpochAlignment {
        var up = V3.ZERO
        for (k in epoch) up += sig.gravity[k]
        val eU = up.unit()
        val axis = V3.AXES.minBy { abs(it dot eU) }
        val h1 = (axis - eU * (axis dot eU)).unit()
        val h2 = eU cross h1
        val pairs = inputs(sig, epoch, gnss, lagS, h1, h2)
        val kept = pairs.filter { abs(it.l) >= cfg.alignMinExcitationG || abs(it.c) >= cfg.alignMinExcitationG }
        val solution = solve(kept)
        val phi = solution?.phi ?: 0.0
        val eF = h1 * cos(phi) + h2 * sin(phi)
        val eL = eU cross eF
        val longEnough = epoch.count() >= cfg.alignMinEpochS * cfg.fs
        val converged = solution != null && solution.converged && longEnough
        val bLon = bias(kept.map { (it.p1 * cos(phi) + it.p2 * sin(phi)) - it.l })
        val bLat = bias(kept.map { (-it.p1 * sin(phi) + it.p2 * cos(phi)) - it.c })
        return EpochAlignment(epoch, converged, eF, eL, eU, bLon, bLat, if (converged) settled(kept, phi) else null)
    }

    private data class Solution(val phi: Double, val converged: Boolean)

    private fun solve(kept: List<Pair1Hz>): Solution? {
        if (kept.isEmpty()) return null
        var sc = 0.0
        var ss = 0.0
        var pp = 0.0
        var lc = 0.0
        for (s in kept) {
            sc += s.p1 * s.l + s.p2 * s.c
            ss += s.p2 * s.l - s.p1 * s.c
            pp += s.p1 * s.p1 + s.p2 * s.p2
            lc += s.l * s.l + s.c * s.c
        }
        val rho = if (pp > 0 && lc > 0) sqrt(sc * sc + ss * ss) / sqrt(pp * lc) else 0.0
        val excited = lc >= cfg.alignMinKept * cfg.alignExcitationFloorG * cfg.alignExcitationFloorG
        return Solution(atan2(ss, sc), kept.size >= cfg.alignMinKept && rho >= cfg.alignMinCoherence && excited)
    }

    /** First kept time from which every running solution is converged and within 5° of the final φ. */
    private fun settled(kept: List<Pair1Hz>, phi: Double): Double? {
        var settledAt: Double? = null
        for (i in kept.indices) {
            val s = solve(kept.subList(0, i + 1))
            val ok = s != null && s.converged && angleDiffDeg(s.phi, phi) <= cfg.alignSettleDeg
            settledAt = if (ok) settledAt ?: kept[i].tS else null
        }
        return settledAt
    }

    private fun bias(values: List<Double>): Double {
        val b = Baselines.median(values) ?: 0.0
        return if (abs(b) > cfg.alignMaxBiasG) 0.0 else b
    }

    /** 1 Hz pairs: mean device acceleration around each fix against GNSS longitudinal and centripetal (step 3). */
    @Suppress("LongParameterList", "CyclomaticComplexMethod", "LoopWithTooManyJumpStatements") // §2.5 step 3
    private fun inputs(sig: DeviceSignal, epoch: IntRange, gnss: List<GatedFix>, lagS: Double, h1: V3, h2: V3): List<Pair1Hz> {
        val out = mutableListOf<Pair1Hz>()
        val usable = gnss.filter { f ->
            f.speedMps >= cfg.alignMinSpeedMps && (f.courseAccDeg == null || f.courseAccDeg <= cfg.alignMaxCourseAccDeg)
        }
        for (i in 1 until usable.size - 1) {
            val a = usable[i - 1]
            val b = usable[i]
            val c = usable[i + 1]
            val span = c.tS - a.tS
            if (span <= 0 || span > MAX_PAIR_SPAN_S) continue
            val from = ((a.tS + lagS) * cfg.fs).toInt()
            val to = ((c.tS + lagS) * cfg.fs).toInt()
            if (from < epoch.first || to > epoch.last) continue
            var p1 = 0.0
            var p2 = 0.0
            var n = 0
            for (k in from until to) {
                if (sig.quality[k] and com.driveperf.pipeline.score.QualityBits.IMU_GAP != 0) continue
                p1 += sig.accel[k] dot h1
                p2 += sig.accel[k] dot h2
                n++
            }
            val course0 = a.courseDeg
            val course1 = c.courseDeg
            if (n == 0 || course0 == null || course1 == null) continue
            val l = (c.speedMps - a.speedMps) / span / Units.G
            val dCourse = wrapDeg(course1 - course0) * DEG_TO_RAD
            val cent = -b.speedMps * dCourse / span / Units.G
            out += Pair1Hz(b.tS, p1 / n, p2 / n, l, cent)
        }
        return out
    }

    private companion object {
        const val DEG_TO_RAD = PI / 180.0
        const val RAD_TO_DEG = 180.0 / PI
        const val MAX_PAIR_SPAN_S = 3.0

        fun wrapDeg(d: Double): Double {
            var x = d % FULL_TURN
            if (x > HALF_TURN) x -= FULL_TURN
            if (x < -HALF_TURN) x += FULL_TURN
            return x
        }

        fun angleDiffDeg(a: Double, b: Double): Double = abs(wrapDeg((a - b) * RAD_TO_DEG))

        const val FULL_TURN = 360.0
        const val HALF_TURN = 180.0
    }
}
