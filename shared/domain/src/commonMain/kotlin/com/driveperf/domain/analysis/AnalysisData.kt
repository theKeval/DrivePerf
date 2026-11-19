package com.driveperf.domain.analysis

import com.driveperf.domain.replay.ReplaySpan
import com.driveperf.domain.replay.SegmentStyle
import com.driveperf.domain.trip.PreviewRow
import com.driveperf.domain.tripfile.AnalysisTrace
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Every channel of S-ANL-01 for one trip, built once when Analysis opens (F-RPL-05, F-RPL-06, F-RPL-08).
 *
 * - Speed (m/s) is the gated 1 Hz fixes; it breaks wherever there was no fix (EC-GPS).
 * - Longitudinal and lateral G are the R1 motion channels at full rate; they break only where the IMU had a gap, so a
 *   tunnel keeps its G lanes (EC-TUN). Jerk (g/s) is the slope of longitudinal G at 10 Hz.
 * - Elevation (m) is the preview track's `elevM`.
 * - [bands] are the trip's quality spans (estimated, no GPS, excluded) that the timeline shades: the lines still run
 *   through estimated and excluded time, drawn dashed or hatched.
 * - [gg] holds only scorable moments (F-RPL-06 AC3).
 *
 * When the R1 file is gone or unreadable ([summaryOnly], EC-STO) every channel comes from the ≤ 400-row preview.
 */
@Suppress("LongParameterList") // one property per channel
public class AnalysisData internal constructor(
    public val durationS: Double,
    public val speed: ChannelTrack?,
    public val lonG: ChannelTrack?,
    public val latG: ChannelTrack?,
    public val jerk: ChannelTrack?,
    public val elevation: ChannelTrack?,
    public val bands: List<ReplaySpan>,
    public val distance: DistanceAxis,
    public val summaryOnly: Boolean,
    public val gg: GgCloud,
) {
    public val time: TimeAxis = TimeAxis(durationS)

    /** The track behind [channel], or null when the trip has no such data. Sample quality draws [bands] instead. */
    public fun track(channel: AnalysisChannel): ChannelTrack? = when (channel) {
        AnalysisChannel.SPEED -> speed
        AnalysisChannel.LON_G -> lonG
        AnalysisChannel.LAT_G -> latG
        AnalysisChannel.ELEVATION -> elevation
        AnalysisChannel.JERK -> jerk
        AnalysisChannel.QUALITY -> null
    }

    public fun axis(axis: AnalysisAxis): AxisScale = if (axis == AnalysisAxis.TIME) time else distance

    /** The end of the estimated start (drawn dashed), or 0 when the start was observed. */
    public val estimatedUntilS: Double = bands.filter { it.style == SegmentStyle.ESTIMATED }.maxOfOrNull { it.t1S } ?: 0.0
}

/** Builds [AnalysisData] from what a finalised trip stores: its R1 trace, its preview track and its quality spans. */
public object AnalysisDataBuilder {
    /** A wait between fixes longer than this breaks the speed line: the 1 Hz fixes allow a dropped fix or two. */
    public const val MAX_FIX_GAP_S: Double = 3.0

    /** The rate of the jerk lane and the G-G cloud. */
    public const val CLOUD_HZ: Int = 10

    /** R1 quality bits (pipeline-and-scoring.md §2.7). */
    private const val BIT_IMU_GAP = 1 shl 4
    private const val NOT_SCORABLE = 0b111110
    private const val GAP_ROW = 2
    private const val OBSERVED_ROW = 0
    private const val EARTH_RADIUS_M = 6_371_008.8

    /**
     * @param durationS the trip's duration; [distanceM] its measured distance, which the distance axis is scaled to.
     * @param trace the R1 file, or null when it is gone (the preview stands in).
     * @param spans the Replay's quality spans (`Replay.spans`): sorted, non-overlapping, never observed.
     */
    public fun build(
        durationS: Double,
        distanceM: Double?,
        trace: AnalysisTrace?,
        preview: List<PreviewRow>,
        spans: List<ReplaySpan>,
    ): AnalysisData {
        val noGps = spans.filter { it.style == SegmentStyle.NO_GPS }
        val excluded = spans.filter { it.style == SegmentStyle.EXCLUDED }
        val elevation = preview.takeIf { rows -> rows.any { it.elevM != null } }
            ?.let { rows -> track(rows.map { it.tS }, rows.map { it.elevM ?: Double.NaN }) }
        return if (trace != null) {
            fromTrace(durationS, distanceM, trace, noGps, excluded).let { p ->
                AnalysisData(durationS, p.speed, p.lon, p.lat, p.jerk, elevation, spans, p.distance, false, p.gg)
            }
        } else {
            fromPreview(durationS, distanceM, preview).let { p ->
                AnalysisData(durationS, p.speed, p.lon, p.lat, null, elevation, spans, p.distance, true, p.gg)
            }
        }
    }

    private class Parts(
        val speed: ChannelTrack?,
        val lon: ChannelTrack?,
        val lat: ChannelTrack?,
        val jerk: ChannelTrack?,
        val distance: DistanceAxis,
        val gg: GgCloud,
    )

