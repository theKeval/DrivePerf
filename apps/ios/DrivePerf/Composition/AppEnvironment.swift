import CoreLocation
import DPFeatures
import DPPlatform
import DPSharedBridge
import Foundation

/// Composition root (ADR-002 §5). Builds the platform adapters and the recorder once per process and hands features
/// their protocols. The only place that knows concrete types.
@MainActor
final class AppEnvironment {
    static let shared = AppEnvironment()

    let preferences = UserDefaultsPreferences()
    let permissions = SystemPermissions()
    let crashReporter = ConsentGatedCrashReporter()
    let flags = BuildFlags.fromBundle()
    let files = FileTripStore()
    let triggers = WakeTriggerMonitor()
    let recorder: InterimTripRecorder
    private(set) lazy var selfTest = SelfTestRunner(environment: self)
    private let sessions = LocationServiceSessions()
    private let authorization = CLLocationManager()
    private var launched = false
    private var triggersArmed = false

    /// Unit tests host the app: never touch Core Location or the trip files from a test run.
    static let isRunningUnitTests = ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil

    private init() {
        recorder = InterimTripRecorder(
            location: CoreLocationSource(),
            motion: CoreMotionSource(),
            activity: CoreMotionActivitySource(),
            files: files,
            index: TripIndexStore(root: FileTripStore.defaultRoot),
            triggers: triggers,
            liveSurface: LiveActivityController(),
            notifier: NotificationAdapter(),
            assertions: UIKitBackgroundAssertions())
    }

    var dependencies: AppDependencies {
        AppDependencies(
            recorder: recorder,
            history: recorder,
            permissions: permissions,
            preferences: preferences,
            crashReporter: crashReporter,
            selfTest: selfTest,
            flags: flags,
            coreInfo: SharedCoreHelloProvider(),
            refreshCapture: { [weak self] in self?.refreshCapture() },
            runSelfTestOnLaunch: ProcessInfo.processInfo.arguments.contains("-selfTest"))
    }

    /// Runs inside `didFinishLaunching`, synchronously up to the Core Location sessions: a background relaunch only
    /// keeps its events for a few seconds (ADR-002 §5, A3 §7.3 #4). The Kotlin core is not started on this path.
    func launch() {
        guard !launched, !Self.isRunningUnitTests else { return }
        // Before the first unlock after a reboot the trip files are unreadable; wait for protected data (EC-RBT).
        guard files.isWritable() else { return }
        launched = true
        refreshCapture()
        recorder.launch()
        crashReporter.apply(consent: preferences.preferences.sendCrashReports)
    }

    func protectedDataBecameAvailable() {
        launch()
    }

    /// Re-reads the permission and the automatic-trips choice, then holds the right `CLServiceSession` and arms or
    /// disarms the wake triggers. Called at launch, on every foreground and after onboarding choices.
    func refreshCapture() {
        guard !Self.isRunningUnitTests else { return }
        let status = authorization.authorizationStatus
        let canRecord = status == .authorizedAlways || status == .authorizedWhenInUse
        let always = status == .authorizedAlways
        let auto = preferences.preferences.autoTripsEnabled
        if canRecord {
            sessions.hold(always: auto && always)
        } else {
            sessions.release()
        }
        if auto && always && files.isWritable() {
            if !triggersArmed {
                triggers.arm { [weak self] wake in self?.recorder.handle(wake) }
                triggersArmed = true
            }
        } else if triggersArmed {
            triggers.disarm()
            triggersArmed = false
        }
        recorder.updateAuthorization(always: always, autoDetectEnabled: auto)
    }
}

/// Entry point for `StopTripIntent` (Live Activity Stop), which runs in the app's process.
enum TripIntentHandler {
    @MainActor
    static func stopTrip() async {
        AppEnvironment.shared.launch()
        _ = await AppEnvironment.shared.recorder.stopTrip()
    }
}
