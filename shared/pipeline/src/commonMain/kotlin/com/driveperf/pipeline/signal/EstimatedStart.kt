package com.driveperf.pipeline.signal

import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.capture.Parking
import com.driveperf.domain.capture.WakeKind
import com.driveperf.pipeline.geo.Geo

/** A §2.8 estimate: the trip starts [startOffsetS] before the wake (negative), [distanceM] from the parking spot. */
internal data class StartEstimate(val startOffsetS: Double, val distanceM: Double)

/**
 * §2.8 estimated start, with Spike 1 change 3: the motion-start search is bounded by the last parking, and a run that
 * begins at the edge of the 15-minute window is not a motion start (the true start is unknown).
 */
internal class EstimatedStart(private val cfg: SignalConfig) {
    /** @param gatedFromWake gated fixes on the wake's time base (seconds from the header's start). */
    fun estimate(capture: CapturedTrip, gatedFromWake: List<GatedFix>, historyS: Double): StartEstimate? {
        val h = capture.header
        val parking = h.parking
        val first = gatedFromWake.firstOrNull()
        val windowFromBound = h.startWallMs - (historyS * MS).toLong()
        val windowStart = maxOf(windowFromBound, parking?.wallMs ?: windowFromBound)
        val runStart = automotiveRunStart(capture, windowStart)
        val atEdge = runStart != null && runStart <= windowStart + (cfg.historyEdgeS * MS).toLong() &&
            windowStart == windowFromBound
        @Suppress("ComplexCondition") // §2.8 needs all four: an automatic wake, a parking spot, a fix and a motion start
        return if (h.wakeKind == WakeKind.MANUAL || parking == null || first == null || runStart == null || atEdge) {
            null
        } else {
            check(capture, parking, first, runStart)
        }
    }

    /** §2.8 plausibility: within 5 km, under 60 m/s and 900 s from the motion start to the first fix. */
    private fun check(capture: CapturedTrip, parking: Parking, first: GatedFix, runStart: Long): StartEstimate? {
        val tStart = (runStart - capture.header.startWallMs) / MS
        val d = Geo.haversineM(parking.point.lat, parking.point.lng, first.lat, first.lng)
        val dt = first.tS - tStart
        val plausible = d <= cfg.estimateMaxM && dt > 0 && d / dt <= cfg.estimateMaxMps && dt <= cfg.estimateMaxS
        return if (plausible && tStart < 0) StartEstimate(tStart, d) else null
    }

    /** The UTC start of the automotive run that is still going at the wake, or null. */
    private fun automotiveRunStart(capture: CapturedTrip, windowStart: Long): Long? {
        val wake = capture.header.startWallMs
        val history = capture.activity.filter { it.wallMs in windowStart..wake }.sortedBy { it.wallMs }
        var start: Long? = null
        for (sample in history.asReversed()) {
            val automotive = sample.type == ActivityType.AUTOMOTIVE && sample.confidence != ActivityConfidence.LOW
            if (!automotive) break
            start = sample.wallMs
        }
        return start
    }

    private companion object {
        const val MS = 1000.0
    }
}
