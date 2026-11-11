package com.driveperf.pipeline.signal

import com.driveperf.domain.capture.CaptureMarkType
import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.trip.Placement
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.SegmentReason
import com.driveperf.domain.trip.TripSegment
import com.driveperf.pipeline.score.QualityBits
import com.driveperf.pipeline.score.R1Fix
import com.driveperf.pipeline.score.R1Trace
import com.driveperf.pipeline.score.SpeedEstimator
import kotlin.math.floor
import kotlin.math.round

/** The R0 → R1 result (§2) on the trip time base: `t = 0` at `startedAt`. */
@Suppress("LongParameterList") // one value per §2 output
internal class SignalResult(
    val trace: R1Trace,
    val t0Ns: Long,
    val startWallMs: Long,
    val estimate: StartEstimate?,
    val endS: Double,
    val fixes: List<GatedFix>,
    /** Spans not derived from the moving mask: estimated start, marks. Moving-dependent spans come later. */
    val fixedSpans: List<TripSegment>,
    val still: BooleanArray,
    val phoneMovedSpans: Int,
    val gravitySpreadDeg: Double,
    val anyEpochConverged: Boolean,
    val alignmentSettledS: Double?,
    val noGyro: Boolean,
    val hasImu: Boolean,
    val imuMedianHz: Double,
    val imuDelivery: Double,
) {
    /** §2.4 placement, Estimated. `POCKET` and `BAG` are never claimed in v1. */
    fun placement(cfg: SignalConfig): Placement = when {
        phoneMovedSpans == 0 && gravitySpreadDeg < cfg.mountedGravityStdDeg -> Placement.MOUNTED
        anyEpochConverged -> Placement.CUPHOLDER
        else -> Placement.UNKNOWN
    }
}

/** §2.1–§2.8: gate GNSS, detect phone movement, align per mount epoch, resample to 25 Hz, flag quality. */
internal class SignalPipeline(private val cfg: SignalConfig = SignalConfig(), private val historyS: Double = 900.0) {
    private val gate = GnssGate(cfg)
    private val movement = PhoneMovement(cfg)
    private val alignment = Alignment(cfg)
    private val lag = GnssLag(cfg)
    private val start = EstimatedStart(cfg)
    private val spans = Spans(cfg)

    @Suppress("LongMethod", "CyclomaticComplexMethod") // §2.1–§2.8 in order; splitting would hide the sequence
    fun process(capture: CapturedTrip, endNs: Long?): SignalResult {
        val h = capture.header
        val estimate = start.estimate(capture, gate.gate(capture.fixes, h.startMonoNs), historyS)
        val shiftNs = ((estimate?.startOffsetS ?: 0.0) * NS).toLong()
        val t0 = h.startMonoNs + shiftNs
        val lastNs = endNs ?: listOfNotNull(capture.fixes.lastOrNull()?.tMonoNs, capture.motion.lastOrNull()?.tMonoNs).maxOrNull() ?: t0
        val endS = maxOf(0.0, (lastNs - t0) / NS)
        val n = floor(endS * cfg.fs).toInt()
        // Cached fixes from before t = 0 stay in R0 but are not part of the trip (§1.3).
        val fixes = gate.gate(capture.fixes.filter { it.tMonoNs in t0..lastNs }, t0)
        val motion = capture.motion.filter { it.tMonoNs in t0..lastNs }
        val sig = DeviceSignal.resample(motion, t0, n, cfg)
        val estimatedEndS = estimate?.let { fixes.firstOrNull()?.tS ?: 0.0 }
        markEstimated(sig.quality, estimatedEndS)
        val phone = movement.spans(sig, fixes)
        phone.forEach { r -> for (k in r) sig.quality[k] = sig.quality[k] or QualityBits.EXCLUDED_PHONE }
        val epochs = epochs(n, phone)
        var aligned = epochs.map { alignment.align(sig, it, fixes, 0.0) }
        val lagS = lag.estimateS(vehicle(sig, aligned).lon, { sig.quality[it] and QualityBits.NOT_OK_MASK == 0 }, fixes)
        if (lagS != 0.0) aligned = epochs.map { alignment.align(sig, it, fixes, lagS) }
        aligned.filter { !it.converged }.forEach { a -> for (k in a.epoch) sig.quality[k] = sig.quality[k] or QualityBits.NOT_ALIGNED }
        val v = vehicle(sig, aligned)
        val trace = R1Trace(
            v.lon, v.lat, v.vert, v.yaw, sig.quality,
            fixes.map { R1Fix(it.tS + lagS, it.speedMps, it.lat, it.lng, it.speedAccMps) }.sortedBy { it.tS },
            cfg.fs,
        )
        val estimatedSpan = estimatedEndS
            ?.let { listOf(TripSegment(SegmentKind.ESTIMATED, 0.0, it, SegmentReason.ESTIMATED_START)) }.orEmpty()
        val wakeS = -(estimate?.startOffsetS ?: 0.0)
        return SignalResult(
            trace = trace,
            t0Ns = t0,
            startWallMs = h.startWallMs + round((estimate?.startOffsetS ?: 0.0) * MS).toLong(),
            estimate = estimate,
            endS = endS,
            fixes = fixes,
            fixedSpans = estimatedSpan + spans.fromMarks(capture, t0),
            still = movement.still(sig),
            phoneMovedSpans = phone.size,
            gravitySpreadDeg = movement.gravitySpreadDeg(sig, (wakeS * cfg.fs).toInt().coerceAtMost(n) until n),
            anyEpochConverged = aligned.any { it.converged },
            alignmentSettledS = aligned.firstOrNull { it.converged }?.settledS,
            noGyro = motion.isNotEmpty() && motion.all { it.rotRateDps.x == 0.0 && it.rotRateDps.y == 0.0 && it.rotRateDps.z == 0.0 },
            hasImu = motion.isNotEmpty(),
            imuMedianHz = medianRate(sig.perSecond, wakeS),
            imuDelivery = delivery(capture, sig.perSecond, wakeS, endS),
        )
    }

