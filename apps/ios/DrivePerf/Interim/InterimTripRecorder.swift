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

/// A short UIKit background assertion that keeps a wake alive until the first fix arrives.
@MainActor
protocol BackgroundAssertions: AnyObject {
    func begin()
    func end()
}

/// Interim `TripRecording` and `TripHistory` until DRIVE-62's shared `TripRecorder` lands. It follows the spec's
/// state machine for the paths the shell needs (§1.4): wake → CANDIDATE → RECORDING or rejected, manual Start →
/// RECORDING, Stop or stop timer → FINISHING → PROCESSED, and kill-and-relaunch recovery (§1.7).
///
/// Spike 1 changes it applies: recording starts at the wake and the candidate's samples are kept (2); the activity
/// history query is bounded by the previous trip's end (3); a trip only starts when the trip files are writable (5);
/// no Live Activity for automatic trips, a passive notification instead (9); the parked region is re-armed at every
/// trip end (1).
@MainActor
final class InterimTripRecorder: TripRecording, TripHistory {
    private(set) var snapshot = RecorderSnapshot() {
        didSet { if snapshot != oldValue { snapshotObservers.values.forEach { $0.yield(snapshot) } } }
    }
    private(set) var history: [TripSummary] = [] {
        didSet { historyObservers.values.forEach { $0.yield(history) } }
    }

    private let config: TripRulesConfig
    private let location: any LocationSourceBridge
    private let motion: any MotionSourceBridge
    private let activity: any ActivitySourceBridge
    private let files: any TripFileStoreBridge
    private let index: TripIndexStore
    private let triggers: (any WakeTriggerBridge)?
    private let liveSurface: (any LiveSurfacing)?
    private let notifier: (any TripNotifying)?
    private let assertions: (any BackgroundAssertions)?
    private let now: @MainActor () -> Date

    private var active: ActiveTrip?
    private var tick: Task<Void, Never>?
    private var autoDetectEnabled = false
    private var alwaysAuthorized = false
    private var lastTripEndedAt: Date?
    private var snapshotObservers: [UUID: AsyncStream<RecorderSnapshot>.Continuation] = [:]
    private var historyObservers: [UUID: AsyncStream<[TripSummary]>.Continuation] = [:]

    private struct ActiveTrip {
        var id: String
        var source: TripSource
        var startedAt: Date
        var confirmed: Bool
        var writer: any TripFileWriter
        var candidate: CandidateDecider?
        var stop: StopDetector
        var distance: DistanceAccumulator
        var hasFix = false
        var lastCheckpointAt: Date
    }

    init(
        config: TripRulesConfig = TripRulesConfig(),
        location: any LocationSourceBridge,
        motion: any MotionSourceBridge,
        activity: any ActivitySourceBridge,
        files: any TripFileStoreBridge,
        index: TripIndexStore,
        triggers: (any WakeTriggerBridge)? = nil,
        liveSurface: (any LiveSurfacing)? = nil,
        notifier: (any TripNotifying)? = nil,
        assertions: (any BackgroundAssertions)? = nil,
        now: @escaping @MainActor () -> Date = { Date() }
    ) {
        self.config = config
        self.location = location
        self.motion = motion
        self.activity = activity
        self.files = files
        self.index = index
        self.triggers = triggers
        self.liveSurface = liveSurface
        self.notifier = notifier
        self.assertions = assertions
        self.now = now
    }

    // MARK: Launch

    /// Loads history and recovers a trip that was running when the app died (§1.7). Call after the platform
    /// sessions are recreated.
    func launch() {
        history = index.loadSummaries()
        lastTripEndedAt = history.first?.endedAt
        recoverInterruptedTrip()
    }

    /// The current permission and choice: automatic trips need Always (S-ONB-08).
    func updateAuthorization(always: Bool, autoDetectEnabled enabled: Bool) {
        alwaysAuthorized = always
        autoDetectEnabled = enabled
        publishAutoDetect()
    }

    // MARK: TripRecording

    func snapshots() -> AsyncStream<RecorderSnapshot> {
        let id = UUID()
        return AsyncStream { continuation in
            continuation.yield(snapshot)
            snapshotObservers[id] = continuation
            continuation.onTermination = { [weak self] _ in
                Task { @MainActor in self?.snapshotObservers[id] = nil }
            }
        }
    }

    func startManualTrip() throws(RecorderError) {
        if let active {
            guard !active.confirmed else { throw .alreadyRecording }
            // A manual Start during a candidate confirms it as a manual trip from the wake.
            self.active?.source = .manual
            confirm()
            liveSurface?.start(tripId: active.id, startedAt: active.startedAt)
            return
        }
        guard files.isWritable() else { throw .storageLocked }
        try begin(source: .manual, wokeAt: now())
        if let active { liveSurface?.start(tripId: active.id, startedAt: active.startedAt) }
    }

    func stopTrip() async -> TripSummary? {
        guard active?.confirmed == true else { return nil }
        return finish(endedAt: now())
    }

