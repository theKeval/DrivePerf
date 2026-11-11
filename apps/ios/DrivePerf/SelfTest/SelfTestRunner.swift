import CoreLocation
import DPPlatform
import DPSharedBridge
import DrivePerfShared
import Foundation

/// On-device self-test of the full trip path (spike change 10): three of Spike 1's four bugs were invisible to unit
/// tests and only showed on the phone. It runs the real adapters, through the Kotlin port types the shared core calls
/// (`CorePortChecks`), and a throw-away `DrivePerfCore` with a short candidate timeout, on its own database and
/// folder, so no trip of the user's is touched. Results hold counts, rates and timings, never a
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
        guard !environment.recorder.status.isDriving else {
            for index in steps.indices { steps[index].state = .skipped("A drive is recording") }
            finish()
            return
        }
        await step("storage") {
            self.environment.files.isUnlockedSinceBoot()
                ? .passed("Trip files writable (unlocked since boot)") : .failed("Trip files are locked")
        }
        await step("corePorts") { await CorePortChecks.checkPorts() }
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
            let now = SystemClock().wallMs()
            let stale = filter.accepts(
                source: .significantChange, eventWallMs: now - 600_000, nowWallMs: now, processStartWallMs: 0,
                appInForeground: false)
            return stale ? .failed("A 10-minute-old fix was accepted") : .passed("10-minute-old fix dropped")
        }
        // The self-test never shows a permission prompt: steps that need one are skipped until onboarding granted it.
        let granted = await environment.permissions.current()
        let needsLocation = SelfTestState.skipped("Needs location access (finish onboarding first)")
        await step("gps") { granted.location.canRecord ? await CorePortChecks.checkGPS() : needsLocation }
        await step("motion") { await CorePortChecks.checkMotion() }
        await step("activity") {
            granted.motion == .authorized
                ? await CorePortChecks.checkActivityHistory() : .skipped("Needs Motion & Fitness access")
        }
        await step("wakePath") { granted.location.canRecord ? await self.checkWakePath() : needsLocation }
        await step("manualPath") { granted.location.canRecord ? await self.checkManualPath() : needsLocation }
        finish()
    }

    private static let plan: [SelfTestStep] = [
        SelfTestStep(id: "storage", title: "Storage after first unlock"),
        SelfTestStep(id: "corePorts", title: "Shared core ports (Kotlin types)"),
        SelfTestStep(id: "monitorName", title: "CLMonitor name"),
        SelfTestStep(id: "permissions", title: "Permissions"),
        SelfTestStep(id: "triggers", title: "Wake triggers"),
        SelfTestStep(id: "staleWake", title: "Stale wake filter"),
        SelfTestStep(id: "gps", title: "GPS fix"),
        SelfTestStep(id: "motion", title: "Motion at 50 Hz, off the main thread"),
        SelfTestStep(id: "activity", title: "Motion activity history"),
        SelfTestStep(id: "wakePath", title: "Wake → candidate records → rejected"),
        SelfTestStep(id: "manualPath", title: "Manual start → record → stop (DrivePerfCore)"),
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

    /// A synthetic `MOVE_SIGNAL` wake into a throw-away core: it must record from the wake (samples in R0 before any
    /// confirmation), then reject on the short timeout and delete them.
    private func checkWakePath() async -> SelfTestState {
        let scratch = ScratchCore(candidateTimeoutS: 8)
        defer { scratch.close() }
        let clock = SystemClock()
        scratch.core.onWake(DPSharedBridge.WakeEvent(kind: .moveSignal, tMonoNs: clock.monotonicNs(), wallMs: clock.wallMs()))
        guard let tripId = await scratch.waitFor(seconds: 5, { $0.state == .candidate })?.tripId else {
            return .failed("Wake did not open a candidate")
        }
        try? await Task.sleep(for: .seconds(6))
        let size = scratch.files.describe(tripId: tripId, kind: .r0, withSha256: false)?.sizeBytes ?? 0
        guard await scratch.waitFor(seconds: 10, { $0.state == .idle }) != nil else {
            return .failed("Candidate did not time out")
        }
        guard scratch.files.tripIds().isEmpty else { return .failed("Rejected samples were not deleted") }
        return size > 0
            ? .passed("Recorded \(size / 1024) KB of R0 before confirmation, then rejected and deleted")
            : .failed("No samples written after the wake")
    }

    /// A manual trip through the whole core: record, stop, the pipeline, and the commit with R0 and R1 gzipped.
    private func checkManualPath() async -> SelfTestState {
        let scratch = ScratchCore(candidateTimeoutS: 8)
        defer { scratch.close() }
        let clock = SystemClock()
        scratch.core.onWake(
            DPSharedBridge.WakeEvent(kind: .manual, tMonoNs: clock.monotonicNs(), wallMs: clock.wallMs(), sourceDetail: "app"))
        guard let tripId = await scratch.waitFor(seconds: 5, { $0.state == .recording })?.tripId else {
            return .failed("Manual start did not record")
        }
        try? await Task.sleep(for: .seconds(8))
        scratch.core.stop()
        guard let summary = await scratch.waitForTrip(seconds: 20) else { return .failed("Stop saved no trip") }
        guard summary.id == tripId, summary.source == .manual else { return .failed("The saved trip is not the manual one") }
        let files = scratch.files
        guard let r0 = files.describe(tripId: tripId, kind: .r0, withSha256: false), r0.compressed else {
            return .failed("R0 was not compressed at the end")
        }
        guard let r1 = files.describe(tripId: tripId, kind: .r1, withSha256: false), r1.compressed else {
            return .failed("No gzipped R1 analysis file")
        }
        return .passed(
            "Saved as “\(summary.status.rawValue)” after \(Int(summary.duration)) s: R0 \(r0.sizeBytes / 1024) KB, "
                + "R1 \(r1.sizeBytes / 1024) KB, both gzipped")
    }
}

