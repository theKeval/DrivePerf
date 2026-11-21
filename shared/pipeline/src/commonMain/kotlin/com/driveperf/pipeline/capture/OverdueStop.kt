package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.Parking
import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.WakeKind
import com.driveperf.foundation.Units

/*
 * DRIVE-223 helpers for [TripMachine]: when a paused trip's stop is overdue, and the wake kept while a trip finishes.
 * Platform timers can stall while the CPU sleeps; event times (`nowNs`) count in sleep, so the reducer decides here.
 */

/** The stop timer for [trip]: 900 s for a manual trip (P-08), 240 s for an automatic one (P-06). */
internal fun CaptureConfig.stopSeconds(trip: ActiveTrip): Double = if (trip.manual) manualStopS else autoStopS

/** True while paused at or past the stop deadline, `stoppingBeganAtNs` plus the stop timer. */
internal fun MachineState.isStopOverdue(nowNs: Long, cfg: CaptureConfig): Boolean {
    val began = stoppingBeganAtNs
    val t = trip
    return phase == RecorderState.PAUSED && began != null && t != null && nowNs >= began + secondsNs(cfg.stopSeconds(t))
}

/**
 * Whether [e], arriving while a trip finishes, replaces the kept wake: a stale automatic wake ([stale]) never displaces
 * a usable one, and nothing displaces the user's Start.
 */
internal fun MachineState.replacesPendingWake(e: MachineEvent.Wake, stale: Boolean): Boolean = when {
    pendingWake?.wake?.kind == WakeKind.MANUAL -> false
    e.wake.kind == WakeKind.MANUAL -> true
    else -> !stale
}

/** This wake as handled at [nowNs]: its trip starts then, bounded by the [parking] just recorded. */
internal fun MachineEvent.Wake.deferredTo(nowNs: Long, parking: Parking?): MachineEvent.Wake {
    val waitedNs = maxOf(0L, nowNs - this.nowNs)
    val wallMs = nowWallMs + waitedNs / Units.NANOS_PER_MILLI
    val header = newTrip.copy(startMonoNs = nowNs, startWallMs = wallMs, parking = parking)
    return copy(nowNs = nowNs, nowWallMs = wallMs, newTrip = header)
}
