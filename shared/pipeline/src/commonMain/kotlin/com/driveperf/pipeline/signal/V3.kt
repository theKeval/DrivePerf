package com.driveperf.pipeline.signal

import kotlin.math.sqrt

/** A small immutable 3-vector for the R0 → R1 stage, where trigonometry is allowed (§0.5 rule 1). */
internal data class V3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: V3): V3 = V3(x + o.x, y + o.y, z + o.z)

    operator fun minus(o: V3): V3 = V3(x - o.x, y - o.y, z - o.z)

    operator fun times(s: Double): V3 = V3(x * s, y * s, z * s)

    infix fun dot(o: V3): Double = x * o.x + y * o.y + z * o.z

    infix fun cross(o: V3): V3 = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)

    fun norm(): Double = sqrt(this dot this)

    fun unit(): V3 {
        val n = norm()
        return if (n == 0.0) this else this * (1.0 / n)
    }

    companion object {
        val ZERO: V3 = V3(0.0, 0.0, 0.0)
        val AXES: List<V3> = listOf(V3(1.0, 0.0, 0.0), V3(0.0, 1.0, 0.0), V3(0.0, 0.0, 1.0))
    }
}
