import DPSharedBridge
import Foundation

/// The only tags an iOS crash report may carry (ADR-007, DRIVE-196), the same list as Android's `CrashTag`. Values come
/// from fixed sets: no free text, no location, no trip ID. The crash engine drops any other tag before sending.
public enum CrashTags {
    /// `true` while a trip is being captured (candidate to finishing), so capture-time crashes stand out (F-ANA-03 AC2).
    public static let inTrip = "in_trip"
    /// `auto`, `manual` or `none`.
    public static let captureMode = "capture_mode"
    /// The recorder state: `idle`, `candidate`, `recording`, `paused` or `finishing`.
    public static let tripState = "trip_state"
    /// Reserved by ADR-007 for the database state; not set yet.
    public static let dbState = "db_state"
    /// The app's version and build number.
    public static let appBuild = "app_build"

    public static let allowed: Set<String> = [inTrip, captureMode, tripState, dbState, appBuild]

    public static func forTrip(state: RecorderState, isManual: Bool) -> [String: String] {
        let capturing = state != .idle
        return [
            inTrip: capturing ? "true" : "false",
            captureMode: !capturing ? "none" : isManual ? "manual" : "auto",
            tripState: state.rawValue,
        ]
    }
}

/// Finds coordinate-like pairs in text so crash reports never carry a location (ADR-007), the same pattern as
/// Android's `CoordinateScrubber`: two decimal numbers with at least four decimals.
public enum CoordinateScrubber {
    // swiftlint:disable:next force_try
    private static let pair = try! NSRegularExpression(pattern: #"-?\d{1,3}\.\d{4,}\s*[,;/ ]\s*-?\d{1,3}\.\d{4,}"#)
    public static let marker = "[location removed]"

    public static func containsCoordinates(_ text: String?) -> Bool {
        guard let text else { return false }
        return pair.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) != nil
    }

    /// Replaces every coordinate-like pair with a fixed marker.
    public static func scrub(_ text: String) -> String {
        pair.stringByReplacingMatches(
            in: text, range: NSRange(text.startIndex..., in: text), withTemplate: NSRegularExpression.escapedTemplate(for: marker))
    }
}