/// A throw-away `DrivePerfCore` for the self-test: its own trip folder and database, real sensors, no wake triggers.
@MainActor
private final class ScratchCore {
    let files: FileTripStore
    let core: KotlinCore
    private let services: CoreServices
    private let root: URL
    private let databaseName = "selftest-\(UUID().uuidString).db"

    init(candidateTimeoutS: Double) {
        root = FileManager.default.temporaryDirectory.appendingPathComponent("selftest-\(UUID().uuidString)")
        files = FileTripStore(root: root)
        let ports = CorePorts(
            clock: SystemClock(), location: CoreLocationSource(), motion: CoreMotionSource(),
            activity: CoreMotionActivitySource(), power: SystemPowerState(), parkedRegion: NoParkedRegion(),
            tripFiles: files)
        services = DrivePerfCore.shared.startScratch(
            platform: CorePlatformBindings(ports: ports, deviceId: "selftest"), databaseName: databaseName,
            candidateTimeoutS: candidateTimeoutS)
        core = KotlinCore(services)
    }

    /// The first status matching [predicate] within [seconds], or nil.
    func waitFor(seconds: Double, _ predicate: @escaping @Sendable (DPSharedBridge.RecorderStatus) -> Bool) async -> DPSharedBridge.RecorderStatus? {
        await first(seconds: seconds, in: core.statusUpdates(), where: predicate)
    }

    /// The first finished trip within [seconds], or nil.
    func waitForTrip(seconds: Double) async -> TripSummary? {
        await first(seconds: seconds, in: core.tripUpdates(), where: { !$0.isEmpty })?.first
    }

    func close() {
        services.close()
        try? FileManager.default.removeItem(at: root)
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        for suffix in ["", "-wal", "-shm", "-journal"] {
            try? FileManager.default.removeItem(at: support.appendingPathComponent("databases/\(databaseName)\(suffix)"))
        }
    }

    private func first<T: Sendable>(
        seconds: Double, in stream: AsyncStream<T>, where predicate: @escaping @Sendable (T) -> Bool
    ) async -> T? {
        await withTaskGroup(of: T?.self) { group in
            group.addTask {
                for await value in stream where predicate(value) { return value }
                return nil
            }
            group.addTask {
                try? await Task.sleep(for: .seconds(seconds))
                return nil
            }
            let result = await group.next() ?? nil
            group.cancelAll()
            return result
        }
    }
}

/// The self-test never arms a real wake region.
private final class NoParkedRegion: ParkedRegionMonitorBridge, @unchecked Sendable {
    func arm(center: DPSharedBridge.GeoPoint, radiusM: Double) {}
    func disarm() {}
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
