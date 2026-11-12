import DPSharedBridge
import Foundation

/// Shows a trip on the Lock Screen and in the Dynamic Island (ActivityKit).
@MainActor
protocol LiveSurfacing: AnyObject {
    var activitiesEnabled: Bool { get }
    var hasEnded: Bool { get }
    func start(tripId: String, startedAt: Date)
    func present(tripId: String, startedAt: Date, distanceM: Double) -> Bool
    func resume(tripId: String) -> Bool
    func update(distanceM: Double, hasFix: Bool)
    func end()
}

extension LiveSurfacing {
    var activitiesEnabled: Bool { true }
    var hasEnded: Bool { false }
    func resume(tripId: String) -> Bool { false }

    func present(tripId: String, startedAt: Date, distanceM: Double) -> Bool {
        start(tripId: tripId, startedAt: startedAt)
        return true
    }
}

@MainActor
protocol TripNotifying: AnyObject {
    func postAutoTripRecording(tripId: String)
    func removeTripNotifications(tripId: String)
    func postDriveDecoded(tripId: String, smoothness: Int?)
}

extension TripNotifying {
    func postDriveDecoded(tripId: String, smoothness: Int?) {}
}

/// A short UIKit background assertion that keeps a wake alive until the trip is moving.
@MainActor
protocol BackgroundAssertions: AnyObject {
    func begin()
    func end()
}

/// The shared core as the app sees it: the Kotlin `TripRecorder` and `TripRepository`, already mapped to the Swift
/// mirrors in DPSharedBridge. `KotlinCore` implements it over `DrivePerfCore`; tests use a fake.
@MainActor
protocol SharedCore: AnyObject {
    /// The recorder status now, then every change.
    func statusUpdates() -> AsyncStream<RecorderStatus>
    /// Finished trips, newest first, now and after every change.
    func tripUpdates() -> AsyncStream<[TripSummary]>
    func onLaunch()
    func onWake(_ event: WakeEvent)
    func stop()
    func markPassenger(keep: Bool)
    func setAutoDetect(enabled: Bool)
    func onLaunch(reason: RearmReason)
    func onForeground()
    func onWatchdogOpened()
    func setTriggersAvailable(_ available: Bool)
    func setInDriveScreenVisible(_ visible: Bool)
}

/// Defaults so test fakes keep compiling; `KotlinCore` forwards each call to the Kotlin recorder.
extension SharedCore {
    func onLaunch(reason: RearmReason) { onLaunch() }
    func onForeground() {}
    func onWatchdogOpened() {}
    func setTriggersAvailable(_ available: Bool) {}
    func setInDriveScreenVisible(_ visible: Bool) {}
}

/// `TripRecording` and `TripHistory` for the features, on the shared core's recorder (DRIVE-206). The core decides
/// everything about a trip; this only adds the iOS surfaces around it: a Live Activity for manual trips, a passive
/// notification for automatic ones (Spike 1 change 9), and a background assertion from the wake until the trip moves.
///
/// It exists before the core starts, because the UI is built first and the core waits for the Core Location sessions
/// and the first unlock (ADR-002 §5, EC-RBT). Until `attach`, it is idle and drops commands: no trip can start then.
@MainActor
final class CoreTripRecorder: TripRecording, TripHistory {
    private(set) var status = RecorderStatus.idle {
        didSet { if status != oldValue { statusObservers.values.forEach { $0.yield(status) } } }
    }
    private(set) var history: [TripSummary] = [] {
        didSet { historyObservers.values.forEach { $0.yield(history) } }
    }

    private let liveSurface: (any LiveSurfacing)?
    private let notifier: (any TripNotifying)?
    private let assertions: (any BackgroundAssertions)?
    private var core: (any SharedCore)?
    private var autoDetect: Bool
    private var liveTripId: String?
    private var notifiedTripId: String?
    private var notifiedAutomaticTrip = false
    private var manualActivityAttemptID: String?
    private var activityRecoveryAttemptID: String?
    private(set) var isStartingFromIntent = false
    var surfaceData: TripSurfaceData?
    var liveActivitiesEnabled: Bool { liveSurface?.activitiesEnabled ?? false }
    private var statusObservers: [UUID: AsyncStream<RecorderStatus>.Continuation] = [:]
    private var historyObservers: [UUID: AsyncStream<[TripSummary]>.Continuation] = [:]

    init(
        liveSurface: (any LiveSurfacing)? = nil, notifier: (any TripNotifying)? = nil,
        assertions: (any BackgroundAssertions)? = nil, autoDetectEnabled: Bool = true
    ) {
        self.liveSurface = liveSurface
        self.notifier = notifier
        self.assertions = assertions
        autoDetect = autoDetectEnabled
        status.autoDetectEnabled = autoDetectEnabled
    }

    var isAttached: Bool { core != nil }

    /// Connects the started core. Later calls are ignored.
    func attach(_ core: any SharedCore) {
        guard self.core == nil else { return }
        self.core = core
        core.setAutoDetect(enabled: autoDetect)
        Task { [weak self] in
            for await next in core.statusUpdates() { self?.apply(next) }
        }
        Task { [weak self] in
            for await trips in core.tripUpdates() {
                self?.history = trips
                await self?.surfaceData?.updateLatestDrive(trips)
            }
        }
    }

    // MARK: TripRecording

    func statuses() -> AsyncStream<RecorderStatus> {
        let id = UUID()
        return AsyncStream { continuation in
            continuation.yield(status)
            statusObservers[id] = continuation
            continuation.onTermination = { [weak self] _ in
                Task { @MainActor in self?.statusObservers[id] = nil }
            }
        }
    }

