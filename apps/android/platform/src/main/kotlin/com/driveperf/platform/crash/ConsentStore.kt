package com.driveperf.platform.crash

import android.content.Context
import androidx.core.content.edit

/**
 * The user's opt-in choices (ADR-007: nothing pre-selected, asked separately) and whether onboarding is finished.
 * Plain app-private preferences: none of it is sensitive.
 */
class ConsentStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("driveperf_consent", Context.MODE_PRIVATE)

    var crashReportsAllowed: Boolean
        get() = prefs.getBoolean(KEY_CRASH, false)
        set(value) = prefs.edit { putBoolean(KEY_CRASH, value) }

    var usageStatisticsAllowed: Boolean
        get() = prefs.getBoolean(KEY_STATS, false)
        set(value) = prefs.edit { putBoolean(KEY_STATS, value) }

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDED, value) }

    private companion object {
        const val KEY_CRASH = "crash_reports"
        const val KEY_STATS = "usage_statistics"
        const val KEY_ONBOARDED = "onboarding_completed"
    }
}
