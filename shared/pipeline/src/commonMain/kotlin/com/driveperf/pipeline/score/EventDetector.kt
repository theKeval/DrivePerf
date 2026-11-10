package com.driveperf.pipeline.score

import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.EventType
import com.driveperf.foundation.Units
import kotlin.math.abs

/** One detected event before IDs and positions (§4.5). Indices are R1 samples. */
internal data class DetectedEvent(
    val type: EventType,
    val sign: Int,
    val start: Int,
    val end: Int,
    val peak: Int,
    val peakG: Double,
    val margin: Double,
    val confidence: Confidence,
)

/** The three rule-based detectors (§4.1–§4.4), run on `a3` over ok samples only. */
internal class EventDetector(private val cfg: ScoringConfig) {
    private data class Spec(val type: EventType, val sign: Int, val enter: Double, val exit: Double, val minS: Double)

    private val specs = listOf(
        Spec(EventType.BRAKE, -1, cfg.brakeEnterG, cfg.brakeExitG, cfg.brakeMinS),
        Spec(EventType.ACCEL, 1, cfg.accelEnterG, cfg.accelExitG, cfg.accelMinS),
        Spec(EventType.CORNER, 1, cfg.cornerEnterG, cfg.cornerExitG, cfg.cornerMinS),
        Spec(EventType.CORNER, -1, cfg.cornerEnterG, cfg.cornerExitG, cfg.cornerMinS),
    )

    /**
     * @param a3Lon `a3` longitudinal, g. @param a3Lat `a3` lateral, g. @param v speed estimate, m/s.
     * @param impact the §4.3 impact mask. @param limited the trip badge is Limited (§4.4 downgrade).
     */
    @Suppress("LongParameterList")
    fun detect(
        trace: R1Trace,
        a3Lon: DoubleArray,
        a3Lat: DoubleArray,
        v: DoubleArray,
        impact: BooleanArray,
        limited: Boolean,
    ): List<DetectedEvent> {
        val events = specs.flatMap { spec ->
            val channel = if (spec.type == EventType.CORNER) a3Lat else a3Lon
            merge(intervals(trace, channel, spec)).mapNotNull { (s, e) ->
                evaluate(trace, channel, spec, s, e, v, impact, limited, a3Lon)
            }
        }
        val byMargin = compareByDescending<DetectedEvent> { it.margin }.thenBy { it.peak }
        val capped = if (events.size <= cfg.maxEvents) events else events.sortedWith(byMargin).take(cfg.maxEvents)
        return capped.sortedWith(compareBy<DetectedEvent> { it.peak }.thenBy { it.type.name })
    }

    private fun intervals(trace: R1Trace, c: DoubleArray, spec: Spec): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        val n = c.size
        var k = 0
        while (k < n) {
            if (spec.sign * c[k] >= spec.enter && trace.ok(k)) {
                var start = k
                while (start > 0 && spec.sign * c[start - 1] >= spec.exit && trace.ok(start - 1)) start--
                var end = k
                while (end < n - 1 && spec.sign * c[end + 1] >= spec.exit && trace.ok(end + 1)) end++
                out += start to end
                k = end + 1
            } else {
                k++
            }
        }
        return out
    }

    private fun merge(intervals: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        val gap = (cfg.eventMergeS * cfg.fs).toInt()
        val out = mutableListOf<Pair<Int, Int>>()
        for (iv in intervals) {
            val last = out.lastOrNull()
            if (last != null && iv.first - last.second <= gap) out[out.lastIndex] = last.first to iv.second else out += iv
        }
        return out
    }

    @Suppress("LongParameterList")
    private fun evaluate(
        trace: R1Trace,
        c: DoubleArray,
        spec: Spec,
        start: Int,
        end: Int,
        v: DoubleArray,
        impact: BooleanArray,
        limited: Boolean,
        a3Lon: DoubleArray,
    ): DetectedEvent? {
        var peak = start
        for (k in start..end) if (spec.sign * c[k] > spec.sign * c[peak]) peak = k
        val p = spec.sign * c[peak]
        val durS = (end - start + 1).toDouble() / cfg.fs
        val gate = when (spec.type) {
            EventType.BRAKE -> v[start] >= cfg.eventSpeedGateMps
            EventType.ACCEL -> v[end] >= cfg.eventSpeedGateMps
            EventType.CORNER -> v[start] >= cfg.eventSpeedGateMps &&
                abs(trace.yawDps[peak]) >= cfg.cornerYawGateDps && trace.yawDps[peak] * spec.sign > 0
        }
        if (durS < spec.minS || !gate || roadImpact(trace, impact, start, end, peak, p, durS)) return null
        val m = (p - spec.enter) / spec.enter
        val confidence = confidence(trace, spec, start, end, m, limited, a3Lon)
        return DetectedEvent(spec.type, spec.sign, start, end, peak, p, m, confidence)
    }

    /** §4.3: a short event at an impact whose vertical spike dwarfs it is the road, not the driver. */
    @Suppress("LongParameterList")
    private fun roadImpact(trace: R1Trace, impact: BooleanArray, start: Int, end: Int, peak: Int, p: Double, durS: Double): Boolean {
        if (!impact[peak] || durS >= cfg.impactMaxS) return false
        val from = maxOf(0, start - cfg.impactMargin)
        val to = minOf(trace.size - 1, end + cfg.impactMargin)
        var maxVert = 0.0
        for (k in from..to) maxVert = maxOf(maxVert, abs(trace.vertG[k]))
        return maxVert >= cfg.impactRatio * p
    }

    @Suppress("LongParameterList")
    private fun confidence(trace: R1Trace, spec: Spec, start: Int, end: Int, m: Double, limited: Boolean, a3Lon: DoubleArray): Confidence {
        var level = when {
            m >= cfg.confidenceHigh -> 2
            m >= cfg.confidenceMedium -> 1
            else -> 0
        }
        val t0 = start.toDouble() / cfg.fs - 1.0
        val t1 = end.toDouble() / cfg.fs + 1.0
        val window = trace.gnss.filter { it.tS in t0..t1 }
        if (spec.type != EventType.CORNER && window.size >= 2) {
            var integral = 0.0
            for (k in start..end) integral += a3Lon[k] * Units.G / cfg.fs
            val dvGnss = window.last().speedMps - window.first().speedMps
            if (abs(dvGnss) < HALF * abs(integral)) level--
        }
        if (limited || window.isEmpty()) level--
        return when {
            level >= 2 -> Confidence.HIGH
            level == 1 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
    }

    /** §4.3 impact mask: within ±15 samples of any `|vert_g| ≥ 0.40 g`. */
    fun impactMask(trace: R1Trace): BooleanArray {
        val mask = BooleanArray(trace.size)
        for (k in 0 until trace.size) {
            if (abs(trace.vertG[k]) >= cfg.impactVertG) {
                for (j in maxOf(0, k - cfg.impactHalfWidth)..minOf(trace.size - 1, k + cfg.impactHalfWidth)) mask[j] = true
            }
        }
        return mask
    }

    private companion object {
        const val HALF = 0.5
    }
}
