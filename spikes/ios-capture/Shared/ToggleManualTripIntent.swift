import AppIntents

/// Compiled into both targets so the control and the Live Activity can reference it.
/// A LiveActivityIntent runs in the app process, which is why only the app build has a body.
struct ToggleManualTripIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Start or stop spike drive"

    init() {}

    func perform() async throws -> some IntentResult {
        #if CAPTURE_APP
            await CaptureEngine.shared.toggleManual(source: "intent")
        #endif
        return .result()
    }
}
