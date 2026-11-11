import DPSharedBridge
import DrivePerfShared
import Foundation

/// The Kotlin `AnalyticsSink` until Firebase Analytics is configured (DRIVE-42: no Firebase project or
/// `GoogleService-Info.plist` exists yet). It sends nothing. The real adapter replaces it in `CorePlatformBindings` and
/// calls `Analytics.setAnalyticsCollectionEnabled`, `setConsent` (`analytics_storage` only) and `resetAnalyticsData`;
/// collection is already off at build time through `FIREBASE_ANALYTICS_COLLECTION_ENABLED = NO` (ADR-007). No
/// AdSupport and no AppTrackingTransparency are linked.
final class StubAnalyticsSink: NSObject, DrivePerfShared.AnalyticsSink, @unchecked Sendable {
    func setCollectionEnabled(enabled: Bool) {}
    func log(event: any AnalyticsEvent) {}
    func resetId() {}
}

/// `UsageAnalytics` over the started core's consent-gated `Analytics` port. The user can answer before the core has
/// started (the core waits for the first unlock), so the latest answer is kept and applied on `attach`.
@MainActor
final class CoreUsageAnalytics: UsageAnalytics {
    private var core: (any DrivePerfShared.Analytics)?
    private var answer: Bool?

    func attach(_ analytics: any DrivePerfShared.Analytics) {
        core = analytics
        if let answer { analytics.setUsageConsent(allowed: answer) }
    }

    func setUsageConsent(_ allowed: Bool) {
        answer = allowed
        core?.setUsageConsent(allowed: allowed)
    }

    func resetID() {
        core?.resetId()
    }
}
