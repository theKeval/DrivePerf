import DPSharedBridge
import Foundation

/// Debug launch arguments for Replay with no real data: `-replayFixture` lists one synthetic trip, skips onboarding
/// and keeps the core and Core Location off; `-replayOffline` also reports no network (the plain canvas). Used by the
/// UI test and for PR screenshots, so no real route is ever shown.
enum ReplayFixture {
    #if DEBUG
    static let isActive = ProcessInfo.processInfo.arguments.contains("-replayFixture")
    static let isOffline = ProcessInfo.processInfo.arguments.contains("-replayOffline")
    #else
    static let isActive = false
    static let isOffline = false
    #endif

    static let tripId = "synthetic-replay"

    /// Yesterday, 5:42 pm, 30 min, 19.9 km.
    static var trip: TripSummary {
        let calendar = Calendar.current
        let yesterday = calendar.date(byAdding: .day, value: -1, to: Date()) ?? Date()
        let start = calendar.date(bySettingHour: 17, minute: 42, second: 0, of: yesterday) ?? yesterday
        return TripSummary(
            id: tripId, source: .auto, status: .finalised, startedAt: start, endedAt: start.addingTimeInterval(1_800),
            distanceM: 19_900, samples: SampleCounts(location: 1_760, motion: 90_000, activity: 12),
            stats: TripStats(estimatedDistanceM: 300, startEstimated: true))
    }
}

/// The fixture's one trip.
@MainActor
final class FixtureTripHistory: TripHistory {
    func observeTrips() -> AsyncStream<[TripSummary]> {
        AsyncStream { continuation in continuation.yield([ReplayFixture.trip]) }
    }
}

/// A fixed network state for the fixture.
@MainActor
final class FixedConnectivity: ConnectivityMonitoring {
    let online: Bool

    init(online: Bool) { self.online = online }

    func onlineUpdates() -> AsyncStream<Bool> {
        AsyncStream { continuation in continuation.yield(online) }
    }
}
