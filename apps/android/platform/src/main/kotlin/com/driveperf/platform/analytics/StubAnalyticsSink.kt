package com.driveperf.platform.analytics

import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.analytics.AnalyticsSink

/**
 * The [AnalyticsSink] until Firebase Analytics is configured (DRIVE-42: no Firebase project or `google-services.json`
 * exists yet). It sends nothing. The real adapter replaces it in `AppModule` and calls
 * `FirebaseAnalytics.setAnalyticsCollectionEnabled`, `setConsent` (`analytics_storage` only) and `resetAnalyticsData`;
 * collection is already off at build time through the manifest (ADR-007).
 */
class StubAnalyticsSink : AnalyticsSink {
    @Volatile
    var collectionEnabled: Boolean = false
        private set

    override fun setCollectionEnabled(enabled: Boolean) {
        collectionEnabled = enabled
    }

    override fun log(event: AnalyticsEvent) = Unit

    override fun resetId() = Unit
}
