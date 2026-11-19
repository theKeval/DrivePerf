package com.driveperf.feature.replay

import com.driveperf.domain.replay.Replay
import com.driveperf.domain.replay.ReplaySpan
import com.driveperf.domain.trip.BoundingBox
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Web Mercator fit of a trip's bounding box into a `width × height` box, inside [insets] (the top cluster and the
 * playback bar float over the map), used by the plain-canvas renderer. [x] and [y] allocate nothing, so the marker
 * can be projected every frame.
 */
class ReplayProjection(bbox: BoundingBox, width: Float, height: Float, insets: Insets) {
    /** Space kept clear on each side, in pixels. */
    data class Insets(val left: Float, val top: Float, val right: Float, val bottom: Float)

    private val minX = worldX(bbox.minLng)
    private val minY = worldY(bbox.maxLat)
    private val offsetX: Double
    private val offsetY: Double

    /** Pixels per unit of Web Mercator world coordinates. */
    val pixelsPerWorld: Double

    init {
        val spanX = (worldX(bbox.maxLng) - minX).coerceAtLeast(MIN_SPAN)
        val spanY = (worldY(bbox.minLat) - minY).coerceAtLeast(MIN_SPAN)
        val innerW = (width - insets.left - insets.right).coerceAtLeast(1f)
        val innerH = (height - insets.top - insets.bottom).coerceAtLeast(1f)
        pixelsPerWorld = min(innerW / spanX, innerH / spanY)
        offsetX = insets.left + (innerW - spanX * pixelsPerWorld) / 2
        offsetY = insets.top + (innerH - spanY * pixelsPerWorld) / 2
    }

    /** The Google-style zoom level of this fit, for `Replay.segments(zoomBucket)` (ADR-005). */
    fun zoomBucket(density: Float): Int = zoomFor(pixelsPerWorld / density)

    fun x(lng: Double): Float = (offsetX + (worldX(lng) - minX) * pixelsPerWorld).toFloat()

    fun y(lat: Double): Float = (offsetY + (worldY(lat) - minY) * pixelsPerWorld).toFloat()

    companion object {
        private const val TILE = 256.0
        private const val MIN_SPAN = 1e-7

        /** Longitude to Web Mercator x in 0..1. */
        fun worldX(lng: Double): Double = (lng + 180.0) / 360.0

        /** Latitude to Web Mercator y in 0..1 (north at 0). */
        fun worldY(lat: Double): Double {
            val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
            return (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0
        }

        /** World pixels per unit of world coordinates is `256 × 2^zoom`; floor it into a bucket. */
        fun zoomFor(pixelsPerWorld: Double): Int = floor(log2(pixelsPerWorld / TILE)).toInt().coerceIn(0, Replay.MAX_ZOOM)
    }
}

/**
 * Per-trip lookups the screen needs every frame, computed once when the replay loads: distance so far (readout) and
 * the quality span at a time (F-RPL-08 AC2 label). Lookups are allocation-free.
 */
class ReplayTimeline(val replay: Replay) {
    private val times: DoubleArray
    private val distances: DoubleArray
    val durationS: Double = replay.durationS

    init {
        val points = replay.track
        times = DoubleArray(points.size) { points[it].tS }
        distances = DoubleArray(points.size)
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            distances[i] = distances[i - 1] + haversineM(a.lat, a.lng, b.lat, b.lng)
        }
    }

    /** Total route length in metres. */
    val totalM: Double get() = if (distances.isEmpty()) 0.0 else distances.last()

    /** Metres travelled by [tS], interpolated between fixes. */
    fun distanceAt(tS: Double): Double {
        if (times.isEmpty()) return 0.0
        val t = tS.coerceIn(times[0], times.last())
        var lo = 0
        var hi = times.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (times[mid] <= t) lo = mid else hi = mid
        }
        val span = times[hi] - times[lo]
        val f = if (span > 0) ((t - times[lo]) / span).coerceIn(0.0, 1.0) else 1.0
        return distances[lo] + (distances[hi] - distances[lo]) * f
    }

    /** The overlay span (estimated, no GPS, excluded) the marker is in at [tS], or null on observed data. */
    fun spanAt(tS: Double): ReplaySpan? = replay.spanAt(tS)

    private companion object {
        const val EARTH_RADIUS_M = 6_371_000.0

        fun haversineM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val h = sin(dLat / 2).let { it * it } +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).let { it * it }
            return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
        }
    }
}
