import DPSharedBridge
import Foundation
import Observation

/// S-ACC-01 in local mode, reduced to what the shell needs: analytics and diagnostics choices (S-PRV-07), the
/// on-device self-test and About. Sign in with Apple, push and TestFlight stay behind `BuildFlags`.
@MainActor
@Observable
public final class ProfileViewModel {
    public private(set) var shareUsageStatistics: Bool
    public private(set) var sendCrashReports: Bool
    public private(set) var crashStatus: CrashReportingStatus
    public private(set) var selfTestSteps: [SelfTestStep] = []
    public private(set) var isSelfTestRunning = false
    public let flags: BuildFlags
    public let coreInfo: HelloContent

    private let preferences: any PreferencesStoring
    private let crashReporter: any CrashReporting
    private let analytics: any UsageAnalytics
    private let selfTest: any SelfTesting

    public init(dependencies: AppDependencies) {
        preferences = dependencies.preferences
        crashReporter = dependencies.crashReporter
        analytics = dependencies.analytics
        selfTest = dependencies.selfTest
        flags = dependencies.flags
        coreInfo = dependencies.coreInfo.hello()
        shareUsageStatistics = dependencies.preferences.preferences.shareUsageStatistics
        sendCrashReports = dependencies.preferences.preferences.sendCrashReports
        crashStatus = dependencies.crashReporter.status
    }

    /// Re-reads the saved choices. The model outlives onboarding, which saves them too, so Profile calls this when it
    /// opens and the app calls it when onboarding finishes.
    public func refresh() {
        shareUsageStatistics = preferences.preferences.shareUsageStatistics
        sendCrashReports = preferences.preferences.sendCrashReports
        crashStatus = crashReporter.status
    }

    public func setShareUsageStatistics(_ on: Bool) {
        shareUsageStatistics = on
        preferences.update { $0.shareUsageStatistics = on }
        // Takes effect within this session: the gate stops (or starts) sending at once (F-ONB-08.3).
        analytics.setUsageConsent(on)
    }

    /// S-PRV-07 "Reset analytics ID". The row arrives with the Privacy Centre; the action is ready for it.
    public func resetAnalyticsID() {
        analytics.resetID()
    }

    /// On → start the reporter; off → stop it (ADR-007).
    public func setSendCrashReports(_ on: Bool) {
        sendCrashReports = on
        preferences.update { $0.sendCrashReports = on }
        crashReporter.apply(consent: on)
        crashStatus = crashReporter.status
    }

    public var crashStatusText: String {
        switch crashStatus {
        case .off: "Off"
        case .notConfigured: "On · not connected in this build"
        case .active: "On"
        }
    }

    public func runSelfTest() async {
        guard !isSelfTestRunning else { return }
        isSelfTestRunning = true
        for await steps in selfTest.run() {
            selfTestSteps = steps
        }
        isSelfTestRunning = false
    }

    public var selfTestSummary: String? {
        guard !selfTestSteps.isEmpty, selfTestSteps.allSatisfy(\.state.isFinished) else { return nil }
        let failed = selfTestSteps.filter { if case .failed = $0.state { true } else { false } }.count
        return failed == 0 ? "All checks passed" : "\(failed) check\(failed == 1 ? "" : "s") failed"
    }
}
