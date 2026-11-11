package com.driveperf.pipeline.signal

import com.driveperf.domain.trip.BoundingBox
import com.driveperf.domain.trip.PartOfDay
import com.driveperf.domain.trip.Stop
import com.driveperf.pipeline.geo.Geo
import com.driveperf.pipeline.score.Baselines
import com.driveperf.pipeline.score.StopMetrics
import kotlin.math.abs

/** Summary metrics of §3 that come from gated GNSS and the moving mask. */
internal data class Metrics(
    val movingS: Double,
    val stoppedS: Double,
    val gatedDistanceM: Double,
    val avgMovingSpeedMps: Double?,
    val maxSpeedMps: Double?,
    val maxSpeedAtS: Double?,
    val stops: List<Stop>,
    val elevGainM: Double?,
    val elevLossM: Double?,
    val elevMinM: Double?,
    val elevMaxM: Double?,
    val bbox: BoundingBox?,
    /** Gated fixes ÷ expected at 1 Hz over moving time (Spike 1 change 4: only while moving), 0–100. */
    val gnssCoveragePct: Double,
)

internal class TripMetrics(private val cfg: SignalConfig) {
    @Suppress("LongMethod")
    fun compute(fixes: List<GatedFix>, moving: BooleanArray): Metrics {
        val fs = cfg.fs
        val movingS = moving.count { it }.toDouble() / fs
        val stoppedS = (moving.size - moving.count { it }).toDouble() / fs
        fun movingAt(t: Double): Boolean = moving.getOrElse((t * fs).toInt().coerceAtLeast(0)) { false }
        var distance = 0.0
        for (i in 1 until fixes.size) {
            val a = fixes[i - 1]
            val b = fixes[i]
            if (b.tS - a.tS <= cfg.continuityS && movingAt(b.tS)) distance += Geo.haversineM(a.lat, a.lng, b.lat, b.lng)
        }
        val (maxSpeed, maxAt) = maxSpeed(fixes)
        val stops = StopMetrics.stops(moving, fs, cfg.stopMinS).map { run ->
            val t = run.first.toDouble() / fs
            val near = fixes.minByOrNull { abs(it.tS - t) }?.takeIf { abs(it.tS - t) <= NEAR_S }
            Stop(t, run.count().toDouble() / fs, near?.lat, near?.lng)
        }
        val elevation = elevation(fixes)
        val gatedWhileMoving = fixes.count { movingAt(it.tS) }
        val coverage = if (movingS <= 0) 0.0 else minOf(PERCENT, gatedWhileMoving / movingS * PERCENT)
        return Metrics(
            movingS = movingS,
            stoppedS = stoppedS,
            gatedDistanceM = distance,
            avgMovingSpeedMps = if (movingS > 0) distance / movingS else null,
            maxSpeedMps = maxSpeed,
            maxSpeedAtS = maxAt,
            stops = stops,
            elevGainM = elevation?.gain,
            elevLossM = elevation?.loss,
            elevMinM = elevation?.min,
            elevMaxM = elevation?.max,
            bbox = bbox(fixes),
            gnssCoveragePct = coverage,
        )
    }

    private fun bbox(f: List<GatedFix>): BoundingBox? =
        if (f.isEmpty()) null else BoundingBox(f.minOf { it.lat }, f.minOf { it.lng }, f.maxOf { it.lat }, f.maxOf { it.lng })

    /** §3 max speed: median-of-3 gated speed with `speedAcc ≤ 2 m/s`, held by 3 consecutive fixes within 2 m/s. */
    private fun maxSpeed(fixes: List<GatedFix>): Pair<Double?, Double?> {
        val good = fixes.filter { (it.speedAccMps ?: 0.0) <= cfg.maxSpeedAccMps }
        var best: Double? = null
        var at: Double? = null
        for (i in 1 until good.size - 1) {
            val m = Baselines.median(listOf(good[i - 1].speedMps, good[i].speedMps, good[i + 1].speedMps)) ?: continue
            val held = (i - 1..i + 1).all { abs(good[it].speedMps - m) <= cfg.maxSpeedBandMps }
            if (held && (best == null || m > best)) {
                best = m
                at = good[i].tS
            }
        }
        return best to at
    }

    private data class Elevation(val gain: Double, val loss: Double, val min: Double, val max: Double)

    /** §3 elevation: `vAcc ≤ 15 m`, median-of-5, 10 s mean, then a 5 m dead-band for gain and loss. */
    private fun elevation(fixes: List<GatedFix>): Elevation? {
        val alt = fixes.filter { it.altM != null && (it.vAccM ?: Double.MAX_VALUE) <= cfg.elevVAccM }
        if (alt.size < MEDIAN_WINDOW) return null
        val median = alt.indices.map { i ->
            val w = alt.subList(maxOf(0, i - 2), minOf(alt.size, i + 3)).map { it.altM ?: 0.0 }
            alt[i].tS to (Baselines.median(w) ?: 0.0)
        }
        val smooth = slidingMean(median)
        var reference = smooth.first()
        var gain = 0.0
        var loss = 0.0
        for (h in smooth) {
            if (h - reference >= cfg.elevDeadBandM) { gain += h - reference; reference = h }
            if (reference - h >= cfg.elevDeadBandM) { loss += reference - h; reference = h }
        }
        return Elevation(gain, loss, smooth.min(), smooth.max())
    }

    /** Mean of the values within ±5 s of each point. Times ascend, so two moving bounds keep it linear. */
    private fun slidingMean(points: List<Pair<Double, Double>>): List<Double> {
        val out = ArrayList<Double>(points.size)
        var lo = 0
        var hi = 0
        var sum = 0.0
        for ((t, _) in points) {
            while (hi < points.size && points[hi].first - t <= MEAN_HALF_S) sum += points[hi++].second
            while (t - points[lo].first > MEAN_HALF_S) sum -= points[lo++].second
            out += sum / (hi - lo)
        }
        return out
    }

    /** §3 part of day from the local start time. */
    fun partOfDay(startWallMs: Long, tzOffsetMin: Int): PartOfDay {
        val minuteOfDay = ((startWallMs / MS_PER_MIN + tzOffsetMin) % MIN_PER_DAY + MIN_PER_DAY) % MIN_PER_DAY
        val hour = (minuteOfDay / MIN_PER_HOUR).toInt()
        return when (hour) {
            in MORNING_FROM until MIDDAY_FROM -> PartOfDay.MORNING
            in MIDDAY_FROM until AFTERNOON_FROM -> PartOfDay.MIDDAY
            in AFTERNOON_FROM until EVENING_FROM -> PartOfDay.AFTERNOON
            in EVENING_FROM until NIGHT_FROM -> PartOfDay.EVENING
            else -> PartOfDay.NIGHT
        }
    }

    private companion object {
        const val NEAR_S = 3.0
        const val PERCENT = 100.0
        const val MEDIAN_WINDOW = 5
        const val MEAN_HALF_S = 5.0
        const val MS_PER_MIN = 60_000L
        const val MIN_PER_DAY = 1440L
        const val MIN_PER_HOUR = 60L
        const val MORNING_FROM = 5
        const val MIDDAY_FROM = 11
        const val AFTERNOON_FROM = 14
        const val EVENING_FROM = 17
        const val NIGHT_FROM = 21
    }
}
