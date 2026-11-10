import ActivityKit
import CaptureCore
import CoreLocation
import CoreMotion
import Observation
import UIKit
import UserNotifications

extension CMSensorDataList: @retroactive Sequence {
    public func makeIterator() -> NSFastEnumerationIterator { NSFastEnumerationIterator(self) }
}

/// Owns the whole A3 §1.3 capture loop: armed triggers, candidate confirmation, recording,
/// trip end, backfill and re-arming.
@MainActor
@Observable
final class CaptureEngine: NSObject {
    static let shared = CaptureEngine()

    enum Phase: String {
        case armed = "Armed"
        case candidate = "Candidate (confirming)"
        case recording = "Recording"
    }

    private enum Key {
        static let legacyKeeper = "legacyKeeperEnabled"
        static let profileExpiry = "lastProfileExpiry"
    }

    private enum Tuning {
        static let parkedRegionRadius: CLLocationDistance = 120
        /// Alphanumeric only: any other character makes CLMonitor.init raise an assertion.
        static let monitorName = "parkedSpot"
        static let parkedIdentifier = "parked"
        static let watchdogIdentifier = "watchdog"
        static let watchdogInterval: TimeInterval = 18 * 3600
        static let recorderDuration: TimeInterval = 43_200
        static let staleLocationAge: TimeInterval = 300
        static let staleRegionOrVisitAge: TimeInterval = 900
        static let initialSLCWindow: TimeInterval = 10
        static let resumeWindow: TimeInterval = 600
        static let afterTripQuiet: TimeInterval = 120
        static let historyWindow: TimeInterval = 900
        static let imuHz: Double = 50
        static let checkpointInterval: TimeInterval = 10
        static let cachedFixTolerance: TimeInterval = 5
    }

    // MARK: Observable state for the debug UI

    private(set) var phase: Phase = .armed
    private(set) var active: TripRecord?
    private(set) var trips: [TripRecord] = []
    private(set) var installs: [InstallEntry] = []
    private(set) var authorization: CLAuthorizationStatus = .notDetermined
    private(set) var fullAccuracy = false
    private(set) var motionAuthorized = CMMotionActivityManager.authorizationStatus()
    private(set) var notificationsAuthorized = false
    private(set) var lowPowerMode = ProcessInfo.processInfo.isLowPowerModeEnabled
    private(set) var parkedRegionArmed = false
    private(set) var sessionDiagnostic = "none"
    private(set) var lastEvent = "none"
    var legacyKeeperEnabled: Bool {
        didSet { UserDefaults.standard.set(legacyKeeperEnabled, forKey: Key.legacyKeeper) }
    }

    // MARK: System objects

    @ObservationIgnored private let slcManager = CLLocationManager()
    @ObservationIgnored private let visitManager = CLLocationManager()
    @ObservationIgnored private let streamManager = CLLocationManager()
    @ObservationIgnored private let motionManager = CMMotionManager()
    @ObservationIgnored private let activityManager = CMMotionActivityManager()
    @ObservationIgnored private let processStart = Date()
    @ObservationIgnored private var bootstrapped = false
    @ObservationIgnored private var firstWakeHandled = false
    @ObservationIgnored private var serviceSession: CLServiceSession?
    @ObservationIgnored private var serviceDiagnosticsTask: Task<Void, Never>?
    @ObservationIgnored private var backgroundSession: CLBackgroundActivitySession?
    @ObservationIgnored private var backgroundDiagnosticsTask: Task<Void, Never>?
    @ObservationIgnored private var monitor: CLMonitor?
    @ObservationIgnored private var monitorTask: Task<Void, Never>?
    @ObservationIgnored private var liveTask: Task<Void, Never>?
    @ObservationIgnored private var tickTask: Task<Void, Never>?
    @ObservationIgnored private var wakeAssertion: UIBackgroundTaskIdentifier = .invalid
    @ObservationIgnored private var writer: TripWriter?
    @ObservationIgnored private var confirmation: DriveConfirmation?
    @ObservationIgnored private var endDetector: TripEndDetector?
    @ObservationIgnored private var latestActivity: ActivityObservation?
    @ObservationIgnored private var lastAcceptedFix: Date?
    @ObservationIgnored private var liveStationary = false
    @ObservationIgnored private var lastCheckpoint = Date()
    @ObservationIgnored private var lastTripEnd: Date?
    @ObservationIgnored private var liveActivity: Activity<CaptureActivityAttributes>?
    @ObservationIgnored private var finishing = false
    @ObservationIgnored private var wantsAlwaysUpgrade = false

    override private init() {
        legacyKeeperEnabled = UserDefaults.standard.object(forKey: Key.legacyKeeper) as? Bool ?? true
        super.init()
    }

    // MARK: Launch and arming

