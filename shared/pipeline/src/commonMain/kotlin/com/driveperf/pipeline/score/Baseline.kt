package com.driveperf.pipeline.score

import com.driveperf.domain.score.Band
import com.driveperf.domain.score.Calibrating
import com.driveperf.domain.score.ContributorId
import com.driveperf.domain.score.ScoreBasis
import com.driveperf.domain.trip.DriveContext
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.SuspectAnswer
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import kotlin.math.floor

/**
 * A prior trip as the baseline sees it: only eligible trips (§5.7) are passed in. Efficiency measures are null when
 * that trip had none.
 */
public data class PriorTrip(
    val tripId: String,
    val startedAt: Long,
    val context: DriveContext,
    val routeId: String?,
    val smoothness: Int,
    val subScores: Map<ContributorId, Double>,
    val stopsPerKm: Double? = null,
    val abruptAccelsPerMin: Double? = null,
    val speedWobbleMps: Double? = null,
)

/** What §5.7 decides for one trip. Exactly one of [baseline] and [calibrating] is set. */
public data class BaselineResult(
    val basis: ScoreBasis?,
    val basisRouteId: String?,
    val baseline: Int?,
    val delta: Int?,
    val calibrating: Calibrating?,
    val bands: Map<ContributorId, Band>,
    /** The prior trips the comparison used, newest first. */
    val basisTrips: List<PriorTrip>,
)

/** Baseline, delta, calibrating and contributor bands (§5.7). Pure: the caller supplies the eligible history. */
public object Baselines {
    internal const val DEPTH = 10
    private const val NEEDED = 3
    private const val BAND = 6.0
    private const val HALF = 0.5

    /** §5.7 eligibility: a scored driver trip with Good or Usable data and no unanswered or confirmed suspicion. */
    public fun eligible(trip: Trip, smoothness: Int?): Boolean {
        val badge = trip.quality?.badge
        val suspectOk = trip.suspect == Suspect.NONE || trip.suspectAnswer == SuspectAnswer.NO
        return trip.kind == TripKind.DRIVER && smoothness != null && suspectOk &&
            (badge == QualityBadge.GOOD || badge == QualityBadge.USABLE)
    }

    /**
     * @param priors eligible trips that started before this one, any order.
     * @param routeId the route this trip matched, if any.
     */
    public fun compare(
        smoothness: Int,
        subScores: Map<ContributorId, Double>,
        context: DriveContext,
        routeId: String?,
        priors: List<PriorTrip>,
    ): BaselineResult {
        val ordered = priors.sortedWith(compareByDescending<PriorTrip> { it.startedAt }.thenByDescending { it.tripId })
        val onRoute = routeId?.let { id -> ordered.filter { it.routeId == id } }.orEmpty()
        val inContext = ordered.filter { it.context == context }
        val (basis, trips) = when {
            routeId != null && onRoute.size >= NEEDED -> ScoreBasis.ROUTE to onRoute.take(DEPTH)
            inContext.size >= NEEDED -> ScoreBasis.CONTEXT to inContext.take(DEPTH)
            else -> null to emptyList()
        }
        if (basis == null) {
            return BaselineResult(null, null, null, null, Calibrating(minOf(NEEDED, inContext.size + 1), NEEDED), emptyMap(), emptyList())
        }
        val baseline = medianHalfUp(trips.map { it.smoothness.toDouble() })
        val bands = subScores.mapValues { (id, sub) ->
            val usual = median(trips.mapNotNull { it.subScores[id] })
            when {
                usual == null -> Band.USUAL
                sub - usual >= BAND -> Band.SMOOTHER
                sub - usual <= -BAND -> Band.ROUGHER
                else -> Band.USUAL
            }
        }
        return BaselineResult(
            basis, if (basis == ScoreBasis.ROUTE) routeId else null, baseline, smoothness - baseline, null, bands, trips,
        )
    }

    /** Median; an even count takes the mean of the two middle values (§0.5 rule 3). Null when empty. */
    public fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
    }

    private fun medianHalfUp(values: List<Double>): Int = floor((median(values) ?: 0.0) + HALF).toInt()
}
