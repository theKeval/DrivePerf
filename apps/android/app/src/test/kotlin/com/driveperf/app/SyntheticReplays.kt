package com.driveperf.app

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.replay.Replay
import com.driveperf.domain.replay.ReplayPoint
import com.driveperf.domain.replay.ReplayRepository
import com.driveperf.domain.replay.ReplaySpan
import com.driveperf.domain.replay.SegmentStyle
import com.driveperf.domain.trip.BoundingBox
import com.driveperf.domain.trip.SegmentReason
import com.driveperf.feature.replay.ReplayMapConfig
import com.driveperf.feature.replay.ReplayViewModel
import com.driveperf.presentation.replay.ReplayPresenter
import com.driveperf.presentation.replay.ReplaySource
import kotlin.math.sin

/**
 * A synthetic 15-minute trip for the Replay UI tests (made-up coordinates, no real locations): an estimated start
 * 0–60 s, a tunnel with no GPS 360–400 s and a phone-moved span 600–660 s, on the shared Replay model.
 */
internal class SyntheticReplays : ReplayRepository {
    override suspend fun replay(tripId: String): Replay {
        val points = (0..DURATION_S).map { t ->
            val f = t / DURATION_S.toDouble()
            ReplayPoint(t.toDouble(), LAT + f * SPAN_LAT, LNG + f * SPAN_LNG + sin(f * WIGGLES) * WIGGLE_LNG, SPEED_MPS)
        }
        return Replay(
            tripId = tripId,
            bbox = BoundingBox(LAT, LNG - WIGGLE_LNG, LAT + SPAN_LAT, LNG + SPAN_LNG + WIGGLE_LNG),
            durationS = DURATION_S.toDouble(),
            spans = listOf(
                ReplaySpan(SegmentStyle.ESTIMATED, 0.0, ESTIMATED_END.toDouble()),
                ReplaySpan(SegmentStyle.NO_GPS, GAP_START.toDouble(), GAP_END.toDouble()),
                ReplaySpan(SegmentStyle.EXCLUDED, EXCLUDED_START.toDouble(), EXCLUDED_END.toDouble(), SegmentReason.PHONE_MOVED),
            ),
            summaryOnly = false,
            track = points.filterNot { it.tS > GAP_START && it.tS < GAP_END },
        )
    }

    private companion object {
        const val DURATION_S = 900
        const val ESTIMATED_END = 60
        const val GAP_START = 360
        const val GAP_END = 400
        const val EXCLUDED_START = 600
        const val EXCLUDED_END = 660
        const val LAT = 10.0
        const val LNG = 10.0
        const val SPAN_LAT = 0.05
        const val SPAN_LNG = 0.08
        const val WIGGLES = 12.0
        const val WIGGLE_LNG = 0.004
        const val SPEED_MPS = 14.0
    }
}

/**
 * A Replay view model on the shared presenter, loading [SyntheticReplays], with the presenter's clock tied to the
 * compose test clock so frame-driven playback is deterministic.
 */
internal fun replayViewModel(maps: ReplayMapConfig, analytics: Analytics, compose: ComposeContentTestRule): ReplayViewModel =
    ReplayViewModel(
        presenterFactory = { scope ->
            ReplayPresenter(
                tripId = "synthetic",
                source = ReplaySource.TRIP_DETAIL,
                replays = SyntheticReplays(),
                isRecording = { false },
                analytics = analytics,
                monoNowNs = { compose.mainClock.currentTime * NANOS_PER_MILLI },
                scope = scope,
            )
        },
        maps = maps,
    )

/** A tap on the scrubber at [tS]: a scrub with no drag. */
internal fun ReplayViewModel.seekTo(tS: Double) {
    scrubStart()
    scrubTo(tS)
    scrubEnd()
}

private const val NANOS_PER_MILLI = 1_000_000L
