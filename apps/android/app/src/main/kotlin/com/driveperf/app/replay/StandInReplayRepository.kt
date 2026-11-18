package com.driveperf.app.replay

import com.driveperf.domain.replay.Replay
import com.driveperf.domain.replay.ReplayPoint
import com.driveperf.domain.replay.ReplayRepository
import com.driveperf.domain.replay.ReplaySpan
import com.driveperf.domain.replay.SegmentStyle
import com.driveperf.domain.trip.BoundingBox
import kotlin.math.sin

/**
 * DRIVE-200 SEAM (R1 stand-in): a synthetic replay for every trip, until the shared model (`CoreServices.replay`,
 * branch DRIVE-200-replay-model) merges and the app binds it instead. The route is made up (no real locations) and has
 * each overlay style: an estimated start, a tunnel gap and an excluded span.
 */
class StandInReplayRepository : ReplayRepository {
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
                ReplaySpan(SegmentStyle.EXCLUDED, EXCLUDED_START.toDouble(), EXCLUDED_END.toDouble()),
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
