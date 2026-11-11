import ActivityKit
import AppIntents
import Foundation

/// Start a manual drive, or attach the Live Activity to the automatic drive shown by the surface.
struct StartTripIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Start drive"
    static let description = IntentDescription("Starts recording or shows the current automatic drive.")
    static let isDiscoverable = false
    static let supportedModes: IntentModes = [.background, .foreground(.dynamic)]

    @Parameter(title: "Automatic trip")
    var tripID: String?

    @Parameter(title: "From Control Center", default: false)
    var fromControlCenter: Bool

    @Parameter(title: "Stop recording", default: false)
    var stopsTrip: Bool

    init() {}

    init(tripID: String? = nil, fromControlCenter: Bool = false, stopsTrip: Bool = false) {
        self.tripID = tripID
        self.fromControlCenter = fromControlCenter
        self.stopsTrip = stopsTrip
    }

    func perform() async throws -> some IntentResult {
        #if DRIVEPERF_APP
            let environment = await AppEnvironment.shared
            await environment.launch()
            if stopsTrip {
                if let tripID { await TripIntentHandler.stop(recorder: environment.recorder, expectedTripID: tripID) }
                return .result()
            }
            let needsLocation = try await TripIntentHandler.start(
                recorder: environment.recorder, clock: environment.clock, canRecord: environment.canRecordFromSystemSurface,
                expectedTripID: tripID, source: fromControlCenter ? "control_center" : "widget")
            if needsLocation {
                if systemContext.currentMode != .foreground {
                    try await continueInForeground(alwaysConfirm: false)
                }
                await environment.surfaceRouter.showLocationHelp()
            }
        #endif
        return .result()
    }
}
