import CoreLocation
import DPPlatform
import DPSharedBridge
import Foundation

/// On-device self-test of the full trip path (spike change 10): three of Spike 1's four bugs were invisible to unit
/// tests and only showed on the phone. It runs the real adapters and a throw-away recorder with short timers,
/// in a separate folder, so no trip of the user's is touched. Results hold counts, rates and timings, never a
/// location. Run it from Profile › Diagnostics, or launch with `-selfTest` from a Mac (README).
@MainActor
final class SelfTestRunner: SelfTesting {
    private unowned let environment: AppEnvironment
    private var steps: [SelfTestStep] = []
    private var continuation: AsyncStream<[SelfTestStep]>.Continuation?

    init(environment: AppEnvironment) {
        self.environment = environment
    }

    func run() -> AsyncStream<[SelfTestStep]> {
        AsyncStream { continuation in
            self.continuation = continuation
            let task = Task { await self.runAll() }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    private func runAll() async {
        steps = Self.plan
        publish()
        guard !environment.recorder.snapshot.isDriving else {
            for index in steps.indices { steps[index].state = .skipped("A drive is recording") }
            finish()
            return
        }
        await step("storage") {
            self.environment.files.isWritable()
                ? .passed("Trip files writable (unlocked since boot)") : .failed("Trip files are locked")
        }
        await step("monitorName") {
            MonitorNaming.isValid(WakeTriggerMonitor.monitorName)
                ? .passed("“\(WakeTriggerMonitor.monitorName)” is alphanumeric") : .failed("Invalid CLMonitor name")
        }
        await step("permissions") {
            let snapshot = await self.environment.permissions.current()
            guard snapshot.location.canRecord else { return .failed("Location is \(snapshot.location.rawValue)") }
            return .passed(
                "Location \(snapshot.location.rawValue), precise \(snapshot.preciseLocation ? "on" : "off"), "
                    + "motion \(snapshot.motion.rawValue)")
        }
        await step("triggers") {
            guard self.environment.preferences.preferences.autoTripsEnabled else { return .skipped("Manual mode") }
            return self.environment.triggers.isParkedRegionArmed
                ? .passed("Parked region armed") : .skipped("No parking spot yet (armed after the first trip)")
        }
        await step("staleWake") {
            let filter = WakeFilter()
            let now = SystemClock().nowWallMs()
            let stale = filter.accepts(
                source: .significantChange, eventWallMs: now - 600_000, nowWallMs: now, processStartWallMs: 0,
                appInForeground: false)
            return stale ? .failed("A 10-minute-old fix was accepted") : .passed("10-minute-old fix dropped")
        }
        // The self-test never shows a permission prompt: steps that need one are skipped until onboarding granted it.
        let granted = await environment.permissions.current()
        let needsLocation = SelfTestState.skipped("Needs location access (finish onboarding first)")
        await step("gps") { granted.location.canRecord ? await Self.checkGPS() : needsLocation }
        await step("motion") { await Self.checkMotion() }
        await step("activity") {
            granted.motion == .authorized
                ? await Self.checkActivityHistory() : .skipped("Needs Motion & Fitness access")
        }
        await step("wakePath") { granted.location.canRecord ? await self.checkWakePath() : needsLocation }
        await step("manualPath") { granted.location.canRecord ? await self.checkManualPath() : needsLocation }
        finish()
    }

    private static let plan: [SelfTestStep] = [
        SelfTestStep(id: "storage", title: "Storage after first unlock"),
        SelfTestStep(id: "monitorName", title: "CLMonitor name"),
        SelfTestStep(id: "permissions", title: "Permissions"),
        SelfTestStep(id: "triggers", title: "Wake triggers"),
        SelfTestStep(id: "staleWake", title: "Stale wake filter"),
        SelfTestStep(id: "gps", title: "GPS fix"),
        SelfTestStep(id: "motion", title: "Motion at 50 Hz, off the main thread"),
        SelfTestStep(id: "activity", title: "Motion activity history"),
        SelfTestStep(id: "wakePath", title: "Wake → candidate records → rejected"),
        SelfTestStep(id: "manualPath", title: "Manual start → record → stop"),
    ]

    private func step(_ id: String, _ body: @MainActor () async -> SelfTestState) async {
        guard let index = steps.firstIndex(where: { $0.id == id }), !Task.isCancelled else { return }
        steps[index].state = .running
        publish()
        steps[index].state = await body()
        publish()
    }

    private func publish() {
        continuation?.yield(steps)
    }

    private func finish() {
        SelfTestReport.write(steps)
        continuation?.finish()
        continuation = nil
    }

    // MARK: Checks

    private static func checkGPS() async -> SelfTestState {
        let source = CoreLocationSource()
        let started = Date()
        let result: (TimeInterval, Double?)? = await withCheckedContinuation { continuation in
            var done = false
            let finish: @MainActor ((TimeInterval, Double?)?) -> Void = { value in
                guard !done else { return }
                done = true
                source.stop()
                continuation.resume(returning: value)
            }
            source.start(
                notBeforeWallMs: SystemClock().nowWallMs(),
                onSample: { sample in finish((Date().timeIntervalSince(started), sample.hAccM)) },
                onStationary: { _ in }, onIssue: { _ in })
            Task { @MainActor in
                try? await Task.sleep(for: .seconds(30))
                finish(nil)
            }
        }
        guard let (latency, accuracy) = result else { return .failed("No fix in 30 s (go outside or near a window)") }
        let bucket = accuracy.map { $0 <= 20 ? "≤ 20 m" : $0 <= 50 ? "≤ 50 m" : "> 50 m" } ?? "unknown"
        return .passed(String(format: "First fix after %.1f s, accuracy %@", latency, bucket))
    }

    private static func checkMotion() async -> SelfTestState {
        let source = CoreMotionSource()
        guard source.isAvailable else { return .failed("Device motion unavailable") }
        let probe = MotionProbe()
        source.start(rateHz: 50) { @Sendable sample in probe.record(sample, onMain: Thread.isMainThread) }
        try? await Task.sleep(for: .seconds(5))
        source.stop()
        let result = probe.result
        let rate = Double(result.count) / 5
        guard result.count > 0 else { return .failed("No samples") }
        guard !result.anyOnMain else { return .failed("Callback ran on the main thread") }
        let gravity = String(format: "%.3f", result.meanGravityNorm)
        return rate >= 45
            ? .passed(String(format: "%.1f Hz, |gravity| %@ g, gravityUp.z %.2f", rate, gravity, result.lastGravityUpZ))
            : .failed(String(format: "Only %.1f Hz", rate))
    }

    private static func checkActivityHistory() async -> SelfTestState {
        let source = CoreMotionActivitySource()
        guard source.isAvailable else { return .skipped("Motion activity unavailable") }
        let now = SystemClock().nowWallMs()
        let samples: [ActivitySample] = await withCheckedContinuation { continuation in
            source.queryHistory(fromWallMs: now - 900_000, toWallMs: now) { continuation.resume(returning: $0) }
        }
        return .passed("\(samples.count) activity changes in the last 15 min")
    }

    /// A synthetic `MOVE_SIGNAL` wake into a throw-away recorder: it must record from the wake (fixes and motion
    /// before any confirmation), then reject on the short timeout and delete its samples.
    private func checkWakePath() async -> SelfTestState {
        let (recorder, files, root) = makeScratchRecorder(candidateTimeoutS: 8)
        defer { try? FileManager.default.removeItem(at: root) }
        recorder.updateAuthorization(always: true, autoDetectEnabled: true)
        let wall = SystemClock().nowWallMs()
        recorder.handle(WakeEvent(kind: .moveSignal, eventWallMs: wall, receivedWallMs: wall))
        guard recorder.snapshot.phase == .candidate, let tripId = recorder.snapshot.live?.id else {
            return .failed("Wake did not open a candidate")
        }
        try? await Task.sleep(for: .seconds(4))
        let size = files.sizeBytes(tripId: tripId)
        try? await Task.sleep(for: .seconds(6))
        guard recorder.snapshot.phase == .idle else { return .failed("Candidate did not time out") }
        guard files.sizeBytes(tripId: tripId) == 0 else { return .failed("Rejected samples were not deleted") }
        return size > 0
            ? .passed("Recorded \(size / 1024) KB before confirmation, then rejected and deleted")
            : .failed("No samples written after the wake")
    }

    private func checkManualPath() async -> SelfTestState {
        let (recorder, _, root) = makeScratchRecorder(candidateTimeoutS: 8)
        defer { try? FileManager.default.removeItem(at: root) }
        do {
            try recorder.startManualTrip()
        } catch {
            return .failed("Start failed: \(error)")
        }
        try? await Task.sleep(for: .seconds(6))
        guard let summary = await recorder.stopTrip() else { return .failed("Stop returned no trip") }
        guard summary.samples.motion > 0 else { return .failed("No motion samples saved") }
        return .passed(
            "Saved as “\(summary.status.rawValue)”: \(summary.samples.motion) motion, "
                + "\(summary.samples.location) location samples")
    }

    private func makeScratchRecorder(candidateTimeoutS: TimeInterval) -> (InterimTripRecorder, FileTripStore, URL) {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("selftest-\(UUID().uuidString)")
        var config = TripRulesConfig()
        config.candidateTimeoutS = candidateTimeoutS
        config.checkpointS = 1
        let files = FileTripStore(root: root)
        let recorder = InterimTripRecorder(
            config: config, location: CoreLocationSource(), motion: CoreMotionSource(),
            activity: CoreMotionActivitySource(), files: files, index: TripIndexStore(root: root))
        return (recorder, files, root)
    }
}

/// Collects motion statistics from Core Motion's queue.
private final class MotionProbe: @unchecked Sendable {
    struct Result {
        var count = 0
        var gravityNormSum = 0.0
        var anyOnMain = false
        var lastGravityUpZ = 0.0
        var meanGravityNorm: Double { count == 0 ? 0 : gravityNormSum / Double(count) }
    }

    private let lock = NSLock()
    private var value = Result()

    func record(_ sample: MotionSample, onMain: Bool) {
        lock.withLock {
            value.count += 1
            value.gravityNormSum += sample.gravityUp.norm
            value.lastGravityUpZ = sample.gravityUp.z
            value.anyOnMain = value.anyOnMain || onMain
        }
    }

    var result: Result { lock.withLock { value } }
}

/// Writes the last result to the app's Documents folder (timings and counts only), so it can be pulled with
/// `xcrun devicectl device copy from --domain-type appDataContainer`.
enum SelfTestReport {
    static func write(_ steps: [SelfTestStep]) {
        let lines = steps.map { step -> String in
            switch step.state {
            case .passed(let detail): "PASS  \(step.title): \(detail)"
            case .failed(let detail): "FAIL  \(step.title): \(detail)"
            case .skipped(let detail): "SKIP  \(step.title): \(detail)"
            case .pending, .running: "----  \(step.title)"
            }
        }
        let text = "DrivePerf self-test \(Date().ISO8601Format())\n" + lines.joined(separator: "\n") + "\n"
        let url = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("self-test.txt")
        try? text.write(to: url, atomically: true, encoding: .utf8)
    }
}
