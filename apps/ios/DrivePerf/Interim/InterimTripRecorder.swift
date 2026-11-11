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

/// Interim implementation of the `TripRecorder` contract (`TripRecording`) and the trip list (`TripHistory`) until
/// the shared recorder and repositories land (DRIVE-62 slices 2–3). It runs on the same ports the shared core will
/// use — `LocationSource`, `MotionSource`, `ActivitySource`, `TripFileStore` and `ParkedRegionMonitor` —
/// and follows the spec's state machine for the paths the shell needs (§1.4): wake → CANDIDATE → RECORDING or
/// rejected, manual wake → RECORDING, stop or stop timer → FINISHING → PROCESSED, and kill-and-relaunch recovery
/// (§1.7).
///
/// Spike 1 changes it applies: recording starts at the wake and the candidate's samples are kept (2); the activity
/// history query is bounded by the previous trip's end (3); a trip starts only when its R0 file can be written,
/// which the store reports as `unavailable` before the first unlock (5); fixes cached from before the wake are
/// dropped (8); no Live Activity for automatic trips, a passive notification instead (9); the parked region is
/// re-armed at every trip end (1).
///
/// R0 here is an interim CSV capture log (`R0Lines`), appended through `TripFileStore` and gzip-compressed at
/// finalisation; the real R0 codec belongs to the shared core.
@MainActor
final class InterimTripRecorder: TripRecording, TripHistory {
    private(set) var status = RecorderStatus.idle {
        didSet { if status != oldValue { statusObservers.values.forEach { $0.yield(status) } } }
    }
    private(set) var history: [TripSummary] = [] {
        didSet { historyObservers.values.forEach { $0.yield(history) } }
    }

    private let config: TripRulesConfig
    private let location: any LocationSourceBridge
    private let motion: any MotionSourceBridge
    private let activity: any ActivitySourceBridge
    private let files: any TripFileStoreBridge
    private let parkedRegion: (any ParkedRegionMonitorBridge)?
    private let index: TripIndexStore
    private let liveSurface: (any LiveSurfacing)?
    private let notifier: (any TripNotifying)?
    private let assertions: (any BackgroundAssertions)?
    private let now: @MainActor () -> Date

    private var active: ActiveTrip?
    private var tick: Task<Void, Never>?
    private var lastTripEndedAt: Date?
    private var statusObservers: [UUID: AsyncStream<RecorderStatus>.Continuation] = [:]
    private var historyObservers: [UUID: AsyncStream<[TripSummary]>.Continuation] = [:]