    func setAutoDetect(enabled: Bool) {
        autoDetectEnabled = enabled
        publishAutoDetect()
    }

    // MARK: TripHistory

    func trips() -> AsyncStream<[TripSummary]> {
        let id = UUID()
        return AsyncStream { continuation in
            continuation.yield(history)
            historyObservers[id] = continuation
            continuation.onTermination = { [weak self] _ in
                Task { @MainActor in self?.historyObservers[id] = nil }
            }
        }
    }

    func delete(tripId: String) {
        files.delete(tripId: tripId)
        index.remove(tripId: tripId)
        history = index.loadSummaries()
    }

    // MARK: Wakes

    /// A platform wake (§1.3). Ignored unless automatic trips are on with Always location and storage is open.
    func handle(_ wake: WakeEvent) {
        guard active == nil, autoDetectEnabled, alwaysAuthorized, files.isWritable() else { return }
        let wokeAt = Date(timeIntervalSince1970: TimeInterval(wake.receivedWallMs) / 1000)
        try? begin(source: .automatic, wokeAt: wokeAt)
    }

    // MARK: Trip loop

    private func begin(source: TripSource, wokeAt: Date) throws(RecorderError) {
        let id = UUID().uuidString.lowercased()
        let writer: any TripFileWriter
        do {
            writer = try files.open(tripId: id)
        } catch {
            throw .storageLocked
        }
        assertions?.begin()
        let stopWindow = source == .manual ? config.manualStopS : config.autoStopS
        active = ActiveTrip(
            id: id, source: source, startedAt: wokeAt, confirmed: source == .manual, writer: writer,
            candidate: source == .automatic ? CandidateDecider(wokeAt: wokeAt, config: config) : nil,
            stop: StopDetector(startedAt: wokeAt, stopWindowS: stopWindow, movingSpeedMps: config.movingSpeedMps),
            distance: DistanceAccumulator(config: config), lastCheckpointAt: wokeAt)
        startSampling(notBefore: wokeAt, writer: writer)
        if source == .automatic { queryActivityHistory(until: wokeAt) }
        publishActive()
        checkpoint()
    }

