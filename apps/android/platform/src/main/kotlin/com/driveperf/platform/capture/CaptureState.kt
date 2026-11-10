package com.driveperf.platform.capture

import com.driveperf.platform.ports.CaptureFailure

/** How the GNSS stream looks right now. Shown as a status, never as a score. */
enum class GpsStatus { SEARCHING, GOOD, LOST }

/** What the capture machinery is doing. One process-wide value, shared by the UI and the foreground service. */
sealed interface CaptureState {
    data object Idle : CaptureState

    /** A trip is being recorded. [distanceM] is a live display figure only; the shared core decides the real distance. */
    data class Recording(
        val tripId: String,
        val startedWallMs: Long,
        val startedMonoNs: Long,
        val distanceM: Double,
        val fixCount: Int,
        val gps: GpsStatus,
    ) : CaptureState

    /** Capture stopped because something it needs went away (for example a revoked permission). */
    data class Failed(val failure: CaptureFailure) : CaptureState
}
