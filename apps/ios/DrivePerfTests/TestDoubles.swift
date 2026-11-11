import DPSharedBridge
import Foundation
@testable import DrivePerf

// Test doubles for the app target. Coordinates in these tests are synthetic (null island offsets), never real places.

final class SpyParkedRegion: ParkedRegionMonitorBridge, @unchecked Sendable {
    private(set) var armed: [(center: GeoPoint, radiusM: Double)] = []

    func arm(center: GeoPoint, radiusM: Double) { armed.append((center, radiusM)) }
    func disarm() { armed = [] }
}

@MainActor
final class SpyLiveSurface: LiveSurfacing {
    private(set) var started: [String] = []
    private(set) var updates: [Double] = []
    private(set) var ended = 0

    func start(tripId: String, startedAt: Date) { started.append(tripId) }
    func update(distanceM: Double, hasFix: Bool) { updates.append(distanceM) }
    func end() { ended += 1 }
}

@MainActor
final class SpyNotifier: TripNotifying {
    private(set) var posted: [String] = []
    private(set) var removed: [String] = []

    func postAutoTripRecording(tripId: String) { posted.append(tripId) }
    func removeTripNotifications(tripId: String) { removed.append(tripId) }
}

@MainActor
final class SpyAssertions: BackgroundAssertions {
    private(set) var begun = 0
    private(set) var ended = 0

    func begin() { begun += 1 }
    func end() { ended += 1 }
}

/// A `SharedCore` the test drives: it records commands and publishes the statuses and trips the test sends.
@MainActor
final class FakeSharedCore: SharedCore {
    private(set) var commands: [String] = []
    // Created up front and buffered, so values sent before the recorder's task subscribes are not lost.
    private let statusStream = AsyncStream.makeStream(of: RecorderStatus.self)
    private let tripStream = AsyncStream.makeStream(of: [TripSummary].self)

    func statusUpdates() -> AsyncStream<RecorderStatus> { statusStream.stream }

    func tripUpdates() -> AsyncStream<[TripSummary]> { tripStream.stream }

    func send(_ status: RecorderStatus) { statusStream.continuation.yield(status) }
    func send(_ trips: [TripSummary]) { tripStream.continuation.yield(trips) }

    func onLaunch() { commands.append("launch") }
    func onWake(_ event: WakeEvent) { commands.append("wake:\(event.kind)") }
    func stop() { commands.append("stop") }
    func markPassenger(keep: Bool) { commands.append("passenger:\(keep)") }
    func setAutoDetect(enabled: Bool) { commands.append("auto:\(enabled)") }
}

func makeTempDirectory() -> URL {
    let url = FileManager.default.temporaryDirectory.appendingPathComponent("dp-tests-\(UUID().uuidString)")
    try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
    return url
}
