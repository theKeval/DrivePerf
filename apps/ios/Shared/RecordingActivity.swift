import ActivityKit
import AppIntents
import Foundation

/// The recording Live Activity (S-LIVE-01, S-LIVE-02). Compiled into the app and the widget extension. Status
/// only: elapsed time and distance, never speed, score or record pace (SAF-02).
struct RecordingActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var startedAt: Date
        var distanceKm: Double
        var hasFix: Bool
        /// True after Stop: "Drive decoded." until the activity is dismissed.
        var ended: Bool
        var carName: String? = nil
    }

    var tripId: String
}

/// Stop on the Live Activity, without unlocking. A `LiveActivityIntent` runs in the app's process, so only the app
/// build has a body (`DRIVEPERF_APP`); the extension only needs the type to draw the button.
struct StopTripIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Stop drive"
    static let description = IntentDescription("Ends the drive DrivePerf is recording.")

    @Parameter(title: "Trip")
    var tripID: String

    init() {}

    init(tripID: String) { self.tripID = tripID }

    func perform() async throws -> some IntentResult {
        #if DRIVEPERF_APP
            let environment = await AppEnvironment.shared
            await environment.launch()
            await TripIntentHandler.stop(recorder: environment.recorder, expectedTripID: tripID)
        #endif
        return .result()
    }
}
