import CoreLocation
import DPFeatures
import DPPlatform
import DPSharedBridge
import DrivePerfShared
import Foundation

/// Composition root (ADR-002 §5). Builds the platform adapters once per process, starts the shared core on them
/// (`DrivePerfCore.start`, through `corePorts`) and hands features their protocols. The only place that knows
/// concrete types.
@MainActor
final class AppEnvironment {
    static let shared = AppEnvironment()

    let preferences = UserDefaultsPreferences()
    let permissions = SystemPermissions()
    let crashReporter = ConsentGatedCrashReporter()
    let usageAnalytics = CoreUsageAnalytics()
    let flags = BuildFlags.fromBundle()
    let clock = SystemClock()
    let files = FileTripStore()
    let triggers = WakeTriggerMonitor()
    let location = CoreLocationSource()
    let motion = CoreMotionSource()
    let activity = CoreMotionActivitySource()
    let power = SystemPowerState()
    /// Its own activity source for the in-drive content hold: the core's `activity` keeps one sink and is stopped at
    /// the end of every trip.
    let movement = ActivityMovementMonitor(source: CoreMotionActivitySource())
    let recorder: CoreTripRecorder
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
        recorder = CoreTripRecorder(
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
            analytics: usageAnalytics,
            selfTest: selfTest,
            flags: flags,
            coreInfo: SharedCoreHelloProvider(),
            refreshCapture: { [weak self] in self?.refreshCapture() },
            runSelfTestOnLaunch: ProcessInfo.processInfo.arguments.contains("-selfTest"),
            movement: movement)
    }

    /// Runs inside `didFinishLaunching`, synchronously: the Core Location sessions first, because a background
    /// relaunch only keeps its events for a few seconds (ADR-002 §5, A3 §7.3 #4), then the shared core on the same
    /// adapters, then `onLaunch`, which re-arms the parked region and recovers a killed trip (§1.7).
    func launch() {
        guard !launched, !Self.isRunningUnitTests else { return }
        // Before the first unlock after a reboot the trip files and the database are unreadable; wait (EC-RBT).
        guard files.isUnlockedSinceBoot() else { return }
        launched = true
        refreshCapture()
        let services = DrivePerfCore.shared.start(
            platform: CorePlatformBindings(ports: corePorts, deviceId: DeviceIdentity.current()))
        recorder.attach(KotlinCore(services))
        recorder.onLaunch()
        crashReporter.apply(consent: preferences.preferences.sendCrashReports)
        // Usage statistics: events wait in memory until the user has chosen (onboarding finished means S-ONB-13 was
        // answered), then are sent or dropped; a later change in Profile takes effect at once (F-ONB-08).
        usageAnalytics.attach(services.analytics)
        if preferences.preferences.onboardingCompleted {
            usageAnalytics.setUsageConsent(preferences.preferences.shareUsageStatistics)
        }
    }

    /// Every unlock: the first one after a reboot launches; later ones compress R0 files that a finalisation while
    /// locked could not (the core keeps the trip and its plain R0 then).
    func protectedDataBecameAvailable() {
        if launched {
            for trip in recorder.history where files.describe(tripId: trip.id, kind: .r0, withSha256: false)?.compressed == false {
                _ = files.compress(tripId: trip.id, kind: .r0)
            }
        } else {
            launch()
        }
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
