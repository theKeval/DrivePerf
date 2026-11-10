import DPSharedBridge
import Foundation
@testable import DrivePerf

// Test doubles for the ports. Coordinates in these tests are synthetic (null island offsets), never real places.

@MainActor
final class FakeLocationSource: LocationSourceBridge {
    private(set) var isRunning = false
    private(set) var notBeforeWallMs: Int64?
    private var onSample: (@MainActor (LocationSample) -> Void)?

    func start(
        notBeforeWallMs: Int64, onSample: @escaping @MainActor (LocationSample) -> Void,
        onStationary: @escaping @MainActor (Bool) -> Void, onIssue: @escaping @MainActor (LocationIssue) -> Void
    ) {
        isRunning = true
        self.notBeforeWallMs = notBeforeWallMs
        self.onSample = onSample
    }

    func stop() {
        isRunning = false
        onSample = nil
    }

    /// Sends a fix `metresNorth` metres north of 0,0.
    func send(metresNorth: Double, speedMps: Double, at date: Date, accuracyM: Double = 5) {
        onSample?(
            LocationSample(
                tMonoNs: Int64(date.timeIntervalSince1970 * 1e9), wallMs: Int64(date.timeIntervalSince1970 * 1000),
                lat: metresNorth / 111_195, lng: 0, hAccM: accuracyM, speedMps: speedMps))
    }
}

final class FakeMotionSource: MotionSourceBridge, @unchecked Sendable {
    private let lock = NSLock()
    private var handler: (@Sendable (MotionSample) -> Void)?
    private(set) var rates: [Int] = []

    var isAvailable: Bool { true }

    func start(rateHz: Int, onSample: @escaping @Sendable (MotionSample) -> Void) {
        lock.withLock {
            handler = onSample
            rates.append(rateHz)
        }
    }

    func setRate(hz: Int) {}

    func stop() {
        lock.withLock { handler = nil }
    }

    var isRunning: Bool { lock.withLock { handler != nil } }

    func send(count: Int) {
        let handler = lock.withLock { self.handler }
        for index in 0..<count {
            handler?(
                MotionSample(
                    tMonoNs: Int64(index) * 20_000_000, userAccelG: Vec3(x: 0, y: 0, z: 0),
                    gravityUp: Vec3(x: 0, y: 0, z: 1), rotRateDps: Vec3(x: 0, y: 0, z: 0), attitude: nil))
        }
    }
}

@MainActor
final class FakeActivitySource: ActivitySourceBridge {
    var history: [ActivitySample] = []
    private(set) var queriedFromWallMs: Int64?
    private var onActivity: (@MainActor (ActivitySample) -> Void)?

    var isAvailable: Bool { true }

    func start(onActivity: @escaping @MainActor (ActivitySample) -> Void) { self.onActivity = onActivity }
    func stop() { onActivity = nil }

    func queryHistory(
        fromWallMs: Int64, toWallMs: Int64, completion: @escaping @MainActor ([ActivitySample]) -> Void
    ) {
        queriedFromWallMs = fromWallMs
        completion(history)
    }

    func send(_ kind: ActivityKind, confidence: ActivityConfidence = .high, at date: Date) {
        onActivity?(
            ActivitySample(startWallMs: Int64(date.timeIntervalSince1970 * 1000), kind: kind, confidence: confidence))
    }
}

@MainActor
final class FakeWakeTriggers: WakeTriggerBridge {
    private(set) var armedSpots: [(lat: Double, lng: Double, radiusM: Double)] = []
    var isParkedRegionArmed: Bool { !armedSpots.isEmpty }

    func arm(onWake: @escaping @MainActor (WakeEvent) -> Void) {}
    func disarm() {}
    func armParkedRegion(lat: Double, lng: Double, radiusM: Double) { armedSpots.append((lat, lng, radiusM)) }
}

final class MemoryTripFiles: TripFileStoreBridge, @unchecked Sendable {
    final class Writer: TripFileWriter, @unchecked Sendable {
        private let lock = NSLock()
        private var value = SampleCounts()
        private(set) var closed = false

        func append(_ sample: LocationSample) { lock.withLock { value.location += 1 } }
        func append(_ sample: MotionSample) { lock.withLock { value.motion += 1 } }
        func append(_ sample: ActivitySample) { lock.withLock { value.activity += 1 } }
        func flush() {}
        func close() { lock.withLock { closed = true } }
        var counts: SampleCounts { lock.withLock { value } }
    }

    var writable = true
    private(set) var writers: [String: Writer] = [:]
    private(set) var deleted: [String] = []

    func isWritable() -> Bool { writable }

    func open(tripId: String) throws(TripFileError) -> any TripFileWriter {
        guard writable else { throw .storageLocked }
        let writer = writers[tripId] ?? Writer()
        writers[tripId] = writer
        return writer
    }

    func delete(tripId: String) {
        deleted.append(tripId)
        writers[tripId] = nil
    }

    func sizeBytes(tripId: String) -> Int64 { writers[tripId] == nil ? 0 : 1 }
}

@MainActor
final class SpyLiveSurface: LiveSurfacing {
    private(set) var started: [String] = []
    private(set) var ended = 0

    func start(tripId: String, startedAt: Date) { started.append(tripId) }
    func update(distanceM: Double, hasFix: Bool) {}
    func end() { ended += 1 }
}

@MainActor
final class SpyNotifier: TripNotifying {
    private(set) var posted: [String] = []

    func postAutoTripRecording(tripId: String) { posted.append(tripId) }
    func removeTripNotifications(tripId: String) {}
}

/// A controllable clock for the recorder.
@MainActor
final class TestClock {
    var now: Date

    init(_ now: Date = Date(timeIntervalSince1970: 1_800_000_000)) {
        self.now = now
    }

    func advance(_ seconds: TimeInterval) {
        now = now.addingTimeInterval(seconds)
    }
}

func makeTempDirectory() -> URL {
    let url = FileManager.default.temporaryDirectory.appendingPathComponent("dp-tests-\(UUID().uuidString)")
    try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
    return url
}
