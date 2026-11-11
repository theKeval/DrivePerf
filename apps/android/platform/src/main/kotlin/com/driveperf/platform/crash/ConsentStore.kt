package com.driveperf.platform.crash

import android.content.Context
import androidx.core.content.edit

/**
 * The user's choices that gate data leaving the phone (ADR-007: nothing pre-selected, asked separately) and whether
 * onboarding is finished. An interface so the logic that applies them is unit-tested without a [Context].
 */
interface ConsentSettings {
    var crashReportsAllowed: Boolean

    /** "Share usage statistics". Only meaningful once [usageStatisticsDecided]; false until then. */
    var usageStatisticsAllowed: Boolean

    /** True once the user has answered S-ONB-13 or S-PRV-07. Until then events are held in memory (F-ONB-08 AC2). */
    var usageStatisticsDecided: Boolean

    var onboardingCompleted: Boolean
}

/** [ConsentSettings] in plain app-private preferences: none of it is sensitive. */
class ConsentStore(context: Context) : ConsentSettings {
    private val prefs = context.applicationContext.getSharedPreferences("driveperf_consent", Context.MODE_PRIVATE)

    override var crashReportsAllowed: Boolean
        get() = prefs.getBoolean(KEY_CRASH, false)
        set(value) = prefs.edit { putBoolean(KEY_CRASH, value) }

    override var usageStatisticsAllowed: Boolean
        get() = prefs.getBoolean(KEY_STATS, false)
        set(value) = prefs.edit { putBoolean(KEY_STATS, value) }

    override var usageStatisticsDecided: Boolean
        get() = prefs.getBoolean(KEY_STATS_DECIDED, false)
        set(value) = prefs.edit { putBoolean(KEY_STATS_DECIDED, value) }

    override var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDED, value) }

    private companion object {
        const val KEY_CRASH = "crash_reports"
        const val KEY_STATS = "usage_statistics"
        const val KEY_STATS_DECIDED = "usage_statistics_decided"
        const val KEY_ONBOARDED = "onboarding_completed"
    }
}
