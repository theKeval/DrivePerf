package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.capture.WakeKind

/** A geofence can precede IN_VEHICLE; reconstruct only a labelled estimate, never an activity sample. */
internal object GeofenceDeparture {
    fun startWallMs(capture: CapturedTrip, distanceM: Double, firstFixS: Double, speedMps: Double): Long? {
        val h = capture.header
        val parking = h.parking ?: return null
        val cfg = CaptureConfig()
        val through = h.startWallMs + (cfg.candidateTimeoutS * MS).toLong()
        val activity = capture.activity.filter { it.wallMs in h.startWallMs..through }.minByOrNull { it.wallMs }
        val geofence = h.wakeKind == WakeKind.PARKED_EXIT && h.sourceDetail == "geofence"
        val automotive = activity?.type == ActivityType.AUTOMOTIVE && activity.confidence != ActivityConfidence.LOW
        val moving = speedMps.isFinite() && speedMps >= cfg.resumeSpeedMps && distanceM > 0.0
        return if (geofence && automotive && moving) {
            // shortcut: constant-speed chord projection, replace with measured departure history when the OS supplies it.
            val start = h.startWallMs + ((firstFixS - distanceM / speedMps) * MS).toLong()
            start.takeIf { it >= parking.wallMs && it < h.startWallMs }
        } else null
    }

    private const val MS = 1000.0
}
