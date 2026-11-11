import CoreLocation
import DPFeatures
import DPPlatform
import DPSharedBridge
import Foundation

/// Composition root (ADR-002 §5). Builds the platform adapters and the recorder once per process and hands features
/// their protocols. The only place that knows concrete types. `corePorts` wraps the same adapters as the Kotlin
/// ports, ready for `DrivePerfCore.start` when the shared recorder lands.
@MainActor
final class AppEnvironment {
    static let shared = AppEnvironment()

    let preferences = UserDefaultsPreferences()
    let permissions = SystemPermissions()
    let crashReporter = ConsentGatedCrashReporter()
    let flags = BuildFlags.fromBundle()
    let clock = SystemClock()
    let files = FileTripStore()
    let triggers = WakeTriggerMonitor()
    let location = CoreLocationSource()
    let motion = CoreMotionSource()
    let activity = CoreMotionActivitySource()
    let power = SystemPowerState()
    let recorder: InterimTripRecorder
    private(set) lazy var corePorts = CorePorts(
        clock: clock, location: location, motion: motion, activity: activity, power: power, parkedRegion: triggers,
        tripFiles: files)
    private(set) lazy var selfTest = SelfTestRunner(environment: self)
    private let sessions = LocationServiceSessions()
    private let authorization = CLLocationManager()
    private var launched = false
    private var triggersArmed = false

    /// Unit tests host the app: never touch Core Location or the trip files from a test run.
    static let isRunningUnitTests = ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil

    private init() {
        recorder = InterimTripRecorder(
            location: location,
            motion: motion,
            activity: activity,
            files: files,
            parkedRegion: triggers,
            index: TripIndexStore(root: FileTripStore.defaultRoot),
            liveSurface: LiveActivityController(),
            notifier: NotificationAdapter(),
            assertions: UIKitBackgroundAssertions(),
            autoDetectEnabled: preferences.preferences.autoTripsEnabled)
    }

    var dependencies: AppDependencies {
        AppDependencies(
            recorder: recorder,
            history: recorder,
            clock: clock,
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
        guard files.isUnlockedSinceBoot() else { return }
        launched = true
        refreshCapture()
        recorder.onLaunch()
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
        if auto && always && files.isUnlockedSinceBoot() {
            if !triggersArmed {
                triggers.startTriggers { [weak self] wake in self?.recorder.onWake(wake) }
                triggersArmed = true
            }
            motion.recordAccelerometerHistory()
        } else if triggersArmed {
            triggers.stopTriggers()
            triggersArmed = false
        }
        recorder.setAutoDetect(enabled: auto)
    }
}

/// Entry point for `StopTripIntent` (Live Activity Stop), which runs in the app's process.
enum TripIntentHandler {
    @MainActor
    static func stopTrip() async {
        AppEnvironment.shared.launch()
        AppEnvironment.shared.recorder.stop()
    }
}