    /// Runs inside didFinishLaunching. Core Location only keeps sessions across a relaunch for a
    /// few seconds, so everything here is synchronous up to the Task that restores the monitor.
    func launch(launchedForLocation: Bool) {
        guard !bootstrapped else { return }
        let application = UIApplication.shared
        let unlockedSinceBoot = TripStore.hasBeenUnlockedSinceBoot()
        WakeLog.append(
            kind: "launch",
            detail: "location=\(launchedForLocation) state=\(Self.name(application.applicationState)) "
                + "protectedData=\(application.isProtectedDataAvailable) unlockedSinceBoot=\(unlockedSinceBoot) "
                + "lowPower=\(lowPowerMode) bgRefresh=\(application.backgroundRefreshStatus.rawValue)")
        // Not isProtectedDataAvailable: that is false on every lock, and a background wake on a
        // locked phone is the normal case for a drive. Only a pre-first-unlock launch must wait.
        guard unlockedSinceBoot else { return }
        bootstrap()
    }

    func protectedDataBecameAvailable() {
        WakeLog.append(kind: "protectedDataAvailable", detail: "")
        bootstrap()
    }

    private func bootstrap() {
        guard !bootstrapped else { return }
        bootstrapped = true
        UIDevice.current.isBatteryMonitoringEnabled = true

        slcManager.delegate = self
        visitManager.delegate = self
        streamManager.delegate = self
        streamManager.activityType = .automotiveNavigation
        streamManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        streamManager.distanceFilter = kCLDistanceFilterNone
        streamManager.pausesLocationUpdatesAutomatically = false
        streamManager.showsBackgroundLocationIndicator = true

        NotificationCenter.default.addObserver(
            self, selector: #selector(powerStateChanged), name: .NSProcessInfoPowerStateDidChange, object: nil)

        refreshPermissions()
        trips = TripStore.loadAll()
        recordInstallIfNew()
        armTriggers()
        recoverInterruptedTrips()
        TripStore.writeSummary(trips: trips, installs: installs)
        runSelfTestIfRequested()
    }

    /// Runs a real automatic trip from a Mac launch (`-selfTest`), with confirmation and end forced,
    /// so the sensor, backfill and trip-end paths are exercised before a drive depends on them.
    private func runSelfTestIfRequested() {
        guard ProcessInfo.processInfo.arguments.contains("-selfTest") else { return }
        WakeLog.append(kind: "selfTest", detail: "start")
        Task {
            try? await Task.sleep(for: .seconds(3))
            let start = Date()
            if active == nil { beginCandidate(trigger: .significantChange, at: start) }
            active?.addNote("self-test")
            try? await Task.sleep(for: .seconds(25))
            confirmCandidate(reason: "self-test", now: Date())
            try? await Task.sleep(for: .seconds(35))
            // Ten minutes back, because CMSensorRecorder data can lag by a few minutes.
            active?.motionStartAt = start.addingTimeInterval(-600)
            await finishTrip(reason: "self-test")
            WakeLog.append(kind: "selfTest", detail: "done")
            TripStore.writeSummary(trips: trips, installs: installs)
        }
    }

    /// Called when the UI appears or returns to the foreground.
    func foregrounded() {
        guard bootstrapped else { return }
        refreshPermissions()
        if serviceSession == nil || sessionDiagnostic != "none" {
            startServiceSession()
        }
        rearmRecorder()
        pushWatchdog()
    }

    private func armTriggers() {
        guard authorization == .authorizedAlways || authorization == .authorizedWhenInUse else { return }
        startServiceSession()
        slcManager.startMonitoringSignificantLocationChanges()
        visitManager.startMonitoringVisits()
        rearmRecorder()
        pushWatchdog()
        restoreMonitor()
    }

    private func startServiceSession() {
        let session = CLServiceSession(authorization: .always)
        serviceSession?.invalidate()
        serviceDiagnosticsTask?.cancel()
        serviceSession = session
        sessionDiagnostic = "none"
        serviceDiagnosticsTask = Task { [weak self] in
            do {
                for try await diagnostic in session.diagnostics {
                    self?.handle(serviceDiagnostic: diagnostic)
                }
            } catch {
                WakeLog.append(kind: "serviceSessionError", detail: error.localizedDescription)
            }
        }
    }

    private func handle(serviceDiagnostic diagnostic: CLServiceSession.Diagnostic) {
        var flags: [String] = []
        if diagnostic.authorizationDenied { flags.append("authorizationDenied") }
        if diagnostic.alwaysAuthorizationDenied { flags.append("alwaysDenied") }
        if diagnostic.insufficientlyInUse { flags.append("insufficientlyInUse") }
        if diagnostic.serviceSessionRequired { flags.append("serviceSessionRequired") }
        if diagnostic.fullAccuracyDenied { flags.append("fullAccuracyDenied") }
        if diagnostic.authorizationRequestInProgress { flags.append("requestInProgress") }
        let summary = flags.isEmpty ? "none" : flags.joined(separator: ",")
        guard summary != sessionDiagnostic else { return }
        sessionDiagnostic = summary
        WakeLog.append(kind: "serviceSessionDiagnostic", detail: summary)
        active?.addNote("CLServiceSession: \(summary)")
    }

    private func rearmRecorder() {
        guard CMSensorRecorder.isAccelerometerRecordingAvailable() else {
            WakeLog.append(kind: "sensorRecorderUnavailable", detail: "")
            return
        }
        let duration = Tuning.recorderDuration
        // Recording continues after the recorder object is released, and even if the app dies.
        DispatchQueue.global(qos: .utility).async { CMSensorRecorder().recordAccelerometer(forDuration: duration) }
    }

    private func pushWatchdog() {
        let content = UNMutableNotificationContent()
        content.title = "Auto-detect may be paused"
        content.body = "The capture spike has not woken for 18 hours. Open it to resume."
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: Tuning.watchdogInterval, repeats: false)
        UNUserNotificationCenter.current().add(
            UNNotificationRequest(identifier: Tuning.watchdogIdentifier, content: content, trigger: trigger))
    }

