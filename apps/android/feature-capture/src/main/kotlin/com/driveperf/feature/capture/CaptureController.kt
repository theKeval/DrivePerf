package com.driveperf.feature.capture

import kotlinx.coroutines.flow.StateFlow

/** How the GNSS stream looks. A status, never a score. */
enum class GpsHealth { SEARCHING, GOOD, LOST }

/** What the capture machinery is doing, as the screens need it. */
sealed interface CaptureSnapshot {
    data object Idle : CaptureSnapshot

    /** [isManual] tells "Started by you" from "Started automatically"; [tripId] names the trip for its saved screen. */
    data class Recording(
        val startedWallMs: Long,
        val distanceM: Double,
        val gps: GpsHealth,
        val isManual: Boolean = true,
        val tripId: String? = null,
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

    /**
     * The user marked the current trip as a passenger trip (F-CAP-08). [keep]: the core finalises it as a passenger
     * trip, saved without a score; otherwise it deletes the trip and its samples at once.
     */
    fun markPassenger(keep: Boolean)

    /**
     * The finalised trip, or null while the core has not committed it yet or when it was discarded. The trip list's
     * data, reduced to what the saved screen shows.
     */
    suspend fun finishedTrip(tripId: String): FinishedTrip? = null

    /**
     * Whether the phone still looks like it is in a moving vehicle since [sinceWallMs] (the trip's end): the OS
     * activity at medium confidence or better is automotive. False when unknown. The post-trip hold (F-CAP-07.4) waits
     * for 60 s of false.
     */
    suspend fun isMoving(sinceWallMs: Long): Boolean = false

    /** The post-trip hold is over (released, or capped): stop any movement watching [isMoving] started. */
    fun onMovementWatchEnded() {}
}

/** A committed trip as the saved screen shows it. */
data class FinishedTrip(
    val id: String,
    val manual: Boolean,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val distanceM: Double,
    val durationS: Long,
    /** The core marked it as interrupted (killed app, dead battery) rather than ended normally. */
    val interrupted: Boolean = false,
)
