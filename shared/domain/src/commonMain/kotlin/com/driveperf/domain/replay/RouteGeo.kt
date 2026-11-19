package com.driveperf.domain.replay

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** The few geodesy helpers Replay needs. Distances in metres, angles in degrees. */
internal object RouteGeo {
    private const val EARTH_RADIUS_M = 6_371_008.8
    private const val DEG = PI / 180.0
    private const val FULL_TURN = 360.0

    /** Great-circle distance. */
    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = (lat2 - lat1) * DEG
        val dLng = (lng2 - lng1) * DEG
        val h = sin(dLat / 2).let { it * it } + cos(lat1 * DEG) * cos(lat2 * DEG) * sin(dLng / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Initial great-circle bearing from the first point to the second, clockwise from north, `[0, 360)`. */
    fun bearingDeg(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val p1 = lat1 * DEG
        val p2 = lat2 * DEG
        val dLng = (lng2 - lng1) * DEG
        val y = sin(dLng) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dLng)
        val deg = atan2(y, x) / DEG
        return (deg + FULL_TURN) % FULL_TURN
    }

    /**
     * Distance from [p] to the segment [a]–[b] on a local equirectangular projection around [a]. Accurate to well under
     * a metre over the few kilometres a simplified run spans, which is all the simplifier needs.
     */
    fun crossTrackM(p: ReplayPoint, a: ReplayPoint, b: ReplayPoint): Double {
        val k = cos(a.lat * DEG) * DEG * EARTH_RADIUS_M
        val m = DEG * EARTH_RADIUS_M
        val bx = (b.lng - a.lng) * k
        val by = (b.lat - a.lat) * m
        val px = (p.lng - a.lng) * k
        val py = (p.lat - a.lat) * m
        val len2 = bx * bx + by * by
        val u = if (len2 == 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        val dx = px - u * bx
        val dy = py - u * by
        return sqrt(dx * dx + dy * dy)
    }

    /** Ground size of one map point at web-mercator [zoom] and [lat] (256-point tiles, as MapKit and Google Maps). */
    fun metresPerPoint(zoom: Int, lat: Double): Double =
        2 * PI * MERCATOR_RADIUS_M * cos(lat * DEG) / (TILE_POINTS * (1L shl zoom))

    private const val TILE_POINTS = 256.0
    private const val MERCATOR_RADIUS_M = 6_378_137.0
}
