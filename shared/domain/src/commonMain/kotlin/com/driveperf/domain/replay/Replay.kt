package com.driveperf.domain.replay

import com.driveperf.domain.trip.BoundingBox
import kotlin.math.floor
import kotlin.math.ln

/**
 * One trip ready to replay on a map (F-RPL-01) with its data-quality overlay (F-RPL-08). Both apps draw from this, so
 * the route, the marker and the overlay are identical on iOS and Android.
 *
 * [track] is the time-ordered route: the parking spot first when the start was estimated, then the gated 1 Hz fixes
 * (or the preview track's rows when [summaryOnly]). [spans] are the non-observed overlay spans, sorted and
 * non-overlapping, on the trip time base `[0, durationS]`. Each stretch between two track points takes the style of
 * the span at its midpoint, so a stretch never mixes styles.
 *
 * @property bbox fits the map: the trip's bounds, grown to include every track point (the parking spot included).
 * @property summaryOnly the detailed trace is gone (EC-STO, F-HIS-03.4), so the route is the ≤ 400-row preview track.
 */
public class Replay(
    public val tripId: String,
    public val bbox: BoundingBox,
    public val durationS: Double,
    public val spans: List<ReplaySpan>,
    public val summaryOnly: Boolean,
    public val track: List<ReplayPoint>,
) {
    init {
        require(track.isNotEmpty()) { "a replay needs at least one point" }
    }

    private val times: DoubleArray = DoubleArray(track.size) { track[it].tS }

    /** Style of each stretch `track[i]`–`track[i + 1]`. */
    private val stretchStyles: List<SegmentStyle> =
        (0 until track.size - 1).map { styleAt((track[it].tS + track[it + 1].tS) / 2) }

    /** Runs of equal style as inclusive index ranges into [track]; neighbouring runs share their boundary index. */
    private val runs: List<IntRange> = buildList {
        var start = 0
        for (i in 1..stretchStyles.size) {
            if (i == stretchStyles.size || stretchStyles[i] != stretchStyles[start]) {
                add(start..i)
                start = i
            }
        }
    }

    /** Douglas–Peucker importance of each point, computed once for every zoom bucket. */
    private val importance: DoubleArray by lazy { RouteSimplifier.importance(track, runs) }

    /** Bearing of each stretch, carried over stretches too short to have one. */
    private val bearings: Array<Double?> by lazy { stretchBearings() }

    /**
     * The route as styled polylines at map [zoomBucket] (the web-mercator zoom level, 0–22, as Google Maps' camera
     * zoom; see [zoomBucketFor] for MapKit). Douglas–Peucker per run with a tolerance of one map point at that zoom
     * (ADR-005). Every run keeps its end points, so styles never bleed into each other. Cheap after the first call.
     */
    public fun segments(zoomBucket: Int): List<ReplaySegment> {
        if (stretchStyles.isEmpty()) return listOf(ReplaySegment(styleAt(track[0].tS), track))
        val zoom = zoomBucket.coerceIn(0, MAX_ZOOM)
        val toleranceM = RouteGeo.metresPerPoint(zoom, (bbox.minLat + bbox.maxLat) / 2) * TOLERANCE_POINTS
        return runs.map { run ->
            ReplaySegment(stretchStyles[run.first], run.filter { importance[it] > toleranceM }.map { track[it] })
        }
    }

    /**
     * The marker at trip time [tS], clamped to `[0, durationS]`. Display-only linear interpolation between neighbouring
     * track points (F-RPL-01). Inside a [SegmentStyle.NO_GPS] stretch the marker stays at the last fix with no speed
     * or bearing, then jumps to the next fix: a gap is never interpolated (EC-GPS, EC-TUN).
     */
    public fun frameAt(tS: Double): ReplayFrame {
        val t = if (tS.isNaN()) 0.0 else tS.coerceIn(0.0, durationS)
        val style = styleAt(t)
        val i = stretchIndex(t)
        return when {
            i < 0 -> frameOn(track.first(), t, bearings.firstOrNull(), style)
            i >= stretchStyles.size -> frameOn(track.last(), t, bearings.lastOrNull(), style)
            stretchStyles[i] == SegmentStyle.NO_GPS -> frameOn(track[i], t, null, SegmentStyle.NO_GPS)
            else -> interpolate(track[i], track[i + 1], t, bearings[i], style)
        }
    }

    private fun interpolate(a: ReplayPoint, b: ReplayPoint, t: Double, bearing: Double?, style: SegmentStyle): ReplayFrame {
        val dt = b.tS - a.tS
        val f = if (dt > 0) (t - a.tS) / dt else 0.0
        val speed = if (a.speedMps != null && b.speedMps != null) a.speedMps + (b.speedMps - a.speedMps) * f else null
        return ReplayFrame(t, a.lat + (b.lat - a.lat) * f, a.lng + (b.lng - a.lng) * f, speed, bearing, style)
    }

    /** Trip seconds per wall-clock second: [PlaybackSpeed.AUTO] plays the whole trip in about 60 s (F-RPL-01 AC2). */
    public fun playbackRate(speed: PlaybackSpeed): Double = when (speed) {
        PlaybackSpeed.AUTO -> if (durationS > 0) durationS / AUTO_PLAY_S else 1.0
        PlaybackSpeed.X1 -> 1.0
        PlaybackSpeed.X10 -> 10.0
        PlaybackSpeed.X30 -> 30.0
    }

    /** The overlay span at [tS] for the marker's chip (F-RPL-08 AC2), or null on an observed stretch. */
    public fun spanAt(tS: Double): ReplaySpan? =
        spans.firstOrNull { tS >= it.t0S && (tS < it.t1S || (tS == it.t1S && it.t1S >= durationS)) }

    private fun styleAt(t: Double): SegmentStyle = spanAt(t)?.style ?: SegmentStyle.OBSERVED

    /** Index of the stretch holding [t]: -1 before the first point, `stretchStyles.size` at or after the last. */
    private fun stretchIndex(t: Double): Int = when {
        t < times[0] -> -1
        t >= times[times.size - 1] -> stretchStyles.size
        else -> {
            var lo = 0
            var hi = times.size - 1
            while (hi - lo > 1) {
                val mid = (lo + hi) ushr 1
                if (times[mid] <= t) lo = mid else hi = mid
            }
            lo
        }
    }

    private fun frameOn(p: ReplayPoint, t: Double, bearing: Double?, style: SegmentStyle): ReplayFrame {
        val gap = style == SegmentStyle.NO_GPS
        return ReplayFrame(t, p.lat, p.lng, if (gap) null else p.speedMps, if (gap) null else bearing, style)
    }

    private fun stretchBearings(): Array<Double?> {
        val out = arrayOfNulls<Double>(stretchStyles.size)
        var last: Double? = null
        for (i in out.indices) {
            val a = track[i]
            val b = track[i + 1]
            val moved = stretchStyles[i] != SegmentStyle.NO_GPS &&
                RouteGeo.distanceM(a.lat, a.lng, b.lat, b.lng) >= MIN_BEARING_M
            if (moved) last = RouteGeo.bearingDeg(a.lat, a.lng, b.lat, b.lng)
            out[i] = last
        }
        // Before the car first moves, face the way it will leave.
        val first = out.firstOrNull { it != null }
        for (i in out.indices) if (out[i] == null) out[i] = first else break
        return out
    }

    public companion object {
        /** The deepest zoom bucket; deeper zooms use it. */
        public const val MAX_ZOOM: Int = 22

        private const val AUTO_PLAY_S = 60.0
        private const val TOLERANCE_POINTS = 1.0
        private const val MIN_BEARING_M = 2.0
        private const val WORLD_DEGREES = 360.0
        private const val TILE_POINTS = 256.0

        /**
         * The zoom bucket for a map [viewWidthPoints] wide showing [longitudeDeltaDeg] degrees of longitude, as
         * MapKit's `MKCoordinateRegion.span.longitudeDelta` gives it.
         */
        public fun zoomBucketFor(longitudeDeltaDeg: Double, viewWidthPoints: Double): Int {
            if (longitudeDeltaDeg <= 0 || viewWidthPoints <= 0) return MAX_ZOOM
            val zoom = ln(WORLD_DEGREES * viewWidthPoints / (TILE_POINTS * longitudeDeltaDeg)) / ln(2.0)
            return floor(zoom).toInt().coerceIn(0, MAX_ZOOM)
        }
    }
}
