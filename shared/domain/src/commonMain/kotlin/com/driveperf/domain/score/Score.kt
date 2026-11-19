package com.driveperf.domain.score

import com.driveperf.domain.trip.EventType

/** What the baseline is drawn from (§5.7). */
public enum class ScoreBasis { ROUTE, CONTEXT }

/** The four Smoothness contributors (§5.6). */
public enum class ContributorId { BRAKING, ACCELERATION, CORNERING, CONSISTENCY }

/** A contributor compared with the driver's usual (§5.7): ±6 points on the sub-score. */
public enum class Band { SMOOTHER, USUAL, ROUGHER }

/** Efficiency habits against the driver's usual (§5.9). */
public enum class EfficiencyRating { BETTER, USUAL, WORSE }

/** Tip categories in the §6.2 tie order. */
public enum class TipCategory { BRAKING, CORNERING, ACCELERATION, CONSISTENCY, ANTICIPATION }

/**
 * One contributor (data-model.md §1.11, plus `value` and `subScore` from pipeline-and-scoring.md §0.8).
 *
 * @property value the measure behind it: `R_i` in m/s² per moving minute, or speed wobble in m/s for consistency.
 * @property band null while calibrating.
 * @property eventIds that channel's active events, by cost descending (tie: earliest).
 */
public data class Contributor(
    val id: ContributorId,
    val subScore: Double,
    val value: Double?,
    val band: Band?,
    val reasonKey: String?,
    val eventIds: List<String>,
)

/** "Calibrating · k of 3" (§5.7). */
public data class Calibrating(val k: Int, val of: Int)

/** Exposure and excess-jerk sums for one ledger row (§5.4). [movingS] is `T`. */
public data class LedgerRow(val movingS: Double, val brake: Double, val accel: Double, val corner: Double) {
    /** Element-wise sum. */
    public operator fun plus(other: LedgerRow): LedgerRow =
        LedgerRow(movingS + other.movingS, brake + other.brake, accel + other.accel, corner + other.corner)

    public companion object {
        /** The empty row. */
        public val ZERO: LedgerRow = LedgerRow(0.0, 0.0, 0.0, 0.0)
    }
}

/** One event's own ledger row, including its event cost, so feedback can drop it exactly (§5.4). */
public data class EventLedgerRow(val eventId: String, val type: EventType, val row: LedgerRow)

/**
 * The jerk ledger (§5.4). Totals are [base] plus the rows of every active event, so "Was it needed?" re-scores
 * without R1.
 *
 * @property stopStartCount the efficiency stop-start count (§5.9) as measured at finalisation. Like the noise floors it
 *   never changes with feedback, so every re-score starts from it rather than from the previous re-score's efficiency.
 *   Null on rows written before it was kept.
 * @property abruptAccelCount the efficiency abrupt-acceleration count (§5.9) as measured at finalisation; null likewise.
 */
public data class Ledger(
    val base: LedgerRow,
    val perEvent: List<EventLedgerRow>,
    val noiseFloorLonMps3: Double,
    val noiseFloorLatMps3: Double,
    val stopStartCount: Int? = null,
    val abruptAccelCount: Int? = null,
)

/** Efficiency habits (§5.9). Always labelled Estimated; never energy, fuel or money. */
public data class Efficiency(
    val rating: EfficiencyRating?,
    val stopsPerKm: Double?,
    val abruptAccelsPerMin: Double?,
    val speedWobbleMps: Double?,
    val speedConsistencyCv: Double?,
    val stopStartCount: Int,
)

/** The one coaching tip of a scorable trip (§6). [placeKey] ties it to a place. */
public data class Tip(val tipId: String, val category: TipCategory, val eventId: String?, val placeKey: String?)

/** Snapshot of the route record for the Story frame. */
public data class RecordState(val isNew: Boolean, val previousScore: Int?, val previousTripId: String?)

/**
 * A trip's current score (data-model.md §1.11). [smoothness] is null for unscorable and non-driver trips; it is an
 * absolute 0–100 number, shown as "±n vs your usual" (decision D1).
 */
public data class Score(
    val tripId: String,
    val smoothness: Int?,
    val smoothnessRaw: Double?,
    val baseline: Int?,
    val delta: Int?,
    val basis: ScoreBasis?,
    val basisRouteId: String?,
    val calibrating: Calibrating?,
    val contributors: List<Contributor>,
    val ledger: Ledger?,
    val efficiency: Efficiency?,
    val tip: Tip?,
    val recordState: RecordState?,
    val algorithmVersion: String,
    val computedAt: Long,
)

/**
 * pipeline-and-scoring.md §5.7 "Corrections" (F-HIS-02, F-SCO-05): after a trip's kind, an event's feedback or a
 * deletion changes, re-derives the scores of that trip and every later one, in `(startedAt, id)` order, from the
 * stored ledgers and without R1. Deterministic, and it never changes a score's [Score.algorithmVersion].
 * Implemented in `:shared:pipeline`; the feedback store, the corrections and the trip deletion call it.
 */
public fun interface TripRecompute {
    /** Re-derives the scores of trip [tripId] and every later trip. A trip that does not exist changes nothing. */
    public suspend fun fromTrip(tripId: String)

    public companion object {
        /** Recomputes nothing: for scratch cores and tests that do not need it. */
        public val NONE: TripRecompute = TripRecompute { }
    }
}

/** One score a recompute changed: [before] as its walk read it, [after] as it recomputed it. */
public data class ScoreChange(val before: Score, val after: Score)

/** What [TripRecompute] reads and writes. Implemented in `:shared:data:local`. */
public interface ScoreHistoryStore {
    /**
     * Finished, live trips that started at or after [startedAt], oldest first by `(startedAt, id)`, each with its
     * score and route match.
     */
    public suspend fun finishedFrom(startedAt: Long): List<com.driveperf.domain.repository.ScoredTrip>

    /**
     * In one transaction, writes each change's [ScoreChange.after] to the `score` table while the stored score still
     * equals its [ScoreChange.before], and returns the scores written. A score that changed after the walk read it, or
     * whose trip was deleted, is left alone: a newer change wrote it, and that change's own recompute follows.
     */
    public suspend fun saveScores(changes: List<ScoreChange>): List<Score>
}

/**
 * The `score_bucket` analytics value of a Smoothness score (analytics-crash-privacy.md §2): `lt_50`, `50_59`, `60_69`,
 * `70_79`, `80_89` or `90_100`. Null when there is no score (unscorable or not a drive), so the property is left out.
 */
public fun scoreBucket(smoothness: Int?): String? = when {
    smoothness == null -> null
    smoothness < BUCKET_50 -> "lt_50"
    smoothness >= BUCKET_90 -> "90_100"
    else -> (smoothness / BUCKET_WIDTH * BUCKET_WIDTH).let { "${it}_${it + BUCKET_WIDTH - 1}" }
}

private const val BUCKET_50 = 50
private const val BUCKET_90 = 90
private const val BUCKET_WIDTH = 10
