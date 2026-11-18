package com.driveperf.pipeline.signal

import com.driveperf.domain.capture.CaptureMarkType
import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.SegmentReason
import com.driveperf.domain.trip.TripSegment
import com.driveperf.pipeline.geo.Geo
import com.driveperf.pipeline.score.QualityBits

/** §2.7 spans and GNSS gaps. Gaps are only measured while the car moves (Spike 1 change 4). */
internal class Spans(private val cfg: SignalConfig) {
    data class Gaps(val spans: List<TripSegment>, val chordM: Double)

    /** GNSS gaps over 3 s while moving; tunnels; chord distance where plausible (§2.7). */
    @Suppress("LoopWithTooManyJumpStatements") // one rule per guard, as §2.7 lists them
    fun gnssGaps(fixes: List<GatedFix>, still: BooleanArray): Gaps {
        val spans = mutableListOf<TripSegment>()
        var chord = 0.0
        for (i in 1 until fixes.size) {
            val a = fixes[i - 1]
            val b = fixes[i]
            val gap = b.tS - a.tS
            if (gap <= cfg.continuityS) continue
            val imuMoving = fractionNotStill(still, a.tS, b.tS) > HALF
            val moving = a.speedMps >= MOVING_MPS || b.speedMps >= MOVING_MPS || imuMoving
            if (!moving) continue
            if (gap >= cfg.gapLabelS) {
                spans += TripSegment(SegmentKind.GAP, a.tS, b.tS, SegmentReason.NO_GNSS, "gap.labelled")
                continue
            }
            val tunnel = gap >= cfg.tunnelMinS && imuMoving
            spans += TripSegment(SegmentKind.GAP, a.tS, b.tS, if (tunnel) SegmentReason.TUNNEL else SegmentReason.NO_GNSS)
            val d = Geo.haversineM(a.lat, a.lng, b.lat, b.lng)
            val expected = (a.speedMps + b.speedMps) / 2 * gap
            if (expected > 0 && d / expected in cfg.chordMin..cfg.chordMax) chord += d
        }
        return Gaps(spans, chord)
    }

    private fun fractionNotStill(still: BooleanArray, t0: Double, t1: Double): Double {
        val from = (t0 * cfg.fs).toInt().coerceIn(0, still.size)
        val to = (t1 * cfg.fs).toInt().coerceIn(0, still.size)
        if (to <= from) return 0.0
        return (from until to).count { !still[it] }.toDouble() / (to - from)
    }

    /**
     * Excluded spans from quality bits (§2.7), over moving time only: nothing is scored while the car is stopped, so a
     * stop never counts against the scored share (§2.9). Each run of a bit is cut to its moving samples; a stop shorter
     * than a §3 stop (`stopMinS`) inside the run stays in the span, so a crawl does not split it. A run with no moving
     * sample makes no span. The bits themselves are untouched, so events and Smoothness still skip those samples.
     */
    fun excluded(quality: IntArray, moving: BooleanArray, estimatedEndS: Double?): List<TripSegment> {
        val fs = cfg.fs.toDouble()
        val firstScorable = ((estimatedEndS ?: 0.0) * fs).toInt()
        val bridge = (cfg.stopMinS * fs).toInt()
        val out = mutableListOf<TripSegment>()
        fun add(bit: Int, reason: SegmentReason) {
            var k = firstScorable
            while (k < quality.size) {
                if (quality[k] and bit == 0) { k++; continue }
                val start = k
                while (k < quality.size && quality[k] and bit != 0) k++
                movingPieces(moving, start, k, bridge).forEach { (a, b) ->
                    out += TripSegment(SegmentKind.EXCLUDED, a / fs, b / fs, reason)
                }
            }
        }
        add(QualityBits.EXCLUDED_PHONE, SegmentReason.PHONE_MOVED)
        add(QualityBits.NOT_ALIGNED, SegmentReason.NOT_ALIGNED)
        add(QualityBits.LOW_RATE, SegmentReason.LOW_IMU_RATE)
        add(QualityBits.IMU_GAP, SegmentReason.IMU_GAP)
        return out
    }

    /** Moving stretches of `[from, to)` as half-open sample ranges, joined across stops shorter than [bridge] samples. */
    private fun movingPieces(moving: BooleanArray, from: Int, to: Int, bridge: Int): List<Pair<Int, Int>> {
        val pieces = mutableListOf<Pair<Int, Int>>()
        var k = from
        while (k < to) {
            if (!moving.getOrElse(k) { false }) { k++; continue }
            val start = k
            while (k < to && moving.getOrElse(k) { false }) k++
            val last = pieces.lastOrNull()
            if (last != null && start - last.second < bridge) pieces[pieces.lastIndex] = last.first to k else pieces += start to k
        }
        return pieces
    }

    /**
     * §2.7: IMU at 10 Hz while moving is excluded "with THERMAL or LOW_IMU_RATE". A `LOW_IMU_RATE` span that overlaps
     * a time the recorder marked the phone critically hot (THERMAL marks, F-CAP-16) becomes `THERMAL`, so the
     * data-quality notes say why.
     */
    fun relabelThermal(spans: List<TripSegment>, capture: CapturedTrip, t0Ns: Long): List<TripSegment> {
        val hot = mutableListOf<ClosedFloatingPointRange<Double>>()
        var hotSince: Double? = null
        for (m in capture.marks) {
            if (m.type != CaptureMarkType.THERMAL) continue
            val t = (m.tMonoNs - t0Ns) / NS
            val critical = m.detail == CRITICAL
            if (critical && hotSince == null) hotSince = t
            if (!critical && hotSince != null) {
                hot += hotSince..t
                hotSince = null
            }
        }
        hotSince?.let { hot += it..Double.MAX_VALUE }
        return spans.map { span ->
            val overlaps = hot.any { span.t0S < it.endInclusive && span.t1S > it.start }
            if (span.reason == SegmentReason.LOW_IMU_RATE && overlaps) span.copy(reason = SegmentReason.THERMAL) else span
        }
    }

    /** `PAUSED` spans from the recorder's marks, and `APP_KILLED` gaps (§1.7), on the R1 time base. */
    fun fromMarks(capture: CapturedTrip, t0Ns: Long): List<TripSegment> {
        val out = mutableListOf<TripSegment>()
        var pausedAt: Double? = null
        for (m in capture.marks) {
            val t = (m.tMonoNs - t0Ns) / NS
            when (m.type) {
                CaptureMarkType.PAUSED -> pausedAt = t
                CaptureMarkType.RESUMED -> {
                    pausedAt?.let { out += TripSegment(SegmentKind.PAUSED, it, t, null) }
                    pausedAt = null
                }
                CaptureMarkType.APP_KILLED -> {
                    val age = m.detail?.toDoubleOrNull() ?: 0.0
                    out += TripSegment(SegmentKind.GAP, maxOf(0.0, t - age), t, SegmentReason.APP_KILLED)
                }
                else -> Unit
            }
        }
        return out
    }

    private companion object {
        const val HALF = 0.5
        const val MOVING_MPS = 1.0
        const val NS = 1e9
        const val CRITICAL = "critical"
    }
}
