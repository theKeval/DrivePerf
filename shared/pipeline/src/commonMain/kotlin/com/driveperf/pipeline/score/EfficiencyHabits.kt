package com.driveperf.pipeline.score

import com.driveperf.domain.score.Efficiency
import com.driveperf.domain.score.EfficiencyRating
import com.driveperf.domain.trip.DriveContext

/** Efficiency habits (§5.9): a proxy, always labelled Estimated, never energy, fuel, money or "% saved". */
public object EfficiencyHabits {
    private const val DEPTH = 10
    private const val NEEDED = 3
    private const val THRESHOLD = 0.15
    private val FLOORS = doubleArrayOf(0.05, 0.10, 0.20)
    private const val METRES_PER_KM = 1000.0
    private const val SECONDS_PER_MINUTE = 60.0

    /**
     * @param eligible the trip may be rated: same eligibility as the baseline and GNSS coverage ≥ 0.60.
     * @param priors eligible prior trips (any context; only the same context counts).
     */
    @Suppress("LongParameterList")
    public fun rate(
        stopStartCount: Int,
        distanceM: Double,
        abruptAccels: Int,
        movingS: Double,
        wobbleMps: Double?,
        cv: Double?,
        context: DriveContext,
        eligible: Boolean,
        priors: List<PriorTrip>,
    ): Efficiency {
        val x1 = if (distanceM > 0) stopStartCount / (distanceM / METRES_PER_KM) else null
        val x2 = if (movingS > 0) abruptAccels / (movingS / SECONDS_PER_MINUTE) else null
        return Efficiency(rating(x1, x2, wobbleMps, context, eligible, priors), x1, x2, wobbleMps, cv, stopStartCount)
    }

    /**
     * Rates [efficiency] again against [priors] from its stored measures, for a recompute after an earlier trip
     * changed (§5.7 "Corrections"). The measures themselves never change.
     */
    public fun rerate(efficiency: Efficiency, context: DriveContext, eligible: Boolean, priors: List<PriorTrip>): Efficiency =
        efficiency.copy(
            rating = rating(efficiency.stopsPerKm, efficiency.abruptAccelsPerMin, efficiency.speedWobbleMps, context, eligible, priors),
        )

    @Suppress("LongParameterList")
    private fun rating(
        x1: Double?,
        x2: Double?,
        wobbleMps: Double?,
        context: DriveContext,
        eligible: Boolean,
        priors: List<PriorTrip>,
    ): EfficiencyRating? {
        val same = priors.filter { it.context == context }
            .sortedWith(compareByDescending<PriorTrip> { it.startedAt }.thenByDescending { it.tripId })
            .take(DEPTH)
        val xs = listOf(x1, x2, wobbleMps)
        val bs = listOf(
            same.mapNotNull { it.stopsPerKm },
            same.mapNotNull { it.abruptAccelsPerMin },
            same.mapNotNull { it.speedWobbleMps },
        )
        val indices = xs.indices.mapNotNull { i ->
            val x = xs[i]
            val history = bs[i]
            val b = Baselines.median(history)
            if (x == null || b == null || history.size < NEEDED) null else (x - b) / maxOf(b, FLOORS[i])
        }
        if (!eligible || indices.isEmpty()) return null
        val i = indices.sum() / indices.size
        return when {
            i <= -THRESHOLD -> EfficiencyRating.BETTER
            i >= THRESHOLD -> EfficiencyRating.WORSE
            else -> EfficiencyRating.USUAL
        }
    }
}
