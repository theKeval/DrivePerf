import DPSharedBridge
import Foundation

extension TripIntentHandler {
    enum StartError: LocalizedError {
        case unavailable

        var errorDescription: String? { "DrivePerf couldn’t start recording. Open the app to check capture status." }
    }

    /// True when the intent needs to open the location help sheet.
    @MainActor
    static func start(
        recorder: CoreTripRecorder, clock: any ClockBridge, canRecord: Bool, expectedTripID: String?, source: String
    ) async throws -> Bool {
        if let expectedTripID {
            guard await matchesActiveTrip(expectedTripID, recorder: recorder), !recorder.status.isManual else { return false }
            _ = recorder.showLiveActivity()
            return false
        }
        guard !recorder.status.isDriving else { return false }
        guard canRecord else { return true }
        guard recorder.isAttached, await recorder.startFromIntent(
            WakeEvent(kind: .manual, tMonoNs: clock.monotonicNs(), wallMs: clock.wallMs(), sourceDetail: source)
        ) else { throw StartError.unavailable }
        return false
    }

    @MainActor
    static func stop(recorder: CoreTripRecorder, expectedTripID: String) async {
        guard await matchesActiveTrip(expectedTripID, recorder: recorder),
            recorder.status.state != .finishing else { return }
        recorder.stop()
    }

    @MainActor
    static func matchesActiveTrip(_ tripID: String, recorder: CoreTripRecorder) async -> Bool {
        guard recorder.isAttached, !tripID.isEmpty else { return false }
        // A system action can cold-launch the app while the core is recovering its active trip.
        _ = await recorder.waitForStatus { $0.isDriving }
        return recorder.status.isDriving && recorder.status.tripId == tripID
    }
}
