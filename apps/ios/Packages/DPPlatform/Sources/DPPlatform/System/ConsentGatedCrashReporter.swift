import DPSharedBridge
import Foundation

/// The crash SDK behind [ConsentGatedCrashReporter]. The app target implements it with Sentry Cocoa (`AppEnvironment`),
/// because DPPlatform does not link the SDK; tests use a fake.
@MainActor
public protocol CrashReportingEngine: AnyObject {
    /// Starts the SDK with the EU DSN and the ADR-007 options. Called only with consent.
    func start(dsn: String)
    /// Closes the SDK and deletes its cached reports (an opt-out also removes what was queued but not sent).
    func stop()
    /// Replaces the allow-listed trip tags ([CrashTags]).
    func setTags(_ tags: [String: String])
}

/// `CrashReporter` for iOS behind the user's crash-report choice (ADR-007: Sentry, EU region; Crashlytics is never
/// linked). It initialises nothing without consent, and nothing without a DSN.
///
/// The DSN comes from the build setting `DP_SENTRY_DSN` (Info.plist `DPSentryDSN`), injected by the release lane or the
/// gitignored `Local.xcconfig`, never committed. Without it the status is `notConfigured` and nothing starts.
@MainActor
public final class ConsentGatedCrashReporter: CrashReporting {
    public private(set) var status: CrashReportingStatus = .off
    /// True when this build carries a DSN.
    public let hasDSN: Bool
    private let dsn: String?
    private let engine: (any CrashReportingEngine)?
    private var tripTags = CrashTags.forTrip(state: .idle, isManual: false)

    public init(bundle: Bundle = .main, engine: (any CrashReportingEngine)? = nil) {
        let value = (bundle.object(forInfoDictionaryKey: "DPSentryDSN") as? String)?
            .trimmingCharacters(in: .whitespaces)
        dsn = value?.isEmpty == false ? value : nil
        hasDSN = dsn != nil
        self.engine = engine
    }

    public func apply(consent: Bool) {
        guard consent else {
            if status == .active { engine?.stop() }
            status = .off
            return
        }
        guard let dsn, let engine else {
            status = .notConfigured
            return
        }
        if status != .active {
            engine.start(dsn: dsn)
            engine.setTags(tripTags)
        }
        status = .active
    }

    /// The recorder's state for the trip tags (F-ANA-03 AC2). Kept while reporting is off, applied when it starts.
    public func updateTrip(state: RecorderState, isManual: Bool) {
        let next = CrashTags.forTrip(state: state, isManual: isManual)
        guard next != tripTags else { return }
        tripTags = next
        if status == .active { engine?.setTags(next) }
    }
}
