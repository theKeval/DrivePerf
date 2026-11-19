package com.driveperf.domain.analysis

/**
 * One channel over trip time (seconds from `Trip.startedAt`), in time order. A `NaN` value is a gap: nothing was
 * measured there, and a line through it breaks (EC-GPS). Either uniform (sample `i` at `t0S + i / rateHz`, the R1 motion
 * channels) or sampled at given times (the 1 Hz fixes, the preview track).
 */
public class Series private constructor(
    private val times: DoubleArray?,
    private val t0S: Double,
    private val rateHz: Double,
    public val values: DoubleArray,
) {
    init {
        require(times == null || times.size == values.size)
        require(times != null || rateHz > 0.0)
    }

    public val size: Int get() = values.size

    /** The time of sample [i]. */
    public fun timeAt(i: Int): Double = times?.get(i) ?: (t0S + i / rateHz)

    /** The first sample at or after [tS], or [size] when there is none. */
    public fun lowerBound(tS: Double): Int {
        if (times == null) {
            val raw = kotlin.math.ceil((tS - t0S) * rateHz - EPSILON)
            return raw.coerceIn(0.0, size.toDouble()).toInt()
        }
        var lo = 0
        var hi = size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (times[mid] < tS) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /**
     * The value at [tS], linear between the two samples around it. `NaN` outside the series and next to a gap, so a
     * cursor in a gap reads "—" rather than an invented value.
     */
    public fun valueAt(tS: Double): Double {
        if (size == 0 || tS < timeAt(0) - EPSILON || tS > timeAt(size - 1) + EPSILON) return Double.NaN
        val b = lowerBound(tS).coerceAtMost(size - 1)
        val tb = timeAt(b)
        val ta = if (b == 0) tb else timeAt(b - 1)
        return if (b == 0 || tb - tS <= EPSILON) values[b] else values[b - 1] + (values[b] - values[b - 1]) * (tS - ta) / (tb - ta)
    }

    /** The largest finite value, or `NaN` when there is none. */
    public fun maxValue(): Double = values.fold(Double.NaN) { m, v -> if (v.isNaN() || (!m.isNaN() && m >= v)) m else v }

    /** The smallest finite value, or `NaN` when there is none. */
    public fun minValue(): Double = values.fold(Double.NaN) { m, v -> if (v.isNaN() || (!m.isNaN() && m <= v)) m else v }

    public companion object {
        private const val EPSILON = 1e-9

        /** Sample `i` at `t0S + i / rateHz`. */
        public fun uniform(rateHz: Double, values: DoubleArray, t0S: Double = 0.0): Series = Series(null, t0S, rateHz, values)

        /** Samples at [times], which must be in order. */
        public fun sampled(times: DoubleArray, values: DoubleArray): Series = Series(times, 0.0, 0.0, values)
    }
}

/** What one range of samples holds: its finite extremes, how many finite samples, and whether a gap is in it. */
internal class RangeStats {
    var min: Double = Double.POSITIVE_INFINITY
    var max: Double = Double.NEGATIVE_INFINITY
    var count: Int = 0
    var gap: Boolean = false

    fun reset() {
        min = Double.POSITIVE_INFINITY
        max = Double.NEGATIVE_INFINITY
        count = 0
        gap = false
    }

    fun add(v: Double) {
        if (v.isNaN()) {
            gap = true
        } else {
            if (v < min) min = v
            if (v > max) max = v
            count++
        }
    }

    fun addBlock(bMin: Float, bMax: Float, bCount: Int, bGap: Boolean) {
        if (bGap) gap = true
        if (bCount == 0) return
        if (bMin < min) min = bMin.toDouble()
        if (bMax > max) max = bMax.toDouble()
        count += bCount
    }
}

/**
 * Exact min/max over any index range in `O(BASE + log n)`, so a zoom or pan over an hour of 50 Hz data never scans
 * every sample (S-ANL-01 performance). Level 1 holds blocks of [BASE] samples, each level above pairs the one below.
 * Floats keep the levels small: about a quarter of the raw channel.
 */
internal class MinMaxPyramid(private val values: DoubleArray) {
    private val mins = ArrayList<FloatArray>()
    private val maxs = ArrayList<FloatArray>()
    private val counts = ArrayList<IntArray>()
    private val gaps = ArrayList<BooleanArray>()

    init {
        buildLevel1()
        while (mins.last().size > 1) buildNext()
    }

    private fun buildLevel1() {
        val n = (values.size + BASE - 1) / BASE
        val mn = FloatArray(n)
        val mx = FloatArray(n)
        val ct = IntArray(n)
        val gp = BooleanArray(n)
        val acc = RangeStats()
        for (b in 0 until n) {
            acc.reset()
            for (i in b * BASE until minOf(values.size, (b + 1) * BASE)) acc.add(values[i])
            mn[b] = acc.min.toFloat()
            mx[b] = acc.max.toFloat()
            ct[b] = acc.count
            gp[b] = acc.gap
        }
        mins += mn
        maxs += mx
        counts += ct
        gaps += gp
    }

    private fun buildNext() {
        val pm = mins.last()
        val px = maxs.last()
        val pc = counts.last()
        val pg = gaps.last()
        val n = (pm.size + 1) / 2
        val mn = FloatArray(n) { b -> if (2 * b + 1 < pm.size) minOf(pm[2 * b], pm[2 * b + 1]) else pm[2 * b] }
        val mx = FloatArray(n) { b -> if (2 * b + 1 < px.size) maxOf(px[2 * b], px[2 * b + 1]) else px[2 * b] }
        val ct = IntArray(n) { b -> pc[2 * b] + if (2 * b + 1 < pc.size) pc[2 * b + 1] else 0 }
        val gp = BooleanArray(n) { b -> pg[2 * b] || (2 * b + 1 < pg.size && pg[2 * b + 1]) }
        mins += mn
        maxs += mx
        counts += ct
        gaps += gp
    }

    /** Fills [out] with the samples `[from, to)`. */
    fun query(from: Int, to: Int, out: RangeStats) {
        out.reset()
        var lo = from.coerceAtLeast(0)
        var hi = to.coerceAtMost(values.size)
        if (hi - lo < 2 * BASE) {
            for (i in lo until hi) out.add(values[i])
            return
        }
        while (lo < hi && lo % BASE != 0) out.add(values[lo++])
        while (hi > lo && hi % BASE != 0) out.add(values[--hi])
        lo /= BASE
        hi /= BASE
        var level = 0
        while (lo < hi) {
            if (level == mins.lastIndex) {
                for (b in lo until hi) take(level, b, out)
                return
            }
            if (lo % 2 != 0) take(level, lo++, out)
            if (hi % 2 != 0 && hi > lo) take(level, --hi, out)
            lo /= 2
            hi /= 2
            level++
        }
    }

    private fun take(level: Int, block: Int, out: RangeStats) {
        out.addBlock(mins[level][block], maxs[level][block], counts[level][block], gaps[level][block])
    }

    companion object {
        const val BASE: Int = 8
    }
}
