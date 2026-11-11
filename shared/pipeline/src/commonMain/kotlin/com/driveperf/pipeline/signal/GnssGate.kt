package com.driveperf.pipeline.signal

import com.driveperf.domain.capture.LocationSample
import com.driveperf.pipeline.geo.Geo
import kotlin.math.abs

/** A fix that passed the §2.3 gate, on the trip time base (seconds from `startedAt`). */
internal data class GatedFix(
    val tS: Double,
    val lat: Double,
    val lng: Double,
    val altM: Double?,
    val hAccM: Double,
    val vAccM: Double?,
    val speedMps: Double,
    val speedAccMps: Double?,
    val courseDeg: Double?,
    val courseAccDeg: Double?,
    /** Speed was missing and derived from positions. */
    val derived: Boolean,
)

/** §2.3 GNSS gate. Rejected fixes stay in R0 and are simply absent here. */
internal class GnssGate(private val cfg: SignalConfig) {
    /** @param t0Ns the monotonic time of `t = 0`. */
    fun gate(fixes: List<LocationSample>, t0Ns: Long): List<GatedFix> {
        val out = mutableListOf<GatedFix>()
        for (f in fixes.sortedBy { it.tMonoNs }) {
            val hAcc = f.hAccM
            val tS = (f.tMonoNs - t0Ns) / NS
            val prev = out.lastOrNull()
            val speed = f.speedMps ?: derivedSpeed(out, f, tS)
            val ok = (speed == null || speed <= cfg.maxSpeedMps) && (prev == null || plausible(prev, f, tS, speed))
            if (hAcc != null && hAcc <= cfg.maxHAccM && ok) {
                out += GatedFix(
                    tS, f.lat, f.lng, f.altM, hAcc, f.vAccM, speed ?: 0.0, f.speedAccMps, f.courseDeg, f.courseAccDeg,
                    derived = f.speedMps == null,
                )
            }
        }
        return out
    }

    private fun plausible(prev: GatedFix, f: LocationSample, tS: Double, speed: Double?): Boolean {
        val dt = tS - prev.tS
        if (dt <= 0) return false
        val jump = speed != null && abs(speed - prev.speedMps) / dt > cfg.maxAccelMps2
        val dist = Geo.haversineM(prev.lat, prev.lng, f.lat, f.lng)
        val limit = maxOf(cfg.teleportFactor * maxOf(speed ?: 0.0, prev.speedMps), cfg.teleportFloorMps)
        return !jump && dist / dt <= limit
    }

    /** Distance over the last 3 fixes divided by elapsed time; null with no history. */
    private fun derivedSpeed(history: List<GatedFix>, f: LocationSample, tS: Double): Double? {
        val tail = history.takeLast(DERIVE_FIXES - 1)
        if (tail.isEmpty()) return null
        val points = tail.map { it.lat to it.lng } + (f.lat to f.lng)
        var d = 0.0
        for (i in 1 until points.size) d += Geo.haversineM(points[i - 1].first, points[i - 1].second, points[i].first, points[i].second)
        val dt = tS - tail.first().tS
        return if (dt > 0) d / dt else null
    }

    private companion object {
        const val NS = 1e9
        const val DERIVE_FIXES = 3
    }
}
