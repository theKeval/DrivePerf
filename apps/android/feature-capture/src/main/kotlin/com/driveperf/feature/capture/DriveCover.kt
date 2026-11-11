package com.driveperf.feature.capture

/** Where S-DRV-03 stands: closed, asking what to do, or asking to confirm the deletion (a second, explicit step). */
enum class PassengerStep { HIDDEN, CHOOSE, CONFIRM_DISCARD }

/** What a held request wanted to open (F-CAP-07.2). Paywalls and share prompts are never named on the hold screen. */
enum class ContentTarget(val noun: HeldNoun) {
    STORY(HeldNoun.STORY),
    REPLAY(HeldNoun.REPLAY),
    ANALYSIS(HeldNoun.ANALYSIS),
    RECAP(HeldNoun.RECAP),
    WRAPPED(HeldNoun.WRAPPED),
    SHARE(HeldNoun.STORY),
    PAYWALL(HeldNoun.STORY),
}

/** The word the S-DRV-05 title uses: "Your <noun> will be ready when you've parked". */
enum class HeldNoun { STORY, REPLAY, ANALYSIS, RECAP, WRAPPED }

/** What the saved screen says about how the trip ended. */
enum class SavedKind { DRIVE, SHORT, PASSENGER }

/**
 * The full-screen cover the app shows instead of its navigation while a drive is on (the D1 gate, screen-map.md
 * section 2.2), or null when the app is free to navigate.
 */
sealed interface DriveCover {
    /** S-DRV-01: a driver trip is recording. */
    data class InDrive(val state: CaptureUiState.Recording) : DriveCover

    /**
     * S-DRV-05: content was requested during a driver trip, or before the phone has been parked for 60 s afterwards.
     * [canMarkPassenger] is true only while a trip is still recording.
     */
    data class Held(
        val noun: HeldNoun,
        val passengerStep: PassengerStep,
        val canMarkPassenger: Boolean,
    ) : DriveCover

    /** S-DRV-04, loading: the trip is being finalised. */
    data class Decoding(val tripId: String?, val passenger: Boolean) : DriveCover

    /**
     * S-DRV-04, done. The Story screen does not exist yet, so this is a "Drive saved" placeholder tied to [tripId].
     * [autoEnded]: the 15-minute stationary timeout ended the trip, which the note says.
     */
    data class Saved(
        val tripId: String?,
        val distanceKm: String?,
        val minutes: Int?,
        val kind: SavedKind,
        val autoEnded: Boolean,
    ) : DriveCover

    /** The user chose "Don't save this trip": nothing was kept. */
    data object NotSaved : DriveCover
}

/** Rules about how a trip ended, derived from the finalised trip (the capture core is not touched). */
internal object TripEnd {
    /** `CaptureConfig.manualStopS`: a manual trip with no Stop ends after 15 minutes stationary (F-CAP-01.4). */
    const val MANUAL_STOP_MS = 900_000L

    /** Finalising runs a little after the timer; the trip's `endedAt` is when stopping began. */
    const val SLACK_MS = 30_000L

    const val SHORT_DISTANCE_M = 500.0
    const val SHORT_DURATION_S = 120

    /**
     * A manual trip the user did not stop ended by the stop timer: the core sets its end time to when stopping began,
     * so it is finalised about [MANUAL_STOP_MS] after it. Interrupted trips (killed app, flat battery) are not that.
     */
    fun endedByTimeout(trip: FinishedTrip, userStopped: Boolean, nowWallMs: Long): Boolean =
        trip.manual && !userStopped && !trip.interrupted && nowWallMs - trip.endedAtMs >= MANUAL_STOP_MS - SLACK_MS

    fun isShort(distanceM: Double, durationS: Long): Boolean =
        distanceM < SHORT_DISTANCE_M || durationS < SHORT_DURATION_S
}
