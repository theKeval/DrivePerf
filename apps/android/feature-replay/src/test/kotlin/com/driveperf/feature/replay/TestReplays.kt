package com.driveperf.feature.replay

import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.replay.Replay
import com.driveperf.domain.replay.ReplayPoint
import com.driveperf.domain.replay.ReplayRepository
import com.driveperf.domain.replay.ReplaySpan
import com.driveperf.domain.replay.SegmentStyle
import com.driveperf.domain.trip.BoundingBox

/**
 * A synthetic 600 s trip heading north at 10 m/s: estimated start 0–60 s, a tunnel (no GPS) 200–240 s and an excluded
 * span 400–450 s. Coordinates are made up.
 */
internal object TestReplays {
    const val DURATION = 600.0

    fun trip(id: String = "t1"): Replay {
        val points = (0..600).map { t -> ReplayPoint(t.toDouble(), 10.0 + t * 0.00009, 20.0, 10.0) }
        return Replay(
            tripId = id,
            bbox = BoundingBox(10.0, 19.999, 10.054, 20.001),
            durationS = DURATION,
            spans = listOf(
                ReplaySpan(SegmentStyle.ESTIMATED, 0.0, 60.0),
                ReplaySpan(SegmentStyle.NO_GPS, 200.0, 240.0),
                ReplaySpan(SegmentStyle.EXCLUDED, 400.0, 450.0),
            ),
            summaryOnly = false,
            track = points.filterNot { it.tS > 200.0 && it.tS < 240.0 },
        )
    }
}

internal class FakeReplayRepository(var result: () -> Replay? = { TestReplays.trip() }) : ReplayRepository {
    var calls = 0
    override suspend fun replay(tripId: String): Replay? {
        calls++
        return result()
    }
}

internal class RecordingAnalytics : Analytics {
    val events = mutableListOf<AnalyticsEvent>()
    val names: List<String> get() = events.map { it.name }

    override fun track(event: AnalyticsEvent) {
        events += event
    }

    override fun setUsageConsent(allowed: Boolean) = Unit

    override fun resetId() = Unit
}
