package com.driveperf.pipeline.signal

import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.QualityReason
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.TripSegment

/** Inputs to the §2.9 badge. */
internal data class BadgeInputs(
    val totalS: Double,
    val spans: List<TripSegment>,
    val gnssCoveragePct: Double,
    val imuDelivery: Double,
    val scoredMovingS: Double,
    val hasImu: Boolean,
    val hasPreciseLocation: Boolean,
    val anyEpochConverged: Boolean,
    val noGyro: Boolean,
)

/** [share] is f: scored time ÷ (total − estimated start), §2.9. */
internal data class BadgeResult(
    val badge: QualityBadge,
    val reason: QualityReason?,
    val excludedS: Double,
    val scoredS: Double,
    val share: Double,
)

/** §2.9: evaluated top to bottom; the first match wins and sets `reason`. */
internal class Badge(private val cfg: SignalConfig) {
    @Suppress("CyclomaticComplexMethod") // the §2.9 table, top to bottom
    fun evaluate(i: BadgeInputs): BadgeResult {
        val notScored = setOf(SegmentKind.EXCLUDED, SegmentKind.ESTIMATED, SegmentKind.INTERRUPTED)
        val excluded = unionLength(i.spans.filter { it.kind in notScored }).coerceAtMost(i.totalS)
        val scored = i.totalS - excluded
        // The estimated start is never scorable by design (D6), so it is in neither the numerator nor the denominator.
        val estimated = unionLength(i.spans.filter { it.kind == SegmentKind.ESTIMATED }).coerceAtMost(i.totalS)
        val scorable = i.totalS - estimated
        val f = if (scorable > 0) (scored / scorable).coerceIn(0.0, 1.0) else 0.0
        val gnss = i.gnssCoveragePct / PERCENT
        val (badge, reason) = when {
            !i.hasImu -> QualityBadge.UNSCORABLE to QualityReason.NO_IMU
            !i.hasPreciseLocation -> QualityBadge.UNSCORABLE to QualityReason.NO_PRECISE_LOCATION
            !i.anyEpochConverged -> QualityBadge.UNSCORABLE to QualityReason.NOT_ALIGNED
            f < cfg.unscorableShare -> QualityBadge.UNSCORABLE to QualityReason.LOW_SCORED_SHARE
            i.scoredMovingS < cfg.minScoredS -> QualityBadge.UNSCORABLE to QualityReason.TOO_LITTLE_SCORED_TIME
            f < cfg.limitedShare -> QualityBadge.LIMITED to QualityReason.LOW_SCORED_SHARE
            gnss < cfg.limitedGnss -> QualityBadge.LIMITED to QualityReason.LOW_GNSS
            i.noGyro -> QualityBadge.LIMITED to QualityReason.NO_GYRO
            f < cfg.usableShare -> QualityBadge.USABLE to QualityReason.LOW_SCORED_SHARE
            gnss < cfg.usableGnss -> QualityBadge.USABLE to QualityReason.LOW_GNSS
            i.imuDelivery < cfg.usableImu -> QualityBadge.USABLE to QualityReason.LOW_IMU_RATE
            else -> QualityBadge.GOOD to null
        }
        return BadgeResult(badge, reason, excluded, scored, f)
    }

    /** Total length of the union of half-open spans. */
    private fun unionLength(spans: List<TripSegment>): Double {
        var total = 0.0
        var end = Double.NEGATIVE_INFINITY
        for (s in spans.sortedBy { it.t0S }) {
            val from = maxOf(s.t0S, end)
            if (s.t1S > from) total += s.t1S - from
            end = maxOf(end, s.t1S)
        }
        return total
    }

    private companion object {
        const val PERCENT = 100.0
    }
}