    private struct ActiveTrip {
        var id: String
        var source: TripSource
        var startedAt: Date
        var confirmed: Bool
        var counter: SampleCounter
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
        parkedRegion: (any ParkedRegionMonitorBridge)? = nil,
        index: TripIndexStore,
        liveSurface: (any LiveSurfacing)? = nil,
        notifier: (any TripNotifying)? = nil,
        assertions: (any BackgroundAssertions)? = nil,
        autoDetectEnabled: Bool = true,
        now: @escaping @MainActor () -> Date = { Date() }
    ) {
        self.config = config
        self.location = location
        self.motion = motion
        self.activity = activity
        self.files = files
        self.parkedRegion = parkedRegion
        self.index = index
        self.liveSurface = liveSurface
        self.notifier = notifier
        self.assertions = assertions
        self.now = now
        status.autoDetectEnabled = autoDetectEnabled
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

    /// Loads history and recovers a trip that was running when the app died (§1.7). The parked region needs no
    /// re-arming here: `CLMonitor` keeps its condition across launches and the platform monitor restores it.
    func onLaunch() {
        history = index.loadSummaries()
        lastTripEndedAt = history.first?.endedAt
        recoverInterruptedTrip()
    }

    func onWake(_ event: WakeEvent) {
        let wokeAt = Date(wallMs: event.wallMs)
        if event.kind == .manual {
            startManual(wokeAt: wokeAt)
            return
        }
        // Stale events are dropped (spike change 8); the platform filter already removes most of them.
        guard active == nil, status.autoDetectEnabled,
            now().timeIntervalSince(wokeAt) <= config.maxWakeAgeS
        else { return }
        begin(source: .auto, wokeAt: wokeAt)
    }

    func stop() {
        guard active?.confirmed == true else { return }
        finish(endedAt: now())
    }

    /// Passenger mode is outside the shell (DRIVE-67): keeping finishes the trip as recorded, discarding deletes it.
    func markPassenger(keep: Bool) {
        guard let trip = active, trip.confirmed else { return }
        if keep {
            finish(endedAt: now())
        } else {
            discardActive()
            liveSurface?.end()
            notifier?.removeTripNotifications(tripId: trip.id)
        }
    }

    func setAutoDetect(enabled: Bool) {
        status.autoDetectEnabled = enabled
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

    func delete(tripId: String) {
        _ = files.deleteTrip(tripId: tripId)
        index.remove(tripId: tripId)
        history = index.loadSummaries()
    }

    // MARK: Trip loop

    private func startManual(wokeAt: Date) {
        if let trip = active {
            guard !trip.confirmed else { return }
            // A manual start during a candidate confirms it as a manual trip from the wake.
            active?.source = .manual
            confirm()
            liveSurface?.start(tripId: trip.id, startedAt: trip.startedAt)
            return
        }
        begin(source: .manual, wokeAt: wokeAt)
        if let active { liveSurface?.start(tripId: active.id, startedAt: active.startedAt) }
    }

    private func begin(source: TripSource, wokeAt: Date) {
        let id = UUID().uuidString.lowercased()
        // The first write decides whether a trip can start: before the first unlock the store says `unavailable`.
        guard files.append(tripId: id, kind: .r0, bytes: R0Lines.header(tripId: id, source: source)) == .ok else {
            _ = files.deleteTrip(tripId: id)
            return
        }
        assertions?.begin()
        let stopWindow = source == .manual ? config.manualStopS : config.autoStopS
        let counter = SampleCounter()
        active = ActiveTrip(
            id: id, source: source, startedAt: wokeAt, confirmed: source == .manual, counter: counter,
            candidate: source == .auto ? CandidateDecider(wokeAt: wokeAt, config: config) : nil,
            stop: StopDetector(startedAt: wokeAt, stopWindowS: stopWindow, movingSpeedMps: config.movingSpeedMps),
            distance: DistanceAccumulator(config: config), lastCheckpointAt: wokeAt)
        startSampling(tripId: id, counter: counter)
        if source == .auto { queryActivityHistory(until: wokeAt) }
        publish()
        checkpoint()
    }

    private func startSampling(tripId: String, counter: SampleCounter) {
        let files = files
        location.start { [weak self] samples in
            deliverOnMain { self?.ingest(samples, tripId: tripId) }
        }
        // Motion stays on the motion queue: encode and append there, never on the main actor (spike change 6).
        motion.start(rateHz: config.imuHz) { samples in
            guard !samples.isEmpty,
                files.append(tripId: tripId, kind: .r0, bytes: R0Lines.encode(samples)) == .ok
            else { return }
            counter.add(motion: samples.count)
        }
        activity.start { [weak self] samples in
            deliverOnMain { self?.ingest(samples, tripId: tripId) }
        }
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
        guard let tripId = active?.id else { return }
        var from = wokeAt.addingTimeInterval(-config.activityHistoryWindowS)
        if let lastTripEndedAt, lastTripEndedAt > from { from = lastTripEndedAt }
        activity.queryHistory(fromWallMs: from.wallMs, toWallMs: wokeAt.wallMs) { [weak self] samples in
            deliverOnMain { self?.ingest(samples, tripId: tripId) }
        }
    }

    private func ingest(_ samples: [LocationSample], tripId: String) {
        guard var trip = active, trip.id == tripId else { return }
        // A stream's first fix can be cached from before the wake (spike change 8).
        let earliest = trip.startedAt.addingTimeInterval(-config.wakeFixToleranceS).wallMs
        let fresh = samples.filter { $0.wallMs >= earliest }
        guard !fresh.isEmpty else { return }
        if files.append(tripId: tripId, kind: .r0, bytes: R0Lines.encode(fresh)) == .ok {
            trip.counter.add(location: fresh.count)
        }
        for sample in fresh {
            trip.distance.ingest(sample)
            let time = Date(wallMs: sample.wallMs)
            trip.candidate?.observe(speedMps: sample.speedMps, accuracyM: sample.hAccM, at: time)
            trip.stop.observe(speedMps: sample.speedMps, at: time)
        }
        let firstFix = !trip.hasFix
        trip.hasFix = true
        active = trip
        if firstFix { assertions?.end() }
        publish()
        if trip.confirmed, trip.source == .manual {
            liveSurface?.update(distanceM: trip.distance.distanceM, hasFix: true)
        }
    }

    private func ingest(_ samples: [ActivitySample], tripId: String) {
        guard active?.id == tripId, !samples.isEmpty else { return }
        if files.append(tripId: tripId, kind: .r0, bytes: R0Lines.encode(samples)) == .ok {
            active?.counter.add(activity: samples.count)
        }
        for sample in samples.sorted(by: { $0.wallMs < $1.wallMs }) {
            active?.candidate?.observe(sample)
            active?.stop.observe(sample)
        }
    }

    /// One tick of the trip loop (every second while sampling). Internal so tests drive it with a test clock.
    func evaluate() {
        guard let trip = active else { return }
        let time = now()
        if let candidate = trip.candidate, !trip.confirmed {
            switch candidate.decide(now: time) {
            case .pending: break
            case .confirmed: confirm()
            case .rejected, .timedOut:
                discardActive()
                return
            }
        } else if trip.stop.hasEnded(now: time) {
            finish(endedAt: trip.source == .auto ? trip.stop.lastMovingAt : time)
            return
        }
        publish()
        if time.timeIntervalSince(trip.lastCheckpointAt) >= config.checkpointS { checkpoint() }
    }

    private func confirm() {
        guard var trip = active else { return }
        trip.confirmed = true
        trip.candidate = nil
        active = trip
        if trip.source == .auto { notifier?.postAutoTripRecording(tripId: trip.id) }
        publish()
        checkpoint()
    }

    /// CANDIDATE → IDLE (or a discarded passenger trip): stop and delete the samples now (§1.4).
    private func discardActive() {
        guard let trip = active else { return }
        stopSampling()
        _ = files.deleteTrip(tripId: trip.id)
        index.clearCheckpoint()
        active = nil
        publish()
    }

    private func finish(endedAt: Date) {
        guard let trip = active else { return }
        status.state = .finishing
        stopSampling()
        _ = files.sync(tripId: trip.id, kind: .r0)
        let counts = trip.counter.counts
        liveSurface?.end()
        notifier?.removeTripNotifications(tripId: trip.id)
        if let spot = trip.distance.lastUsable {
            parkedRegion?.arm(center: GeoPoint(lat: spot.lat, lng: spot.lng), radiusM: config.parkedRadiusM)
        }
        index.clearCheckpoint()
        active = nil
        lastTripEndedAt = endedAt
        let duration = max(endedAt.timeIntervalSince(trip.startedAt), 0)
        switch classifyTrip(
            source: trip.source, distanceM: trip.distance.distanceM, durationS: duration, config: config)
        {
        case .discard:
            _ = files.deleteTrip(tripId: trip.id)
        case .keep(let tripStatus):
            _ = files.compress(tripId: trip.id, kind: .r0)
            index.append(
                TripSummary(
                    id: trip.id, source: trip.source, status: tripStatus, startedAt: trip.startedAt,
                    endedAt: endedAt, distanceM: trip.distance.distanceM, samples: counts))
            history = index.loadSummaries()
        }
        publish()
    }

    private func checkpoint() {
        guard var trip = active else { return }
        _ = files.sync(tripId: trip.id, kind: .r0)
        trip.lastCheckpointAt = now()
        active = trip
        index.save(
            TripIndexStore.Checkpoint(
                tripId: trip.id, source: trip.source, confirmed: trip.confirmed, startedAt: trip.startedAt,
                lastCheckpointAt: trip.lastCheckpointAt, distanceM: trip.distance.distanceM,
                samples: trip.counter.counts))
    }

    // MARK: Recovery (§1.7)

    private func recoverInterruptedTrip() {
        guard active == nil, let saved = index.loadCheckpoint() else { return }
        let age = now().timeIntervalSince(saved.lastCheckpointAt)
        guard saved.confirmed else {
            // A candidate that died before confirmation is not a trip.
            _ = files.deleteTrip(tripId: saved.tripId)
            index.clearCheckpoint()
            return
        }
        // Resuming appends to the existing R0; the store reports `unavailable` if it cannot be reopened (locked).
        if age <= config.resumeWindowS,
            files.append(tripId: saved.tripId, kind: .r0, bytes: R0Lines.resumed(at: now())) == .ok
        {
            let stopWindow = saved.source == .manual ? config.manualStopS : config.autoStopS
            let counter = SampleCounter(saved.samples)
            active = ActiveTrip(
                id: saved.tripId, source: saved.source, startedAt: saved.startedAt, confirmed: true, counter: counter,
                candidate: nil,
                stop: StopDetector(startedAt: now(), stopWindowS: stopWindow, movingSpeedMps: config.movingSpeedMps),
                distance: DistanceAccumulator(config: config, distanceM: saved.distanceM),
                lastCheckpointAt: now())
            startSampling(tripId: saved.tripId, counter: counter)
            if saved.source == .manual { liveSurface?.start(tripId: saved.tripId, startedAt: saved.startedAt) }
            publish()
            return
        }
        index.append(
            TripSummary(
                id: saved.tripId, source: saved.source, status: .interrupted, startedAt: saved.startedAt,
                endedAt: saved.lastCheckpointAt, distanceM: saved.distanceM, samples: saved.samples))
        index.clearCheckpoint()
        history = index.loadSummaries()
    }

    // MARK: Publishing

    private func publish() {
        guard let trip = active else {
            status = RecorderStatus(
                state: .idle, tripId: nil, isManual: false, elapsedS: 0, distanceM: 0,
                autoDetectEnabled: status.autoDetectEnabled)
            return
        }
        status = RecorderStatus(
            state: trip.confirmed ? .recording : .candidate, tripId: trip.id, isManual: trip.source == .manual,
            elapsedS: max(now().timeIntervalSince(trip.startedAt), 0).rounded(.down),
            distanceM: trip.distance.distanceM, autoDetectEnabled: status.autoDetectEnabled)
    }
}

/// Sample counts, updated from the motion queue and the main actor.
final class SampleCounter: @unchecked Sendable {
    private let lock = NSLock()
    private var value: SampleCounts

    init(_ initial: SampleCounts = SampleCounts()) {
        value = initial
    }

    var counts: SampleCounts { lock.withLock { value } }

    func add(location: Int = 0, motion: Int = 0, activity: Int = 0) {
        lock.withLock {
            value.location += location
            value.motion += motion
            value.activity += activity
        }
    }
}

/// Port sinks may be called on any thread; the recorder runs on the main actor. Queued in call order.
func deliverOnMain(_ work: @escaping @MainActor @Sendable () -> Void) {
    if Thread.isMainThread {
        MainActor.assumeIsolated(work)
    } else {
        DispatchQueue.main.async { MainActor.assumeIsolated(work) }
    }
}
