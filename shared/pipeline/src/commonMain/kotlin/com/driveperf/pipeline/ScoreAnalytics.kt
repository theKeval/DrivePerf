package com.driveperf.pipeline

import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.capture.FinalisedTrip
import com.driveperf.domain.repository.TripHistory
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.TripKind
import com.driveperf.pipeline.capture.FinaliseRequest
import com.driveperf.pipeline.capture.FinaliseResult
import com.driveperf.pipeline.capture.TripFinaliser

/**
 * Scoring analytics at trip end. Values are fixed keys and buckets only (ADR-007); nothing identifies a trip or a
 * place.
 *
 * - `events_detected {type, count_bucket}` (F-SCO-04): one per event type for every scored drive, zero included.
 * - `baseline_established {trips}` (F-SCO-02): once, on the first drive compared with a baseline, with how many scored
 *   drives that took, this one included.
 */
internal data class ScoreEvent(override val name: String, override val properties: Map<String, String>) : AnalyticsEvent {
    companion object {
        /** `events_detected {type, count_bucket}` for [count] events of [type] on one trip. */
        fun eventsDetected(type: EventType, count: Int): ScoreEvent = ScoreEvent(
            "events_detected",
            mapOf("type" to type.name.lowercase(), "count_bucket" to countBucket(count)),
        )

        /** `baseline_established {trips}` after [trips] scored drives. */
        fun baselineEstablished(trips: Int): ScoreEvent = ScoreEvent("baseline_established", mapOf("trips" to tripsBucket(trips)))

        /** The `events_detected` events of one finalised trip: none unless it is a scored drive. */
        fun eventsFor(finished: FinalisedTrip): List<ScoreEvent> {
            if (finished.trip.kind != TripKind.DRIVER || finished.score?.smoothness == null) return emptyList()
            return EventType.entries.map { type -> eventsDetected(type, finished.events.count { it.type == type }) }
        }
    }
}

/**
 * Sends the [ScoreEvent]s of every trip [inner] finalises through the consent-gated [analytics] port, reading
 * [history] (committed trips, newest first) to tell whether a baseline is new. The finaliser stays free of analytics,
 * so replays and developer tools never send events.
 *
 * "First" looks back one page of history: a user with 200 calibrating drives in a row would be counted again, which
 * cannot happen while a baseline needs 3 eligible drives. A baseline lost to corrections and earned again counts again.
 */
public class ScoreAnalyticsFinaliser(
    private val inner: TripFinaliser,
    private val analytics: Analytics,
    private val history: TripHistory,
) : TripFinaliser {
    override suspend fun finalise(request: FinaliseRequest): FinaliseResult {
        val result = inner.finalise(request)
        val earlier = mutableListOf<FinalisedTrip>()
        for (finished in result.trips) {
            ScoreEvent.eventsFor(finished).forEach(analytics::track)
            baselineEstablished(finished, earlier)?.let(analytics::track)
            earlier += finished
        }
        return result
    }

    /** The event when [finished] is the first scored drive with a baseline; [parts] are earlier parts of its split. */
    private suspend fun baselineEstablished(finished: FinalisedTrip, parts: List<FinalisedTrip>): ScoreEvent? {
        val compared = finished.trip.kind == TripKind.DRIVER && finished.score?.baseline != null
        if (!compared || parts.any { it.score?.baseline != null }) return null
        val past = history.finishedBefore(finished.trip.startedAt, PAGE)
        val scored = past.count { it.trip.kind == TripKind.DRIVER && it.score?.smoothness != null } +
            parts.count { it.trip.kind == TripKind.DRIVER && it.score?.smoothness != null }
        return ScoreEvent.baselineEstablished(scored + 1).takeIf { past.none { it.score?.baseline != null } }
    }

    private companion object {
        const val PAGE = 200
    }
}

private const val FOUR = 4
private const val TEN = 10
private const val SIX = 6
private const val TWENTY = 20

private fun countBucket(n: Int): String = when {
    n <= 1 -> "$n"
    n < FOUR -> "2_3"
    n < TEN -> "4_9"
    else -> "10_plus"
}

private fun tripsBucket(n: Int): String = when {
    n < FOUR -> "lt_4"
    n == FOUR -> "4"
    n <= SIX -> "5_6"
    n <= TEN -> "7_10"
    n <= TWENTY -> "11_20"
    else -> "gt_20"
}
