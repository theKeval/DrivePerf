package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.capture.FinalisedTrip
import com.driveperf.domain.capture.Parking
import com.driveperf.domain.trip.InterruptionReason
import com.driveperf.domain.trip.TripKind

/**
 * What finalisation is asked to do.
 *
 * @property endNs monotonic end of the trip; null means the last sample in R0 (a trip interrupted by a kill).
 * @property manual the trip was started by the user, so a short trip is kept as "Short trip" (§1.6).
 * @property nowWallMs commit time, for `createdAt`/`updatedAt`.
 */
public data class FinaliseRequest(
    val capture: CapturedTrip,
    val endNs: Long?,
    val kind: TripKind,
    val manual: Boolean,
    val interruption: InterruptionReason?,
    val nowWallMs: Long,
)

/**
 * The result: the trips to commit, in time order, and the new parking spot. Empty when the trip is discarded
 * (F-CAP-17); more than one when a trip longer than 12 h was split (§1.6). The first keeps the captured trip's ID
 * unless it was discarded.
 */
public data class FinaliseResult(val trips: List<FinalisedTrip>, val parking: Parking?) {
    /** A single trip, or none when it is discarded. */
    public constructor(trip: FinalisedTrip?, parking: Parking?) : this(listOfNotNull(trip), parking)

    /** The first trip, or null when everything was discarded. */
    public val trip: FinalisedTrip? get() = trips.firstOrNull()
}

/** Turns a captured trip into committed rows: the batch pipeline of §2–§7. */
public fun interface TripFinaliser {
    public suspend fun finalise(request: FinaliseRequest): FinaliseResult
}
