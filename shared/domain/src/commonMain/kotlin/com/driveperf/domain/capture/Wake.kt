package com.driveperf.domain.capture

/**
 * The platform-neutral reason the app was woken (pipeline-and-scoring.md §1.3). Platform code maps its OS callbacks
 * to one of these; the shared core never sees OS types.
 */
public enum class WakeKind {
    /** iOS `CLMonitor` condition exit at the last parking spot; Android geofence exit. The primary trigger (Spike 1). */
    PARKED_EXIT,

    /** iOS significant location change. A fallback trigger. */
    MOVE_SIGNAL,

    /** Android Activity Recognition `IN_VEHICLE` enter. On iOS activity history is queried on wake instead. */
    ACTIVITY_VEHICLE,

    /** The user started a trip: in-app Start, Control Center control, widget, Quick Settings tile. */
    MANUAL,

    /** The OS relaunched the app for location (iOS) or restarted the foreground service (Android). */
    RELAUNCH,
}

/**
 * One wake.
 *
 * @property tMonoNs when the OS event happened, on the monotonic clock. A stale event (Spike 1: every iOS cold launch
 *   delivered a cached significant-change fix 504–52,106 s old) is dropped by the core, so adapters must pass the
 *   event's own time, not the delivery time.
 * @property wallMs the event's UTC time.
 * @property sourceDetail for [WakeKind.MANUAL]: where the user started it (`app`, `control_center`, `widget`,
 *   `qs_tile`, `live_activity`). A code key, never free text.
 */
public data class WakeEvent(
    val kind: WakeKind,
    val tMonoNs: Long,
    val wallMs: Long,
    val sourceDetail: String? = null,
)
