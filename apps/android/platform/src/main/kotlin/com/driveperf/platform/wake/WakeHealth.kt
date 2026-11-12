package com.driveperf.platform.wake

import android.content.SharedPreferences
import androidx.annotation.MainThread
import androidx.core.content.edit
import com.driveperf.foundation.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AutoDetectMode { OFF, ON, NEEDS_ATTENTION }

data class WakeStatus(
    val mode: AutoDetectMode,
    val lastCheckInMs: Long?,
    val forceStopNotice: Boolean,
)

/** Status of the Android wake registration, including the last successful check-in across process starts. */
@MainThread
class WakeHealth(private val preferences: SharedPreferences, private val clock: Clock) {
    private val mutableStatus = MutableStateFlow(
        WakeStatus(
            AutoDetectMode.OFF,
            preferences.getLong(LAST_CHECK_IN, 0L).takeIf { it > 0 },
            preferences.getBoolean(FORCE_STOP_NOTICE, false),
        ),
    )
    val status = mutableStatus.asStateFlow()

    private var registration = 0L

    fun onLaunch(optedIn: Boolean, wasForceStopped: (sinceMs: Long) -> Boolean) {
        val now = clock.wallMs()
        val previousLaunch = preferences.getLong(LAST_LAUNCH, 0L)
        val pending = optedIn && (status.value.forceStopNotice || wasForceStopped(previousLaunch))
        preferences.edit {
            putLong(LAST_LAUNCH, now)
            putBoolean(FORCE_STOP_NOTICE, pending)
        }
        mutableStatus.value = status.value.copy(forceStopNotice = pending)
    }

    /** Returns an ID so a late Play services callback cannot override newer settings. */
    fun refresh(optedIn: Boolean, allowed: Boolean): Long {
        val current = status.value
        val mode = when {
            !optedIn -> AutoDetectMode.OFF
            allowed && current.mode == AutoDetectMode.ON -> AutoDetectMode.ON
            else -> AutoDetectMode.NEEDS_ATTENTION
        }
        mutableStatus.value = current.copy(mode = mode)
        return ++registration
    }

    fun activityRegistrationFinished(attempt: Long, armed: Boolean) {
        if (attempt != registration || status.value.mode == AutoDetectMode.OFF) return
        if (armed) recordCheckIn() else mutableStatus.value = status.value.copy(mode = AutoDetectMode.NEEDS_ATTENTION)
    }

    fun parkedRegionRegistered() {
        if (status.value.mode == AutoDetectMode.ON) recordCheckIn()
    }

    fun dismissForceStopNotice() {
        preferences.edit { remove(FORCE_STOP_NOTICE) }
        mutableStatus.value = status.value.copy(forceStopNotice = false)
    }

    private fun recordCheckIn() {
        val now = clock.wallMs()
        preferences.edit { putLong(LAST_CHECK_IN, now) }
        mutableStatus.value = status.value.copy(mode = AutoDetectMode.ON, lastCheckInMs = now)
    }

    private companion object {
        const val LAST_CHECK_IN = "last_check_in_ms"
        const val LAST_LAUNCH = "last_launch_ms"
        const val FORCE_STOP_NOTICE = "force_stop_notice"
    }
}
