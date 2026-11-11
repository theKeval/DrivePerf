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
        /// S-DRV-05: content was opened while the lock applies (a driver trip, or the hold after one).
        case held
        /// S-DRV-04 "Decoding your drive": the trip is being finalised and listed.
        case decoding
        /// S-DRV-04 done state for the trip that just ended.
        case finished(TripOutcome)

        public var id: String {
            switch self {
            case .recording: "recording"
            case .held: "held"
            case .decoding: "decoding"
            case .finished(let outcome): "finished-\(outcome.id)"
            }
        }
    }

    /// How the trip that just ended turned out, for the S-DRV-04 done state.
    public enum TripOutcome: Equatable {
        /// Saved. `summary` is nil when the core discarded it (a short automatic trip). `autoEnded`: the 15-minute
        /// stationary timeout ended a manual trip, which the Story has to say (F-CAP-01.4).
        case saved(TripSummary?, autoEnded: Bool)
        /// Marked "passenger" and kept: saved without a score (F-CAP-08.2).
        case passenger(TripSummary?)
        /// "Don't save": nothing was kept (F-CAP-08.3).
        case discarded

        var id: String {
            switch self {
            case .saved(let summary, _): "saved-\(summary?.id ?? "none")"
            case .passenger(let summary): "passenger-\(summary?.id ?? "none")"
            case .discarded: "discarded"
            }
        }
    }

    /// The S-DRV-03 steps. "Don't save" deletes, so it takes a second, explicit step.
    public enum PassengerStep: Equatable, Sendable {
        case choose, confirmDiscard
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
    public var isPassengerSheetPresented = false
    public private(set) var passengerStep = PassengerStep.choose
    public var startProblem: StartProblem?
    public private(set) var isStopping = false
    /// Content waiting behind S-DRV-05. It opens by itself once the lock lifts.
    public private(set) var heldContent: HeldContent?
    /// Where the app opens content that is not locked (the Story router, once it exists, and notification taps).
    public var onOpenContent: ((HeldContent) -> Void)?
    /// The car's nickname for the chip on S-DRV-01 and S-DRV-04. Garage (DRIVE-173) fills it; until then nothing shows.
    public var carName: String?
    private var location = LocationAccess.notDetermined
    /// A trip the user is ending right now (Stop, passenger, Don't save): the outcome is theirs to set.
    private var ending: EndKind?
    /// The lock after a trip: from the trip's end until the phone has been calm for `DriveLock.calmInterval`.
    private var holdActive = false
    private var calmSince: Date?
    private var isMoving = false
    private var heldShownAt: Date?

    private enum EndKind { case user, passenger, discard }

    private let recorder: any TripRecording
    private let history: any TripHistory
    private let permissions: any PermissionRequesting
    private let clock: any ClockBridge
    private let movement: (any MovementMonitoring)?
    private let now: @MainActor () -> Date
    /// How long to wait for a stopped trip to appear in the list before showing the done screen without it.
    private let finishTimeout: Duration
    private let startTimeout: Duration

    public init(
        recorder: any TripRecording, history: any TripHistory, permissions: any PermissionRequesting,
        clock: any ClockBridge, movement: (any MovementMonitoring)? = nil,
        now: @escaping @MainActor () -> Date = { Date() }, finishTimeout: Duration = .seconds(3),
        startTimeout: Duration = .seconds(5)
    ) {
        self.recorder = recorder
        self.history = history
        self.permissions = permissions
        self.clock = clock
        self.movement = movement
        self.now = now
        self.finishTimeout = finishTimeout
        self.startTimeout = startTimeout
        apply(recorder.status)
    }

    /// Observes the recorder for the lifetime of the calling view's `.task`.
    public func observe() async {
        await refreshReadiness()
        for await next in recorder.statuses() {
            apply(next)
        }
    }

    /// Follows the movement signal for the lifetime of the calling view's `.task` (F-CAP-07.4).
    public func observeMovement() async {
        guard let movement else { return }
        for await moving in movement.movements() {
            setMoving(moving)
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
            // A new trip ends the hold of the last one (the lock applies to it anyway).
            holdActive = false
            calmSince = nil
        } else if next.tripId == nil {
            tripStartedAt = nil
        }
        if next.isDriving {
            if cover == nil { cover = heldContent == nil ? .recording : .held }
        } else if previous.isDriving, cover == .recording || cover == .held, ending == nil,
            let tripId = previous.tripId
        {
            // The trip ended on its own (stop timer) or from the Live Activity while the app was open.
            isEndSheetPresented = false
            isPassengerSheetPresented = false
            beginHold()
            if heldContent == nil {
                Task { await showFinished(tripId: tripId, userStopped: false) }
            } else {
                cover = .held
            }
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
        // A failure flag already set belongs to an earlier start; the core only re-reports it if this one fails too.
        let earlierFailure = recorder.status.startFailed
        recorder.onWake(
            WakeEvent(kind: .manual, tMonoNs: clock.monotonicNs(), wallMs: clock.wallMs(), sourceDetail: "app"))
        // The recorder starts only when the trip file can be written (before the first unlock it cannot).
        if await !started(earlierFailure: earlierFailure) { startProblem = .storageLocked }
    }

    /// Waits for the manual start's outcome: the shared recorder reports it asynchronously. True once a trip is
    /// recording; false when the recorder newly reports a failed start, or nothing starts within `startTimeout` (the
    /// core is not up before the first unlock and drops the wake; a repeated failure leaves the status unchanged).
    private func started(earlierFailure: Bool) async -> Bool {
        if recorder.status.tripId != nil { return true }
        let recorder = recorder
        let watcher = Task { @MainActor () -> Bool in
            for await next in recorder.statuses() {
                if next.tripId != nil { return true }
                if next.startFailed && !earlierFailure { return false }
            }
            return false
        }
        let timeout = startTimeout
        let timer = Task {
            try? await Task.sleep(for: timeout)
            watcher.cancel()
        }
        let outcome = await watcher.value
        timer.cancel()
        return outcome
    }

    // MARK: Stop (S-DRV-02)

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
        ending = .user
        isEndSheetPresented = false
        recorder.stop()
        beginHold()
        await showFinished(tripId: tripId, userStopped: true)
        ending = nil
        isStopping = false
    }

    /// S-DRV-04 "Back to Drives".
    public func dismissFinished() {
        cover = nil
    }

    // MARK: Passenger (S-DRV-01 / S-DRV-05 → S-DRV-03)

    /// "I'm a passenger" → S-DRV-03.
    public func requestPassenger() {
        guard status.isDriving else { return }
        passengerStep = .choose
        isPassengerSheetPresented = true
    }

    /// S-DRV-03 Cancel, or Keep recording on the delete confirmation.
    public func cancelPassenger() {
        isPassengerSheetPresented = false
        passengerStep = .choose
    }

    /// S-DRV-03 "Don't save this trip" → the explicit delete step.
    public func askDiscard() {
        passengerStep = .confirmDiscard
    }

    /// S-DRV-03 "Keep the trip as a passenger trip": scoring stops, the trip is saved without a score and the lock
    /// lifts (F-CAP-08.2). The shared recorder finalises a kept passenger trip, so recording ends here.
    public func keepAsPassenger() async {
        guard let tripId = status.tripId else { return }
        ending = .passenger
        isPassengerSheetPresented = false
        isEndSheetPresented = false
        passengerStep = .choose
        recorder.markPassenger(keep: true)
        await showFinished(tripId: tripId, userStopped: false, passenger: true)
        ending = nil
        releaseHeldContent()
    }

    /// S-DRV-03 "Delete this trip": recording stops and every sample is deleted (F-CAP-08.3). The core discards the
    /// trip at once, well inside the one minute allowed.
    public func discardTrip() async {
        guard status.tripId != nil else { return }
        ending = .discard
        isPassengerSheetPresented = false
        isEndSheetPresented = false
        passengerStep = .choose
        recorder.markPassenger(keep: false)
        cover = .finished(.discarded)
        ending = nil
        releaseHeldContent()
    }

    // MARK: Content lock (S-DRV-05)

    /// True while content must not open: a driver trip is active, or it ended and the phone has not yet been calm for
    /// `DriveLock.calmInterval` (F-CAP-07.2, .4).
    public func isContentLocked(now date: Date) -> Bool {
        if status.isDriving { return true }
        guard holdActive else { return false }
        guard let calmSince else { return true }
        return date.timeIntervalSince(calmSince) < DriveLock.calmInterval
    }

    /// Something asked to open content (a notification or deep link). While the lock applies S-DRV-05 shows and the
    /// content waits; otherwise it opens.
    public func requestContent(_ target: HeldContent) {
        let date = now()
        guard isContentLocked(now: date) else {
            onOpenContent?(target)
            return
        }
        heldContent = target
        heldShownAt = date
        cover = .held
    }

    /// The movement signal changed. While the post-trip lock holds, moving restarts the calm count.
    func setMoving(_ moving: Bool) {
        isMoving = moving
        guard holdActive else { return }
        calmSince = moving ? nil : (calmSince ?? now())
    }

    /// Call about once a second while S-DRV-05 shows: opens the held content once the lock lifts, and steps back to
    /// S-DRV-01 while driving so Stop stays within reach.
    public func tick(now date: Date) {
        if holdActive, !status.isDriving, !isContentLocked(now: date) {
            holdActive = false
            releaseHeldContent()
            return
        }
        if status.isDriving, cover == .held, let shown = heldShownAt,
            date.timeIntervalSince(shown) >= DriveLock.heldReturnAfter
        {
            cover = .recording
        }
    }

    private func beginHold() {
        holdActive = true
        calmSince = isMoving ? nil : now()
    }

    /// Opens what was waiting (dropping paywalls and share prompts) and closes S-DRV-05.
    private func releaseHeldContent() {
        guard let target = heldContent else { return }
        heldContent = nil
        heldShownAt = nil
        if cover == .held { cover = nil }
        if target.isReopened { onOpenContent?(target) }
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
    private func showFinished(tripId: String, userStopped: Bool, passenger: Bool = false) async {
        // Held content keeps S-DRV-05 up; a passenger trip has nothing to decode.
        if !passenger, cover != .held { cover = .decoding }
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
        let finalisedAt = now()
        let outcome: TripOutcome =
            passenger
            ? .passenger(summary)
            : .saved(
                summary,
                autoEnded: !userStopped && summary.map { DriveLock.endedOnStopTimer($0, finalisedAt: finalisedAt) } == true)
        // Held content keeps S-DRV-05 up: the saved screen is not content, and the lock still applies.
        if heldContent != nil, cover == .held, !passenger { return }
        // "Back to Drives" while decoding: the trip is listed, so the saved screen has nothing to add.
        if !passenger, cover == nil { return }
        cover = .finished(outcome)
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
