package com.driveperf.domain.analysis

/**
 * The timeline's x-axis (F-RPL-05 AC3): trip time, or distance driven. Every lane, event tick and quality band goes
 * through the same scale, so they stay aligned whichever axis and zoom are on.
 */
public interface AxisScale {
    /** The axis length: seconds for time, metres for distance. */
    public val length: Double

    /** Trip time to axis units. */
    public fun xOf(tS: Double): Double

    /** Axis units back to trip time. Where the car stood still on the distance axis, the earliest such time. */
    public fun tOf(x: Double): Double
}

/** Seconds from the start. */
public class TimeAxis(override val length: Double) : AxisScale {
    override fun xOf(tS: Double): Double = tS.coerceIn(0.0, length)

    override fun tOf(x: Double): Double = x.coerceIn(0.0, length)
}

/**
 * Metres driven by trip time: piecewise linear through ([times], [metres]), both in order and [metres] never
 * decreasing. Before the first point the distance is 0, after the last it is the total.
 */
public class DistanceAxis(private val times: DoubleArray, private val metres: DoubleArray, private val durationS: Double) : AxisScale {
    init {
        require(times.size == metres.size)
    }

    override val length: Double = metres.lastOrNull() ?: 0.0

    override fun xOf(tS: Double): Double {
        if (times.isEmpty()) return 0.0
        val i = upper(times, tS)
        return when {
            i == 0 -> metres[0]
            i == times.size -> metres.last()
            else -> lerp(times[i - 1], times[i], metres[i - 1], metres[i], tS)
        }
    }

    override fun tOf(x: Double): Double {
        if (times.isEmpty() || length <= 0.0) return 0.0
        if (x <= metres[0]) return 0.0
        val i = lower(metres, x)
        if (i == metres.size) return durationS
        val t = if (metres[i] == metres[i - 1]) times[i - 1] else lerp(metres[i - 1], metres[i], times[i - 1], times[i], x)
        return t.coerceIn(0.0, durationS)
    }

    private fun lerp(x0: Double, x1: Double, y0: Double, y1: Double, x: Double): Double =
        if (x1 == x0) y0 else y0 + (y1 - y0) * (x - x0) / (x1 - x0)

    private companion object {
        /** First index whose value is greater than [v]. */
        fun upper(a: DoubleArray, v: Double): Int {
            var lo = 0
            var hi = a.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (a[mid] <= v) lo = mid + 1 else hi = mid
            }
            return lo
        }

        /** First index whose value is at least [v]. */
        fun lower(a: DoubleArray, v: Double): Int {
            var lo = 0
            var hi = a.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (a[mid] < v) lo = mid + 1 else hi = mid
            }
            return lo
        }
    }
}

/** A lane's value range: [min] at the bottom of the lane, [max] at the top. */
public data class LaneRange(val min: Double, val max: Double) {
    /** [v] as a height in `[0, 1]` (outside when [v] is out of range; the app clips). `NaN` stays `NaN`. */
    public fun norm(v: Double): Double = if (max == min) 0.5 else (v - min) / (max - min)
}

/**
 * A polyline in one lane's box: [xs] and [ys] both in `[0, 1]` of the visible window and of the lane height (y up).
 * A `NaN` y breaks the line. Points just outside the window keep the line running to its edges; the app clips.
 */
public class LanePath(public val xs: DoubleArray, public val ys: DoubleArray) {
    public val size: Int get() = xs.size
}

/**
 * One channel ready to draw: [series] plus its [MinMaxPyramid], built once per trip. [path] downsamples any window to
 * about `2 × buckets` points, so drawing cost does not depend on the trip length or the zoom.
 */
public class ChannelTrack(public val series: Series) {
    private val pyramid by lazy { MinMaxPyramid(series.values) }

    /** The window `[x0, x1)` of [axis] as a path in [buckets] columns, scaled by [range]. */
    public fun path(axis: AxisScale, x0: Double, x1: Double, buckets: Int, range: LaneRange): LanePath {
        val width = x1 - x0
        if (series.size == 0 || width <= 0.0 || buckets <= 0) return LanePath(DoubleArray(0), DoubleArray(0))
        val i0 = (series.lowerBound(axis.tOf(x0)) - 1).coerceAtLeast(0)
        val i1 = (series.lowerBound(axis.tOf(x1)) + 1).coerceAtMost(series.size)
        return if (i1 - i0 <= 2 * buckets) raw(axis, x0, width, i0, i1, range) else bucketed(axis, x0, width, buckets, range)
    }

    private fun raw(axis: AxisScale, x0: Double, width: Double, i0: Int, i1: Int, range: LaneRange): LanePath {
        val n = (i1 - i0).coerceAtLeast(0)
        val xs = DoubleArray(n) { (axis.xOf(series.timeAt(i0 + it)) - x0) / width }
        val ys = DoubleArray(n) { range.norm(series.values[i0 + it]) }
        return LanePath(xs, ys)
    }

    private fun bucketed(axis: AxisScale, x0: Double, width: Double, buckets: Int, range: LaneRange): LanePath {
        val xs = DoubleArray(buckets * 3)
        val ys = DoubleArray(buckets * 3)
        var n = 0
        fun emit(x: Double, y: Double) {
            xs[n] = x
            ys[n] = y
            n++
        }
        val stats = RangeStats()
        var from = series.lowerBound(axis.tOf(x0))
        for (k in 0 until buckets) {
            val edge = axis.tOf(x0 + (k + 1) * width / buckets)
            val to = series.lowerBound(if (k == buckets - 1) edge + TAIL_S else edge)
            pyramid.query(from, to, stats)
            from = to
            val xc = (k + HALF) / buckets
            if (stats.count > 0) {
                emit(xc, range.norm(stats.min))
                emit(xc, range.norm(stats.max))
            }
            if (stats.gap) emit(xc, Double.NaN)
        }
        return LanePath(xs.copyOf(n), ys.copyOf(n))
    }

    private companion object {
        const val HALF = 0.5

        /** The last column includes a sample exactly at the window's end. */
        const val TAIL_S = 1e-6
    }
}
