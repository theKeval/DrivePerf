import DPSharedBridge
import Foundation
import Observation

/// Capture status, manual Start/Stop and the in-drive gate (S-HOME-01 accessory, S-DRV-01, S-DRV-02, S-DRV-04).
@MainActor
@Observable
public final class CaptureViewModel {
    /// What covers the whole app (D1 gate, screen-map.md §2.2).
    public enum Cover: Equatable, Identifiable {
        /// S-DRV-01: a confirmed driver trip is active.
        case recording
        /// S-DRV-04 done state for the trip that just ended, or nil when it was discarded.
        case finished(TripSummary?)

        public var id: String {
            switch self {
            case .recording: "recording"
            case .finished(let summary): "finished-\(summary?.id ?? "none")"
            }
        }
    }

    /// The bottom-accessory capsule (S-HOME-01 copy).
    public enum Accessory: Equatable {
        case ready, manual, attention(AttentionReason), recording(startedAt: Date, distanceM: Double)
    }

    public enum StartProblem: String, Identifiable, Sendable {
        case locationDenied, storageLocked

        public var id: String { rawValue }
    }

    public private(set) var snapshot = RecorderSnapshot()
    public private(set) var cover: Cover?
    public var isEndSheetPresented = false
    public var startProblem: StartProblem?
    public private(set) var isStopping = false

    private let recorder: any TripRecording
    private let permissions: any PermissionRequesting

    public init(recorder: any TripRecording, permissions: any PermissionRequesting) {
        self.recorder = recorder
        self.permissions = permissions
        apply(recorder.snapshot)
    }

    /// Observes the recorder for the lifetime of the calling view's `.task`.
    public func observe() async {
        for await next in recorder.snapshots() {
            apply(next)
        }
    }

    func apply(_ next: RecorderSnapshot) {
        let wasDriving = snapshot.isDriving
        snapshot = next
        if next.isDriving {
            if cover == nil { cover = .recording }
        } else if wasDriving, cover == .recording, !isStopping {
            // The trip ended on its own (stop timer) or from the Live Activity while the app was open.
            isEndSheetPresented = false
            cover = .finished(next.lastFinished)
        }
    }

    public var accessory: Accessory {
        if let live = snapshot.live, snapshot.isDriving {
            return .recording(startedAt: live.startedAt, distanceM: live.distanceM)
        }
        switch snapshot.autoDetect {
        case .ready: return .ready
        case .manual: return .manual
        case .needsAttention(let reason): return .attention(reason)
        }
    }

    /// Start (accessory, S-HOME-01). Without location nothing records, not even manual trips (S-ONB-16).
    public func start() async {
        let current = await permissions.current()
        guard current.location.canRecord else {
            startProblem = .locationDenied
            return
        }
        do {
            try recorder.startManualTrip()
        } catch .storageLocked {
            startProblem = .storageLocked
        } catch {
            // Already recording: the gate shows the in-drive screen.
        }
    }

    /// S-DRV-01 Stop → S-DRV-02.
    public func requestStop() {
        isEndSheetPresented = true
    }

    /// S-DRV-02 Keep recording.
    public func keepRecording() {
        isEndSheetPresented = false
    }

    /// S-DRV-02 End drive / End anyway → S-DRV-04.
    public func endDrive() async {
        isStopping = true
        isEndSheetPresented = false
        let summary = await recorder.stopTrip()
        cover = .finished(summary)
        isStopping = false
    }

    /// S-DRV-04 "Back to Drives".
    public func dismissFinished() {
        cover = nil
    }

    public func openSettings() {
        permissions.openSystemSettings()
    }

    /// S-DRV-02 short-trip banner: under 500 m or 2 minutes (F-CAP-17).
    public func isShortTrip(now: Date) -> Bool {
        guard let live = snapshot.live else { return false }
        return live.distanceM < 500 || now.timeIntervalSince(live.startedAt) < 120
    }
}

/// Formatting for the in-drive numbers. Status only: elapsed time and distance (SAF-02).
public enum DriveFormat {
    /// "14:24", or "1:02:09" from one hour.
    public static func elapsed(_ seconds: TimeInterval) -> String {
        let total = max(Int(seconds), 0)
        let hours = total / 3600
        let minutes = (total % 3600) / 60
        let secs = total % 60
        return hours > 0
            ? String(format: "%d:%02d:%02d", hours, minutes, secs) : String(format: "%d:%02d", minutes, secs)
    }

    /// "9.6" (km, one decimal, truncated so it never runs ahead of the road).
    public static func kilometres(_ metres: Double) -> String {
        String(format: "%.1f", (max(metres, 0) / 100).rounded(.down) / 10)
    }

    /// "5:31 pm"
    public static func clock(_ date: Date, locale: Locale = .current, timeZone: TimeZone = .current) -> String {
        var style = Date.FormatStyle(date: .omitted, time: .shortened, locale: locale)
        style.timeZone = timeZone
        return date.formatted(style)
    }
}
