package com.driveperf.pipeline.score

import com.driveperf.domain.score.LedgerRow
import com.driveperf.domain.trip.DriveContext

/** Everything §3–§5 derive from one R1 trace. Arrays are per R1 sample. */
@Suppress("LongParameterList") // one value per derived signal
internal class R1Analysis(
    val trace: R1Trace,
    val v: DoubleArray,
    val moving: BooleanArray,
    val a3Lon: DoubleArray,
    val a3Lat: DoubleArray,
    val a15Lon: DoubleArray,
    val impact: BooleanArray,
    val events: List<DetectedEvent>,
    val ledger: RawLedger,
    val jerk: JerkTrace,
    val wobbleMps: Double?,
    val speedConsistencyCv: Double?,
    val context: DriveContext,
) {
    /** Base plus the rows of the events at [active] indices (§5.4). */
    fun total(active: Collection<Int> = events.indices.toList()): LedgerRow =
        active.fold(ledger.base) { sum, i -> sum + ledger.perEvent[i] }
}

/** Runs §3 metrics, §4 detectors and the §5.2 ledger on an R1 trace. */
internal class R1Analyser(private val cfg: ScoringConfig = ScoringConfig()) {
    private val detector = EventDetector(cfg)
    private val ledger = JerkLedger(cfg)
    private val smoothness = SmoothnessCalculator(cfg)

    /**
     * @param limited the trip badge is Limited, which downgrades event confidence (§4.4).
     * @param still optional IMU stillness for the speed estimate during GNSS gaps (§2.3).
     */
    fun analyse(trace: R1Trace, limited: Boolean = false, still: BooleanArray? = null): R1Analysis {
        val v = SpeedEstimator.estimate(trace, still)
        val moving = MovingMask.of(v, cfg)
        val runs = okRuns(trace)
        val a3Lon = filterRuns(trace.lonG, runs, Biquad.LOWPASS_3HZ)
        val a3Lat = filterRuns(trace.latG, runs, Biquad.LOWPASS_3HZ)
        val a15Lon = filterRuns(trace.lonG, runs, Biquad.LOWPASS_1_5HZ)
        val a15Lat = filterRuns(trace.latG, runs, Biquad.LOWPASS_1_5HZ)
        val jerkValid = BooleanArray(trace.size)
        for (run in runs) for (k in run.first + 1 until run.last) jerkValid[k] = true
        val impact = detector.impactMask(trace)
        val events = detector.detect(trace, a3Lon, a3Lat, v, impact, limited)
        val (raw, jerk) = ledger.compute(JerkLedger.Inputs(trace, a15Lon, a15Lat, jerkValid, moving, impact, events))
        val (wobble, cv) = SpeedMetrics.wobble(trace.gnss)
        return R1Analysis(
            trace, v, moving, a3Lon, a3Lat, a15Lon, impact, events, raw, jerk, wobble, cv, smoothness.context(trace.gnss),
        )
    }

    /** Smoothness over [active] events (all by default). Null below the exposure floor. */
    fun score(a: R1Analysis, active: Collection<Int> = a.events.indices.toList()): SmoothnessResult? =
        smoothness.score(a.total(active), a.wobbleMps, a.context)

    /** The event cost of [e], for tip selection (§6.2). */
    fun eventCost(e: DetectedEvent): Double = ledger.eventCost(e)

    /** Contiguous runs of ok samples; each is filtered separately so excluded spans cannot ring (§2.6). */
    private fun okRuns(trace: R1Trace): List<IntRange> {
        val runs = mutableListOf<IntRange>()
        var k = 0
        while (k < trace.size) {
            if (!trace.ok(k)) {
                k++
            } else {
                val start = k
                while (k < trace.size && trace.ok(k)) k++
                runs += start until k
            }
        }
        return runs
    }

    private fun filterRuns(x: DoubleArray, runs: List<IntRange>, filter: Biquad): DoubleArray {
        val out = DoubleArray(x.size)
        for (run in runs) {
            val y = filter.filtfilt(x.copyOfRange(run.first, run.last + 1))
            y.copyInto(out, run.first)
        }
        return out
    }
}