    private fun fromTrace(
        durationS: Double,
        distanceM: Double?,
        trace: AnalysisTrace,
        noGps: List<ReplaySpan>,
        excluded: List<ReplaySpan>,
    ): Parts {
        val rate = trace.rateHz.toDouble()
        val q = trace.quality
        val lon = DoubleArray(trace.size) { if (q[it] and BIT_IMU_GAP != 0) Double.NaN else trace.lonG[it] }
        val lat = DoubleArray(trace.size) { if (q[it] and BIT_IMU_GAP != 0) Double.NaN else trace.latG[it] }
        val block = (trace.rateHz / CLOUD_HZ).coerceAtLeast(1)
        val cloudRate = rate / block
        val scorable = { i: Int -> q[i] and NOT_SCORABLE == 0 && excluded.none { s -> i / rate >= s.t0S && i / rate < s.t1S } }
        val lon10 = decimate(lon, block) { !lon[it].isNaN() }
        val gg = GgCloud(
            Series.uniform(cloudRate, decimate(trace.latG, block, scorable), (block - 1) / (2 * rate)),
            Series.uniform(cloudRate, decimate(trace.lonG, block, scorable), (block - 1) / (2 * rate)),
        )
        val fixes = trace.fixes.sortedBy { it.tS }
        val speed = speedTrack(fixes.map { it.tS }, fixes.map { it.speedMps }, noGps)
        val positioned = fixes.filter { it.lat != null && it.lng != null }
        val distance = distanceAxis(positioned.map { it.tS }, positioned.map { it.lat!! }, positioned.map { it.lng!! }, durationS, distanceM)
        return Parts(
            speed,
            ChannelTrack(Series.uniform(rate, lon)),
            ChannelTrack(Series.uniform(rate, lat)),
            ChannelTrack(Series.uniform(cloudRate, slope(lon10, cloudRate), (block - 1) / (2 * rate))),
            distance,
            gg,
        )
    }

    private fun fromPreview(durationS: Double, distanceM: Double?, rows: List<PreviewRow>): Parts {
        val sorted = rows.sortedBy { it.tS }
        val times = sorted.map { it.tS }
        fun channel(pick: (PreviewRow) -> Double?): List<Double> =
            sorted.map { r -> if (r.quality == GAP_ROW) Double.NaN else pick(r) ?: Double.NaN }
        val ok = sorted.map { it.quality == OBSERVED_ROW && it.lonG != null && it.latG != null }
        val gg = GgCloud(
            Series.sampled(times.toDoubleArray(), DoubleArray(sorted.size) { if (ok[it]) sorted[it].latG!! else Double.NaN }),
            Series.sampled(times.toDoubleArray(), DoubleArray(sorted.size) { if (ok[it]) sorted[it].lonG!! else Double.NaN }),
        )
        val real = sorted.filter { it.quality != GAP_ROW }
        return Parts(
            track(times, channel { it.speedMps }),
            track(times, channel { it.lonG }),
            track(times, channel { it.latG }),
            null,
            distanceAxis(real.map { it.tS }, real.map { it.lat }, real.map { it.lng }, durationS, distanceM),
            gg,
        )
    }

    private fun track(times: List<Double>, values: List<Double>): ChannelTrack? =
        if (values.all { it.isNaN() }) null else ChannelTrack(Series.sampled(times.toDoubleArray(), values.toDoubleArray()))

    /** The fixes' speed, with a `NaN` sample between two fixes too far apart or across a no-GPS span. */
    private fun speedTrack(times: List<Double>, speeds: List<Double>, noGps: List<ReplaySpan>): ChannelTrack? {
        if (times.isEmpty()) return null
        val t = ArrayList<Double>(times.size + 8)
        val v = ArrayList<Double>(times.size + 8)
        for (i in times.indices) {
            if (i > 0) {
                val a = times[i - 1]
                val b = times[i]
                if (b - a > MAX_FIX_GAP_S || noGps.any { it.t0S < b && it.t1S > a }) {
                    t += (a + b) / 2
                    v += Double.NaN
                }
            }
            t += times[i]
            v += speeds[i]
        }
        return ChannelTrack(Series.sampled(t.toDoubleArray(), v.toDoubleArray()))
    }

    /** Means of [block] samples that pass [keep]; `NaN` where fewer than half of a block do. */
    private fun decimate(values: DoubleArray, block: Int, keep: (Int) -> Boolean): DoubleArray {
        val n = values.size / block
        return DoubleArray(n) { b ->
            var sum = 0.0
            var count = 0
            for (i in b * block until (b + 1) * block) {
                if (keep(i)) {
                    sum += values[i]
                    count++
                }
            }
            if (count * 2 >= block && count > 0) sum / count else Double.NaN
        }
    }

    /** Central difference in units per second; `NaN` at the ends and next to gaps. */
    private fun slope(values: DoubleArray, rateHz: Double): DoubleArray =
        DoubleArray(values.size) { i ->
            if (i == 0 || i == values.size - 1) Double.NaN else (values[i + 1] - values[i - 1]) * rateHz / 2
        }

    /** Great-circle distance along the fixes, scaled to the trip's measured distance when it has one. */
    private fun distanceAxis(
        times: List<Double>,
        lats: List<Double>,
        lngs: List<Double>,
        durationS: Double,
        distanceM: Double?,
    ): DistanceAxis {
        val cum = DoubleArray(times.size)
        for (i in 1 until times.size) cum[i] = cum[i - 1] + haversine(lats[i - 1], lngs[i - 1], lats[i], lngs[i])
        val total = cum.lastOrNull() ?: 0.0
        if (distanceM != null && distanceM > 0.0 && total > 0.0) {
            val k = distanceM / total
            for (i in cum.indices) cum[i] *= k
        }
        return DistanceAxis(times.toDoubleArray(), cum, durationS)
    }

    private fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val p1 = lat1.toRadians()
        val p2 = lat2.toRadians()
        val dp = p2 - p1
        val dl = (lng2 - lng1).toRadians()
        val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    private fun Double.toRadians(): Double = this * kotlin.math.PI / 180.0
}
