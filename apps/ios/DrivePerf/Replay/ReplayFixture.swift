import DPFeatures
import DPSharedBridge
import DrivePerfShared
import Foundation
import SwiftUI

/// Debug launch arguments for Replay with no real data: `-replayFixture` lists one synthetic trip, skips onboarding
/// and keeps the core and Core Location off; `-replayOffline` also reports no network (the plain canvas);
/// `-replayFixtureAt <seconds>` opens the synthetic Replay straight away at that time (screenshots on a phone that
/// no tool can tap). Used by the UI test and for PR screenshots, so no real route is ever shown.
enum ReplayFixture {
    #if DEBUG
    static let isActive = arguments.contains("-replayFixture") || screenTime != nil
    static let isOffline = arguments.contains("-replayOffline")
    static let screenTime: Double? = arguments.firstIndex(of: "-replayFixtureAt")
        .flatMap { arguments.indices.contains($0 + 1) ? Double(arguments[$0 + 1]) : nil }
    #else
    static let isActive = false
    static let isOffline = false
    static let screenTime: Double? = nil
    #endif

    private static var arguments: [String] { ProcessInfo.processInfo.arguments }

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

extension ReplayFixture {
    /// The shared Drives presenter over the fixture's one trip, as a Kotlin `Trip` (no corrections).
    static func drivesPresenter(clock: any ClockBridge) -> DrivePerfShared.DrivesPresenter {
        let summary = trip
        let startMs = Int64((summary.startedAt.timeIntervalSince1970 * 1000).rounded())
        let endMs = Int64((summary.endedAt.timeIntervalSince1970 * 1000).rounded())
        let kotlinTrip = DrivePerfShared.Trip(
            id: summary.id, status: .finalised, kind: .driver, source: .auto, sourceDetail: nil, vehicleId: nil,
            startedAt: startMs, startTzOffsetMin: Int32(TimeZone.current.secondsFromGMT(for: summary.startedAt) / 60),
            startEstimated: summary.stats.startEstimated, endedAt: KotlinLong(value: endMs), endTzOffsetMin: nil,
            tzId: nil, partOfDay: nil, fromLabel: nil, toLabel: nil, labelOverride: nil, context: nil,
            distanceM: KotlinDouble(value: summary.distanceM),
            estimatedDistanceM: KotlinDouble(value: summary.stats.estimatedDistanceM),
            durationS: KotlinDouble(value: summary.duration), movingS: nil, stoppedS: nil, stopCount: nil,
            avgMovingSpeedMps: nil, maxSpeedMps: nil, maxSpeedAtS: nil, elevGainM: nil, elevLossM: nil, elevMinM: nil,
            elevMaxM: nil, bbox: nil, stops: [], cleanest: nil, quality: nil, interrupted: nil, suspect: .none,
            suspectAnswer: nil, correction: nil, correctedAt: nil, captureMeta: nil, pipelineVersion: nil,
            rawState: .full, createdAt: startMs, updatedAt: endMs, deletedAt: nil)
        return DrivePerfShared.DrivesPresenter.companion.onMain(
            trips: [kotlinTrip], units: UnitSystem.companion.defaultFor(region: Locale.current.region?.identifier ?? ""),
            format: CoreDrivesFormatter(), clock: KotlinClockPort(clock))
    }
}

/// The fixture's one trip.
@MainActor
final class FixtureTripHistory: DPSharedBridge.TripHistory {
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

/// `-replayFixtureAt`: S-RPL-01 on the synthetic trip, paused at the given time.
struct ReplayFixtureScreen: View {
    let time: Double
    let environment: AppEnvironment
    @State private var model: ReplayViewModel?

    var body: some View {
        NavigationStack {
            if let model {
                ReplayView(
                    model: model, title: "Replay", subtitle: "Tuesday evening",
                    mapSummary: "Map of your drive, 19.9 km.")
            }
        }
        .task {
            let model = ReplayViewModel(
                presenter: SyntheticReplayPresenters().presenter(tripId: ReplayFixture.tripId, source: .tripDetail),
                connectivity: environment.connectivity, display: DisplaySettingsModel(storage: environment.displaySettings))
            self.model = model
            // The presenter loads on the main thread; seek once the replay is there.
            while model.phase == .loading, !Task.isCancelled { try? await Task.sleep(for: .milliseconds(50)) }
            model.scrub(to: time)
            model.endScrub()
        }
    }
}
