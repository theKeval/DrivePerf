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

    /** F-ONB-02.3: the version of the Terms and Privacy Policy the user accepted, and when. Kept only on this phone. */
    var termsAcceptedVersion: String?
    var termsAcceptedAtMillis: Long

    /** F-ONB-02.2: only a local flag is stored when the user says they are under 18. */
    var under18Blocked: Boolean

    /**
     * S-ONB-08: automatic trips are an opt-in, **off until the user turns them on** (Law 25 default, F-ONB-04.2). It gates
     * the wake path: with this off nothing records on its own, whatever permissions are granted.
     */
    var automaticTripsEnabled: Boolean

    /** S-ONB-12: [RELIABILITY_DONE], [RELIABILITY_REMIND_LATER] or null when the step was not shown. */
    var reliabilityGuide: String?

    companion object {
        const val RELIABILITY_DONE = "done"
        const val RELIABILITY_REMIND_LATER = "remind_later"
    }
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

    override var termsAcceptedVersion: String?
        get() = prefs.getString(KEY_TERMS_VERSION, null)
        set(value) = prefs.edit { putString(KEY_TERMS_VERSION, value) }

    override var termsAcceptedAtMillis: Long
        get() = prefs.getLong(KEY_TERMS_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_TERMS_AT, value) }

    override var under18Blocked: Boolean
        get() = prefs.getBoolean(KEY_UNDER_18, false)
        set(value) = prefs.edit { putBoolean(KEY_UNDER_18, value) }

    override var automaticTripsEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_TRIPS, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_TRIPS, value) }

    override var reliabilityGuide: String?
        get() = prefs.getString(KEY_RELIABILITY, null)
        set(value) = prefs.edit { putString(KEY_RELIABILITY, value) }

    private companion object {
        const val KEY_CRASH = "crash_reports"
        const val KEY_STATS = "usage_statistics"
        const val KEY_STATS_DECIDED = "usage_statistics_decided"
        const val KEY_ONBOARDED = "onboarding_completed"
        const val KEY_TERMS_VERSION = "terms_version"
        const val KEY_TERMS_AT = "terms_accepted_at"
        const val KEY_UNDER_18 = "under_18_blocked"
        const val KEY_AUTO_TRIPS = "automatic_trips_enabled"
        const val KEY_RELIABILITY = "reliability_guide"
    }
}
