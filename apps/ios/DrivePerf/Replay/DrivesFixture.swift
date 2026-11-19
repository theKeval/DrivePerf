import DPSharedBridge
import DrivePerfShared
import Foundation

// The Drives side of the `-replayFixture` launch argument (`ReplayFixture`): the fixture's one trip in the shared
// Drives presenter and in the capture history.

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
