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

    /** Excluded spans from quality bits; a bit must cover at least one moving sample to make a span (§2.7). */
    fun excluded(quality: IntArray, moving: BooleanArray, estimatedEndS: Double?): List<TripSegment> {
        val fs = cfg.fs.toDouble()
        val firstScorable = ((estimatedEndS ?: 0.0) * fs).toInt()
        val out = mutableListOf<TripSegment>()
        fun add(bit: Int, reason: SegmentReason) {
            var k = firstScorable
            while (k < quality.size) {
                if (quality[k] and bit == 0) { k++; continue }
                val start = k
                var anyMoving = false
                while (k < quality.size && quality[k] and bit != 0) { anyMoving = anyMoving || moving[k]; k++ }
                if (anyMoving || bit != QualityBits.LOW_RATE && bit != QualityBits.IMU_GAP) {
                    out += TripSegment(SegmentKind.EXCLUDED, start / fs, k / fs, reason)
                }
            }
        }
        add(QualityBits.EXCLUDED_PHONE, SegmentReason.PHONE_MOVED)
        add(QualityBits.NOT_ALIGNED, SegmentReason.NOT_ALIGNED)
        add(QualityBits.LOW_RATE, SegmentReason.LOW_IMU_RATE)
        add(QualityBits.IMU_GAP, SegmentReason.IMU_GAP)
        return out
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
    }
}
