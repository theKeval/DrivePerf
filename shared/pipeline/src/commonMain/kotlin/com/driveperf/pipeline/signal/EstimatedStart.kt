package com.driveperf.pipeline.signal

import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.capture.Parking
import com.driveperf.domain.capture.WakeKind
import com.driveperf.pipeline.capture.GeofenceDeparture
import com.driveperf.pipeline.geo.Geo

/** A §2.8 estimate on the wake time base; negative start offset. */
internal data class StartEstimate(
    val startOffsetS: Double,
    val distanceM: Double,
    val method: String,
    val firstFixOffsetS: Double,
)

/** §2.8: prefer motion history; a bounded distance/speed estimate covers an unknown departure time. */
internal class EstimatedStart(private val cfg: SignalConfig) {
    /** @param gatedFromWake gated fixes on the wake's time base (seconds from the header's start). */
    fun estimate(capture: CapturedTrip, gatedFromWake: List<GatedFix>, historyS: Double): StartEstimate? {
        val h = capture.header
        val parking = h.parking
        val first = gatedFromWake.firstOrNull()
        if (h.wakeKind == WakeKind.MANUAL || parking == null || first == null) return null
        val windowFromBound = h.startWallMs - (historyS * MS).toLong()
        val windowStart = maxOf(windowFromBound, parking.wallMs)
        val history = capture.activity.filter { it.wallMs in windowStart..h.startWallMs }.sortedBy { it.wallMs }
        val runStart = automotiveRunStart(history)
        val atEdge = runStart != null && runStart <= windowStart + (cfg.historyEdgeS * MS).toLong() &&
            windowStart == windowFromBound
        return if (runStart != null && !atEdge) {
            check(capture, parking, first, (runStart - h.startWallMs) / MS, "motion_history")
        } else {
            fallback(capture, parking, gatedFromWake, history.isEmpty() || atEdge, runStart)
        }
    }

    private fun fallback(
        capture: CapturedTrip, parking: Parking, fixes: List<GatedFix>, unknownDeparture: Boolean, runStart: Long?,
    ): StartEstimate? {
        val h = capture.header
        return if (h.sourceDetail == "geofence") {
            // Keep DRIVE-212's Android geofence policy; the robust fallback below is for iOS wakes.
            if (runStart == null) geofenceRunStart(capture, fixes)?.let {
                check(capture, parking, fixes.first(), (it - h.startWallMs) / MS, "distance_speed")
            } else null
        } else if (h.sourceDetail == null && h.wakeKind != WakeKind.ACTIVITY_VEHICLE && unknownDeparture) {
            distanceSpeed(capture, parking, fixes)
        } else null
    }

    private fun distanceSpeed(capture: CapturedTrip, parking: Parking, fixes: List<GatedFix>): StartEstimate? {
        val reliable = fixes.filter {
            !it.derived && it.hAccM in 0.0..FALLBACK_HACC_M && it.speedMps.isFinite() && it.speedMps >= 0 &&
                it.speedAccMps?.let { accuracy -> accuracy in 0.0..cfg.maxSpeedAccMps } == true
        }
        val first = reliable.firstOrNull() ?: return null
        val speeds = reliable.takeWhile { it.tS <= first.tS + FALLBACK_WINDOW_S }.map { it.speedMps }.sorted()
        val middle = speeds.size / 2
        val speed = if (speeds.size % 2 == 0) (speeds[middle - 1] + speeds[middle]) / 2 else speeds[middle]
        return if (speeds.size >= MIN_SPEED_FIXES && speed > 0 && speed <= cfg.estimateMaxMps) {
            val d = Geo.haversineM(parking.point.lat, parking.point.lng, first.lat, first.lng)
            check(capture, parking, first, first.tS - d / speed, "distance_speed")
        } else null
    }

    private fun geofenceRunStart(capture: CapturedTrip, fixes: List<GatedFix>): Long? {
        val parking = capture.header.parking
        val first = fixes.firstOrNull()
        val driving = fixes.firstOrNull { it.speedMps >= 3.0 && it.tS <= 180.0 && !it.derived }
        return if (parking == null || first == null || driving == null) null else {
            val d = Geo.haversineM(parking.point.lat, parking.point.lng, first.lat, first.lng)
            GeofenceDeparture.startWallMs(capture, d, first.tS, driving.speedMps)
        }
    }

    /** Never clip an implausible result into the allowed range: keep it unresolved. */
    private fun check(capture: CapturedTrip, parking: Parking, first: GatedFix, tStart: Double, method: String): StartEstimate? {
        val d = Geo.haversineM(parking.point.lat, parking.point.lng, first.lat, first.lng)
        val dt = first.tS - tStart
        val afterParking = capture.header.startWallMs + tStart * MS >= parking.wallMs
        val plausible = d <= cfg.estimateMaxM && dt > 0 && d / dt <= cfg.estimateMaxMps && dt <= cfg.estimateMaxS
        return if (plausible && tStart < 0 && afterParking) StartEstimate(tStart, d, method, first.tS) else null
    }

    /** The UTC start of the automotive run that is still going at the wake, or null. */
    private fun automotiveRunStart(history: List<ActivitySample>): Long? {
        var start: Long? = null
        for (sample in history) {
            when {
                sample.type != ActivityType.AUTOMOTIVE -> start = null
                sample.confidence != ActivityConfidence.LOW && start == null -> start = sample.wallMs
                // A lower-confidence automotive update does not end an already established automotive run.
            }
        }
        return start
    }

    private companion object {
        const val MS = 1000.0
        const val FALLBACK_WINDOW_S = 30.0
        const val FALLBACK_HACC_M = 20.0
        const val MIN_SPEED_FIXES = 3
    }
}
