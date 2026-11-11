import Foundation

// MARK: - Permissions (platform-only flow, ADR-002 §4: native view models call these directly)

public enum LocationAccess: String, Equatable, Sendable {
    case notDetermined, denied, restricted, whenInUse, always

    public var canRecord: Bool { self == .whenInUse || self == .always }
}

public enum MotionAccess: String, Equatable, Sendable {
    case notDetermined, denied, restricted, authorized, unavailable
}

public enum NotificationAccess: String, Equatable, Sendable {
    case notDetermined, denied, authorized
}

public struct PermissionSnapshot: Equatable, Sendable {
    public var location: LocationAccess
    public var preciseLocation: Bool
    public var motion: MotionAccess
    public var notifications: NotificationAccess

    public init(
        location: LocationAccess = .notDetermined, preciseLocation: Bool = false,
        motion: MotionAccess = .notDetermined, notifications: NotificationAccess = .notDetermined
    ) {
        self.location = location
        self.preciseLocation = preciseLocation
        self.motion = motion
        self.notifications = notifications
    }
}

@MainActor
public protocol PermissionRequesting: AnyObject {
    func current() async -> PermissionSnapshot
    /// S-ONB-06: the While Using prompt.
    func requestLocationWhenInUse() async -> PermissionSnapshot
    /// S-ONB-08: the one-time Always prompt. Never asked again after a decline (F-ONB-04.3).
    func requestLocationAlways() async -> PermissionSnapshot
    /// S-ONB-11: temporary full accuracy with the "PreciseDrives" purpose.
    func requestPreciseLocation() async -> PermissionSnapshot
    /// S-ONB-07: the Motion & Fitness prompt (a short activity query).
    func requestMotion() async -> PermissionSnapshot
    /// S-ONB-10: alert, sound and badge; local notifications only.
    func requestNotifications() async -> PermissionSnapshot
    func openSystemSettings()
}

// MARK: - Preferences

/// Choices stored on this phone. No account data.
public struct AppPreferences: Equatable, Sendable, Codable {
    public var onboardingCompleted = false
    public var under18Blocked = false
    public var termsAcceptedVersion: String?
    public var termsAcceptedAt: Date?
    public var autoTripsEnabled = false
    /// The Always prompt was shown once; it is never requested again in-app.
    public var alwaysPromptShown = false
    /// F-ONB-08: both off unless the user turns them on.
    public var shareUsageStatistics = false
    public var sendCrashReports = false
    public var installedAt: Date?

    public init() {}
}

@MainActor
public protocol PreferencesStoring: AnyObject {
    var preferences: AppPreferences { get }
    func update(_ change: (inout AppPreferences) -> Void)
}

// MARK: - Diagnostics

/// Crash reporting behind consent (ADR-007): started only when the user opted in, stopped when they opt out.
@MainActor
public protocol CrashReporting: AnyObject {
    /// Applies the current consent. Starting needs both consent and a configured DSN.
    func apply(consent: Bool)
    var status: CrashReportingStatus { get }
}

public enum CrashReportingStatus: String, Equatable, Sendable {
    /// No consent: nothing is initialised.
    case off
    /// Consent given, but this build has no DSN or SDK (the Sentry account is not set up yet).
    case notConfigured
    case active
}

/// Features that wait for accounts (DRIVE-42 Apple Developer Program): Sign in with Apple, push, TestFlight.
public struct BuildFlags: Equatable, Sendable {
    public var signInWithApple: Bool
    public var pushNotifications: Bool
    public var testFlight: Bool

    public init(signInWithApple: Bool = false, pushNotifications: Bool = false, testFlight: Bool = false) {
        self.signInWithApple = signInWithApple
        self.pushNotifications = pushNotifications
        self.testFlight = testFlight
    }
}

// MARK: - On-device self-test (spike change 10)

public enum SelfTestState: Equatable, Sendable {
    case pending, running, passed(String), failed(String), skipped(String)

    public var isFinished: Bool {
        switch self {
        case .pending, .running: false
        case .passed, .failed, .skipped: true
        }
    }
}

public struct SelfTestStep: Identifiable, Equatable, Sendable {
    public var id: String
    public var title: String
    public var state: SelfTestState

    public init(id: String, title: String, state: SelfTestState = .pending) {
        self.id = id
        self.title = title
        self.state = state
    }
}

@MainActor
public protocol SelfTesting: AnyObject {
    /// Runs every step on this phone and streams progress. Results hold timings and counts, never locations.
    func run() -> AsyncStream<[SelfTestStep]>
}
