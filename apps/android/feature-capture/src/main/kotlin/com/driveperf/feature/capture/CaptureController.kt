package com.driveperf.feature.capture

import kotlinx.coroutines.flow.StateFlow

/** How the GNSS stream looks. A status, never a score. */
enum class GpsHealth { SEARCHING, GOOD, LOST }

/** What the capture machinery is doing, as the screens need it. */
sealed interface CaptureSnapshot {
    data object Idle : CaptureSnapshot

    data class Recording(
        val startedWallMs: Long,
        val distanceM: Double,
        val gps: GpsHealth,
    ) : CaptureSnapshot

    /** Capture stopped because something it needs went away, for example a revoked location permission. */
    data object Failed : CaptureSnapshot
}

/**
 * What the capture screens need from the platform. The app implements it over the foreground service and the recorder
 * (and, once DRIVE-62 merges, over the shared core's `TripRecorder` presenter), so this module never sees platform code.
 */
interface CaptureController {
    val snapshot: StateFlow<CaptureSnapshot>

    /** True when fine location is granted, so a trip can start. Re-read each time the app resumes. */
    val canRecord: StateFlow<Boolean>

    fun start()
    fun stop()
}
