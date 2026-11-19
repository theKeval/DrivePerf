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

    let preferences = ReplayFixture.isActive
        ? UserDefaultsPreferences(defaults: UserDefaults(suiteName: "dp.replayFixture") ?? .standard)
        : UserDefaultsPreferences()
    let displaySettings = CoreDisplaySettings()
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
    /// F-CAP-14: schedules the "may be paused" notice the core asks for.
    let watchdog = NotificationWatchdog()
    let resumeNotice = NotificationResumeNotice()
    /// Its own activity source for the in-drive content hold: the core's `activity` keeps one sink and is stopped at
    /// the end of every trip.
    let movement = ActivityMovementMonitor(source: CoreMotionActivitySource())
    let liveActivity = LiveActivityController()
    private let backgroundAssertions = UIKitBackgroundAssertions()
    let recorder: CoreTripRecorder
    let surfaceRouter = TripSurfaceRouter()
    let corrections = CoreTripCorrections()
    /// DRIVE-200, DRIVE-61: S-RPL-01.
    let replaySource = ReplaySourceSeam.make()
    let replayAnalytics = CoreReplayAnalytics()
    let connectivity: any ConnectivityMonitoring = ReplayFixture.isOffline
        ? FixedConnectivity(online: false) : NetworkConnectivity()
    private(set) lazy var corePorts = CorePorts(
        clock: clock, location: location, motion: motion, activity: activity, power: power, parkedRegion: triggers,
        tripFiles: files)
    private(set) lazy var selfTest = SelfTestRunner(environment: self)
    private let sessions = LocationServiceSessions()
    private let authorization = CLLocationManager()
    private var launched = false
    private var launchTask: Task<Void, Never>?
    private var triggersArmed = false

    /// Unit tests host the app: never touch Core Location or the trip files from a test run.
    static let isRunningUnitTests = ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
    /// Unit tests and the Replay fixture (UI tests, screenshots) keep the core and Core Location off.
    static let isCaptureOff = isRunningUnitTests || ReplayFixture.isActive

    private init() {
        recorder = CoreTripRecorder(
            liveSurface: liveActivity,
            notifier: NotificationAdapter(),
            assertions: backgroundAssertions,
            autoDetectEnabled: preferences.preferences.autoTripsEnabled,
            renewMotionHistory: { [motion] in motion.recordAccelerometerHistory() })
        // The Replay fixture opens straight on the shell, in its own preferences suite.
        if ReplayFixture.isActive { preferences.update { $0.onboardingCompleted = true } }
    }

    var dependencies: AppDependencies {
        AppDependencies(
            recorder: recorder,
            history: ReplayFixture.isActive ? FixtureTripHistory() : recorder,
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
            .withTripCorrections(corrections)
            .withStatusRequests(surfaceRouter.statusRequests)
            .withDisplaySettings(displaySettings)
            .withReplay(replaySource, analytics: replayAnalytics, connectivity: connectivity)
    }

    /// Recreate location sessions in `didFinishLaunching`, then open the core off the main actor. Location events
    /// received meanwhile wait in the recorder until recovery has been queued (ADR-002 §5, EC-RBT).
    func launch() {
        guard !launched, !Self.isCaptureOff else { return }
        recorder.beginLaunch()
        refreshCapture()
        // Recreate location sessions even before the first unlock; the database must still wait (EC-RBT).
        guard files.isUnlockedSinceBoot() else {
            backgroundAssertions.end()
            return
        }
        launched = true
        let bindings = CorePlatformBindings(
            ports: corePorts, deviceId: DeviceIdentity.current(), watchdog: watchdog, resumeNotice: resumeNotice)
        launchTask = Task {
            // Database opening and Kotlin initialization must not block CLMonitor restoration on the main actor.
            let services = await Task.detached(priority: .userInitiated) {
                UncheckedSendable(DrivePerfCore.shared.start(platform: bindings))
            }.value.value
            finishLaunch(services)
        }
    }

    func waitForLaunch() async { await launchTask?.value }

    private func finishLaunch(_ services: CoreServices) {
        displaySettings.attach(services)
        recorder.attach(KotlinCore(services))
        recorder.surfaceData = TripSurfaceData(trips: services.trips)
        displaySettings.onChange = { [weak self] in self?.refreshDisplaySurfaces() }
        refreshDisplaySurfaces()
        recorder.surfaceData?.updateLocation(canRecord: canRecordFromSystemSurface)
        corrections.attach(services.corrections)
        replayAnalytics.attach(services.analytics)
        // The first launch after a restart re-arms the triggers and says why (F-CAP-14.3).
        recorder.onLaunch(reason: BootDetector().isFirstLaunchSinceBoot() ? .boot : .launch)
        updateTriggerAvailability()
        crashReporter.apply(consent: preferences.preferences.sendCrashReports)
        // Usage statistics: events wait in memory until the user has chosen (onboarding finished means S-ONB-13 was
        // answered), then are sent or dropped; a later change in Profile takes effect at once (F-ONB-08).
        usageAnalytics.attach(services.analytics)
        if preferences.preferences.onboardingCompleted {
            usageAnalytics.setUsageConsent(preferences.preferences.shareUsageStatistics)
        }
    }

    private func refreshDisplaySurfaces() {
        let system: UnitSystem = displaySettings.current.units == .imperial ? .imperial : .metric
        let unit = system.display(unit: .kilometres)
        liveActivity.displayUnit = unit
        liveActivity.refreshUnits()
        recorder.surfaceData?.distanceFormatter = { "\(unit.format(si: $0)) \(unit.symbol)" }
        recorder.surfaceData?.refreshUnits()
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
        guard !Self.isCaptureOff else { return }
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
        recorder.surfaceData?.updateLocation(canRecord: canRecord)
        recorder.publishRecording()
        updateTriggerAvailability()
    }

    /// The scene became active: re-read permissions and re-arm, then tell the core, which counts it as a check-in and
    /// moves the watchdog notice 24 h ahead (F-CAP-14.1, F-CAP-14.4).
    func foregrounded() {
        refreshCapture()
        guard launched else { return }
        recorder.onForeground()
    }

    /// Tells the core whether the OS triggers can run, from what the user can fix in Settings (Always location, Motion
    /// & Fitness, precise location, Background App Refresh). The core turns it into "Auto-detect needs attention".
    private func updateTriggerAvailability() {
        guard !Self.isCaptureOff else { return }
        Task { [weak self] in
            guard let self else { return }
            let snapshot = await permissions.current()
            recorder.setTriggersAvailable(AutoDetectReadiness.reasons(for: snapshot).isEmpty)
        }
    }
}

