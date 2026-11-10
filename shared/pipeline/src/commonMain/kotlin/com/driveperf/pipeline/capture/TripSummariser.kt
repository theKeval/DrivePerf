package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.CaptureMarkType
import com.driveperf.domain.capture.FinalisedTrip
import com.driveperf.domain.capture.GeoPoint
import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.Parking
import com.driveperf.domain.capture.WakeKind
import com.driveperf.domain.trip.CaptureMeta
import com.driveperf.domain.trip.Interruption
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.foundation.Units
import com.driveperf.pipeline.Versions
import com.driveperf.pipeline.geo.Geo

/**
 * The capture-level part of finalisation: start and end, duration, gated distance, status, the short-trip rule
 * (§1.6, F-CAP-17) and the parking spot. The scoring pipeline builds on its trip; on its own it is the finaliser
 * used until `pipe-1.0.0` scoring is wired in.
 */
public class TripSummariser(private val cfg: SummaryConfig = SummaryConfig()) : TripFinaliser {
    override suspend fun finalise(request: FinaliseRequest): FinaliseResult {
        val c = request.capture
        val h = c.header
        val endNs = request.endNs ?: lastSampleNs(request) ?: h.startMonoNs
        val durationS = maxOf(0.0, (endNs - h.startMonoNs).toDouble() / Units.NANOS_PER_SECOND)
        val track = c.fixes.filter { it.tMonoNs <= endNs && (it.hAccM ?: Double.MAX_VALUE) <= cfg.gateHAccM }
        val distanceM = distance(track)
        val end = track.lastOrNull()?.let { Parking(GeoPoint(it.lat, it.lng), wallAt(request, it.tMonoNs)) }
        val short = distanceM < cfg.shortTripM || durationS < cfg.shortTripS
        if (short && !request.manual && request.interruption == null) return FinaliseResult(null, end)
        val endWall = wallAt(request, endNs)
        val trip = Trip(
            id = h.tripId,
            status = if (request.interruption != null) TripStatus.INTERRUPTED else TripStatus.FINALISED,
            kind = request.kind,
            source = if (h.wakeKind == WakeKind.MANUAL || request.manual) TripSource.MANUAL else TripSource.AUTO,
            sourceDetail = h.sourceDetail,
            startedAt = h.startWallMs,
            startTzOffsetMin = h.tzOffsetMin,
            endedAt = endWall,
            endTzOffsetMin = h.tzOffsetMin,
            tzId = h.tzId,
            distanceM = distanceM,
            estimatedDistanceM = 0.0,
            durationS = durationS,
            interrupted = request.interruption?.let { Interruption(durationS, it) },
            captureMeta = CaptureMeta(h.wakeKind, confirmLatencyS(request), null, null, null),
            pipelineVersion = Versions.PIPELINE,
            createdAt = h.startWallMs,
            updatedAt = request.nowWallMs,
        )
        return FinaliseResult(FinalisedTrip(trip), end)
    }

    private fun lastSampleNs(r: FinaliseRequest): Long? =
        listOfNotNull(r.capture.fixes.lastOrNull()?.tMonoNs, r.capture.motion.lastOrNull()?.tMonoNs).maxOrNull()

    private fun wallAt(r: FinaliseRequest, tNs: Long): Long =
        r.capture.header.startWallMs + (tNs - r.capture.header.startMonoNs) / Units.NANOS_PER_MILLI

    private fun confirmLatencyS(r: FinaliseRequest): Double? =
        r.capture.marks.firstOrNull { it.type == CaptureMarkType.CONFIRMED }
            ?.let { (it.tMonoNs - r.capture.header.startMonoNs).toDouble() / Units.NANOS_PER_SECOND }

    /** Gated distance, ignoring steps under 5 m (Spike 1). §3's full rule (moving only) lives in the pipeline. */
    private fun distance(track: List<LocationSample>): Double {
        var total = 0.0
        var anchor = track.firstOrNull() ?: return 0.0
        for (fix in track.drop(1)) {
            val step = Geo.haversineM(anchor.lat, anchor.lng, fix.lat, fix.lng)
            if (step >= cfg.minStepM) {
                total += step
                anchor = fix
            }
        }
        return total
    }
}

/** Thresholds of [TripSummariser] (§1.6, §2.3). */
public data class SummaryConfig(
    val gateHAccM: Double = 30.0,
    val minStepM: Double = 5.0,
    val shortTripM: Double = 500.0,
    val shortTripS: Double = 120.0,
)
