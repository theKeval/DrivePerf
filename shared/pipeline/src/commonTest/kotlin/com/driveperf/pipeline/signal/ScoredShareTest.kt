package com.driveperf.pipeline.signal

import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.QualityReason
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.SegmentReason
import com.driveperf.domain.trip.TripSegment
import com.driveperf.pipeline.score.QualityBits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DRIVE-164 (F-DQ-02, F-DQ-04): excluded spans cover moving time only, for every exclusion bit (§2.7), and the
 * estimated start is in neither side of the scored share (§2.9).
 */
class ScoredShareTest {
    private val cfg = SignalConfig()
    private val spans = Spans(cfg)
    private val badge = Badge(cfg)
    private val fs = cfg.fs

    /** A trip of [totalS] seconds whose moving mask is true inside [movingS] (seconds, half-open). */
    private fun moving(totalS: Int, vararg movingS: Pair<Int, Int>) = BooleanArray(totalS * fs).also { m ->
        for ((a, b) in movingS) for (k in a * fs until b * fs) m[k] = true
    }

    private fun quality(totalS: Int, bit: Int, vararg runsS: Pair<Int, Int>) = IntArray(totalS * fs).also { q ->
        for ((a, b) in runsS) for (k in a * fs until b * fs) q[k] = q[k] or bit
    }

    private fun TripSegment.range() = t0S to t1S

    @Test
    fun aPhoneMovedWhileStoppedMakesNoSpanButKeepsTheBit() {
        val q = quality(300, QualityBits.EXCLUDED_PHONE, 100 to 120)
        val out = spans.excluded(q, moving(300, 0 to 90, 130 to 300), null)
        assertTrue(out.isEmpty(), "stopped from 90 s to 130 s: nothing to exclude")
        assertTrue((100 * fs until 120 * fs).all { q[it] and QualityBits.EXCLUDED_PHONE != 0 }, "events still skip it")
    }

    @Test
    fun everyBitIsCutToItsMovingTime() {
        val bits = mapOf(
            QualityBits.EXCLUDED_PHONE to SegmentReason.PHONE_MOVED,
            QualityBits.NOT_ALIGNED to SegmentReason.NOT_ALIGNED,
            QualityBits.LOW_RATE to SegmentReason.LOW_IMU_RATE,
            QualityBits.IMU_GAP to SegmentReason.IMU_GAP,
        )
        for ((bit, reason) in bits) {
            // Stopped 0–60 s, moving 60–200 s, stopped 200–260 s, moving 260–300 s; the bit is set from 30 s to 280 s.
            val out = spans.excluded(quality(300, bit, 30 to 280), moving(300, 60 to 200, 260 to 300), null)
            assertEquals(listOf(60.0 to 200.0, 260.0 to 280.0), out.map { it.range() }, "$reason")
            assertTrue(out.all { it.kind == SegmentKind.EXCLUDED && it.reason == reason })
        }
    }

    @Test
    fun aStopShorterThanAThirdSectionStopStaysInsideTheSpan() {
        val q = quality(300, QualityBits.NOT_ALIGNED, 0 to 300)
        // A 6 s crawl stop at 100 s is bridged; the 40 s stop at 200 s splits the span.
        val out = spans.excluded(q, moving(300, 20 to 100, 106 to 200, 240 to 280), null)
        assertEquals(listOf(20.0 to 200.0, 240.0 to 280.0), out.map { it.range() })
    }

    @Test
    fun nothingBeforeTheFirstScorableSampleIsExcluded() {
        val q = quality(300, QualityBits.LOW_RATE, 0 to 150)
        val out = spans.excluded(q, moving(300, 0 to 300), estimatedEndS = 100.0)
        assertEquals(listOf(100.0 to 150.0), out.map { it.range() })
    }

    private fun inputs(totalS: Double, vararg spans: TripSegment) = BadgeInputs(
        totalS = totalS,
        spans = spans.toList(),
        gnssCoveragePct = 100.0,
        imuDelivery = 1.0,
        scoredMovingS = 600.0,
        hasImu = true,
        hasPreciseLocation = true,
        anyEpochConverged = true,
        noGyro = false,
    )

    private fun estimated(t1: Double) = TripSegment(SegmentKind.ESTIMATED, 0.0, t1, SegmentReason.ESTIMATED_START)

    private fun excluded(t0: Double, t1: Double) = TripSegment(SegmentKind.EXCLUDED, t0, t1, SegmentReason.PHONE_MOVED)

    @Test
    fun theEstimatedStartIsInNeitherSideOfTheShare() {
        // 01a1d4f9-like: a 529 s estimated start used to take f from ≈ 0.9 to ≈ 0.5.
        val r = badge.evaluate(inputs(1500.0, estimated(529.0), excluded(600.0, 650.0)))
        assertEquals(921.0, r.scoredS, 1e-9)
        assertEquals(579.0, r.excludedS, 1e-9, "excludedS still adds up with scoredS to totalS")
        assertEquals(921.0 / 971.0, r.share, 1e-9)
        assertEquals(QualityBadge.GOOD, r.badge)
    }

    @Test
    fun movingExclusionsStillLowerTheBadge() {
        val r = badge.evaluate(inputs(1000.0, estimated(200.0), excluded(300.0, 500.0)))
        assertEquals(0.75, r.share, 1e-9)
        assertEquals(QualityBadge.USABLE, r.badge)
        assertEquals(QualityReason.LOW_SCORED_SHARE, r.reason)
        val half = badge.evaluate(inputs(1000.0, estimated(200.0), excluded(300.0, 720.0)))
        assertEquals(QualityBadge.UNSCORABLE, half.badge, "more than half of the scorable time excluded (F-DQ-02 AC3)")
    }

    @Test
    fun aTripThatIsAllEstimatedHasNoShare() {
        val r = badge.evaluate(inputs(300.0, estimated(300.0)))
        assertEquals(0.0, r.share)
        assertEquals(QualityBadge.UNSCORABLE, r.badge)
    }
}
