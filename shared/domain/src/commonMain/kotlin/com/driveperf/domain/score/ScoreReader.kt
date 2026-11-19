package com.driveperf.domain.score

import com.driveperf.domain.route.IneligibleReason
import com.driveperf.domain.route.RoutePreset
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import kotlinx.coroutines.flow.Flow

/** The route a trip matched (F-RTE-01): its user-given [name], or the [preset] it was named with; both null if unnamed. */
public data class RouteName(val routeId: String, val name: String?, val preset: RoutePreset?)

/**
 * What a trip card shows of a trip's score and labels (S-HIS-01, components.md §12). [smoothness] is null for an
 * unscored trip and for a trip that is not a drive: a withheld row keeps its other columns, so show nothing then.
 */
public data class ScoreLine(
    val tripId: String,
    val smoothness: Int?,
    val delta: Int?,
    val calibrating: Calibrating?,
    /** The drive set a new route record (the card's gold rosette). */
    val newRecord: Boolean,
    val route: RouteName?,
    /** The car's nickname, or its make and model; null when the trip has no car. */
    val car: String?,
)

/** Every listed trip's [ScoreLine], by trip ID, and how many active cars the user has (a card names its car only with more than one). */
public data class ScoreLines(val lines: Map<String, ScoreLine>, val carCount: Int) {
    public companion object {
        /** Before the first read. */
        public val EMPTY: ScoreLines = ScoreLines(emptyMap(), 0)
    }
}

/** Everything trip detail and the Smoothness breakdown show of one trip's score (S-TRP-01, S-SCO-01). */
public data class TripScoreDetail(
    val tripId: String,
    /** Null until the trip is scored. */
    val score: Score?,
    /** Detected events, by peak time. */
    val events: List<DrivingEvent>,
    /** "Was it needed?" and "Not accurate?" answers, by event ID. */
    val feedback: Map<String, EventFeedback>,
    val route: RouteName?,
    /** Why this run cannot set a route record (§7.5); null when it can, or when it matched no route. */
    val recordIneligible: IneligibleReason?,
    val car: String?,
    /** Smoothness of the last scored runs of [route], oldest first, ending with this drive; at most ten. */
    val runs: List<Int>,
)

/** Reads scores for the Drives screens (DRIVE-77, DRIVE-126). Each flow emits again when the score rows change. */
public interface ScoreReader {
    public fun observeLines(): Flow<ScoreLines>

    public fun observeDetail(tripId: String): Flow<TripScoreDetail>
}

private const val BUCKET_50 = 50
private const val BUCKET_60 = 60
private const val BUCKET_70 = 70
private const val BUCKET_80 = 80
private const val BUCKET_90 = 90

/**
 * The `score_bucket` analytics property (analytics-crash-privacy.md §2): `lt_50`, `50_59`, `60_69`, `70_79`, `80_89` or
 * `90_100`. Analytics never carry the score itself.
 */
public fun scoreBucket(smoothness: Int): String = when {
    smoothness < BUCKET_50 -> "lt_50"
    smoothness < BUCKET_60 -> "50_59"
    smoothness < BUCKET_70 -> "60_69"
    smoothness < BUCKET_80 -> "70_79"
    smoothness < BUCKET_90 -> "80_89"
    else -> "90_100"
}
