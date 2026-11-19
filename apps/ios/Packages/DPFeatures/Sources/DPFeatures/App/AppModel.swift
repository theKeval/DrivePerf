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
    /// Usage statistics behind the user's choice (F-ONB-08).
    public var analytics: any UsageAnalytics
    public var selfTest: any SelfTesting
    public var flags: BuildFlags
    public var coreInfo: any HelloProviding
    /// Re-reads permissions and the automatic-trips choice (sessions, triggers). Called after onboarding choices.
    public var refreshCapture: @MainActor () -> Void
    /// `-selfTest` launch argument: open and run the on-device self-test at launch.
    public var runSelfTestOnLaunch: Bool
    /// The phone's movement after a trip, for the in-drive content hold (F-CAP-07.4). Nil: no signal, the hold is 60 s.
    public var movement: (any MovementMonitoring)?
    // DRIVE-100, DRIVE-172 (S2): trip corrections and the provenance catalogue for the Drives screens.
    public var corrections: (any TripCorrecting)?
    public var provenance = ProvenanceCatalog()
    public var displaySettings: (any DisplaySettingsStoring)?
    // DRIVE-150: requests to open the capture status sheet from outside the UI (the watchdog notification).
    public var statusRequests: CaptureStatusRequests?
    // DRIVE-200, DRIVE-61: S-RPL-01 Replay over the shared presenter. Nil hides the entry.
    public var replayPresenters: (any ReplayPresenterMaking)?
    public var connectivity: (any ConnectivityMonitoring)?

    public init(
        recorder: any TripRecording, history: any TripHistory, clock: any ClockBridge,
        permissions: any PermissionRequesting, preferences: any PreferencesStoring, crashReporter: any CrashReporting,
        analytics: any UsageAnalytics = NoUsageAnalytics(), selfTest: any SelfTesting, flags: BuildFlags,
        coreInfo: any HelloProviding, refreshCapture: @escaping @MainActor () -> Void,
        runSelfTestOnLaunch: Bool = false, movement: (any MovementMonitoring)? = nil
    ) {
        self.recorder = recorder
        self.history = history
        self.clock = clock
        self.permissions = permissions
        self.preferences = preferences
        self.crashReporter = crashReporter
        self.analytics = analytics
        self.selfTest = selfTest
        self.flags = flags
        self.coreInfo = coreInfo
        self.refreshCapture = refreshCapture
        self.runSelfTestOnLaunch = runSelfTestOnLaunch
        self.movement = movement
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
    public let display: DisplaySettingsModel
    public let capture: CaptureViewModel
    public let trips: TripListViewModel
    public let profile: ProfileViewModel
    public let recordingSettings: RecordingSettingsViewModel
    /// S-HOME-03 (F-CAP-15), shared by the capture status sheet and Recording settings.
    public let pause: PauseAutoDetectViewModel
    public let statusRequests: CaptureStatusRequests
    /// S-RPL-01, when the app has a replay source.
    public let replay: ReplayServices?
    public private(set) var onboarding: OnboardingViewModel?
    public var isSelfTestPresented: Bool

    private let dependencies: AppDependencies

    public init(dependencies: AppDependencies) {
        self.dependencies = dependencies
        route = dependencies.preferences.preferences.onboardingCompleted ? .shell : .onboarding
        isSelfTestPresented = dependencies.runSelfTestOnLaunch
        display = DisplaySettingsModel(storage: dependencies.displaySettings)
        capture = CaptureViewModel(
            recorder: dependencies.recorder, history: dependencies.history, permissions: dependencies.permissions,
            clock: dependencies.clock, movement: dependencies.movement, display: display)
        statusRequests = dependencies.statusRequests ?? CaptureStatusRequests()
        trips = TripListViewModel(
            history: dependencies.history, corrections: dependencies.corrections, catalog: dependencies.provenance, display: display)
        profile = ProfileViewModel(dependencies: dependencies)
        let display = display
        replay = dependencies.replayPresenters.map {
            ReplayServices(presenters: $0, connectivity: dependencies.connectivity, display: display)
        }
        // The accessory and the status sheet read permissions on their own, so re-read them after every change here.
        let capture = capture
        // It reads the capture model's status, so the views that show the pause update with it.
        pause = PauseAutoDetectViewModel(recorder: dependencies.recorder, status: { capture.status })
        recordingSettings = RecordingSettingsViewModel(
            preferences: dependencies.preferences, permissions: dependencies.permissions,
            refreshCapture: {
                dependencies.refreshCapture()
                Task { await capture.refreshReadiness() }
            },
            pause: pause, display: display, capture: capture)
        if route == .onboarding { onboarding = makeOnboarding() }
    }

    private func makeOnboarding() -> OnboardingViewModel {
        let model = OnboardingViewModel(
            permissions: dependencies.permissions, preferences: dependencies.preferences,
            crashReporter: dependencies.crashReporter, analytics: dependencies.analytics, refreshCapture: dependencies.refreshCapture,
            onFinished: { [weak self] in self?.finishOnboarding() })
        model.display = display
        return model
    }

    private func finishOnboarding() {
        profile.refresh()
        recordingSettings.reloadChoice()
        route = .shell
        onboarding = nil
    }
}
