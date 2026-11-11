import DPSharedBridge
import Foundation

/// Shows a manual trip on the Lock Screen and in the Dynamic Island (ActivityKit). Never used for automatic trips.
@MainActor
protocol LiveSurfacing: AnyObject {
    func start(tripId: String, startedAt: Date)
    func update(distanceM: Double, hasFix: Bool)
    func end()
}

@MainActor
protocol TripNotifying: AnyObject {
    func postAutoTripRecording(tripId: String)
    func removeTripNotifications(tripId: String)
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
            for await trips in core.tripUpdates() { self?.history = trips }
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

    private func apply(_ next: RecorderStatus) {
        status = next
        if next.state == .idle || next.distanceM > 0 { assertions?.end() }
        guard let tripId = next.tripId, next.state != .idle else {
            endSurfaces()
            return
        }
        if liveTripId != nil && liveTripId != tripId || notifiedTripId != nil && notifiedTripId != tripId { endSurfaces() }
        if next.isManual, next.isDriving, liveTripId == nil {
            liveSurface?.start(tripId: tripId, startedAt: Date().addingTimeInterval(-next.elapsedS))
            liveTripId = tripId
        }
        if !next.isManual, next.state == .recording, notifiedTripId == nil {
            notifier?.postAutoTripRecording(tripId: tripId)
            notifiedTripId = tripId
        }
        if liveTripId == tripId { liveSurface?.update(distanceM: next.distanceM, hasFix: next.distanceM > 0) }
    }

    private func endSurfaces() {
        if liveTripId != nil {
            liveSurface?.end()
            liveTripId = nil
        }
        if let id = notifiedTripId {
            notifier?.removeTripNotifications(tripId: id)
            notifiedTripId = nil
        }
    }
}
