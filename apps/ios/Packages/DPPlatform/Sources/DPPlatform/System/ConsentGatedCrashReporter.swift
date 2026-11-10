import DPSharedBridge
import Foundation

/// `CrashReporter` for iOS behind the user's crash-report choice (ADR-007: Sentry, EU region; Crashlytics is never
/// linked). It initialises nothing without consent.
///
/// The Sentry SDK is not linked yet: the Sentry account and EU DSN do not exist (ADR-007; the Sentry KMP SDK will sit
/// behind the shared `CrashReporter` port). Until then this is a stub that records the consent and reports
/// `notConfigured`. The DSN comes from the build setting `DP_SENTRY_DSN` (Info.plist `DPSentryDSN`), set only in the
/// gitignored `Local.xcconfig` or CI secrets, never committed.
@MainActor
public final class ConsentGatedCrashReporter: CrashReporting {
    public private(set) var status: CrashReportingStatus = .off
    /// True when this build carries a DSN. Reporting still needs the SDK, which is not linked yet.
    public let hasDSN: Bool

    public init(bundle: Bundle = .main) {
        let value = (bundle.object(forInfoDictionaryKey: "DPSentryDSN") as? String)?
            .trimmingCharacters(in: .whitespaces)
        hasDSN = !(value?.isEmpty ?? true)
    }

    public func apply(consent: Bool) {
        guard consent else {
            // DRIVE-46: Sentry.close() and deleting cached envelopes, once the SDK is linked.
            status = .off
            return
        }
        // DRIVE-46: Sentry.start (EU DSN, sendDefaultPii = false, no screenshots or view hierarchy, tracesSampleRate = 0,
        // beforeSend coordinate scrubber) goes here once the SDK is linked. Without it, nothing is sent.
        status = .notConfigured
    }
}
