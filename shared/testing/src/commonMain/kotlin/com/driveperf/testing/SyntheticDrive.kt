package com.driveperf.testing

import com.driveperf.foundation.Units
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * The §8.3 golden-scenario generator (pipeline-and-scoring.md): vehicle-frame signals at [fs] Hz built from
 * trapezoid pulses, with the speed they imply. Synthetic only: no positions at all.
 *
 * Build with the DSL, then call [build]. `lon` and `lat` are in g and quantised to milli-g; yaw follows from lateral
 * acceleration and speed; vertical is zero unless [overwrite] sets it.
 */
@Suppress("TooManyFunctions") // the §8.3 generator vocabulary
public class SyntheticDrive(public val fs: Int = 25, v0: Double = 0.0) {
    private val lon = mutableListOf<Double>()
    private val lat = mutableListOf<Double>()
    private val vs = mutableListOf<Double>()
    private var v = v0

    /** The current speed, m/s. */
    public val speed: Double get() = v

    public fun push(a: Double, y: Double = 0.0) {
        lon += a
        lat += y
        v = maxOf(0.0, v + a * Units.G / fs)
        vs += v
    }

    /** A trapezoid on the longitudinal channel: ramp at jerk [j] (m/s³) to [p] g, hold [tH] s, ramp down. */
    public fun trap(p: Double, j: Double, tH: Double) {
        shape(p, j, tH) { push(it) }
    }

    /** The same shape on the lateral channel, with no longitudinal acceleration. */
    public fun corner(p: Double, j: Double, tH: Double) {
        shape(p, j, tH) { push(0.0, it) }
    }

    private inline fun shape(p: Double, j: Double, tH: Double, emit: (Double) -> Unit) {
        val n = round(abs(p) * Units.G / j * fs).toInt()
        val m = round(tH * fs).toInt()
        for (i in 0 until n) emit(p * (i + HALF) / n)
        repeat(m) { emit(p) }
        for (i in 0 until n) emit(p * (n - i - HALF) / n)
    }

    public fun hold(t: Double) {
        repeat(round(t * fs).toInt()) { push(0.0) }
    }

    /** Changes speed to [vt] with peak [peak] g and jerk [j]. */
    public fun toSpeed(vt: Double, peak: Double, j: Double) {
        val d = vt - v
        val s = sign(d)
        val p = abs(peak) * Units.G
        val tR = p / j
        val dvr = p * tR
        if (abs(d) < dvr) trap(s * sqrt(abs(d) * j) / Units.G, j, 0.0) else trap(s * abs(peak), j, (abs(d) - dvr) / p)
    }

    /** Brakes to rest and waits [dwell] s. */
    public fun stop(peak: Double, j: Double, dwell: Double) {
        toSpeed(0.0, peak, j)
        v = 0.0
        hold(dwell)
    }

    /** The §8.3 city loop: 8 × (accelerate, cruise, corner, cruise, stop or roll). */
    @Suppress("LongParameterList")
    public fun city(j: Double, pa: Double, pb: Double, corner: Double = 0.15, cj: Double = j, rolling: Boolean = false) {
        for (i in 0..CITY_BLOCKS) {
            toSpeed(CITY_SPEED, pa, j)
            hold(CITY_CRUISE_S)
            corner(if (i % 2 == 1) corner else -corner, cj, CORNER_HOLD_S)
            hold(CITY_AFTER_S)
            if (rolling) {
                toSpeed(ROLLING_SPEED, pb, j)
                hold(ROLL_S)
            } else {
                stop(pb, j, STOP_DWELL_S)
            }
        }
    }

    private val overrides = mutableMapOf<Int, Pair<Double?, Double?>>()

    /** Overwrites `lon` and/or `vert` at samples `[from, from + count)` after generation (G-09, G-10). */
    public fun overwrite(from: Int, count: Int, lonG: Double?, vertG: Double?) {
        for (k in from until from + count) overrides[k] = lonG to vertG
    }

    /** The finished signals. GNSS is 1 Hz from the generator's own speed unless [gnssSpeed] says otherwise. */
    public fun build(gnssSpeed: ((Int, Double) -> Double)? = null): Signals {
        val n = lon.size
        val lonQ = DoubleArray(n) { quantise(overrides[it]?.first ?: lon[it]) }
        val latQ = DoubleArray(n) { quantise(lat[it]) }
        val vert = DoubleArray(n) { overrides[it]?.second ?: 0.0 }
        val yaw = DoubleArray(n) { lat[it] * Units.G / maxOf(vs[it], 1.0) * RAD_TO_DEG }
        val fixes = floor(n.toDouble() / fs).toInt()
        val gnss = List(fixes) { i -> (gnssSpeed?.invoke(i, vs[i * fs]) ?: vs[i * fs]) }
        return Signals(fs, lonQ, latQ, vert, yaw, vs.toDoubleArray(), gnss)
    }

    /** Generated signals: per-sample arrays and one GNSS speed per second (`t = i` s). */
    @Suppress("LongParameterList") // one array per R1 channel
    public class Signals internal constructor(
        public val fs: Int,
        public val lonG: DoubleArray,
        public val latG: DoubleArray,
        public val vertG: DoubleArray,
        public val yawDps: DoubleArray,
        public val speedMps: DoubleArray,
        public val gnssSpeedMps: List<Double>,
    )

    private fun quantise(x: Double): Double = round(x * MILLI) / MILLI

    private companion object {
        const val HALF = 0.5
        const val MILLI = 1000.0
        const val RAD_TO_DEG = 180.0 / PI
        const val CITY_BLOCKS = 7
        const val CITY_SPEED = 13.9
        const val CITY_CRUISE_S = 20.0
        const val CORNER_HOLD_S = 2.0
        const val CITY_AFTER_S = 10.0
        const val ROLLING_SPEED = 2.0
        const val ROLL_S = 0.5
        const val STOP_DWELL_S = 15.0
    }
}
