package com.driveperf.pipeline

import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.capture.FinalisedTrip
import com.driveperf.domain.trip.Placement
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.SegmentReason
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripQuality
import com.driveperf.domain.trip.TripSegment
import com.driveperf.pipeline.capture.FinaliseRequest
import com.driveperf.pipeline.capture.FinaliseResult
import com.driveperf.pipeline.capture.TripFinaliser
import com.driveperf.pipeline.fixtures.DriveFixtures
import com.driveperf.pipeline.fixtures.PipelineHarness
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** PRD F-DQ-02 analytics: `segment_excluded {reason, minutes_bucket}` once per reason at trip end. */
class QualityAnalyticsTest {
    private fun quality(vararg spans: TripSegment) = TripQuality(
        badge = QualityBadge.USABLE,
        reason = null,
        placement = Placement.CUPHOLDER,
        imuHz = 50.0,
        gnssCoveragePct = 100.0,
        alignmentSettledS = 10.0,
        estimatedStartM = null,
        scoredS = 0.0,
        excludedS = 0.0,
        totalS = 3600.0,
        spans = spans.toList(),
    )

    private fun span(kind: SegmentKind, t0: Double, t1: Double, reason: SegmentReason?) = TripSegment(kind, t0, t1, reason)

    @Test
    fun oneEventPerExclusionReasonWithItsMinutesBucket() {
        val q = quality(
            span(SegmentKind.EXCLUDED, 100.0, 200.0, SegmentReason.PHONE_MOVED),
            span(SegmentKind.EXCLUDED, 300.0, 380.0, SegmentReason.PHONE_MOVED),
            span(SegmentKind.EXCLUDED, 400.0, 430.0, SegmentReason.NOT_ALIGNED),
            span(SegmentKind.ESTIMATED, 0.0, 90.0, SegmentReason.ESTIMATED_START),
            span(SegmentKind.GAP, 500.0, 900.0, SegmentReason.APP_KILLED),
            span(SegmentKind.PAUSED, 1000.0, 1300.0, null),
        )
        assertEquals(
            listOf(
                mapOf("reason" to "phone_moved", "minutes_bucket" to "3_10"),
                mapOf("reason" to "not_aligned", "minutes_bucket" to "lt_1"),
            ),
            QualityEvent.forTrip(q).map { it.properties },
        )
        assertEquals(setOf("segment_excluded"), QualityEvent.forTrip(q).map { it.name }.toSet())
    }

    @Test
    fun bucketsCoverTheWholeRange() {
        val buckets = listOf(59.0, 60.0, 179.0, 600.0, 1800.0).map {
            QualityEvent.segmentExcluded(SegmentReason.LOW_IMU_RATE, it).properties.getValue("minutes_bucket")
        }
        assertEquals(listOf("lt_1", "1_3", "1_3", "10_30", "gte_30"), buckets)
    }

    @Test
    fun theDecoratorTracksEveryFinishedTripAndPassesTheResultThrough() = runTest {
        val tracked = mutableListOf<AnalyticsEvent>()
        val analytics = object : Analytics {
            override fun track(event: AnalyticsEvent) { tracked += event }
            override fun setUsageConsent(allowed: Boolean) = Unit
            override fun resetId() = Unit
        }
        val base = PipelineHarness().trip(DriveFixtures.city.capture())
        val moved = base.trip.copy(quality = quality(span(SegmentKind.EXCLUDED, 0.0, 120.0, SegmentReason.PHONE_MOVED)))
        val result = FinaliseResult(listOf(FinalisedTrip(moved), FinalisedTrip(moved.copy(quality = null))), null)
        val request = FinaliseRequest(DriveFixtures.city.capture(), null, TripKind.DRIVER, true, null, 1L)
        val out = QualityAnalyticsFinaliser(TripFinaliser { result }, analytics).finalise(request)
        assertEquals(result, out)
        assertEquals(listOf(mapOf("reason" to "phone_moved", "minutes_bucket" to "1_3")), tracked.map { it.properties })
    }
}
