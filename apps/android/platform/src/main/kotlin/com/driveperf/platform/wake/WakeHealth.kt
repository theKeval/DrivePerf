package com.driveperf.platform.wake

import android.content.SharedPreferences
import androidx.annotation.MainThread
import androidx.core.content.edit
import com.driveperf.foundation.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the Android wake path knows that the shared core cannot: whether Play services refused the activity transition
 * registration, and whether the app was force-stopped since the last launch. The last check-in is the core's
 * (`RecorderStatus.lastCheckInWallMs`); this class keeps no timestamp of its own.
 */
data class WakeStatus(
    /** The latest activity registration for granted permissions failed (Play services error), so wakes cannot arrive. */
    val registrationFailed: Boolean,
    val forceStopNotice: Boolean,
)

@MainThread
class WakeHealth(private val preferences: SharedPreferences, private val clock: Clock) {
    private val mutableStatus = MutableStateFlow(
        WakeStatus(registrationFailed = false, forceStopNotice = preferences.getBoolean(FORCE_STOP_NOTICE, false)),
    )
    val status = mutableStatus.asStateFlow()

    private var registration = 0L
    private var allowed = false

    fun onLaunch(optedIn: Boolean, wasForceStopped: (sinceMs: Long) -> Boolean) {
        val now = clock.wallMs()
        val previousLaunch = preferences.getLong(LAST_LAUNCH, 0L)
        val pending = optedIn && (status.value.forceStopNotice || wasForceStopped(previousLaunch))
        preferences.edit {
            putLong(LAST_LAUNCH, now)
            putBoolean(FORCE_STOP_NOTICE, pending)
            remove(LEGACY_LAST_CHECK_IN) // the duplicate check-in this class once kept (DRIVE-90)
        }
        mutableStatus.value = status.value.copy(forceStopNotice = pending)
    }

    /**
     * A new registration attempt with automatic trips [allowed] or not. Returns an ID so a late Play services callback
     * cannot override newer settings. The previous failure stays until this attempt reports, so the status does not
     * flicker; without permission there is nothing to register, so no failure either.
     */
    fun refresh(allowed: Boolean): Long {
        this.allowed = allowed
        if (!allowed) mutableStatus.value = status.value.copy(registrationFailed = false)
        return ++registration
    }

    fun activityRegistrationFinished(attempt: Long, armed: Boolean) {
        if (attempt != registration || !allowed) return
        mutableStatus.value = status.value.copy(registrationFailed = !armed)
    }

    fun dismissForceStopNotice() {
        preferences.edit { remove(FORCE_STOP_NOTICE) }
        mutableStatus.value = status.value.copy(forceStopNotice = false)
    }

    private companion object {
        const val LEGACY_LAST_CHECK_IN = "last_check_in_ms"
        const val LAST_LAUNCH = "last_launch_ms"
        const val FORCE_STOP_NOTICE = "force_stop_notice"
    }
}
