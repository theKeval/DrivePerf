package com.driveperf.domain.replay

import com.driveperf.domain.trip.BoundingBox
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// DRIVE-200 SEAM (R1 stand-in): the shared Replay contract, copied here so the Android screen builds before the
// shared model (branch DRIVE-200-replay-model) merges. Same package and shape as the agreed contract, so this whole
// file is deleted when R1 lands and the screen picks up `com.driveperf.domain.replay` from :shared:domain unchanged.
// It mirrors R1's PR #76 API (the `track` parameter and `spanAt`), without its simplification and label details.

public enum class SegmentStyle { OBSERVED, ESTIMATED, NO_GPS, EXCLUDED }

public data class ReplayPoint(val tS: Double, val lat: Double, val lng: Double, val speedMps: Double?)

public data class ReplaySegment(val style: SegmentStyle, val points: List<ReplayPoint>)

public data class ReplaySpan(val style: SegmentStyle, val t0S: Double, val t1S: Double)

public data class ReplayFrame(
    val tS: Double,
    val lat: Double,
    val lng: Double,
    val speedMps: Double?,
    val bearingDeg: Double?,
    val style: SegmentStyle,
)

public enum class PlaybackSpeed { AUTO, X1, X10, X30 }

public class Replay(
    public val tripId: String,
    public val bbox: BoundingBox,
    public val durationS: Double,
    public val spans: List<ReplaySpan>,
    public val summaryOnly: Boolean,
    public val track: List<ReplayPoint>,
) {
    /** The stand-in does no simplification: runs of one style, each stretch styled by the span at its midpoint. */
    @Suppress("UnusedParameter")
    public fun segments(zoomBucket: Int): List<ReplaySegment> {
        val runs = mutableListOf<ReplaySegment>()
        var style: SegmentStyle? = null
        var run = mutableListOf<ReplayPoint>()
        for (i in 1 until track.size) {
            val s = spanAt((track[i - 1].tS + track[i].tS) / 2)?.style ?: SegmentStyle.OBSERVED
            if (s != style) {
                style?.let { runs += ReplaySegment(it, run) }
                style = s
                run = mutableListOf(track[i - 1])
            }
            run += track[i]
        }
        style?.let { runs += ReplaySegment(it, run) }
        return runs
    }

    /** Linear interpolation between fixes; inside a NO_GPS span it holds the last fix with no speed or bearing. */
    public fun frameAt(tS: Double): ReplayFrame {
        val t = tS.coerceIn(0.0, durationS)
        val next = track.indexOfFirst { it.tS >= t }.let { if (it < 0) track.lastIndex else it }
        val b = track[next]
        val a = track[(next - 1).coerceAtLeast(0)]
        val style = spanAt(t)?.style ?: SegmentStyle.OBSERVED
        if (style == SegmentStyle.NO_GPS) return ReplayFrame(t, a.lat, a.lng, null, null, style)
        val f = if (b.tS > a.tS) ((t - a.tS) / (b.tS - a.tS)).coerceIn(0.0, 1.0) else 0.0
        val bearing = if (a === b) null else bearing(a, b)
        val speed = a.speedMps?.let { sa -> b.speedMps?.let { sb -> sa + (sb - sa) * f } }
        return ReplayFrame(t, a.lat + (b.lat - a.lat) * f, a.lng + (b.lng - a.lng) * f, speed, bearing, style)
    }

    public fun spanAt(tS: Double): ReplaySpan? = spans.firstOrNull { tS >= it.t0S && tS < it.t1S }

    public fun playbackRate(speed: PlaybackSpeed): Double = when (speed) {
        PlaybackSpeed.AUTO -> durationS / AUTO_SECONDS
        PlaybackSpeed.X1 -> 1.0
        PlaybackSpeed.X10 -> 10.0
        PlaybackSpeed.X30 -> 30.0
    }

    private fun bearing(a: ReplayPoint, b: ReplayPoint): Double {
        val la = Math.toRadians(a.lat)
        val lb = Math.toRadians(b.lat)
        val dl = Math.toRadians(b.lng - a.lng)
        val y = sin(dl) * cos(lb)
        val x = cos(la) * sin(lb) - sin(la) * cos(lb) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + FULL_TURN) % FULL_TURN
    }

    private companion object {
        const val AUTO_SECONDS = 60.0
        const val FULL_TURN = 360.0
    }
}

public interface ReplayRepository {
    public suspend fun replay(tripId: String): Replay?
}
