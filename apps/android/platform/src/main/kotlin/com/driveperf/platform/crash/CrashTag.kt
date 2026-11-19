package com.driveperf.platform.crash

import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.RecorderStatus

/**
 * The only tags a crash report may carry (ADR-007, DRIVE-196). Every value is from a fixed set: no free text, no
 * location, no trip ID. `beforeSend` drops any other tag, and `CrashPrivacyRulesTest` fails if code sets one directly.
 */
enum class CrashTag(val key: String) {
    /** `true` while a trip is being captured (candidate to finishing), so capture-time crashes stand out (AC2). */
    IN_TRIP("in_trip"),

    /** `auto`, `manual` or `none`. */
    CAPTURE_MODE("capture_mode"),

    /** The recorder state: `idle`, `candidate`, `recording`, `paused` or `finishing`. */
    TRIP_STATE("trip_state"),

    /** Reserved by ADR-007 for the database state; not set yet. */
    DB_STATE("db_state"),

    /** The app's version name. */
    APP_BUILD("app_build"),
    ;

    companion object {
        val allowedKeys: Set<String> = entries.map { it.key }.toSet()

        /** The trip tags for [status]. */
        fun forTrip(status: RecorderStatus): Map<CrashTag, String> {
            val inTrip = status.state != RecorderState.IDLE
            val mode = when {
                !inTrip -> "none"
                status.isManual -> "manual"
                else -> "auto"
            }
            return mapOf(
                IN_TRIP to inTrip.toString(),
                CAPTURE_MODE to mode,
                TRIP_STATE to status.state.name.lowercase(),
            )
        }
    }
}
