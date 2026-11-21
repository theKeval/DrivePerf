import CoreLocation
import Foundation
import OSLog
import UIKit

/// DRIVE-222: a small on-device log of what Core Location told the app about its location sessions, so a field run
/// shows why a background candidate got no fixes (`insufficientlyInUse` after a restart, a denied authorization, …).
///
/// One JSON object per line in `Capture/location-diagnostics.jsonl`, next to the trips, so the A19 / DRIVE-211 pull of
/// the `Capture` folder brings it along. It survives a discarded candidate, whose R0 is deleted. Each line holds the
/// wall time in ms, an event key and enum-valued fields only: never a coordinate or free text. The same line goes to
/// the unified log (subsystem `com.driveperf.app`, category `LocationDiagnostics`) for a sysdiagnose.
public final class LocationDiagnosticsLog: @unchecked Sendable {
    public static let fileName = "location-diagnostics.jsonl"
    /// Past this size the older half is dropped.
    public static let defaultMaxBytes = 128 * 1024

    private static let logger = Logger(subsystem: "com.driveperf.app", category: "LocationDiagnostics")

    public let url: URL
    private let maxBytes: Int
    private let now: @Sendable () -> Date
    private let lock = NSLock()

    public init(
        directory: URL = FileTripStore.defaultRoot, maxBytes: Int = LocationDiagnosticsLog.defaultMaxBytes,
        now: @escaping @Sendable () -> Date = { Date() }
    ) {
        url = directory.appendingPathComponent(Self.fileName)
        self.maxBytes = maxBytes
        self.now = now
    }

    public func record(_ event: String, _ fields: [String: String] = [:]) {
        var object: [String: Any] = fields
        object["t"] = now().wallMs
        object["event"] = event
        guard let json = try? JSONSerialization.data(withJSONObject: object, options: [.sortedKeys]) else { return }
        let line = String(decoding: json, as: UTF8.self)
        Self.logger.notice("\(line, privacy: .public)")
        lock.withLock { append(json + Data("\n".utf8)) }
    }

    /// Every line decoded, oldest first. For tests and the self-test.
    public func entries() -> [[String: Any]] {
        lock.withLock {
            guard let data = try? Data(contentsOf: url) else { return [] }
            return data.split(separator: UInt8(ascii: "\n")).compactMap {
                try? JSONSerialization.jsonObject(with: Data($0)) as? [String: Any]
            }
        }
    }

    private func append(_ line: Data) {
        let manager = FileManager.default
        if !manager.fileExists(atPath: url.path) {
            try? manager.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
            try? Data().write(to: url, options: [.completeFileProtectionUntilFirstUserAuthentication])
            var resource = URLResourceValues()
            resource.isExcludedFromBackup = true
            var excluded = url
            try? excluded.setResourceValues(resource)
        }
        let size = (try? manager.attributesOfItem(atPath: url.path)[.size] as? Int) ?? 0
        if size + line.count > maxBytes {
            trim(adding: line)
            return
        }
        guard let handle = try? FileHandle(forWritingTo: url) else { return }
        defer { try? handle.close() }
        _ = try? handle.seekToEnd()
        try? handle.write(contentsOf: line)
    }

    /// Keeps the newer half of the lines, then the new one.
    private func trim(adding line: Data) {
        guard let data = try? Data(contentsOf: url) else { return }
        let lines = data.split(separator: UInt8(ascii: "\n"), omittingEmptySubsequences: true)
        var kept = Data()
        for old in lines.suffix(lines.count / 2) {
            kept.append(contentsOf: old)
            kept.append(UInt8(ascii: "\n"))
        }
        kept.append(line)
        try? kept.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }
}

/// The app's run state as a log key. `inactive` is a user launch before the scene becomes active: it counts as in use.
public enum AppRunState: String, Sendable {
    case active, inactive, background

    @MainActor
    public static var current: AppRunState {
        switch UIApplication.shared.applicationState {
        case .active: .active
        case .inactive: .inactive
        case .background: .background
        @unknown default: .background
        }
    }

    /// Core Location counts the app as in use (a new session may start) in the foreground only.
    public var isInUse: Bool { self != .background }
}

/// Core Location diagnostic flags as one stable key: the set flags, sorted and comma-separated, or `none`. Pure, so it
/// is unit-tested; the session types have no public initialiser.
public enum LocationDiagnosticKey {
    public static func key(_ flags: [String: Bool]) -> String {
        let set = flags.filter(\.value).keys.sorted()
        return set.isEmpty ? "none" : set.joined(separator: ",")
    }

    public static func key(_ d: CLServiceSession.Diagnostic) -> String {
        key([
            "authorizationDenied": d.authorizationDenied,
            "authorizationDeniedGlobally": d.authorizationDeniedGlobally,
            "authorizationRestricted": d.authorizationRestricted,
            "insufficientlyInUse": d.insufficientlyInUse,
            "fullAccuracyDenied": d.fullAccuracyDenied,
            "alwaysAuthorizationDenied": d.alwaysAuthorizationDenied,
            "serviceSessionRequired": d.serviceSessionRequired,
            "authorizationRequestInProgress": d.authorizationRequestInProgress,
        ])
    }

    public static func key(_ d: CLBackgroundActivitySession.Diagnostic) -> String {
        key([
            "authorizationDenied": d.authorizationDenied,
            "authorizationDeniedGlobally": d.authorizationDeniedGlobally,
            "authorizationRestricted": d.authorizationRestricted,
            "insufficientlyInUse": d.insufficientlyInUse,
            "serviceSessionRequired": d.serviceSessionRequired,
            "authorizationRequestInProgress": d.authorizationRequestInProgress,
        ])
    }

    /// The flags of a `liveUpdates` element, without the fix itself. `stationary` is left out: it toggles at every stop.
    public static func key(_ u: CLLocationUpdate) -> String {
        key([
            "authorizationDenied": u.authorizationDenied,
            "authorizationDeniedGlobally": u.authorizationDeniedGlobally,
            "authorizationRestricted": u.authorizationRestricted,
            "insufficientlyInUse": u.insufficientlyInUse,
            "locationUnavailable": u.locationUnavailable,
            "accuracyLimited": u.accuracyLimited,
            "serviceSessionRequired": u.serviceSessionRequired,
            "authorizationRequestInProgress": u.authorizationRequestInProgress,
        ])
    }
}
