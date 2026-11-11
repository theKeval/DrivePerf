import DPSharedBridge
import Foundation
@testable import DrivePerf

// Test doubles for the ports (the Swift mirrors of the Kotlin ones). Sinks are called synchronously on the test's
// main actor. Coordinates in these tests are synthetic (null island offsets), never real places.

final class FakeLocationSource: LocationSourceBridge, @unchecked Sendable {
    private let lock = NSLock()
    private var sink: (@Sendable ([LocationSample]) -> Void)?

    func start(sink: @escaping @Sendable ([LocationSample]) -> Void) { lock.withLock { self.sink = sink } }
    func stop() { lock.withLock { sink = nil } }

    var isRunning: Bool { lock.withLock { sink != nil } }

    /// Sends a fix `metresNorth` metres north of 0,0.
    func send(metresNorth: Double, speedMps: Double, at date: Date, accuracyM: Double = 5) {
        let sink = lock.withLock { self.sink }
        sink?([
            LocationSample(
                tMonoNs: Int64(date.timeIntervalSince1970 * 1e9), wallMs: date.wallMs, lat: metresNorth / 111_195,
                lng: 0, hAccM: accuracyM, speedMps: speedMps)
        ])
    }
}

final class FakeMotionSource: MotionSourceBridge, @unchecked Sendable {
    private let lock = NSLock()
    private var sink: (@Sendable ([MotionSample]) -> Void)?
    private(set) var rates: [Int] = []

    func capability() -> MotionCapability { MotionCapability(hasGyroscope: true, maxRateHz: 100) }

    func start(rateHz: Int, sink: @escaping @Sendable ([MotionSample]) -> Void) {
        lock.withLock {
            self.sink = sink
            rates.append(rateHz)
        }
    }

    func setRate(rateHz: Int) {}

    func stop() {
        lock.withLock { sink = nil }
    }

    func queryRecordedAccelerometer(
        fromWallMs: Int64, toWallMs: Int64, callback: @escaping @Sendable ([RecordedAccelSample]) -> Void
    ) {
        callback([])
    }

    var isRunning: Bool { lock.withLock { sink != nil } }

    func send(count: Int) {
        let sink = lock.withLock { self.sink }
        sink?(
            (0..<count).map { index in
                MotionSample(
                    tMonoNs: Int64(index) * 20_000_000, userAccelG: Vec3(x: 0, y: 0, z: 0),
                    gravityUp: Vec3(x: 0, y: 0, z: 1), rotRateDps: Vec3(x: 0, y: 0, z: 0), attitude: nil)
            })
    }
}

final class FakeActivitySource: ActivitySourceBridge, @unchecked Sendable {
    private let lock = NSLock()
    private var sink: (@Sendable ([ActivitySample]) -> Void)?
    var history: [ActivitySample] = []
    private(set) var queriedFromWallMs: Int64?

    func start(sink: @escaping @Sendable ([ActivitySample]) -> Void) { lock.withLock { self.sink = sink } }
    func stop() { lock.withLock { sink = nil } }

    func queryHistory(fromWallMs: Int64, toWallMs: Int64, callback: @escaping @Sendable ([ActivitySample]) -> Void) {
        queriedFromWallMs = fromWallMs
        callback(history)
    }

    func send(_ type: ActivityType, confidence: ActivityConfidence = .high, at date: Date) {
        let sink = lock.withLock { self.sink }
        sink?([ActivitySample(tMonoNs: 0, wallMs: date.wallMs, type: type, confidence: confidence)])
    }
}

final class SpyParkedRegion: ParkedRegionMonitorBridge, @unchecked Sendable {
    private(set) var armed: [(center: GeoPoint, radiusM: Double)] = []

    func arm(center: GeoPoint, radiusM: Double) { armed.append((center, radiusM)) }
    func disarm() { armed = [] }
}

/// `TripFileStore` in memory. `available = false` answers every write with `unavailable`, as the real store does
/// before the first unlock.
final class MemoryTripFiles: TripFileStoreBridge, @unchecked Sendable {
    private let lock = NSLock()
    private var files: [String: Data] = [:]
    private var compressedKeys: Set<String> = []
    var available = true
    private(set) var deletedTrips: [String] = []

    private func key(_ tripId: String, _ kind: TripFileKind) -> String { "\(tripId)/\(kind.rawValue)" }

    func append(tripId: String, kind: TripFileKind, bytes: Data) -> FileResult {
        lock.withLock {
            guard available else { return .unavailable }
            files[key(tripId, kind), default: Data()].append(bytes)
            return .ok
        }
    }

    func sync(tripId: String, kind: TripFileKind) -> FileResult { .ok }

    func write(tripId: String, kind: TripFileKind, bytes: Data) -> FileResult {
        lock.withLock {
            guard available else { return .unavailable }
            files[key(tripId, kind)] = bytes
            return .ok
        }
    }

    func compress(tripId: String, kind: TripFileKind) -> FileResult {
        lock.withLock {
            guard files[key(tripId, kind)] != nil else { return .ioError }
            compressedKeys.insert(key(tripId, kind))
            return .ok
        }
    }

    func read(tripId: String, kind: TripFileKind) -> Data? { lock.withLock { files[key(tripId, kind)] } }

    func describe(tripId: String, kind: TripFileKind, withSha256: Bool) -> StoredFile? {
        lock.withLock {
            files[key(tripId, kind)].map {
                StoredFile(
                    sizeBytes: Int64($0.count), sha256: nil, compressed: compressedKeys.contains(key(tripId, kind)))
            }
        }
    }

    func delete(tripId: String, kind: TripFileKind) -> FileResult {
        lock.withLock {
            files[key(tripId, kind)] = nil
            return .ok
        }
    }

    func deleteTrip(tripId: String) -> FileResult {
        lock.withLock {
            deletedTrips.append(tripId)
            files = files.filter { !$0.key.hasPrefix("\(tripId)/") }
            return .ok
        }
    }

    func tripIds() -> [String] {
        lock.withLock { Set(files.keys.map { String($0.split(separator: "/")[0]) }).sorted() }
    }

    func freeSpaceBytes() -> Int64 { 1 << 30 }

    /// The R0 text written for a trip, for line counts.
    func r0Lines(tripId: String, prefix: String) -> Int {
        guard let data = read(tripId: tripId, kind: .r0), let text = String(data: data, encoding: .utf8) else {
            return 0
        }
        return text.split(separator: "\n").filter { $0.hasPrefix(prefix + ",") }.count
    }
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

    func wake(_ kind: WakeKind, ago seconds: TimeInterval = 0) -> WakeEvent {
        let at = now.addingTimeInterval(-seconds)
        return WakeEvent(kind: kind, tMonoNs: Int64(at.timeIntervalSince1970 * 1e9), wallMs: at.wallMs)
    }
}

func makeTempDirectory() -> URL {
    let url = FileManager.default.temporaryDirectory.appendingPathComponent("dp-tests-\(UUID().uuidString)")
    try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
    return url
}
