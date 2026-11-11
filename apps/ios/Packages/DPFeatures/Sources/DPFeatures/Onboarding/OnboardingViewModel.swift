import DPSharedBridge
import Foundation
import Observation

/// S-ONB-01…16 (WP-A), iOS order. Add-your-car (S-ONB-14) arrives with the Garage (DRIVE-74), so the numbered setup
/// runs 1 to 7 here.
public enum OnboardingStep: Int, CaseIterable, Sendable {
    case welcome, storyPreview, privacy, ageTerms, capturePromise, location, motion, autoTrips, notifications,
        helpImprove, ready

    public static let setupTotal = 7

    /// "Setup, step n of N" for the numbered steps; the intro pages and the ready screen have none.
    public var setupStep: Int? {
        switch self {
        case .ageTerms: 1
        case .capturePromise: 2
        case .location: 3
        case .motion: 4
        case .autoTrips: 5
        case .notifications: 6
        case .helpImprove: 7
        case .welcome, .storyPreview, .privacy, .ready: nil
        }
    }
}

public enum OnboardingSheet: String, Identifiable, Sendable {
    /// S-ONB-11
    case precise
    /// S-ONB-16 default: location denied.
    case locationDenied
    /// S-ONB-16 alwaysDeclined.
    case alwaysDeclined

    public var id: String { rawValue }
}

@MainActor
@Observable
public final class OnboardingViewModel {
    /// Saved with the acceptance (F-ONB-02): the version of the Terms of Use and Privacy Policy on driveperf.com.
    public static let termsVersion = "2026-11"

    public private(set) var step: OnboardingStep = .welcome
    public var sheet: OnboardingSheet?
    public private(set) var isRequesting = false
    public private(set) var permissions = PermissionSnapshot()

    public var ageConfirmed = false
    public private(set) var under18Blocked: Bool
    public private(set) var locationDenied = false
    public private(set) var preciseRefused = false
    public private(set) var motionDenied = false
    public var autoTripsOn = false
    public private(set) var alwaysDeclined = false
    public private(set) var notificationsDenied = false
    public var shareUsageStatistics = false
    public var sendCrashReports = false
    public var isHowItWorksPresented = false

    private var history: [OnboardingStep] = []
    private let permissionsPort: any PermissionRequesting
    private let preferences: any PreferencesStoring
    private let crashReporter: any CrashReporting
    private let analytics: any UsageAnalytics
    private let refreshCapture: @MainActor () -> Void
    private let onFinished: @MainActor () -> Void

    public init(
        permissions: any PermissionRequesting, preferences: any PreferencesStoring, crashReporter: any CrashReporting,
        analytics: any UsageAnalytics,
        refreshCapture: @escaping @MainActor () -> Void, onFinished: @escaping @MainActor () -> Void
    ) {
        permissionsPort = permissions
        self.preferences = preferences
        self.crashReporter = crashReporter
        self.analytics = analytics
        self.refreshCapture = refreshCapture
        self.onFinished = onFinished
        under18Blocked = preferences.preferences.under18Blocked
        if under18Blocked { step = .ageTerms }
    }

    public var canGoBack: Bool { !history.isEmpty && !under18Blocked }

    // MARK: Navigation

    public func back() {
        guard let previous = history.popLast() else { return }
        step = previous
    }

    /// S-ONB-01 "Get started", S-ONB-02 "Next", S-ONB-03 "Continue", S-ONB-05 "Continue".
    public func next() {
        guard let following = OnboardingStep(rawValue: step.rawValue + 1) else { return }
        go(to: following)
    }

    /// "Skip" on the intro pages goes to the age step, which can't be skipped.
    public func skipIntro() {
        go(to: .ageTerms)
    }

    private func go(to target: OnboardingStep) {
        history.append(step)
        step = target
    }

    // MARK: S-ONB-04 age and terms

    public var canConfirmAge: Bool { ageConfirmed }

    public func confirmAge() {
        guard ageConfirmed else { return }
        preferences.update {
            $0.termsAcceptedVersion = Self.termsVersion
            $0.termsAcceptedAt = Date()
        }
        go(to: .capturePromise)
    }

    /// Only a local flag is stored (F-ONB-02.2).
    public func declareUnder18() {
        under18Blocked = true
        ageConfirmed = false
        preferences.update { $0.under18Blocked = true }
    }

    public func undoUnder18() {
        under18Blocked = false
        preferences.update { $0.under18Blocked = false }
    }

