import Foundation

/// The recorder seam features code against: a Swift mirror of the Kotlin `TripRecorder` contract in
/// `com.driveperf.domain.capture` (pipeline-and-scoring.md §1). The Kotlin `status` StateFlow becomes `status` plus
/// `statuses()`. Until the shared recorder implementation lands (DRIVE-62 slices 2–3), the app target supplies an
/// interim Swift recorder behind this protocol; afterwards it adapts the Kotlin one, and features do not change.

/// Kotlin `RecorderState`. `PROCESSED` is transient and shows as `idle`.
public enum RecorderState: String, Equatable, Sendable, Codable {
    case idle, candidate, recording, paused, finishing
}

/// Kotlin `RecorderStatus`: what live surfaces may show. Elapsed time, distance and capture state only; no live
/// score, event or coaching (SAF-02).
public struct RecorderStatus: Equatable, Sendable {
    public var state: RecorderState
    /// The trip being recorded; nil when idle. A candidate's trip is not shown in the UI.
    public var tripId: String?
    /// A manually started trip (it shows a Stop control).
    public var isManual: Bool
    public var elapsedS: Double
    /// Gated distance so far.
    public var distanceM: Double
    /// False while the user has paused automatic detection.
    public var autoDetectEnabled: Bool

    public init(
        state: RecorderState, tripId: String?, isManual: Bool, elapsedS: Double, distanceM: Double,
        autoDetectEnabled: Bool
    ) {
        self.state = state
        self.tripId = tripId
        self.isManual = isManual
        self.elapsedS = elapsedS
        self.distanceM = distanceM
        self.autoDetectEnabled = autoDetectEnabled
    }

    /// Kotlin `RecorderStatus.IDLE`.
    public static let idle = RecorderStatus(
        state: .idle, tripId: nil, isManual: false, elapsedS: 0, distanceM: 0, autoDetectEnabled: true)

    /// A confirmed trip is running: the D1 gate locks the app to the in-drive screen.
    public var isDriving: Bool { state == .recording || state == .paused || state == .finishing }
}

/// Kotlin `TripRecorder`.
@MainActor
public protocol TripRecording: AnyObject {
    var status: RecorderStatus { get }
    /// Emits the current status first, then every change.
    func statuses() -> AsyncStream<RecorderStatus>
    /// Once per process start, after the platform has recreated its location sessions (ADR-002 §5): re-arms the
    /// parked region and recovers a trip that was recording when the app was killed (§1.7).
    func onLaunch()
    /// A wake from an OS trigger, or `.manual` for a user start. Stale events are dropped.
    func onWake(_ event: WakeEvent)
    /// The user confirmed Stop.
    func stop()
    /// The user marked the current trip as a passenger trip, keeping it or discarding it.
    func markPassenger(keep: Bool)
    /// Turns automatic detection on or off. Manual starts work either way.
    func setAutoDetect(enabled: Bool)
}

// MARK: - History

/// Kotlin `TripSource`.
public enum TripSource: String, Equatable, Sendable, Codable {
    case auto, manual

    /// Also reads `automatic`, the name rows recorded before the shared ports used.
    public init(from decoder: any Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = raw == "automatic" ? .auto : try TripSource(rawValue: raw).orThrow(decoder)
    }
}

extension Optional where Wrapped == TripSource {
    fileprivate func orThrow(_ decoder: any Decoder) throws -> TripSource {
        guard let self else {
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath, debugDescription: "TripSource"))
        }
        return self
    }
}

/// The subset of Kotlin `TripStatus` the interim recorder produces, plus `short`, which the shared core expresses
/// through quality once scoring lands (§1.6).
public enum TripStatus: String, Equatable, Sendable, Codable {
    /// Committed and complete.
    case finalised
    /// The app was killed and the trip could not resume (§1.7).
    case interrupted
    /// Manual trip under 500 m or 120 s: kept, labelled, not scored (§1.6).
    case short
}

/// Sample counts written to a trip's capture log.
public struct SampleCounts: Equatable, Sendable, Codable {
    public var location: Int
    public var motion: Int
    public var activity: Int

    public init(location: Int = 0, motion: Int = 0, activity: Int = 0) {
        self.location = location
        self.motion = motion
        self.activity = activity
    }
}

/// A raw recorded trip row, as the lists show it until the shared `TripRepository` and its `Trip` land. It carries
/// no coordinates.
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

/// The trip list's source, after Kotlin `TripRepository.observeTrips()`.
@MainActor
public protocol TripHistory: AnyObject {
    /// Emits the recorded trips (newest first) now and after every change.
    func observeTrips() -> AsyncStream<[TripSummary]>
}