    func scheduleWatchdogTest() {
        let content = UNMutableNotificationContent()
        content.title = "Watchdog test"
        content.body = "Local notification delivery works."
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 60, repeats: false)
        UNUserNotificationCenter.current().add(
            UNNotificationRequest(identifier: "watchdogTest", content: content, trigger: trigger))
    }

    private func recordInstallIfNew() {
        installs = TripStore.loadInstalls()
        let info = ProvisioningInfo.read()
        let expiry = info?.expiresAt?.timeIntervalSince1970 ?? 0
        guard UserDefaults.standard.double(forKey: Key.profileExpiry) != expiry || installs.isEmpty else { return }
        UserDefaults.standard.set(expiry, forKey: Key.profileExpiry)
        installs.append(
            InstallEntry(firstLaunchAt: Date(), profileCreatedAt: info?.createdAt, profileExpiresAt: info?.expiresAt))
        TripStore.saveInstalls(installs)
        WakeLog.append(kind: "install", detail: "expires=\(info?.expiresAt?.description ?? "unknown")")
        scheduleExpiryReminder(info?.expiresAt)
    }

    private func scheduleExpiryReminder(_ expiry: Date?) {
        guard let expiry else { return }
        let fireAt = expiry.addingTimeInterval(-24 * 3600)
        let interval = fireAt.timeIntervalSinceNow
        guard interval > 60 else { return }
        let content = UNMutableNotificationContent()
        content.title = "Reinstall the capture spike"
        content.body = "The Personal Team profile expires in about 24 hours. After that the app will not launch."
        UNUserNotificationCenter.current().add(
            UNNotificationRequest(
                identifier: "profileExpiry", content: content,
                trigger: UNTimeIntervalNotificationTrigger(timeInterval: interval, repeats: false)))
    }

    // MARK: Permissions

    func requestPermissions() {
        switch slcManager.authorizationStatus {
        case .notDetermined:
            wantsAlwaysUpgrade = true
            slcManager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse: slcManager.requestAlwaysAuthorization()
        default: break
        }
        Task {
            let granted = (try? await UNUserNotificationCenter.current().requestAuthorization(
                options: [.alert, .sound])) ?? false
            notificationsAuthorized = granted
        }
        // A short query triggers the Motion & Fitness prompt.
        let now = Date()
        activityManager.queryActivityStarting(from: now.addingTimeInterval(-60), to: now, to: .main) { _, _ in
            Task { @MainActor in CaptureEngine.shared.refreshPermissions() }
        }
    }

    func refreshPermissions() {
        authorization = slcManager.authorizationStatus
        fullAccuracy = slcManager.accuracyAuthorization == .fullAccuracy
        motionAuthorized = CMMotionActivityManager.authorizationStatus()
        lowPowerMode = ProcessInfo.processInfo.isLowPowerModeEnabled
        Task {
            let settings = await UNUserNotificationCenter.current().notificationSettings()
            notificationsAuthorized = settings.authorizationStatus == .authorized
        }
    }

    @objc nonisolated private func powerStateChanged() {
        Task { @MainActor in
            let enabled = ProcessInfo.processInfo.isLowPowerModeEnabled
            CaptureEngine.shared.lowPowerMode = enabled
            WakeLog.append(kind: "lowPowerMode", detail: "\(enabled)")
        }
    }

    // MARK: Parked region

    private func restoreMonitor() {
        guard monitorTask == nil else { return }
        monitorTask = Task { [weak self] in
            let monitor = await CLMonitor(Tuning.monitorName)
            self?.monitor = monitor
            let identifiers = await monitor.identifiers
            self?.parkedRegionArmed = identifiers.contains(Tuning.parkedIdentifier)
            // A Visit can deliver the spot before this monitor exists, so arm it here too.
            if !identifiers.contains(Tuning.parkedIdentifier), let spot = TripStore.loadParkedSpot() {
                self?.armParkedRegion(at: spot)
            }
            do {
                for try await event in await monitor.events {
                    self?.handle(monitorEvent: event)
                }
            } catch {
                WakeLog.append(kind: "monitorError", detail: error.localizedDescription)
            }
        }
    }

    private func handle(monitorEvent event: CLMonitor.Event) {
        var flags: [String] = []
        if event.authorizationDenied { flags.append("authorizationDenied") }
        if event.insufficientlyInUse { flags.append("insufficientlyInUse") }
        if event.serviceSessionRequired { flags.append("serviceSessionRequired") }
        if event.accuracyLimited { flags.append("accuracyLimited") }
        if event.conditionLimitExceeded { flags.append("conditionLimitExceeded") }
        if event.persistenceUnavailable { flags.append("persistenceUnavailable") }
        let age = Int(Date().timeIntervalSince(event.date))
        WakeLog.append(
            kind: "parkedRegion",
            detail: "state=\(event.state.rawValue) ageSeconds=\(age) flags=\(flags.joined(separator: ","))")
        guard event.identifier == Tuning.parkedIdentifier, event.state == .unsatisfied else { return }
        handleTrigger(.parkedRegionExit, eventTime: event.date, maxAge: Tuning.staleRegionOrVisitAge)
    }

    private func armParkedRegion(at point: GeoPoint) {
        TripStore.saveParkedSpot(point)
        guard let monitor else { return }
        let center = CLLocationCoordinate2D(latitude: point.latitude, longitude: point.longitude)
        let condition = CLMonitor.CircularGeographicCondition(center: center, radius: Tuning.parkedRegionRadius)
        Task { [weak self] in
            await monitor.remove(Tuning.parkedIdentifier)
            await monitor.add(condition, identifier: Tuning.parkedIdentifier, assuming: .satisfied)
            self?.parkedRegionArmed = true
        }
        WakeLog.append(kind: "parkedRegionArmed", detail: "radius=\(Int(Tuning.parkedRegionRadius))")
    }

    // MARK: Triggers

    private func handleTrigger(_ trigger: WakeTrigger, eventTime: Date, maxAge: TimeInterval) {
        let now = Date()
        pushWatchdog()
        rearmRecorder()
        lastEvent = "\(trigger.rawValue) at \(now.formatted(date: .omitted, time: .standard))"
        if active != nil {
            active?.triggers.append(TriggerEvent(kind: trigger, at: now))
            return
        }
        guard now.timeIntervalSince(eventTime) <= maxAge else {
            WakeLog.append(kind: "staleTrigger", detail: "\(trigger.rawValue)")
            return
        }
        if let lastTripEnd, now.timeIntervalSince(lastTripEnd) < Tuning.afterTripQuiet {
            WakeLog.append(kind: "quietPeriodTrigger", detail: "\(trigger.rawValue)")
            return
        }
        guard authorization == .authorizedAlways else {
            WakeLog.append(kind: "triggerWithoutAlways", detail: "\(trigger.rawValue)")
            return
        }
        beginCandidate(trigger: trigger, at: now)
    }

    private func launchKind(now: Date) -> LaunchKind {
        let state = UIApplication.shared.applicationState
        defer { firstWakeHandled = true }
        if state == .active { return .foreground }
        return !firstWakeHandled && now.timeIntervalSince(processStart) < 15 ? .coldBackground : .resumedBackground
    }

    private func beginCandidate(trigger: WakeTrigger, at now: Date) {
        beginWakeAssertion()
        var trip = TripRecord(
            mode: .automatic, wakeTrigger: trigger, launchKind: launchKind(now: now), wakeAt: now,
            legacyKeeperEnabled: legacyKeeperEnabled, lowPowerModeAtStart: lowPowerMode,
            appStateAtWake: Self.name(UIApplication.shared.applicationState), batteryStart: batteryLevel())
        trip.triggers.append(TriggerEvent(kind: trigger, at: now))
        trip.parkingSpot = TripStore.loadParkedSpot()
        guard startSampling(for: trip) else { return }
        active = trip
        phase = .candidate
        confirmation = DriveConfirmation(startedAt: now)
        endDetector = TripEndDetector(startedAt: now, stopWindow: TripEndDetector.automaticStopWindow)
        WakeLog.append(kind: "candidate", detail: "\(trigger.rawValue) \(trip.launchKind.rawValue)")
        queryMotionHistory(now: now)
        startActivityUpdates()
        startTick()
        TripStore.save(trip)
    }

    /// Takes a short UIKit background assertion so a wake survives until the first fix.
    private func beginWakeAssertion() {
        guard wakeAssertion == .invalid else { return }
        wakeAssertion = UIApplication.shared.beginBackgroundTask(withName: "capture-wake") {
            Task { @MainActor in CaptureEngine.shared.endWakeAssertion() }
        }
    }

    private func endWakeAssertion() {
        guard wakeAssertion != .invalid else { return }
        UIApplication.shared.endBackgroundTask(wakeAssertion)
        wakeAssertion = .invalid
    }

    // MARK: Manual trips

    func toggleManual(source: String) async {
        launch(launchedForLocation: false)
        if active != nil {
            await finishTrip(reason: "manual stop (\(source))")
            return
        }
        let now = Date()
        var trip = TripRecord(
            mode: .manual, wakeTrigger: .manual, launchKind: launchKind(now: now), wakeAt: now,
            legacyKeeperEnabled: legacyKeeperEnabled, lowPowerModeAtStart: lowPowerMode,
            appStateAtWake: Self.name(UIApplication.shared.applicationState), batteryStart: batteryLevel())
        trip.confirmedAt = now
        trip.confirmation = "manual (\(source))"
        trip.parkingSpot = TripStore.loadParkedSpot()
        guard startSampling(for: trip) else { return }
        active = trip
        phase = .recording
        endDetector = TripEndDetector(startedAt: now, stopWindow: TripEndDetector.manualStopWindow)
        startLiveActivity(mode: "manual", startedAt: now)
        startActivityUpdates()
        startTick()
        TripStore.save(trip)
    }

    // MARK: Sampling

    private func startSampling(for trip: TripRecord) -> Bool {
        let writer: TripWriter
        do {
            writer = try TripWriter(directory: TripStore.directory(for: trip.id), imuStats: trip.imuStats)
        } catch {
            WakeLog.append(kind: "writerError", detail: error.localizedDescription)
            return false
        }
        self.writer = writer
        lastAcceptedFix = nil
        liveStationary = false
        startBackgroundSession()
        startLiveUpdates()
        if trip.legacyKeeperEnabled { startLegacyStream() }
        startDeviceMotion(writer: writer)
        return true
    }

    private func startBackgroundSession() {
        let session = CLBackgroundActivitySession()
        backgroundSession = session
        backgroundDiagnosticsTask = Task { [weak self] in
            do {
                for try await diagnostic in session.diagnostics {
                    var flags: [String] = []
                    if diagnostic.authorizationDenied { flags.append("authorizationDenied") }
                    if diagnostic.insufficientlyInUse { flags.append("insufficientlyInUse") }
                    if diagnostic.serviceSessionRequired { flags.append("serviceSessionRequired") }
                    if !flags.isEmpty {
                        self?.active?.addNote("CLBackgroundActivitySession: \(flags.joined(separator: ","))")
                    }
                }
            } catch {
                self?.active?.addNote("CLBackgroundActivitySession error: \(error.localizedDescription)")
            }
        }
    }

    private func startLiveUpdates() {
        liveTask = Task { [weak self] in
            do {
                for try await update in CLLocationUpdate.liveUpdates(.automotiveNavigation) {
                    guard let self, !Task.isCancelled else { return }
                    self.handle(liveUpdate: update)
                }
            } catch {
                self?.active?.addNote("liveUpdates error: \(error.localizedDescription)")
            }
        }
    }

    private func handle(liveUpdate update: CLLocationUpdate) {
        liveStationary = update.stationary
        if update.insufficientlyInUse { active?.addNote("liveUpdates: insufficientlyInUse") }
        if update.serviceSessionRequired { active?.addNote("liveUpdates: serviceSessionRequired") }
        if update.locationUnavailable { active?.addNote("liveUpdates: locationUnavailable") }
        if update.authorizationDenied { active?.addNote("liveUpdates: authorizationDenied") }
        if update.stationary { active?.addNote("liveUpdates: reported stationary") }
        if let location = update.location { ingest(location, source: .liveUpdates) }
    }

    private func startLegacyStream() {
        streamManager.allowsBackgroundLocationUpdates = true
        streamManager.startUpdatingLocation()
    }

    private func startDeviceMotion(writer: TripWriter) {
        guard motionManager.isDeviceMotionAvailable else { return }
        motionManager.deviceMotionUpdateInterval = 1 / Tuning.imuHz
        let reference: CMAttitudeReferenceFrame = .xArbitraryCorrectedZVertical
        // @Sendable matters: without it the closure inherits @MainActor from this method, and
        // Swift traps as soon as Core Motion calls it on the writer queue.
        motionManager.startDeviceMotionUpdates(using: reference, to: writer.motionQueue) { @Sendable motion, _ in
            if let motion { writer.append(motion: motion) }
        }
    }

    private func stopSampling() {
        liveTask?.cancel()
        liveTask = nil
        streamManager.stopUpdatingLocation()
        streamManager.allowsBackgroundLocationUpdates = false
        motionManager.stopDeviceMotionUpdates()
        activityManager.stopActivityUpdates()
        backgroundDiagnosticsTask?.cancel()
        backgroundSession?.invalidate()
        backgroundSession = nil
        tickTask?.cancel()
        tickTask = nil
        writer?.close()
        writer = nil
        confirmation = nil
        endDetector = nil
        latestActivity = nil
        endWakeAssertion()
    }

    private func ingest(_ location: CLLocation, source: FixSource) {
        guard var trip = active, let writer else { return }
        // A stream's first fix can be cached from before the wake. Counting it would make the
        // start distance look perfect, because it was taken while still parked.
        if location.timestamp < trip.wakeAt.addingTimeInterval(-Tuning.cachedFixTolerance) {
            trip.addNote("dropped cached fix from before wake")
            active = trip
            return
        }
        switch source {
        case .legacy: trip.fixesLegacy += 1
        case .liveUpdates: trip.fixesLiveUpdates += 1
        }
        // Both streams can run together; keep one fix per ~second, whichever arrives first.
        if let lastAcceptedFix, location.timestamp.timeIntervalSince(lastAcceptedFix) < 0.5 {
            active = trip
            return
        }
        lastAcceptedFix = location.timestamp
        let fix = GPSFix(
            time: location.timestamp,
            point: GeoPoint(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude),
            altitude: location.altitude, horizontalAccuracy: location.horizontalAccuracy,
            verticalAccuracy: location.verticalAccuracy, speed: location.speed,
            speedAccuracy: location.speedAccuracy, course: location.course, source: source)
        writer.append(fix: fix)
        trip.accumulator.ingest(fix)
        confirmation?.observeSpeed(fix.speed, at: fix.time)
        endDetector?.observe(speed: fix.speed, activity: latestActivity, at: fix.time)
        active = trip
        endWakeAssertion()
    }

    // MARK: Motion activity

    private func queryMotionHistory(now: Date) {
        guard CMMotionActivityManager.isActivityAvailable() else {
            active?.addNote("motion activity unavailable")
            return
        }
        let from = now.addingTimeInterval(-Tuning.historyWindow)
        activityManager.queryActivityStarting(from: from, to: now, to: .main) { activities, error in
            let observations = (activities ?? []).map(Self.observation(from:))
            let message = error?.localizedDescription
            Task { @MainActor in
                CaptureEngine.shared.applyHistory(observations, error: message)
            }
        }
    }

    private func applyHistory(_ observations: [ActivityObservation], error: String?) {
        if let error { active?.addNote("motion history error: \(error)") }
        for observation in observations.sorted(by: { $0.start < $1.start }) {
            confirmation?.observe(activity: observation)
            latestActivity = observation
        }
        active?.motionStartAt = MotionHistory.automotiveStart(in: observations)
        evaluate(now: Date())
    }

    private func startActivityUpdates() {
        guard CMMotionActivityManager.isActivityAvailable() else { return }
        activityManager.startActivityUpdates(to: .main) { activity in
            guard let activity else { return }
            let observation = Self.observation(from: activity)
            Task { @MainActor in CaptureEngine.shared.apply(observation) }
        }
    }

    private func apply(_ observation: ActivityObservation) {
        latestActivity = observation
        confirmation?.observe(activity: observation)
        if observation.kind == .automotive, active != nil, active?.motionStartAt == nil {
            active?.motionStartAt = observation.start
        }
    }

    nonisolated private static func observation(from activity: CMMotionActivity) -> ActivityObservation {
        // automotive and stationary can both be true at a red light (A3 §1.1): automotive wins.
        let kind: ActivityKind =
            if activity.automotive { .automotive }
            else if activity.cycling { .cycling }
            else if activity.running { .running }
            else if activity.walking { .walking }
            else if activity.stationary { .stationary }
            else { .unknown }
        let confidence: ActivityConfidence =
            switch activity.confidence {
            case .high: .high
            case .medium: .medium
            default: .low
            }
        return ActivityObservation(start: activity.startDate, kind: kind, confidence: confidence)
    }

    // MARK: Trip loop

    private func startTick() {
        tickTask?.cancel()
        tickTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(1))
                guard let self, !Task.isCancelled else { return }
                self.evaluate(now: Date())
            }
        }
    }

    private func evaluate(now: Date) {
        guard active != nil else { return }
        switch phase {
        case .candidate: evaluateCandidate(now: now)
        case .recording: evaluateRecording(now: now)
        case .armed: break
        }
        if now.timeIntervalSince(lastCheckpoint) >= Tuning.checkpointInterval { checkpoint(now: now) }
    }

    private func evaluateCandidate(now: Date) {
        guard let decision = confirmation?.decide(now: now) else { return }
        switch decision {
        case .pending:
            break
        case .confirmed(let reason):
            confirmCandidate(reason: reason, now: now)
        case .rejected(let reason):
            rejectCandidate(reason: reason, now: now)
        }
    }

    private func confirmCandidate(reason: String, now: Date) {
        active?.status = .recording
        active?.confirmedAt = now
        active?.confirmation = reason
        phase = .recording
        confirmation = nil
        WakeLog.append(kind: "confirmed", detail: reason)
        notifyRecording()
        attemptBackgroundLiveActivity(startedAt: active?.wakeAt ?? now)
        checkpoint(now: now)
    }

    private func evaluateRecording(now: Date) {
        guard let endDetector, !finishing else { return }
        if endDetector.hasEnded(now: now) {
            let window = Int(endDetector.stopWindow)
            Task { await finishTrip(reason: "stationary \(window)s") }
        }
    }

    private func notifyRecording() {
        let content = UNMutableNotificationContent()
        content.title = "Spike recording a drive"
        content.body = "Trigger: \(active?.wakeTrigger.rawValue ?? "unknown")"
        content.interruptionLevel = .passive
        UNUserNotificationCenter.current().add(
            UNNotificationRequest(identifier: "recording", content: content, trigger: nil))
    }

    private func checkpoint(now: Date) {
        guard var trip = active else { return }
        writer?.flush()
        if let writer { trip.imuStats = writer.snapshotIMUStats() }
        trip.lastCheckpointAt = now
        active = trip
        lastCheckpoint = now
        TripStore.save(trip)
    }

    private func rejectCandidate(reason: String, now: Date) {
        guard var trip = active else { return }
        if let writer { trip.imuStats = writer.snapshotIMUStats() }
        trip.status = .rejected
        trip.rejection = reason
        trip.endedAt = now
        trip.batteryEnd = batteryLevel()
        trip.metrics = TripMetrics(record: trip, now: now)
        WakeLog.append(kind: "rejected", detail: reason)
        stopSampling()
        TripStore.deleteSamples(for: trip.id)
        complete(trip)
    }

    private func finishTrip(reason: String) async {
        guard var trip = active, !finishing else { return }
        finishing = true
        defer { finishing = false }
        let now = Date()
        let writer = self.writer
        writer?.flush()
        if let writer { trip.imuStats = writer.snapshotIMUStats() }
        trip.status = trip.status == .candidate ? .rejected : .finished
        if trip.status == .rejected { trip.rejection = reason }
        trip.endReason = reason
        trip.lastMovingAt = endDetector?.lastMovingAt
        trip.endedAt = now
        trip.batteryEnd = batteryLevel()
        if let writer {
            await backfill(trip: &trip, writer: writer)
        }
        trip.metrics = TripMetrics(record: trip, now: now)
        // GPS stays on until here so the backfill runs while the app is still awake.
        stopSampling()
        await endLiveActivity()
        if let parked = trip.accumulator.lastUsableFix?.point {
            armParkedRegion(at: parked)
        }
        lastTripEnd = now
        WakeLog.append(kind: "tripEnded", detail: reason)
        complete(trip)
    }

    private func backfill(trip: inout TripRecord, writer: TripWriter) async {
        guard trip.mode == .automatic, let motionStart = trip.motionStartAt, let firstIMU = trip.imuStats.firstAt,
            motionStart < firstIMU
        else { return }
        let window = firstIMU.timeIntervalSince(motionStart)
        trip.backfillWindowSeconds = window
        trip.backfillSamples = await Self.fetchBackfill(from: motionStart, to: firstIMU, writer: writer)
    }

    nonisolated private static func fetchBackfill(from: Date, to: Date, writer: TripWriter) async -> Int {
        await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                let recorder = CMSensorRecorder()
                var count = 0
                if let list = recorder.accelerometerData(from: from, to: to) {
                    for case let sample as CMRecordedAccelerometerData in list {
                        let a = sample.acceleration
                        writer.appendBackfill(time: sample.startDate.timeIntervalSince1970, x: a.x, y: a.y, z: a.z)
                        count += 1
                    }
                }
                writer.flush()
                continuation.resume(returning: count)
            }
        }
    }

    private func complete(_ trip: TripRecord) {
        TripStore.save(trip)
        active = nil
        phase = .armed
        trips = TripStore.loadAll()
        TripStore.writeSummary(trips: trips, installs: installs)
    }

    // MARK: Recovery

    private func recoverInterruptedTrips() {
        let now = Date()
        for var trip in trips where trip.status == .candidate || trip.status == .recording {
            if trip.status == .recording, now.timeIntervalSince(trip.lastCheckpointAt) < Tuning.resumeWindow,
                active == nil, startSampling(for: trip)
            {
                trip.relaunchCount += 1
                trip.triggers.append(TriggerEvent(kind: .relaunchRecovery, at: now))
                trip.addNote("resumed after process death")
                active = trip
                phase = .recording
                let window =
                    trip.mode == .manual ? TripEndDetector.manualStopWindow : TripEndDetector.automaticStopWindow
                endDetector = TripEndDetector(startedAt: now, stopWindow: window)
                startActivityUpdates()
                startTick()
                WakeLog.append(kind: "resumedTrip", detail: "")
                TripStore.save(trip)
            } else {
                trip.status = trip.status == .recording ? .interrupted : .rejected
                trip.rejection = trip.status == .rejected ? "process died while confirming" : nil
                trip.endedAt = trip.lastCheckpointAt
                trip.metrics = TripMetrics(record: trip, now: now)
                TripStore.save(trip)
                WakeLog.append(kind: "interruptedTrip", detail: trip.status.rawValue)
            }
        }
        trips = TripStore.loadAll()
    }

    // MARK: Live Activity

    private func startLiveActivity(mode: String, startedAt: Date) {
        do {
            let state = CaptureActivityAttributes.ContentState(startedAt: startedAt, distanceKm: 0)
            liveActivity = try Activity.request(
                attributes: CaptureActivityAttributes(mode: mode),
                content: ActivityContent(state: state, staleDate: nil))
            active?.addNote("Live Activity started")
        } catch {
            active?.addNote("Live Activity failed: \(error.localizedDescription)")
        }
    }

    /// A3 §3 / DTS say a local background start must fail. The spike records what actually happens.
    private func attemptBackgroundLiveActivity(startedAt: Date) {
        startLiveActivity(mode: "automatic", startedAt: startedAt)
    }

    private func endLiveActivity() async {
        guard let activity = liveActivity else { return }
        liveActivity = nil
        nonisolated(unsafe) let ending = activity
        await ending.end(nil, dismissalPolicy: .immediate)
    }

    // MARK: Helpers

    private func batteryLevel() -> Double? {
        let level = UIDevice.current.batteryLevel
        return level >= 0 ? Double(level) : nil
    }

    private static func name(_ state: UIApplication.State) -> String {
        switch state {
        case .active: "active"
        case .inactive: "inactive"
        case .background: "background"
        @unknown default: "unknown"
        }
    }
}