    private func startSampling(notBefore: Date, writer: any TripFileWriter) {
        location.start(
            notBeforeWallMs: Int64(notBefore.timeIntervalSince1970 * 1000),
            onSample: { [weak self] sample in self?.ingest(sample) },
            onStationary: { _ in },
            onIssue: { _ in })
        motion.start(rateHz: config.imuHz) { @Sendable sample in writer.append(sample) }
        activity.start { [weak self] sample in self?.ingest(sample) }
        tick?.cancel()
        tick = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(1))
                self?.evaluate()
            }
        }
    }

    private func stopSampling() {
        tick?.cancel()
        tick = nil
        location.stop()
        motion.stop()
        activity.stop()
        assertions?.end()
    }

    /// Bounded by the previous trip's end, so the 15-minute query edge is never mistaken for the motion start
    /// (spike change 3).
    private func queryActivityHistory(until wokeAt: Date) {
        var from = wokeAt.addingTimeInterval(-config.activityHistoryWindowS)
        if let lastTripEndedAt, lastTripEndedAt > from { from = lastTripEndedAt }
        activity.queryHistory(
            fromWallMs: Int64(from.timeIntervalSince1970 * 1000), toWallMs: Int64(wokeAt.timeIntervalSince1970 * 1000)
        ) { [weak self] samples in
            samples.sorted { $0.startWallMs < $1.startWallMs }.forEach { self?.ingest($0) }
        }
    }

    private func ingest(_ sample: LocationSample) {
        guard var trip = active else { return }
        trip.writer.append(sample)
        trip.distance.ingest(sample)
        let time = Date(timeIntervalSince1970: TimeInterval(sample.wallMs) / 1000)
        trip.candidate?.observe(speedMps: sample.speedMps, accuracyM: sample.hAccM, at: time)
        trip.stop.observe(speedMps: sample.speedMps, at: time)
        let firstFix = !trip.hasFix
        trip.hasFix = true
        active = trip
        if firstFix { assertions?.end() }
        publishActive()
        if trip.confirmed, trip.source == .manual {
            liveSurface?.update(distanceM: trip.distance.distanceM, hasFix: true)
        }
    }

    private func ingest(_ sample: ActivitySample) {
        guard active != nil else { return }
        active?.writer.append(sample)
        active?.candidate?.observe(sample)
        active?.stop.observe(sample)
    }

    /// One tick of the trip loop (every second while sampling). Internal so tests drive it with a test clock.
    func evaluate() {
        guard let trip = active else { return }
        let time = now()
        if let candidate = trip.candidate, !trip.confirmed {
            switch candidate.decide(now: time) {
            case .pending: break
            case .confirmed: confirm()
            case .rejected, .timedOut: reject()
            }
        } else if trip.stop.hasEnded(now: time) {
            _ = finish(endedAt: trip.source == .automatic ? trip.stop.lastMovingAt : time)
            return
        }
        if time.timeIntervalSince(trip.lastCheckpointAt) >= config.checkpointS { checkpoint() }
    }

    private func confirm() {
        guard var trip = active else { return }
        trip.confirmed = true
        trip.candidate = nil
        active = trip
        if trip.source == .automatic { notifier?.postAutoTripRecording(tripId: trip.id) }
        publishActive()
        checkpoint()
    }

    /// CANDIDATE → IDLE: stop and delete the samples now (§1.4).
    private func reject() {
        guard let trip = active else { return }
        stopSampling()
        trip.writer.close()
        files.delete(tripId: trip.id)
        index.clearCheckpoint()
        active = nil
        publishActive()
    }

    @discardableResult
    private func finish(endedAt: Date) -> TripSummary? {
        guard let trip = active else { return nil }
        snapshot.phase = .finishing
        trip.writer.flush()
        let counts = trip.writer.counts
        stopSampling()
        trip.writer.close()
        liveSurface?.end()
        notifier?.removeTripNotifications(tripId: trip.id)
        if let spot = trip.distance.lastUsable {
            triggers?.armParkedRegion(lat: spot.lat, lng: spot.lng, radiusM: config.parkedRadiusM)
        }
        index.clearCheckpoint()
        active = nil
        lastTripEndedAt = endedAt
        let duration = max(endedAt.timeIntervalSince(trip.startedAt), 0)
        var summary: TripSummary?
        switch classifyTrip(
            source: trip.source, distanceM: trip.distance.distanceM, durationS: duration, config: config)
        {
        case .discard:
            files.delete(tripId: trip.id)
        case .keep(let status):
            let saved = TripSummary(
                id: trip.id, source: trip.source, status: status, startedAt: trip.startedAt, endedAt: endedAt,
                distanceM: trip.distance.distanceM, samples: counts)
            index.append(saved)
            history = index.loadSummaries()
            summary = saved
        }
        snapshot = RecorderSnapshot(phase: .idle, live: nil, autoDetect: snapshot.autoDetect, lastFinished: summary)
        return summary
    }

    private func checkpoint() {
        guard var trip = active else { return }
        trip.writer.flush()
        trip.lastCheckpointAt = now()
        active = trip
        index.save(
            TripIndexStore.Checkpoint(
                tripId: trip.id, source: trip.source, confirmed: trip.confirmed, startedAt: trip.startedAt,
                lastCheckpointAt: trip.lastCheckpointAt, distanceM: trip.distance.distanceM,
                samples: trip.writer.counts))
    }

    // MARK: Recovery (§1.7)

    private func recoverInterruptedTrip() {
        guard active == nil, let saved = index.loadCheckpoint() else { return }
        let age = now().timeIntervalSince(saved.lastCheckpointAt)
        guard saved.confirmed else {
            // A candidate that died before confirmation is not a trip.
            files.delete(tripId: saved.tripId)
            index.clearCheckpoint()
            return
        }
        if age <= config.resumeWindowS, files.isWritable(), let writer = try? files.open(tripId: saved.tripId) {
            let stopWindow = saved.source == .manual ? config.manualStopS : config.autoStopS
            active = ActiveTrip(
                id: saved.tripId, source: saved.source, startedAt: saved.startedAt, confirmed: true, writer: writer,
                candidate: nil,
                stop: StopDetector(startedAt: now(), stopWindowS: stopWindow, movingSpeedMps: config.movingSpeedMps),
                distance: DistanceAccumulator(config: config, distanceM: saved.distanceM),
                lastCheckpointAt: now())
            startSampling(notBefore: now(), writer: writer)
            if saved.source == .manual { liveSurface?.start(tripId: saved.tripId, startedAt: saved.startedAt) }
            publishActive()
            return
        }
        let summary = TripSummary(
            id: saved.tripId, source: saved.source, status: .interrupted, startedAt: saved.startedAt,
            endedAt: saved.lastCheckpointAt, distanceM: saved.distanceM, samples: saved.samples)
        index.append(summary)
        index.clearCheckpoint()
        history = index.loadSummaries()
    }

    // MARK: Publishing

    private func publishActive() {
        guard let trip = active else {
            snapshot.phase = .idle
            snapshot.live = nil
            return
        }
        snapshot.phase = trip.confirmed ? .recording : .candidate
        snapshot.live = LiveTrip(
            id: trip.id, source: trip.source, startedAt: trip.startedAt, distanceM: trip.distance.distanceM,
            hasFix: trip.hasFix)
    }

    private func publishAutoDetect() {
        if !autoDetectEnabled {
            snapshot.autoDetect = .manual
        } else if !files.isWritable() {
            snapshot.autoDetect = .needsAttention(reason: .storageLocked)
        } else if !alwaysAuthorized {
            snapshot.autoDetect = .needsAttention(reason: .locationWhileUsing)
        } else {
            snapshot.autoDetect = .ready
        }
    }
}
