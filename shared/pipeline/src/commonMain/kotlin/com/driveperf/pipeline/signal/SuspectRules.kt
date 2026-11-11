package com.driveperf.pipeline.signal

import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.trip.Stop
import com.driveperf.domain.trip.Suspect
import com.driveperf.pipeline.geo.Geo
import com.driveperf.pipeline.score.Baselines
import kotlin.math.abs

/**
 * §1.8 bus, train and bike heuristics. Weak by design: they only decide whether to ask the user. Activity-based
 * clauses apply only when the capture has activity data, so a phone without a classifier is never flagged by them.
 */
internal class SuspectRules {
    @Suppress("LongParameterList")
    fun classify(
        activity: List<ActivitySample>,
        t0Ns: Long,
        moving: BooleanArray,
        fs: Int,
        fixes: List<GatedFix>,
        stops: List<Stop>,
        latG: DoubleArray,
        distanceM: Double,
    ): Suspect {
        val shares = activityShares(activity, t0Ns, moving, fs)
        val p95Speed = percentile(fixes.map { it.speedMps }, P95)
        val cycling = shares != null &&
            (shares.cycling >= CYCLING_SHARE || (!shares.anyAutomotive && p95Speed != null && p95Speed <= CYCLING_P95_MPS))
        if (cycling) return Suspect.CYCLING
        val bus = busPattern(stops)
        val rail = railPattern(fixes, latG, distanceM)
        val lowAutomotive = shares != null && shares.automotive < TRANSIT_AUTOMOTIVE
        return if (listOf(bus, rail, lowAutomotive).count { it } >= 2) Suspect.TRANSIT else Suspect.NONE
    }

    private data class Shares(val cycling: Double, val automotive: Double, val anyAutomotive: Boolean)

    @Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
    private fun activityShares(activity: List<ActivitySample>, t0Ns: Long, moving: BooleanArray, fs: Int): Shares? {
        if (activity.isEmpty()) return null
        val sorted = activity.sortedBy { it.tMonoNs }
        var cycling = 0
        var automotive = 0
        var total = 0
        var j = -1
        for (k in moving.indices) {
            if (!moving[k]) continue
            val t = t0Ns + k * (NS / fs)
            while (j + 1 < sorted.size && sorted[j + 1].tMonoNs <= t) j++
            total++
            val a = sorted.getOrNull(j) ?: continue
            if (a.confidence == ActivityConfidence.LOW) continue
            if (a.type == ActivityType.CYCLING) cycling++
            if (a.type == ActivityType.AUTOMOTIVE) automotive++
        }
        val anyAutomotive = sorted.any { it.type == ActivityType.AUTOMOTIVE && it.confidence != ActivityConfidence.LOW }
        return if (total == 0) null else Shares(cycling.toDouble() / total, automotive.toDouble() / total, anyAutomotive)
    }

    /** (a) at least 4 stops of 10–60 s with a median stop-to-stop distance of 200–800 m. */
    private fun busPattern(stops: List<Stop>): Boolean {
        val dwell = stops.filter { it.durationS in BUS_DWELL_MIN..BUS_DWELL_MAX }
            .mapNotNull { s -> s.lat?.let { lat -> s.lng?.let { lng -> lat to lng } } }
        val gaps = dwell.zipWithNext { a, b -> Geo.haversineM(a.first, a.second, b.first, b.second) }
        val median = Baselines.median(gaps)
        return dwell.size >= BUS_STOPS && median != null && median in BUS_SPACING_MIN..BUS_SPACING_MAX
    }

    /** (b) a nearly straight route, little lateral load and at least 5 km. */
    private fun railPattern(fixes: List<GatedFix>, latG: DoubleArray, distanceM: Double): Boolean {
        if (fixes.size < 2 || distanceM < RAIL_MIN_M) return false
        val straight = Geo.haversineM(fixes.first().lat, fixes.first().lng, fixes.last().lat, fixes.last().lng)
        val p95Lat = percentile(latG.map { abs(it) }, P95)
        return straight > 0 && distanceM / straight < RAIL_DIRECTNESS && p95Lat != null && p95Lat < RAIL_LATERAL_G
    }

    /** Nearest-rank percentile (§0.5 rule 3). */
    private fun percentile(values: List<Double>, p: Double): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val rank = kotlin.math.ceil(p * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    private companion object {
        const val NS = 1_000_000_000L
        const val P95 = 0.95
        const val CYCLING_SHARE = 0.30
        const val CYCLING_P95_MPS = 12.0
        const val TRANSIT_AUTOMOTIVE = 0.40
        const val BUS_STOPS = 4
        const val BUS_DWELL_MIN = 10.0
        const val BUS_DWELL_MAX = 60.0
        const val BUS_SPACING_MIN = 200.0
        const val BUS_SPACING_MAX = 800.0
        const val RAIL_MIN_M = 5000.0
        const val RAIL_DIRECTNESS = 1.05
        const val RAIL_LATERAL_G = 0.08
    }
}