    /** Samples before the first gated fix of an estimated start carry ESTIMATED (§2.7 bit 1, D6: never scored). */
    private fun markEstimated(quality: IntArray, untilS: Double?) {
        if (untilS == null) return
        for (k in 0 until minOf(quality.size, (untilS * cfg.fs).toInt())) quality[k] = quality[k] or QualityBits.ESTIMATED
    }

    /** Mount epochs: the stretches between phone-moved spans. */
    private fun epochs(n: Int, phone: List<IntRange>): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var from = 0
        for (span in phone.sortedBy { it.first }) {
            if (span.first > from) out += from until span.first
            from = maxOf(from, span.last + 1)
        }
        if (from < n) out += from until n
        return out
    }

    private class Vehicle(val lon: DoubleArray, val lat: DoubleArray, val vert: DoubleArray, val yaw: DoubleArray)

    /** §2.5 step 8, quantised per §0.5 rule 1 (round-half-even to 0.001 g and 0.01 °/s). */
    private fun vehicle(sig: DeviceSignal, aligned: List<EpochAlignment>): Vehicle {
        val n = sig.size
        val lon = DoubleArray(n)
        val lat = DoubleArray(n)
        val vert = DoubleArray(n)
        val yaw = DoubleArray(n)
        for (a in aligned) {
            for (k in a.epoch) {
                if (sig.quality[k] and QualityBits.IMU_GAP != 0) continue
                val acc = sig.accel[k]
                lon[k] = q((acc dot a.eF) - a.biasLon, ACCEL_UNIT)
                lat[k] = q(-((acc dot a.eL) - a.biasLat), ACCEL_UNIT)
                vert[k] = q(acc dot a.eU, ACCEL_UNIT)
                yaw[k] = q(-(sig.gyro[k] dot a.eU), GYRO_UNIT)
            }
        }
        return Vehicle(lon, lat, vert, yaw)
    }

    private fun q(x: Double, unit: Double): Double = round(x / unit) * unit

    private fun medianRate(perSecond: IntArray, fromS: Double): Double {
        val counts = perSecond.drop(fromS.toInt() + 1).dropLast(1).filter { it > 0 }.sorted()
        return if (counts.isEmpty()) 0.0 else counts[counts.size / 2].toDouble()
    }

    /** Delivered ÷ expected samples at the nominal rate in force, while recording (§2.9). */
    private fun delivery(capture: CapturedTrip, perSecond: IntArray, fromS: Double, endS: Double): Double {
        val h = capture.header
        val rateMarks = capture.marks.filter { it.type == CaptureMarkType.IMU_RATE || it.type == CaptureMarkType.IMU_STOPPED }
        var expected = 0.0
        var delivered = 0.0
        val first = fromS.toInt() + 1
        val last = endS.toInt() - 1
        for (s in first until last) {
            if (s !in perSecond.indices) continue
            val tNs = h.startMonoNs + ((s - fromS) * NS).toLong()
            val mark = rateMarks.lastOrNull { it.tMonoNs <= tNs }
            val rate = when {
                mark == null -> h.nominalImuHz.toDouble()
                mark.type == CaptureMarkType.IMU_STOPPED -> 0.0
                else -> mark.detail?.toDoubleOrNull() ?: h.nominalImuHz.toDouble()
            }
            expected += rate
            delivered += minOf(rate, perSecond[s].toDouble())
        }
        return if (expected <= 0) 0.0 else delivered / expected
    }

    private companion object {
        const val NS = 1e9
        const val MS = 1000.0
        const val ACCEL_UNIT = 0.001
        const val GYRO_UNIT = 0.01
    }
}
