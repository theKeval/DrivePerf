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
    private var statusContinuation: AsyncStream<RecorderStatus>.Continuation?
    private var tripContinuation: AsyncStream<[TripSummary]>.Continuation?

    func statusUpdates() -> AsyncStream<RecorderStatus> {
        AsyncStream { statusContinuation = $0 }
    }

    func tripUpdates() -> AsyncStream<[TripSummary]> {
        AsyncStream { tripContinuation = $0 }
    }

    func send(_ status: RecorderStatus) { statusContinuation?.yield(status) }
    func send(_ trips: [TripSummary]) { tripContinuation?.yield(trips) }

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
