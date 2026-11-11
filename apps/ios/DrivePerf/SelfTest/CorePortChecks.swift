import DPPlatform
import DPSharedBridge
import DrivePerfShared
import Foundation

/// Self-test checks that drive the platform adapters through the Kotlin port types (`CorePorts`), exactly as the
/// shared core will call them: Kotlin sinks and callbacks, Kotlin samples, `KotlinByteArray` bytes. Results hold
/// counts, rates and timings only, never a location.
@MainActor
enum CorePortChecks {
    /// `Clock`, `PowerState`, `MotionSource.capability` and a `TripFileStore` round trip in a scratch folder.
    static func checkPorts() async -> SelfTestState {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("selftest-ports-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: root) }
        let clock = KotlinClockPort(SystemClock())
        let files = KotlinTripFilePort(FileTripStore(root: root))
        let power = KotlinPowerPort(SystemPowerState())
        let motion = KotlinMotionPort(CoreMotionSource())

        let mono0 = clock.monotonicNs()
        try? await Task.sleep(for: .milliseconds(50))
        let elapsedMs = (clock.monotonicNs() - mono0) / 1_000_000
        guard (40...1000).contains(elapsedMs), abs(clock.wallMs() - Date().wallMs) < 1000 else {
            return .failed("Clock: monotonic or wall time is off")
        }

        let payload = Data((0..<20_000).map { UInt8(truncatingIfNeeded: $0 &* 31) })
        let tripId = "selftest"
        guard files.append(tripId: tripId, kind: .r0, bytes: KotlinMapping.kotlin(payload.prefix(12_000))) == .ok,
            files.append(tripId: tripId, kind: .r0, bytes: KotlinMapping.kotlin(payload.suffix(8_000))) == .ok,
            files.sync(tripId: tripId, kind: .r0) == .ok,
            files.describe(tripId: tripId, kind: .r0, withSha256: false)?.sizeBytes == Int64(payload.count),
            files.compress(tripId: tripId, kind: .r0) == .ok,
            let stored = files.describe(tripId: tripId, kind: .r0, withSha256: true), stored.compressed,
            stored.sha256?.count == 64,
            let read = files.read(tripId: tripId, kind: .r0), KotlinMapping.swift(read) == payload,
            files.tripIds() == [tripId],
            files.deleteTrip(tripId: tripId) == .ok, files.tripIds().isEmpty
        else { return .failed("TripFileStore round trip failed") }

        let status = power.current()
        let capability = motion.capability()
        return .passed(
            "Clock, TripFileStore (append, sync, gzip, read, SHA-256), PowerState (thermal \(status.thermal.name.lowercased())), "
                + "MotionSource (gyro \(capability.hasGyroscope ? "yes" : "no"))")
    }

    static func checkGPS() async -> SelfTestState {
        let port = KotlinLocationPort(CoreLocationSource())
        let started = Date()
        let result: (TimeInterval, Double?)? = await withCheckedContinuation { continuation in
            let once = Once()
            let finish: @Sendable ((TimeInterval, Double?)?) -> Void = { value in
                guard once.claim() else { return }
                port.stop()
                continuation.resume(returning: value)
            }
            port.start(
                sink: LocationSinkBlock { samples in
                    guard let first = samples.first else { return }
                    finish((Date().timeIntervalSince(started), first.hAccM?.doubleValue))
                })
            Task {
                try? await Task.sleep(for: .seconds(30))
                finish(nil)
            }
        }
        guard let (latency, accuracy) = result else { return .failed("No fix in 30 s (go outside or near a window)") }
        let bucket = accuracy.map { $0 <= 20 ? "≤ 20 m" : $0 <= 50 ? "≤ 50 m" : "> 50 m" } ?? "unknown"
        return .passed(String(format: "First fix after %.1f s, accuracy %@", latency, bucket))
    }

    static func checkMotion() async -> SelfTestState {
        let source = CoreMotionSource()
        guard source.capability().maxRateHz > 0 else { return .failed("Device motion unavailable") }
        let port = KotlinMotionPort(source)
        let probe = MotionProbe()
        port.start(rateHz: 50, sink: MotionSinkBlock { samples in probe.record(samples, onMain: Thread.isMainThread) })
        try? await Task.sleep(for: .seconds(5))
        port.stop()
        let result = probe.result
        let rate = Double(result.count) / 5
        guard result.count > 0 else { return .failed("No samples") }
        guard !result.anyOnMain else { return .failed("Callback ran on the main thread") }
        guard result.ordered else { return .failed("Samples out of time order") }
        let gravity = String(format: "%.3f", result.meanGravityNorm)
        return rate >= 45
            ? .passed(
                String(
                    format: "%.1f Hz in %d batches, |gravity| %@ g, gravityUp.z %.2f", rate, result.batches, gravity,
                    result.lastGravityUpZ))
            : .failed(String(format: "Only %.1f Hz", rate))
    }

    static func checkActivityHistory() async -> SelfTestState {
        let source = CoreMotionActivitySource()
        guard source.isAvailable else { return .skipped("Motion activity unavailable") }
        let port = KotlinActivityPort(source)
        let now = Date().wallMs
        let count: Int = await withCheckedContinuation { continuation in
            port.queryHistory(
                fromWallMs: now - 900_000, toWallMs: now,
                callback: ActivityHistoryBlock { continuation.resume(returning: $0.count) })
        }
        return .passed("\(count) activity changes in the last 15 min")
    }
}

// MARK: - Kotlin sink objects

private final class LocationSinkBlock: NSObject, LocationSink, @unchecked Sendable {
    private let block: ([DrivePerfShared.LocationSample]) -> Void

