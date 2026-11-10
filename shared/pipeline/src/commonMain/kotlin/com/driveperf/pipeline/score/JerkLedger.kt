package com.driveperf.pipeline.score

import com.driveperf.domain.score.LedgerRow
import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.EventType
import com.driveperf.foundation.Units
import kotlin.math.abs

/** The ledger before event IDs exist: one base row, one row per detected event (same order), and the floors. */
internal data class RawLedger(val base: LedgerRow, val perEvent: List<LedgerRow>, val jxMps3: Double, val jyMps3: Double)

/** Per-sample excess jerk, for tips (§6.2) and the cleanest stretch (§6.3). */
internal data class JerkTrace(
    val counted: BooleanArray,
    val excessLon: DoubleArray,
    val excessLat: DoubleArray,
    val bandLon: DoubleArray,
    val jyMps3: Double,
)

/**
 * The jerk ledger (§5.2–§5.4): time-based exposure `T` and excess jerk above a comfort band, per channel, with each
 * event owning a disjoint window so feedback can drop it exactly. Only `+ − × ÷ √` (§0.5).
 */
internal class JerkLedger(private val cfg: ScoringConfig) {
    private val dt = 1.0 / cfg.fs

    data class Inputs(
        val trace: R1Trace,
        val a15Lon: DoubleArray,
        val a15Lat: DoubleArray,
        val jerkValid: BooleanArray,
        val moving: BooleanArray,
        val impact: BooleanArray,
        val events: List<DetectedEvent>,
    )

    fun compute(input: Inputs): Pair<RawLedger, JerkTrace> {
        val n = input.trace.size
        val jx = jerk(input.a15Lon, input.jerkValid)
        val jy = jerk(input.a15Lat, input.jerkValid)
        val counted = BooleanArray(n) { k -> input.jerkValid[k] && input.trace.ok(k) && input.moving[k] && !input.impact[k] }
        val floorX = noiseFloor(jx, input.a15Lon, counted)
        val floorY = noiseFloor(jy, input.a15Lat, counted)
        val relief = relief(input.moving)
        val owner = owners(n, input.events)
        val rows = Array(input.events.size + 1) { DoubleArray(ROW) }
        val trace = JerkTrace(counted, DoubleArray(n), DoubleArray(n), DoubleArray(n), floorY)
        for (k in 0 until n) {
            if (!counted[k]) continue
            val band = if (relief[k]) 2 * floorX else floorX
            val ex = maxOf(0.0, abs(jx[k]) - band)
            val ey = maxOf(0.0, abs(jy[k]) - floorY)
            val row = rows[owner[k] + 1]
            row[T] += dt
            if (input.a15Lon[k] < 0) row[BRAKE] += ex * dt else row[ACCEL] += ex * dt
            row[CORNER] += ey * dt
            trace.excessLon[k] = ex
            trace.excessLat[k] = ey
            trace.bandLon[k] = band
        }
        input.events.forEachIndexed { i, e ->
            val slot = when (e.type) {
                EventType.BRAKE -> BRAKE
                EventType.ACCEL -> ACCEL
                EventType.CORNER -> CORNER
            }
            rows[i + 1][slot] += eventCost(e)
        }
        val ledgerRows = rows.map { LedgerRow(it[T], it[BRAKE], it[ACCEL], it[CORNER]) }
        return RawLedger(ledgerRows.first(), ledgerRows.drop(1), floorX, floorY) to trace
    }

    /** §5.3: `2.0 × confWeight × (1 + min(1, m))`, in m/s². */
    fun eventCost(e: DetectedEvent): Double {
        val w = when (e.confidence) {
            Confidence.HIGH -> cfg.weightHigh
            Confidence.MEDIUM -> cfg.weightMedium
            Confidence.LOW -> cfg.weightLow
        }
        return cfg.eventCostBase * w * (1 + minOf(1.0, e.margin))
    }

    /** `j[k] = (a[k+1] − a[k−1]) ÷ 2Δ`, in m/s³, where both neighbours are in the same filtered run. */
    private fun jerk(a: DoubleArray, valid: BooleanArray): DoubleArray = DoubleArray(a.size) { k ->
        if (valid[k]) (a[k + 1] - a[k - 1]) * Units.G / (2 * dt) else 0.0
    }

    /** §5.2: three robust sigmas of jerk on quiet samples, clamped to [1.2, 2.4] m/s³; 1.2 with under 30 s of data. */
    private fun noiseFloor(j: DoubleArray, a: DoubleArray, counted: BooleanArray): Double {
        val q = mutableListOf<Double>()
        for (k in j.indices) if (counted[k] && abs(a[k]) <= cfg.noiseQuietG) q += abs(j[k])
        if (q.size < cfg.noiseMinSamples) return cfg.bandMinMps3
        q.sort()
        val sigma = MAD_TO_SIGMA * q[q.size / 2]
        return minOf(cfg.bandMaxMps3, maxOf(cfg.bandMinMps3, cfg.bandSigmaMultiplier * sigma))
    }

    /** §5.2 stop relief: around every stopped run of ≥ 1 s that is not at the start of the trip. */
    private fun relief(moving: BooleanArray): BooleanArray {
        val relief = BooleanArray(moving.size)
        val minSamples = cfg.reliefMinStopS * cfg.fs
        for (run in MovingMask.stoppedRuns(moving)) {
            if (run.first == 0 || run.count() < minSamples) continue
            for (k in maxOf(0, run.first - cfg.reliefBefore) until minOf(moving.size, run.first + cfg.reliefAfter)) relief[k] = true
        }
        return relief
    }

    /** §5.4: each event owns `[start − 25, end + 25]`; on overlap the earlier event (sort order §0.5) keeps it. */
    private fun owners(n: Int, events: List<DetectedEvent>): IntArray {
        val owner = IntArray(n) { -1 }
        events.forEachIndexed { i, e ->
            for (k in maxOf(0, e.start - cfg.eventWindow)..minOf(n - 1, e.end + cfg.eventWindow)) if (owner[k] < 0) owner[k] = i
        }
        return owner
    }

    private companion object {
        const val MAD_TO_SIGMA = 1.4826
        const val ROW = 4
        const val T = 0
        const val BRAKE = 1
        const val ACCEL = 2
        const val CORNER = 3
    }
}
