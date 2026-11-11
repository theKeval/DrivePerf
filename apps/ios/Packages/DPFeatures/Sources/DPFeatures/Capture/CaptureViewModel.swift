import DPSharedBridge
import Foundation
import Observation

/// Why automatic trips cannot run although they are on.
public enum AttentionReason: String, Equatable, Sendable {
    case locationWhileUsing, locationDenied
}

/// Capture status, manual Start/Stop and the in-drive gate (S-HOME-01 accessory, S-DRV-01, S-DRV-02, S-DRV-04).
/// It reads the recorder's `RecorderStatus` (the Kotlin `TripRecorder` contract) and the trip list for the trip
/// that just finished.
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

    public private(set) var status = RecorderStatus.idle
    /// When the current trip started, derived from `elapsedS` when it first appears.
    public private(set) var tripStartedAt: Date?
    public private(set) var cover: Cover?
    public var isEndSheetPresented = false
    public var startProblem: StartProblem?
    public private(set) var isStopping = false
    private var location = LocationAccess.notDetermined

    private let recorder: any TripRecording
    private let history: any TripHistory
    private let permissions: any PermissionRequesting
    private let clock: any ClockBridge
    private let now: @MainActor () -> Date
    /// How long to wait for a stopped trip to appear in the list before showing the done screen without it.
    private let finishTimeout: Duration

    public init(
        recorder: any TripRecording, history: any TripHistory, permissions: any PermissionRequesting,
        clock: any ClockBridge, now: @escaping @MainActor () -> Date = { Date() }, finishTimeout: Duration = .seconds(3)
    ) {
        self.recorder = recorder
        self.history = history
        self.permissions = permissions
        self.clock = clock
        self.now = now
        self.finishTimeout = finishTimeout
        apply(recorder.status)
    }

    /// Observes the recorder for the lifetime of the calling view's `.task`.
    public func observe() async {
        await refreshReadiness()
        for await next in recorder.statuses() {
            apply(next)
        }
    }

    /// Re-reads the location permission behind the accessory's "needs attention" state. Call on every foreground.
    public func refreshReadiness() async {
        location = await permissions.current().location
    }

    func apply(_ next: RecorderStatus) {
        let previous = status
        status = next
        if let tripId = next.tripId, tripId != previous.tripId {
            tripStartedAt = now().addingTimeInterval(-next.elapsedS)
        } else if next.tripId == nil {
            tripStartedAt = nil
        }
        if next.isDriving {
            if cover == nil { cover = .recording }
        } else if previous.isDriving, cover == .recording, !isStopping, let tripId = previous.tripId {
            // The trip ended on its own (stop timer) or from the Live Activity while the app was open.
            isEndSheetPresented = false
            Task { await showFinished(tripId: tripId) }
        }
    }

    public var accessory: Accessory {
        if status.isDriving {
            return .recording(startedAt: tripStartedAt ?? now(), distanceM: status.distanceM)
        }
        guard status.autoDetectEnabled else { return .manual }
        switch location {
        case .always: return .ready
        case .whenInUse: return .attention(.locationWhileUsing)
        case .notDetermined, .denied, .restricted: return .attention(.locationDenied)
        }
    }

    /// Start (accessory, S-HOME-01): a `MANUAL` wake. Without location nothing records, not even manual trips
    /// (S-ONB-16).
    public func start() async {
        let current = await permissions.current()
        location = current.location
        guard current.location.canRecord else {
            startProblem = .locationDenied
            return
        }
        recorder.onWake(
            WakeEvent(kind: .manual, tMonoNs: clock.monotonicNs(), wallMs: clock.wallMs(), sourceDetail: "app"))
        // The recorder starts only when the trip file can be written (before the first unlock it cannot).
        if recorder.status.tripId == nil { startProblem = .storageLocked }
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
        guard let tripId = status.tripId else { return }
        isStopping = true
        isEndSheetPresented = false
        recorder.stop()
        await showFinished(tripId: tripId)
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
    public func isShortTrip(now date: Date) -> Bool {
        guard status.tripId != nil else { return false }
        return status.distanceM < 500 || date.timeIntervalSince(tripStartedAt ?? date) < 120
    }

    /// Shows the done screen with the trip once the list has it, or without it when it was discarded (short
    /// automatic trip) or does not appear in time.
    private func showFinished(tripId: String) async {
        let history = history
        let watcher = Task { @MainActor () -> TripSummary? in
            for await trips in history.observeTrips() {
                if let match = trips.first(where: { $0.id == tripId }) { return match }
            }
            return nil
        }
        let timeout = finishTimeout
        let timer = Task {
            try? await Task.sleep(for: timeout)
            watcher.cancel()
        }
        let summary = await watcher.value
        timer.cancel()
        cover = .finished(summary)
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
