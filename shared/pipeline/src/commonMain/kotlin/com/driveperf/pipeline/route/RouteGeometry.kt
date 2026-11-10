package com.driveperf.pipeline.route

import com.driveperf.domain.trip.PreviewRow
import com.driveperf.pipeline.geo.Geo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

/** A point in the local plane, metres east (x) and north (y) of the route start. */
internal data class XY(val x: Double, val y: Double)

/** §7.3 geometry. The only trigonometry is one `cos(lat0)`, rounded to 6 decimals and computed once (§0.5). */
internal object RouteGeometry {
    private const val M_PER_DEG = 111_320.0
    private const val MICRO = 1e6
    private const val DEG_TO_RAD = PI / 180.0
    private const val ESTIMATED = 1
    private const val GAP = 2

    /** Rows usable for path similarity: estimated rows and rows in labelled gaps are dropped (§7.3). */
    fun usable(rows: List<PreviewRow>): List<PreviewRow> = rows.filter { it.quality != ESTIMATED && it.quality != GAP }

    fun project(rows: List<PreviewRow>, lat0: Double, lng0: Double): List<XY> {
        val c = floor(cos(lat0 * DEG_TO_RAD) * MICRO + HALF) / MICRO
        return rows.map { XY((it.lng - lng0) * M_PER_DEG * c, (it.lat - lat0) * M_PER_DEG) }
    }

    fun length(p: List<XY>): Double {
        var d = 0.0
        for (i in 1 until p.size) d += dist(p[i - 1], p[i])
        return d
    }

    /** Points every [step] metres along the polyline, first and last included. */
    fun resample(p: List<XY>, step: Double): List<XY> {
        if (p.size < 2) return p
        val out = mutableListOf(p.first())
        var carry = 0.0
        for (i in 1 until p.size) {
            val a = p[i - 1]
            val b = p[i]
            val seg = dist(a, b)
            var at = step - carry
            while (at <= seg) {
                val f = at / seg
                out += XY(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
                at += step
            }
            carry = seg - (at - step)
        }
        if (out.last() != p.last()) out += p.last()
        return out
    }

    /** Discrete Fréchet distance: the standard O(nm) dynamic programme on squared distances, square root at the end. */
    fun frechet(a: List<XY>, b: List<XY>): Double {
        if (a.isEmpty() || b.isEmpty()) return Double.POSITIVE_INFINITY
        val m = b.size
        var prev = DoubleArray(m)
        var cur = DoubleArray(m)
        for (i in a.indices) {
            for (j in 0 until m) {
                val d = sq(a[i], b[j])
                cur[j] = when {
                    i == 0 && j == 0 -> d
                    i == 0 -> maxOf(cur[j - 1], d)
                    j == 0 -> maxOf(prev[0], d)
                    else -> maxOf(minOf(prev[j], prev[j - 1], cur[j - 1]), d)
                }
            }
            val t = prev
            prev = cur
            cur = t
        }
        return sqrt(prev[m - 1])
    }

    /** The prefix of [rows] within [metres] of path length from the start. */
    fun firstMetres(rows: List<PreviewRow>, metres: Double): List<PreviewRow> {
        if (rows.isEmpty()) return rows
        val out = mutableListOf(rows.first())
        var d = 0.0
        for (i in 1 until rows.size) {
            d += Geo.haversineM(rows[i - 1].lat, rows[i - 1].lng, rows[i].lat, rows[i].lng)
            if (d > metres) break
            out += rows[i]
        }
        return out
    }

    fun dist(a: XY, b: XY): Double = sqrt(sq(a, b))

    private fun sq(a: XY, b: XY): Double = (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)

    private const val HALF = 0.5
}
