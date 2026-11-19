package com.driveperf.pipeline

import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.capture.FinalisedTrip
import com.driveperf.domain.repository.TripHistory
import com.driveperf.domain.score.scoreBucket
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.TripKind
import com.driveperf.pipeline.capture.FinaliseRequest
import com.driveperf.pipeline.capture.FinaliseResult
import com.driveperf.pipeline.capture.TripFinaliser

/**
 * Scoring analytics at trip end. Values are fixed keys and the buckets of analytics-crash-privacy.md §2 only (ADR-007);
 * nothing identifies a trip, a time or a place.
 *
 * - `trip_finalised {mode, trigger, quality, duration_bucket, distance_bucket, score_bucket}` (F-CAP, F-DQ-04, F-SCO-01):
 *   once for every finalised trip. `score_bucket` is left out when the trip has no score.
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

        /** `trip_finalised` for one finalised trip. */
        fun tripFinalised(finished: FinalisedTrip): ScoreEvent {
            val trip = finished.trip
            val props = linkedMapOf(
                "mode" to trip.source.name.lowercase(),
                "trigger" to (trip.captureMeta?.wakeKind?.name?.lowercase() ?: "unknown"),
                "quality" to qualityOf(trip.quality?.badge),
                "duration_bucket" to durationBucket(trip.durationS ?: 0.0),
                "distance_bucket" to distanceBucket(trip.distanceM ?: 0.0),
            )
            scoreBucket(finished.score?.smoothness?.takeIf { trip.kind == TripKind.DRIVER })?.let { props["score_bucket"] = it }
            return ScoreEvent("trip_finalised", props)
        }

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
 * so replays and developer tools never send events, and re-scores (feedback, corrections, `HistoryRecompute`) never
 * pass through here, so a trip sends `trip_finalised` once.
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
            analytics.track(ScoreEvent.tripFinalised(finished))
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
private const val SIX = 6
private const val SEVEN = 7
private const val TEN = 10
private const val ELEVEN = 11
private const val TWENTY = 20

/** `count_bucket`: 0, 1, 2–3, 4–6, 7–10, 11+ (analytics-crash-privacy.md §2). */
private fun countBucket(n: Int): String = when {
    n <= 1 -> "$n"
    n < FOUR -> "2_3"
    n < SEVEN -> "4_6"
    n < ELEVEN -> "7_10"
    else -> "11_plus"
}

private fun tripsBucket(n: Int): String = when {
    n < FOUR -> "lt_4"
    n == FOUR -> "4"
    n <= SIX -> "5_6"
    n <= TEN -> "7_10"
    n <= TWENTY -> "11_20"
    else -> "gt_20"
}

/** `quality`: good, usable, poor (the Limited badge) or unscorable. */
private fun qualityOf(badge: QualityBadge?): String = when (badge) {
    QualityBadge.GOOD -> "good"
    QualityBadge.USABLE -> "usable"
    QualityBadge.LIMITED -> "poor"
    QualityBadge.UNSCORABLE, null -> "unscorable"
}

private val DURATION_MIN = intArrayOf(5, 15, 30, 60, 120)
private val DISTANCE_KM = intArrayOf(2, 10, 25, 50, 100)
private const val SECONDS_PER_MIN = 60.0
private const val METRES_PER_KM = 1000.0

/** `duration_bucket`: <5, 5–15, 15–30, 30–60, 60–120, >120 min. */
private fun durationBucket(s: Double): String = edges(s / SECONDS_PER_MIN, DURATION_MIN)

/** `distance_bucket`: <2, 2–10, 10–25, 25–50, 50–100, >100 km. */
private fun distanceBucket(m: Double): String = edges(m / METRES_PER_KM, DISTANCE_KM)

/** `lt_<first>`, `<a>_<b>` for each pair of edges (from a, below b), then `gt_<last>`. */
private fun edges(x: Double, e: IntArray): String {
    val i = e.indexOfFirst { x < it }
    return when (i) {
        0 -> "lt_${e[0]}"
        -1 -> "gt_${e.last()}"
        else -> "${e[i - 1]}_${e[i]}"
    }
}
