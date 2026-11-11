import DPSharedBridge
import Observation

/// Everything features need, as protocols (ADR-002 §2: features see DPSharedBridge, never DPPlatform). The app target
/// builds it in its composition root.
@MainActor
public struct AppDependencies {
    public var recorder: any TripRecording
    public var history: any TripHistory
    /// The monotonic and wall clock wakes are stamped with.
    public var clock: any ClockBridge
    public var permissions: any PermissionRequesting
    public var preferences: any PreferencesStoring
    public var crashReporter: any CrashReporting
    public var selfTest: any SelfTesting
    public var flags: BuildFlags
    public var coreInfo: any HelloProviding
    /// Re-reads permissions and the automatic-trips choice (sessions, triggers). Called after onboarding choices.
    public var refreshCapture: @MainActor () -> Void
    /// `-selfTest` launch argument: open and run the on-device self-test at launch.
    public var runSelfTestOnLaunch: Bool

    public init(
        recorder: any TripRecording, history: any TripHistory, clock: any ClockBridge,
        permissions: any PermissionRequesting, preferences: any PreferencesStoring, crashReporter: any CrashReporting, selfTest: any SelfTesting,
        flags: BuildFlags, coreInfo: any HelloProviding, refreshCapture: @escaping @MainActor () -> Void,
        runSelfTestOnLaunch: Bool = false
    ) {
        self.recorder = recorder
        self.history = history
        self.clock = clock
        self.permissions = permissions
        self.preferences = preferences
        self.crashReporter = crashReporter
        self.selfTest = selfTest
        self.flags = flags
        self.coreInfo = coreInfo
        self.refreshCapture = refreshCapture
        self.runSelfTestOnLaunch = runSelfTestOnLaunch
    }
}

/// Top-level routing: onboarding until it is finished, then the four-tab shell (S-NAV-01).
@MainActor
@Observable
public final class AppModel {
    public enum Route: Equatable {
        case onboarding, shell
    }

    public private(set) var route: Route
    public let capture: CaptureViewModel
    public let trips: TripListViewModel
    public let profile: ProfileViewModel
    public private(set) var onboarding: OnboardingViewModel?
    public var isSelfTestPresented: Bool

    private let dependencies: AppDependencies

    public init(dependencies: AppDependencies) {
        self.dependencies = dependencies
        route = dependencies.preferences.preferences.onboardingCompleted ? .shell : .onboarding
        isSelfTestPresented = dependencies.runSelfTestOnLaunch
        capture = CaptureViewModel(
            recorder: dependencies.recorder, history: dependencies.history, permissions: dependencies.permissions,
            clock: dependencies.clock)
        trips = TripListViewModel(history: dependencies.history)
        profile = ProfileViewModel(dependencies: dependencies)
        if route == .onboarding { onboarding = makeOnboarding() }
    }

    private func makeOnboarding() -> OnboardingViewModel {
        OnboardingViewModel(
            permissions: dependencies.permissions, preferences: dependencies.preferences,
            crashReporter: dependencies.crashReporter, refreshCapture: dependencies.refreshCapture,
            onFinished: { [weak self] in self?.finishOnboarding() })
    }

    private func finishOnboarding() {
        route = .shell
        onboarding = nil
    }
}
