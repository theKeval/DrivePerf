package com.driveperf.domain.capture

import kotlinx.coroutines.flow.StateFlow

/** Recorder states (pipeline-and-scoring.md §1.2). `PROCESSED` is transient and shows as [IDLE]. */
public enum class RecorderState { IDLE, CANDIDATE, RECORDING, PAUSED, FINISHING }

/**
 * What the live surfaces (Live Activity, Live Update, recording screen) may show. Only elapsed time, distance and
 * capture state: there is no live score, event or coaching on any surface (SAF-02).
 *
 * @property tripId the trip being recorded; null when idle. A candidate's trip is not listed in the UI.
 * @property isManual true for a manually started trip (it shows a Stop control).
 * @property elapsedS seconds since the trip start.
 * @property distanceM gated distance so far.
 * @property autoDetectEnabled false while the user has paused automatic detection.
 * @property startFailed true when the last start was dropped because its trip file could not be created; cleared by
 *   the next start. The app tells the user that recording could not start.
 * @property autoDetectHealth what Home and Health Check show about automatic detection (F-CAP-14).
 * @property lastCheckInWallMs UTC time of the last check-in: the last launch, wake, foreground or re-arm the core saw
 *   with automatic detection on (F-CAP-14 AC4, Health Check "Last check-in"). Null before the first one.
 * @property mayBatchLocation true when the platform may batch location delivery (Android `setMaxUpdateDelayMillis`,
 *   F-CAP-16 AC3): a confirmed trip in steady cruise, not manoeuvre-dense, with the in-drive screen not visible. False
 *   otherwise, and always false when idle; platforms deliver fixes unbatched then.
 */
public data class RecorderStatus(
    val state: RecorderState,
    val tripId: String?,
    val isManual: Boolean,
    val elapsedS: Double,
    val distanceM: Double,
    val autoDetectEnabled: Boolean,
    val startFailed: Boolean = false,
    val autoDetectHealth: AutoDetectHealth = if (autoDetectEnabled) AutoDetectHealth.ON else AutoDetectHealth.OFF,
    val lastCheckInWallMs: Long? = null,
    val mayBatchLocation: Boolean = false,
) {
    public companion object {
        /** Idle, nothing recorded yet. */
        public val IDLE: RecorderStatus = RecorderStatus(RecorderState.IDLE, null, false, 0.0, 0.0, true)
    }
}

/** What automatic detection looks like to the user (F-CAP-14, Home and Health Check S-SET-05). */
public enum class AutoDetectHealth {
    /** On and checking in. */
    ON,

    /** Turned off or paused by the user (F-CAP-15). Manual starts still work. */
    OFF,

    /**
     * On, but the platform reported that its triggers cannot run ([TripRecorder.setTriggersAvailable]): a permission
     * automatic detection needs is missing. The app asks the user to fix it. A watchdog lapse does not set this: a day
     * without a wake often only means the phone did not move.
     */
    NEEDS_ATTENTION,
}

/** Why the platform is (re)starting the core at launch (F-CAP-14 analytics `rearmed {reason}`). */
public enum class RearmReason {
    /** An ordinary process start, including an OS relaunch for location. */
    LAUNCH,

    /** The first launch after the device restarted (Android `BOOT_COMPLETED`, iOS first launch after a reboot). */
    BOOT,

    /** Android: the first start after the user force-stopped the app (`ApplicationStartInfo.wasForceStopped()`). */
    FORCE_STOP,
}

/**
 * The trip recorder: the shared state machine that turns wakes and samples into trips. Apps own one instance for
 * the process lifetime and call it from any thread; it serialises work on its own confined dispatcher.
 *
 * Platform code maps OS callbacks to [onWake] and implements the capture ports; it never decides whether a trip
 * exists.
 */
public interface TripRecorder {
    /** Live status for UI and live surfaces. */
    public val status: StateFlow<RecorderStatus>

    /**
     * Call once per process start, after the platform has recreated its location sessions (ADR-002 section 5). Arms
     * the parked region and recovers a trip that was recording when the app was killed (§1.7).
     */
    public fun onLaunch()

    /** A wake from an OS trigger, or [WakeKind.MANUAL] for a user start. Stale events are dropped. */
    public fun onWake(event: WakeEvent)

    /** The user confirmed Stop on the recording screen. */
    public fun stop()

    /** The user marked the current trip as a passenger trip, keeping it ([keep]) or discarding it. */
    public fun markPassenger(keep: Boolean)

    /** Turns automatic detection on or off (F-CAP-15 pause). Manual starts work either way. */
    public fun setAutoDetect(enabled: Boolean)

    /**
     * [onLaunch] with the reason for this start (F-CAP-14 AC2–3): after a reboot or an Android force-stop the core
     * re-arms its triggers and records why. Call it instead of [onLaunch], once per process start.
     */
    public fun onLaunch(reason: RearmReason) {
        onLaunch()
    }

    /**
     * The app came to the foreground. Counts as a check-in (F-CAP-14): it re-arms the parked region and moves the
     * watchdog 24 h ahead.
     */
    public fun onForeground() {}

    /** The user opened the app from the watchdog notification (F-CAP-14 analytics `watchdog_opened`). */
    public fun onWatchdogOpened() {}

    /**
     * Whether the OS triggers can run at all: false when the platform knows automatic detection cannot work (iOS
     * Always location or Motion lost, Android background location or activity recognition revoked). Shows as
     * [AutoDetectHealth.NEEDS_ATTENTION]. Call it at launch and whenever a permission changes. The default is true.
     */
    public fun setTriggersAvailable(available: Boolean) {}

    /**
     * Whether the in-drive screen (S-DRV-01) is visible. While it is, location is never batched
     * ([RecorderStatus.mayBatchLocation]). The default is not visible.
     */
    public fun setInDriveScreenVisible(visible: Boolean) {}
}
