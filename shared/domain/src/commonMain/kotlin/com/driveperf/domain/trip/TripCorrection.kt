package com.driveperf.domain.trip

import kotlinx.coroutines.flow.Flow

/**
 * What the user says a trip really was (F-CAP-04 AC3, analytics `trip_corrected {to}`). Each correction sets the
 * trip's [kind]; [TRANSIT], [BIKE], [WALK] and [OTHER] are not drives, so the trip leaves scores and history and waits
 * [NotDrives.RETENTION_MS] in the "Not drives" filter before it is deleted. [DRIVER] undoes a correction.
 */
public enum class TripCorrection(public val kind: TripKind) {
    DRIVER(TripKind.DRIVER),
    PASSENGER(TripKind.PASSENGER),
    TRANSIT(TripKind.TRANSIT),
    BIKE(TripKind.NOT_DRIVING),
    WALK(TripKind.NOT_DRIVING),
    OTHER(TripKind.NOT_DRIVING),
    ;

    /** The trip leaves scores and history for the "Not drives" filter. */
    public val isNotADrive: Boolean get() = kind == TripKind.TRANSIT || kind == TripKind.NOT_DRIVING

    public companion object {
        /** What a "Yes" to the suspect question means. */
        public fun confirming(suspect: Suspect): TripCorrection? = when (suspect) {
            Suspect.TRANSIT -> TRANSIT
            Suspect.CYCLING -> BIKE
            Suspect.NONE -> null
        }
    }
}

/** The "Not drives" filter (F-CAP-04 AC3). */
public object NotDrives {
    /** A trip corrected to not-a-drive is deleted this long after the correction. */
    public const val RETENTION_DAYS: Int = 30

    /** [RETENTION_DAYS] in milliseconds. */
    public const val RETENTION_MS: Long = RETENTION_DAYS * 24L * 60 * 60 * 1000
}

/**
 * Write side of the user's answers about what a trip was (F-CAP-04). Implemented in `:shared:data:local`. Every call
 * stamps the change with the core's clock; a trip that does not exist, is still recording or was deleted is left
 * alone and the call returns null.
 *
 * Per-user tuning from these answers (AC4: no transit question on a route after 2 "No" answers,
 * pipeline-and-scoring.md §1.8) is read by the capture pipeline and lands in Sprint 3 Batch 2.
 */
public interface TripCorrections {
    /** Trips corrected to not-a-drive and not yet purged, newest first. */
    public fun observeNotDrives(): Flow<List<Trip>>

    /**
     * Answers the trip's open question ([openQuestion]). "Yes" corrects it to [TripCorrection.confirming]; "No" keeps
     * it a driver trip and lets it count for baselines and records (decision D7). Returns null when the trip has no
     * suspect.
     */
    public suspend fun answerSuspect(tripId: String, answer: SuspectAnswer): Trip?

    /** Corrects the trip to [to]. Correcting a suspected trip also answers its question. */
    public suspend fun correct(tripId: String, to: TripCorrection): Trip?

    /**
     * Deletes trips corrected to not-a-drive more than [NotDrives.RETENTION_MS] ago, files first (as
     * `TripRepository.delete`). A trip whose files cannot be deleted is kept for the next run. Returns how many were
     * deleted.
     */
    public suspend fun purgeExpiredNotDrives(): Int
}