    // MARK: S-ONB-06 location

    public func continueLocation() async {
        await request { await self.permissionsPort.requestLocationWhenInUse() }
        switch permissions.location {
        case .whenInUse, .always:
            if permissions.preciseLocation {
                go(to: .motion)
            } else {
                sheet = .precise
            }
        case .denied, .restricted:
            locationDenied = true
            sheet = .locationDenied
        case .notDetermined:
            break
        }
    }

    /// "Not now", and "Continue without location" on the denied state.
    public func declineLocation() {
        sheet = .locationDenied
    }

    /// S-ONB-16 "Not now" inside onboarding: on to notifications, with automatic trips off.
    public func continueWithoutLocation() {
        sheet = nil
        autoTripsOn = false
        preferences.update { $0.autoTripsEnabled = false }
        go(to: .notifications)
    }

    // MARK: S-ONB-11 precise location

    public func requestPrecise() async {
        await request { await self.permissionsPort.requestPreciseLocation() }
        if permissions.preciseLocation {
            sheet = nil
            go(to: .motion)
        } else {
            preciseRefused = true
        }
    }

    public func keepApproximate() {
        preciseRefused = true
    }

    /// "Continue" on the refused state, or the precise sheet being swiped away: location is granted (approximate),
    /// so the flow moves on to motion. Also runs when any sheet is dismissed; it only acts on the location step.
    public func finishPreciseSheet() {
        sheet = nil
        guard step == .location, permissions.location.canRecord else { return }
        go(to: .motion)
    }

    // MARK: S-ONB-07 motion

    public func continueMotion() async {
        if motionDenied {
            go(to: .autoTrips)
            return
        }
        await request { await self.permissionsPort.requestMotion() }
        switch permissions.motion {
        case .denied, .restricted: motionDenied = true
        default: go(to: .autoTrips)
        }
    }

    // MARK: S-ONB-08 automatic trips

    public func continueAutoTrips() async {
        guard autoTripsOn else {
            preferences.update { $0.autoTripsEnabled = false }
            refreshCapture()
            go(to: .notifications)
            return
        }
        if !preferences.preferences.alwaysPromptShown {
            preferences.update { $0.alwaysPromptShown = true }
            await request { await self.permissionsPort.requestLocationAlways() }
        } else {
            permissions = await permissionsPort.current()
        }
        if permissions.location == .always {
            preferences.update { $0.autoTripsEnabled = true }
            refreshCapture()
            go(to: .notifications)
        } else {
            alwaysDeclined = true
            autoTripsOn = false
            preferences.update { $0.autoTripsEnabled = false }
            refreshCapture()
            sheet = .alwaysDeclined
        }
    }

    /// S-ONB-16 alwaysDeclined "Continue in manual mode".
    public func continueInManualMode() {
        sheet = nil
        go(to: .notifications)
    }

    // MARK: S-ONB-10 notifications

    public func continueNotifications() async {
        if notificationsDenied {
            go(to: .helpImprove)
            return
        }
        await request { await self.permissionsPort.requestNotifications() }
        if permissions.notifications == .denied {
            notificationsDenied = true
        } else {
            go(to: .helpImprove)
        }
    }

    public func skipNotifications() {
        go(to: .helpImprove)
    }

    // MARK: S-ONB-13 help improve

    /// Saves both choices exactly as set; nothing is pre-selected (F-ONB-08).
    public func continueHelpImprove() async {
        preferences.update {
            $0.shareUsageStatistics = shareUsageStatistics
            $0.sendCrashReports = sendCrashReports
        }
        crashReporter.apply(consent: sendCrashReports)
        // The shared core's gate sends what it held until now if usage statistics are on, and drops it if they are off
        // (F-ONB-08.2). Nothing was sent before this line.
        analytics.setUsageConsent(shareUsageStatistics)
        permissions = await permissionsPort.current()
        go(to: .ready)
    }

    // MARK: S-ONB-15 ready

    public var isManualOnly: Bool { !preferences.preferences.autoTripsEnabled }

    public func finish() {
        preferences.update { $0.onboardingCompleted = true }
        refreshCapture()
        onFinished()
    }

    public func openSettings() {
        permissionsPort.openSystemSettings()
    }

    private func request(_ body: @MainActor () async -> PermissionSnapshot) async {
        isRequesting = true
        permissions = await body()
        isRequesting = false
    }
}
