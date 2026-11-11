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
 */
public data class RecorderStatus(
    val state: RecorderState,
    val tripId: String?,
    val isManual: Boolean,
    val elapsedS: Double,
    val distanceM: Double,
    val autoDetectEnabled: Boolean,
) {
    public companion object {
        /** Idle, nothing recorded yet. */
        public val IDLE: RecorderStatus = RecorderStatus(RecorderState.IDLE, null, false, 0.0, 0.0, true)
    }
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
}