/// Entry point for `StopTripIntent` (Live Activity Stop), which runs in the app's process.
enum TripIntentHandler {
    @MainActor
    static func stopTrip() async {
        let environment = AppEnvironment.shared
        environment.launch()
        await environment.waitForLaunch()
        environment.recorder.stop()
    }
}

extension AppDependencies {
    /// DRIVE-200, DRIVE-61: Replay's source, its analytics and the network state for the offline canvas.
    fileprivate func withReplay(
        _ source: any ReplayLoading, analytics: any ReplayAnalytics, connectivity: any ConnectivityMonitoring
    ) -> AppDependencies {
        var copy = self
        copy.replay = source
        copy.replayAnalytics = analytics
        copy.connectivity = connectivity
        return copy
    }

    fileprivate func withDisplaySettings(_ settings: CoreDisplaySettings) -> AppDependencies {
        var copy = self
        copy.displaySettings = settings
        return copy
    }

    /// DRIVE-150: the watchdog notification's way into the capture status sheet.
    fileprivate func withStatusRequests(_ requests: CaptureStatusRequests) -> AppDependencies {
        var copy = self
        copy.statusRequests = requests
        return copy
    }

    /// DRIVE-100, DRIVE-172 (S2): trip corrections and the shared provenance catalogue for the Drives screens.
    fileprivate func withTripCorrections(_ corrections: CoreTripCorrections) -> AppDependencies {
        var copy = self
        copy.corrections = corrections
        copy.provenance = KotlinMapping.provenanceCatalog()
        return copy
    }
}

extension AppEnvironment {
    var canRecordFromSystemSurface: Bool {
        let status = authorization.authorizationStatus
        return status == .authorizedAlways || status == .authorizedWhenInUse
    }

    /// Read on demand by Health Check so a Settings change is visible on return to the app.
    var liveActivitiesEnabled: Bool { recorder.liveActivitiesEnabled }
}

extension NotificationAdapter {
    func postDriveDecoded(tripId: String, smoothness: Int?) {
        LocalNotifier().postDriveDecoded(tripId: tripId, smoothness: smoothness)
    }
}
