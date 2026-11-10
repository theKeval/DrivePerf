package com.driveperf.pipeline.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** Geodesy helpers. Results are rounded so that platform differences in `sin`/`cos` cannot propagate (§0.5). */
internal object Geo {
    private const val EARTH_RADIUS_M = 6_371_008.8
    private const val DEG_TO_RAD = kotlin.math.PI / 180.0
    private const val CENTI = 100.0

    /** Great-circle distance in metres, rounded to 0.01 m (§0.5 rule 2). */
    fun haversineM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = (lat2 - lat1) * DEG_TO_RAD
        val dLng = (lng2 - lng1) * DEG_TO_RAD
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * DEG_TO_RAD) * cos(lat2 * DEG_TO_RAD) * sin(dLng / 2) * sin(dLng / 2)
        val d = 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
        return floor(d * CENTI + 0.5) / CENTI
    }
}