    init(_ block: @escaping ([DrivePerfShared.LocationSample]) -> Void) {
        self.block = block
    }

    func onLocations(samples: [DrivePerfShared.LocationSample]) { block(samples) }
}

private final class MotionSinkBlock: NSObject, MotionSink, @unchecked Sendable {
    private let block: ([DrivePerfShared.MotionSample]) -> Void

    init(_ block: @escaping ([DrivePerfShared.MotionSample]) -> Void) {
        self.block = block
    }

    func onMotion(samples: [DrivePerfShared.MotionSample]) { block(samples) }
}

private final class ActivityHistoryBlock: NSObject, ActivityHistoryCallback, @unchecked Sendable {
    private let block: ([DrivePerfShared.ActivitySample]) -> Void

    init(_ block: @escaping ([DrivePerfShared.ActivitySample]) -> Void) {
        self.block = block
    }

    func onResult(samples: [DrivePerfShared.ActivitySample]) { block(samples) }
}

/// True for the first caller only.
private final class Once: @unchecked Sendable {
    private let lock = NSLock()
    private var done = false

    func claim() -> Bool {
        lock.withLock {
            defer { done = true }
            return !done
        }
    }
}

/// Collects motion statistics from Core Motion's queue.
private final class MotionProbe: @unchecked Sendable {
    struct Result {
        var count = 0
        var batches = 0
        var gravityNormSum = 0.0
        var anyOnMain = false
        var ordered = true
        var lastT: Int64 = .min
        var lastGravityUpZ = 0.0
        var meanGravityNorm: Double { count == 0 ? 0 : gravityNormSum / Double(count) }
    }

    private let lock = NSLock()
    private var value = Result()

    func record(_ samples: [DrivePerfShared.MotionSample], onMain: Bool) {
        lock.withLock {
            value.batches += 1
            value.anyOnMain = value.anyOnMain || onMain
            for sample in samples {
                let g = sample.gravityUp
                value.count += 1
                value.gravityNormSum += (g.x * g.x + g.y * g.y + g.z * g.z).squareRoot()
                value.lastGravityUpZ = g.z
                value.ordered = value.ordered && sample.tMonoNs > value.lastT
                value.lastT = sample.tMonoNs
            }
        }
    }

    var result: Result { lock.withLock { value } }
}