    func onLaunch() { core?.onLaunch() }

    func onWake(_ event: WakeEvent) {
        guard let core else { return }
        assertions?.begin()
        core.onWake(event)
    }

    func stop() { core?.stop() }

    func markPassenger(keep: Bool) { core?.markPassenger(keep: keep) }

    func onLaunch(reason: RearmReason) { core?.onLaunch(reason: reason) }

    func onForeground() { core?.onForeground() }

    func onWatchdogOpened() { core?.onWatchdogOpened() }

    func setTriggersAvailable(_ available: Bool) { core?.setTriggersAvailable(available) }

    func setInDriveScreenVisible(_ visible: Bool) { core?.setInDriveScreenVisible(visible) }

    func setAutoDetect(enabled: Bool) {
        autoDetect = enabled
        if let core {
            core.setAutoDetect(enabled: enabled)
        } else {
            status.autoDetectEnabled = enabled
        }
    }

    // MARK: TripHistory

    func observeTrips() -> AsyncStream<[TripSummary]> {
        let id = UUID()
        return AsyncStream { continuation in
            continuation.yield(history)
            historyObservers[id] = continuation
            continuation.onTermination = { [weak self] _ in
                Task { @MainActor in self?.historyObservers[id] = nil }
            }
        }
    }

    // MARK: Surfaces

    /// Keeps the ActivityKit request inside the system's LiveActivityIntent execution.
    func startFromIntent(_ wake: WakeEvent) async -> Bool {
        if isStartingFromIntent { return true }
        isStartingFromIntent = true
        defer { isStartingFromIntent = false }
        let previousFailure = status.startFailed
        onWake(wake)
        guard let result = await waitForStatus(where: { $0.isDriving || ($0.startFailed && !previousFailure) }),
            result.isDriving else { return false }
        guard status.isDriving, status.tripId == result.tripId else { return true }
        if result.isManual { manualActivityAttemptID = result.tripId }
        if !showLiveActivity(), result.isManual, let id = result.tripId { postRecordingNotice(tripID: id, automatic: false) }
        return true
    }

    func showLiveActivity() -> Bool {
        guard let id = status.tripId, status.state == .recording || status.state == .paused else { return false }
        if liveTripId == id { return true }
        let startedAt = Date().addingTimeInterval(-max(0, status.elapsedS))
        guard liveSurface?.present(tripId: id, startedAt: startedAt, distanceM: status.distanceM) == true else { return false }
        liveTripId = id
        publishRecording()
        return true
    }

    func waitForStatus(
        where predicate: @escaping @MainActor (RecorderStatus) -> Bool
    ) async -> RecorderStatus? {
        if predicate(status) { return status }
        let stream = statuses()
        let watcher = Task { @MainActor () -> RecorderStatus? in
            for await next in stream where predicate(next) { return next }
            return nil
        }
        let timeout = Task {
            try? await Task.sleep(for: .seconds(5))
            watcher.cancel()
        }
        defer { timeout.cancel() }
        return await withTaskCancellationHandler { await watcher.value } onCancel: { watcher.cancel() }
    }

    func publishRecording() {
        surfaceData?.updateRecording(
            status: status,
            offersLiveActivity: !status.isManual && status.state != .finishing && liveTripId == nil && liveActivitiesEnabled)
    }

    private func apply(_ next: RecorderStatus) {
        status = next
        defer { publishRecording() }
        if next.state == .idle || next.distanceM > 0 { assertions?.end() }
        guard let tripId = next.tripId, next.state != .idle else {
            endSurfaces()
            return
        }
        if liveTripId != nil && liveTripId != tripId || notifiedTripId != nil && notifiedTripId != tripId {
            endSurfaces()
        }
        if next.isDriving, activityRecoveryAttemptID != tripId {
            activityRecoveryAttemptID = tripId
            if liveTripId == nil, liveSurface?.resume(tripId: tripId) == true { liveTripId = tripId }
        }
        if next.state == .recording || next.state == .paused {
            if next.isManual {
                if !isStartingFromIntent, manualActivityAttemptID != tripId {
                    manualActivityAttemptID = tripId
                    if !showLiveActivity() { postRecordingNotice(tripID: tripId, automatic: false) }
                }
                if liveTripId == tripId, liveSurface?.hasEnded == true {
                    postRecordingNotice(tripID: tripId, automatic: false)
                }
            } else {
                postRecordingNotice(tripID: tripId, automatic: true)
            }
        }
        if liveTripId == tripId { liveSurface?.update(distanceM: next.distanceM, hasFix: next.distanceM > 0) }
    }

    private func postRecordingNotice(tripID: String, automatic: Bool) {
        guard notifiedTripId == nil else { return }
        notifier?.postAutoTripRecording(tripId: tripID)
        notifiedTripId = tripID
        notifiedAutomaticTrip = automatic
    }

    private func endSurfaces() {
        manualActivityAttemptID = nil
        activityRecoveryAttemptID = nil
        if liveTripId != nil {
            liveSurface?.end()
            liveTripId = nil
        }
        if let id = notifiedTripId {
            notifier?.removeTripNotifications(tripId: id)
            notifiedTripId = nil
            if notifiedAutomaticTrip, let surfaceData, let notifier {
                Task {
                    guard let decoded = await surfaceData.decodedTrip(tripID: id), !status.isDriving else { return }
                    notifier.postDriveDecoded(tripId: id, smoothness: decoded.smoothness)
                }
            }
            notifiedAutomaticTrip = false
        }
    }
}
