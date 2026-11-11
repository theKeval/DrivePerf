package com.driveperf.platform.crash

import com.driveperf.domain.analytics.Analytics

/**
 * Applies the user's two separate privacy choices (F-ONB-08, S-PRV-07): saves each and switches the matching service at
 * once, so a change takes effect within the session. Usage statistics go through the consent-gated [Analytics] port,
 * crash reports through [CrashReporter].
 */
class PrivacyChoices(
    private val settings: ConsentSettings,
    private val crashReporter: CrashReporter,
    private val analytics: Analytics,
) {
    /** At process start: the stored crash choice, and the usage choice if the user has made one. */
    fun restore() {
        crashReporter.setEnabled(settings.crashReportsAllowed)
        if (settings.usageStatisticsDecided) analytics.setUsageConsent(settings.usageStatisticsAllowed)
    }

    fun setCrashReports(allowed: Boolean) {
        settings.crashReportsAllowed = allowed
        crashReporter.setEnabled(allowed)
    }

    /** The answer to "Share usage statistics". Events held until now are sent if [allowed], dropped if not. */
    fun setUsageStatistics(allowed: Boolean) {
        settings.usageStatisticsAllowed = allowed
        settings.usageStatisticsDecided = true
        analytics.setUsageConsent(allowed)
    }

    /** S-PRV-07 "Reset analytics ID". */
    fun resetAnalyticsId() {
        analytics.resetId()
    }
}