// MARK: - CLLocationManagerDelegate

extension CaptureEngine: @preconcurrency CLLocationManagerDelegate {
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard manager === slcManager else { return }
        refreshPermissions()
        WakeLog.append(kind: "authorization", detail: "\(authorization.rawValue) full=\(fullAccuracy)")
        if authorization == .authorizedWhenInUse || authorization == .authorizedAlways {
            armTriggers()
        }
        if authorization == .authorizedWhenInUse, wantsAlwaysUpgrade {
            wantsAlwaysUpgrade = false
            slcManager.requestAlwaysAuthorization()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        if manager === streamManager {
            locations.forEach { ingest($0, source: .legacy) }
        } else if manager === slcManager, let location = locations.last {
            let age = Int(Date().timeIntervalSince(location.timestamp))
            WakeLog.append(kind: "significantChange", detail: "ageSeconds=\(age)")
            // Starting SLC delivers one immediate fix; opening the app must not look like a drive.
            if UIApplication.shared.applicationState == .active,
                Date().timeIntervalSince(processStart) < Tuning.initialSLCWindow
            {
                WakeLog.append(kind: "initialSignificantChangeIgnored", detail: "")
                return
            }
            handleTrigger(.significantChange, eventTime: location.timestamp, maxAge: Tuning.staleLocationAge)
        }
    }

    func locationManager(_ manager: CLLocationManager, didVisit visit: CLVisit) {
        let departed = visit.departureDate != .distantFuture
        WakeLog.append(kind: "visit", detail: "departed=\(departed)")
        if departed {
            handleTrigger(.visitDeparture, eventTime: visit.departureDate, maxAge: Tuning.staleRegionOrVisitAge)
        } else if TripStore.loadParkedSpot() == nil {
            // PRD: with no previous trip, the parking spot comes from the Visit.
            armParkedRegion(
                at: GeoPoint(latitude: visit.coordinate.latitude, longitude: visit.coordinate.longitude))
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        WakeLog.append(kind: "locationError", detail: error.localizedDescription)
    }
}
