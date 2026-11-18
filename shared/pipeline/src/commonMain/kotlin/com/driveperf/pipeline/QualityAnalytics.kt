package com.driveperf.pipeline

import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.SegmentReason
import com.driveperf.domain.trip.TripQuality
import com.driveperf.domain.trip.leftOut
import com.driveperf.pipeline.capture.FinaliseRequest
import com.driveperf.pipeline.capture.FinaliseResult
import com.driveperf.pipeline.capture.TripFinaliser

/**
 * Data-quality analytics at trip end (PRD F-DQ-02): `segment_excluded {reason, minutes_bucket}`, one event per
 * reason that left moving time out of the score. Values are fixed keys and buckets only (ADR-007).
 */
internal data class QualityEvent(override val name: String, override val properties: Map<String, String>) : AnalyticsEvent {
    companion object {
        /** `segment_excluded {reason, minutes_bucket}` for one exclusion reason and its total length. */
        fun segmentExcluded(reason: SegmentReason, seconds: Double): QualityEvent = QualityEvent(
            "segment_excluded",
            mapOf("reason" to reason.name.lowercase(), "minutes_bucket" to minutesBucket(seconds)),
        )

        /** The `segment_excluded` events for one finalised trip's [quality], longest first. */
        fun forTrip(quality: TripQuality): List<QualityEvent> {
            val excluded = quality.spans.filter { it.kind == SegmentKind.EXCLUDED }.mapNotNull { it.reason }.toSet()
            return quality.leftOut().filter { it.reason in excluded }.map { segmentExcluded(it.reason, it.seconds) }
        }
    }
}

/**
 * Sends the [QualityEvent]s of every trip [inner] finalises through the consent-gated [analytics] port. The
 * finaliser stays free of analytics, so replays and developer tools never send events.
 */
public class QualityAnalyticsFinaliser(private val inner: TripFinaliser, private val analytics: Analytics) : TripFinaliser {
    override suspend fun finalise(request: FinaliseRequest): FinaliseResult {
        val result = inner.finalise(request)
        result.trips.forEach { finished -> finished.trip.quality?.let { q -> QualityEvent.forTrip(q).forEach(analytics::track) } }
        return result
    }
}

private const val MIN_1 = 60.0
private const val MIN_3 = 180.0
private const val MIN_10 = 600.0
private const val MIN_30 = 1_800.0

private fun minutesBucket(s: Double): String = when {
    s < MIN_1 -> "lt_1"
    s < MIN_3 -> "1_3"
    s < MIN_10 -> "3_10"
    s < MIN_30 -> "10_30"
    else -> "gte_30"
}
