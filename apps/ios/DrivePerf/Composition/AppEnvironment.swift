import CoreLocation
import DPFeatures
import DPPlatform
import DPSharedBridge
import DrivePerfShared
import Foundation
import Sentry

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
    let crashReporter = ConsentGatedCrashReporter(engine: SentryCrashEngine())
    let usageAnalytics = CoreUsageAnalytics()
    let flags = BuildFlags.fromBundle()
    let clock = SystemClock()
    let files = FileTripStore()
    /// DRIVE-222: what Core Location said about the location sessions, for field runs (`Capture/location-diagnostics.jsonl`).
    static let locationLog = LocationDiagnosticsLog()
    let triggers = WakeTriggerMonitor(log: AppEnvironment.locationLog)
    let location = CoreLocationSource(log: AppEnvironment.locationLog)
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
    /// DRIVE-217: the Drives screens run the shared `DrivesPresenter`; the Replay fixture runs it over the synthetic
    /// trip.
    let drives = CoreDrivesPresenter()
    /// DRIVE-200, DRIVE-61: S-RPL-01 runs the shared `ReplayPresenter`; the Replay fixture runs it over the synthetic
    /// trip.
    let coreReplay = CoreReplayPresenters()
    var replayPresenters: any ReplayPresenterMaking {
        ReplayFixture.isActive ? SyntheticReplayPresenters() : coreReplay
    }
    /// S-ANL-01 runs the shared `AnalysisPresenter` (a stub until DRIVE-45).
    let coreAnalysis = CoreAnalysisPresenters()
    let connectivity: any ConnectivityMonitoring = ReplayFixture.isOffline
        ? FixedConnectivity(online: false) : NetworkConnectivity()
    private(set) lazy var corePorts = CorePorts(
        clock: clock, location: location, motion: motion, activity: activity, power: power, parkedRegion: triggers,
        tripFiles: files)
    private(set) lazy var selfTest = SelfTestRunner(environment: self)
    private let sessions = LocationServiceSessions(log: AppEnvironment.locationLog)
    /// DRIVE-222: after a restart iOS starts background location only once the app has been opened.
    private let openedSinceBoot = OpenedSinceBoot()
    private let rearmNotice = NotificationRearmNotice()
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
        if ReplayFixture.isActive {
            preferences.update { $0.onboardingCompleted = true }
            drives.attach(ReplayFixture.drivesPresenter(clock: clock))
        }
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
            .withDrives(drives)
            .withStatusRequests(surfaceRouter.statusRequests)
            .withDisplaySettings(displaySettings)
            .withReplay(replayPresenters, connectivity: connectivity)
            .withAnalysis(coreAnalysis)
    }

    /// Recreate location sessions in `didFinishLaunching`, then open the core off the main actor. Location events
    /// received meanwhile wait in the recorder until recovery has been queued (ADR-002 §5, EC-RBT).
    func launch() {
        guard !launched, !Self.isCaptureOff else { return }
        Self.locationLog.record(
            "launch", ["app": AppRunState.current.rawValue, "openedSinceBoot": openedSinceBoot.state().rawValue])
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
        let analytics = services.analytics
        recorder.trackNotice = { analytics.track(event: NoticeAnalyticsEvent($0)) }
        displaySettings.onChange = { [weak self] in self?.refreshDisplaySurfaces() }
        refreshDisplaySurfaces()
        recorder.surfaceData?.updateLocation(canRecord: canRecordFromSystemSurface)
        drives.attach(services)
        coreReplay.attach(services)
        coreAnalysis.attach(services)
        // The first launch after a restart re-arms the triggers and says why (F-CAP-14.3).
        recorder.onLaunch(reason: BootDetector().isFirstLaunchSinceBoot() ? .boot : .launch)
        updateTriggerAvailability()
        crashReporter.apply(consent: preferences.preferences.sendCrashReports)
        // DRIVE-196: crash reports say whether a trip was being captured (in_trip, capture_mode, trip_state).
        Task { [crashReporter, recorder] in
            for await status in recorder.statuses() {
                crashReporter.updateTrip(state: status.state, isManual: status.isManual)
            }
        }
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
            sessions.hold(always: auto && always, runState: AppRunState.current)
        } else {
            sessions.release()
        }
        if auto && always && files.isUnlockedSinceBoot() {
            if !triggersArmed {
                triggers.startTriggers { [weak self] wake in self?.handleWake(wake) }
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
        // DRIVE-222: first, so `refreshCapture` sees the app in use and replaces a session made in the background.
        if openedSinceBoot.state() != .opened {
            Self.locationLog.record("first_foreground_since_boot")
        }
        openedSinceBoot.recordForeground()
        rearmNotice.clear()
        refreshCapture()
        location.renewInForeground()
        guard launched else { return }
        recorder.onForeground()
    }

    /// DRIVE-222: a wake in the background before the app was opened since the phone restarted cannot record (iOS
    /// suspends the app ~10 s into the candidate), so the first one per boot posts "Auto-detect may be paused". The
    /// wake still goes to the core: if iOS lets the location through after all, the trip records.
    private func handleWake(_ wake: DPSharedBridge.WakeEvent) {
        let runState = AppRunState.current
        let opened = openedSinceBoot.state()
        Self.locationLog.record(
            "wake", ["kind": wake.kind.rawValue, "app": runState.rawValue, "openedSinceBoot": opened.rawValue])
        if OpenedSinceBoot.shouldAsk(runState: runState, health: recorder.status.autoDetectHealth),
            openedSinceBoot.claimNotice()
        {
            rearmNotice.post()
            Self.locationLog.record("rearm_notice_posted")
        }
        recorder.onWake(wake)
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
    /// DRIVE-200, DRIVE-61: Replay's presenters and the network state for the offline canvas.
    fileprivate func withReplay(
        _ presenters: any ReplayPresenterMaking, connectivity: any ConnectivityMonitoring
    ) -> AppDependencies {
        var copy = self
        copy.replayPresenters = presenters
        copy.connectivity = connectivity
        return copy
    }

    /// S-ANL-01: Analysis's presenters.
    fileprivate func withAnalysis(_ presenters: any AnalysisPresenterMaking) -> AppDependencies {
        var copy = self
        copy.analysisPresenters = presenters
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

    /// DRIVE-217: the shared Drives presenter (lists, trip detail, data quality, answers and corrections).
    fileprivate func withDrives(_ drives: CoreDrivesPresenter) -> AppDependencies {
        var copy = self
        copy.drives = drives
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

    /// DRIVE-137: the recording notice with its own category for automatic trips, reporting when it was shown.
    func postRecordingNotice(tripId: String, automatic: Bool, posted: @escaping @MainActor () -> Void) {
        LocalNotifier().postRecordingNotice(tripId: tripId, automatic: automatic, onPosted: posted)
    }
}

/// An F-CAP-11 notice event as the Kotlin `AnalyticsEvent` type the consent-gated `Analytics` port accepts.
final class NoticeAnalyticsEvent: NSObject, DrivePerfShared.AnalyticsEvent {
    let name: String
    let properties: [String: String] = [:]

    init(_ event: AutoTripNoticeEvent) {
        name = event.rawValue
    }
}

// MARK: - Crash reporting (DRIVE-196, ADR-007)

/// Sentry Cocoa behind `ConsentGatedCrashReporter`: started only after crash consent, EU DSN from the build.
///
/// - `sendDefaultPii` off and no user ever set; no screenshots, view hierarchy, Session Replay, tracing, network
///   breadcrumbs or failed-request capture. Crashes, app hangs, watchdog terminations and release health (crash-free
///   sessions, F-ANA-03) are on.
/// - Kotlin/Native crashes: `KotlinCrashHook` (Sentry Kotlin Multiplatform) reports the Kotlin exception with its
///   Kotlin stack trace to this SDK. Sentry Cocoa is pinned to the version that SDK was built against.
/// - `beforeSend` keeps only `CrashTags.allowed`, drops extras and removes coordinate-like text from the message and
///   exception values; a breadcrumb that looks like a location is dropped (`CoordinateScrubber`).
@MainActor
final class SentryCrashEngine: CrashReportingEngine {
    private let appBuild: String
    private var cacheDirectory: String?

    init(bundle: Bundle = .main) {
        let version = bundle.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
        let build = bundle.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "0"
        appBuild = "\(version) (\(build))"
    }

    func start(dsn: String) {
        var cache: String?
        SentrySDK.start { options in
            options.dsn = dsn
            options.sendDefaultPii = false
            options.tracesSampleRate = 0
            options.enableAutoPerformanceTracing = false
            options.enableNetworkTracking = false
            options.enableNetworkBreadcrumbs = false
            options.enableCaptureFailedRequests = false
            options.attachScreenshot = false
            options.attachViewHierarchy = false
            options.enableAutoSessionTracking = true
            options.enableAppHangTracking = true
            options.enableWatchdogTerminationTracking = true
            options.beforeSend = { SentryCrashEngine.scrub($0) }
            options.beforeBreadcrumb = { SentryCrashEngine.keep($0) }
            cache = options.cacheDirectoryPath
        }
        cacheDirectory = cache
        let appBuild = appBuild
        SentrySDK.configureScope { $0.setTag(value: appBuild, key: CrashTags.appBuild) }
        KotlinCrashHook.shared.install()
    }

    func stop() {
        SentrySDK.close()
        // Sentry keeps queued envelopes under <cache>/io.sentry; an opt-out deletes them (ADR-007).
        if let cacheDirectory {
            try? FileManager.default.removeItem(atPath: (cacheDirectory as NSString).appendingPathComponent("io.sentry"))
        }
    }

    func setTags(_ tags: [String: String]) {
        let allowed = tags.filter { CrashTags.allowed.contains($0.key) }
        SentrySDK.configureScope { scope in
            for (key, value) in allowed { scope.setTag(value: value, key: key) }
        }
    }

    nonisolated static func scrub(_ event: Sentry.Event) -> Sentry.Event? {
        if let message = event.message {
            let scrubbed = SentryMessage(formatted: CoordinateScrubber.scrub(message.formatted))
            scrubbed.message = message.message.map(CoordinateScrubber.scrub)
            scrubbed.params = message.params?.map(CoordinateScrubber.scrub)
            event.message = scrubbed
        }
        event.exceptions?.forEach { exception in exception.value = exception.value.map(CoordinateScrubber.scrub) }
        event.tags = event.tags?.filter { CrashTags.allowed.contains($0.key) }
        event.extra = nil
        event.breadcrumbs = event.breadcrumbs?.compactMap(keep)
        return event
    }

    nonisolated static func keep(_ breadcrumb: Breadcrumb) -> Breadcrumb? {
        let texts = [breadcrumb.message].compactMap { $0 } + (breadcrumb.data ?? [:]).values.map { "\($0)" }
        return texts.contains(where: CoordinateScrubber.containsCoordinates) ? nil : breadcrumb
    }
}
