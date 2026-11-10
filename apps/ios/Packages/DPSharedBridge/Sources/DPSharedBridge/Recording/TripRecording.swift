import Foundation

/// The recorder seam: what screens need from the trip state machine (pipeline-and-scoring.md §1). DRIVE-62 builds
/// the real `TripRecorder` in `:shared:pipeline`; until it lands, the app target supplies an interim Swift
/// implementation behind this same protocol, so features never change when the Kotlin one replaces it.

public enum TripSource: String, Equatable, Sendable, Codable {
    case manual, automatic
}

/// Recorder states (§1.2). `processed` is transient and never published.
public enum RecorderPhase: String, Equatable, Sendable, Codable {
    case idle, candidate, recording, paused, finishing
}

/// The active trip, as the in-drive screen and the Live Activity show it: status only, never speed or score
/// (SAF-02).
public struct LiveTrip: Equatable, Sendable {
    public var id: String
    public var source: TripSource
    public var startedAt: Date
    public var distanceM: Double
    /// False until the first usable fix ("Finding GPS…").
    public var hasFix: Bool

    public init(id: String, source: TripSource, startedAt: Date, distanceM: Double, hasFix: Bool) {
        self.id = id
        self.source = source
        self.startedAt = startedAt
        self.distanceM = distanceM
        self.hasFix = hasFix
    }
}

public enum AutoDetectStatus: Equatable, Sendable {
    /// Automatic trips are on and the triggers are armed.
    case ready
    /// The user chose manual trips.
    case manual
    /// Automatic trips are on but cannot run (for example location is "While Using").
    case needsAttention(reason: AttentionReason)
}

public enum AttentionReason: String, Equatable, Sendable {
    case locationWhileUsing, locationDenied, storageLocked
}

public struct RecorderSnapshot: Equatable, Sendable {
    public var phase: RecorderPhase
    /// Present in `candidate`, `recording`, `paused` and `finishing`. Screens show it only once confirmed.
    public var live: LiveTrip?
    public var autoDetect: AutoDetectStatus
    /// The trip that finished most recently in this session, for S-DRV-04.
    public var lastFinished: TripSummary?

    public init(
        phase: RecorderPhase = .idle, live: LiveTrip? = nil, autoDetect: AutoDetectStatus = .manual,
        lastFinished: TripSummary? = nil
    ) {
        self.phase = phase
        self.live = live
        self.autoDetect = autoDetect
        self.lastFinished = lastFinished
    }

    /// A confirmed trip is running: the D1 gate locks the app to the in-drive screen.
    public var isDriving: Bool { phase == .recording || phase == .paused }
}

public enum RecorderError: Error, Equatable, Sendable {
    case locationNotAuthorized
    case storageLocked
    case alreadyRecording
}

@MainActor
public protocol TripRecording: AnyObject {
    var snapshot: RecorderSnapshot { get }
    /// Emits the current snapshot first, then every change.
    func snapshots() -> AsyncStream<RecorderSnapshot>
    /// `MANUAL` wake: IDLE → RECORDING (§1.4).
    func startManualTrip() throws(RecorderError)
    /// User Stop, confirmed on S-DRV-02: RECORDING/PAUSED → FINISHING → PROCESSED. Returns the saved trip.
    func stopTrip() async -> TripSummary?
    /// Turns automatic trips on or off (the S-ONB-08 choice).
    func setAutoDetect(enabled: Bool)
}

// MARK: - History

public enum TripStatus: String, Equatable, Sendable, Codable {
    /// Committed and complete.
    case finalised
    /// The app was killed and the trip could not resume (§1.7).
    case interrupted
    /// Manual trip under 500 m or 120 s: kept, labelled, not scored (§1.6).
    case short
}

/// A processed trip as the lists show it. It carries no coordinates.
public struct TripSummary: Identifiable, Equatable, Sendable, Codable {
    public var id: String
    public var source: TripSource
    public var status: TripStatus
    public var startedAt: Date
    public var endedAt: Date
    public var distanceM: Double
    public var samples: SampleCounts

    public init(
        id: String, source: TripSource, status: TripStatus, startedAt: Date, endedAt: Date, distanceM: Double,
        samples: SampleCounts
    ) {
        self.id = id
        self.source = source
        self.status = status
        self.startedAt = startedAt
        self.endedAt = endedAt
        self.distanceM = distanceM
        self.samples = samples
    }

    public var duration: TimeInterval { endedAt.timeIntervalSince(startedAt) }
}

@MainActor
public protocol TripHistory: AnyObject {
    /// Emits the processed trips (newest first) now and after every change.
    func trips() -> AsyncStream<[TripSummary]>
}
