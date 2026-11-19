import Foundation

// Swift mirrors of `com.driveperf.domain.replay` (DRIVE-200 F-RPL-01, DRIVE-61 F-RPL-08), exposed by the core as
// `CoreServices.replay`. The app target maps the Kotlin values into these; features never see Kotlin.

/// Kotlin `SegmentStyle`: how a stretch of the route is drawn (F-RPL-08).
public enum ReplaySegmentStyle: String, Equatable, Sendable, CaseIterable {
    /// Measured: solid.
    case observed
    /// The estimated start (parking spot to first fix): dashed, "Estimated start".
    case estimated
    /// A GPS gap: broken, "No GPS". The marker jumps it.
    case noGps
    /// Measured but not scored: dimmed, "Phone moved — not scored".
    case excluded
}

/// Kotlin `ReplayPoint`, in seconds from the trip start.
public struct ReplayPoint: Equatable, Sendable {
    public var tS: Double
    public var lat: Double
    public var lng: Double
    public var speedMps: Double?

    public init(tS: Double, lat: Double, lng: Double, speedMps: Double? = nil) {
        self.tS = tS
        self.lat = lat
        self.lng = lng
        self.speedMps = speedMps
    }
}

/// Kotlin `ReplaySegment`: one run of points drawn in one style.
public struct ReplaySegment: Equatable, Sendable {
    public var style: ReplaySegmentStyle
    public var points: [ReplayPoint]

    public init(style: ReplaySegmentStyle, points: [ReplayPoint]) {
        self.style = style
        self.points = points
    }
}

/// Kotlin `ReplaySpan`: a labelled stretch of time (never `observed`), for the chip and the scrubber marks.
public struct ReplaySpan: Equatable, Sendable {
    public var style: ReplaySegmentStyle
    public var t0S: Double
    public var t1S: Double
    /// The core's chip text: "Estimated start", "No GPS", "Phone moved — not scored", or "Not scored" when an excluded
    /// span was left out for another reason. Nil falls back to the style's text.
    public var label: String?

    public init(style: ReplaySegmentStyle, t0S: Double, t1S: Double, label: String? = nil) {
        self.style = style
        self.t0S = t0S
        self.t1S = t1S
        self.label = label
    }

    /// Kotlin `Replay.spanAt`: start inclusive, end exclusive, except a span that runs to the end of the trip.
    public func contains(_ tS: Double, durationS: Double) -> Bool {
        tS >= t0S && (tS < t1S || (tS == t1S && t1S >= durationS))
    }
}

/// Kotlin `ReplayFrame`: the marker at one moment. Display-only interpolation between 1 Hz fixes.
public struct ReplayFrame: Equatable, Sendable {
    public var tS: Double
    public var lat: Double
    public var lng: Double
    public var speedMps: Double?
    public var bearingDeg: Double?
    public var style: ReplaySegmentStyle

    public init(
        tS: Double, lat: Double, lng: Double, speedMps: Double? = nil, bearingDeg: Double? = nil,
        style: ReplaySegmentStyle = .observed
    ) {
        self.tS = tS
        self.lat = lat
        self.lng = lng
        self.speedMps = speedMps
        self.bearingDeg = bearingDeg
        self.style = style
    }
}

/// Kotlin `PlaybackSpeed`. Auto plays the whole trip in about 60 s.
public enum PlaybackSpeed: String, Equatable, Sendable, CaseIterable, Identifiable {
    case auto, x1, x10, x30

    public var id: String { rawValue }

    /// The segmented-control label: "Auto", "1×".
    public var label: String {
        switch self {
        case .auto: "Auto"
        case .x1: "1\u{00D7}"
        case .x10: "10\u{00D7}"
        case .x30: "30\u{00D7}"
        }
    }
}

/// Kotlin `BoundingBox`, for fitting the map.
public struct ReplayBounds: Equatable, Sendable {
    public var minLat: Double
    public var minLng: Double
    public var maxLat: Double
    public var maxLng: Double

    public init(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double) {
        self.minLat = minLat
        self.minLng = minLng
        self.maxLat = maxLat
        self.maxLng = maxLng
    }
}

/// Kotlin `Replay`: one trip, ready to draw. Playback lives in the shared presenter (`ReplayPresenting`).
public protocol ReplayTrack: AnyObject, Sendable {
    var tripId: String { get }
    var bbox: ReplayBounds { get }
    var durationS: Double { get }
    /// The overlay spans in time order (`estimated`, `noGps`, `excluded`).
    var spans: [ReplaySpan] { get }
    /// Raw data was removed (EC-STO): this is the summary route only.
    var summaryOnly: Bool { get }
    /// The route simplified for a zoom bucket (Douglas–Peucker, ADR-005). Style boundaries are kept.
    func segments(zoomBucket: Int) -> [ReplaySegment]
    /// Kotlin `Replay.frameAt`: the marker at `tS`. Never interpolates across a `noGps` gap.
    func frame(at tS: Double) -> ReplayFrame
    /// Kotlin `Replay.spanAt`: the overlay span at `tS` for the marker's chip, or nil on an observed stretch.
    func span(at tS: Double) -> ReplaySpan?
    /// Kotlin `Replay.zoomBucketFor`: the simplification bucket for a map `viewWidth` points wide showing
    /// `longitudeDelta` degrees of longitude.
    func zoomBucket(longitudeDelta: Double, viewWidth: Double) -> Int
}

/// Kotlin `Replay.MAX_ZOOM` and the web-Mercator bucket formula, for tests that have no core.
public enum ReplayZoomMath {
    public static let maxZoom = 22

    public static func bucket(longitudeDelta: Double, viewWidth: Double) -> Int {
        guard longitudeDelta > 0, viewWidth > 0 else { return maxZoom }
        let zoom = log2(360 * viewWidth / (256 * longitudeDelta)).rounded(.down)
        guard zoom.isFinite else { return maxZoom }
        return min(max(Int(zoom), 0), maxZoom)
    }
}

// MARK: - The shared presenter (Kotlin `com.driveperf.presentation.replay`, ADR-002 section 4)

/// Kotlin `ReplaySource`: where Replay was opened from (`replay_opened {source}`).
public enum ReplayOpenSource: String, Equatable, Sendable {
    case tripDetail, story
}

/// Kotlin `ReplayUnavailable`: why there is no replay to show.
public enum ReplayMissingReason: String, Equatable, Sendable {
    /// The trip is gone, discarded, or has no position at all.
    case notFound
    /// The trip is being recorded: the drive screen shows instead (F-RPL-01 AC6).
    case recording
    /// The Pro gate for drives older than 30 days (S-SUB-03). The shared presenter never sets it yet.
    case locked
}

/// Kotlin `ReplayState` without the play anchor: the anchor stays in the core, read through
/// `ReplayPresenting.playhead(at:)`.
public struct ReplayScreenState: Sendable {
    public var loading: Bool
    public var track: (any ReplayTrack)?
    public var missing: ReplayMissingReason?
    public var playing: Bool
    public var speed: PlaybackSpeed
    public var scrubbing: Bool
    /// Kotlin `ReplayOverlay`: pins, ticks, stats and elevation (DRIVE-34, DRIVE-203). Nil until they load.
    public var overlay: (any ReplayOverlayReading)?
    /// The pin the user tapped: it grows and the others dim.
    public var selection: ReplaySelectionInfo?
    /// S-RPL-03 for a tapped stop or the max speed.
    public var pinSheet: ReplayPinSheetInfo?

    public init(
        loading: Bool = true, track: (any ReplayTrack)? = nil, missing: ReplayMissingReason? = nil,
        playing: Bool = false, speed: PlaybackSpeed = .auto, scrubbing: Bool = false,
        overlay: (any ReplayOverlayReading)? = nil, selection: ReplaySelectionInfo? = nil,
        pinSheet: ReplayPinSheetInfo? = nil
    ) {
        self.loading = loading
        self.track = track
        self.missing = missing
        self.playing = playing
        self.speed = speed
        self.scrubbing = scrubbing
        self.overlay = overlay
        self.selection = selection
        self.pinSheet = pinSheet
    }
}

/// Kotlin `ReplayIntent`.
public enum ReplayAction: Equatable, Sendable {
    case play, pause
    case setSpeed(PlaybackSpeed)
    case scrubStart
    case scrubTo(Double)
    case scrubEnd
    /// A display frame at `nowNs` on the presenter's clock.
    case tick(Int64)
    case openLegend
    /// An event pin was tapped: pause there and open S-RPL-02 (F-RPL-03, F-RPL-04 AC5).
    case selectEvent(String)
    /// The evidence sheet stepped to another event: the marker and the selection follow.
    case showEvent(String)
    /// A stop pin: S-RPL-03 for stop `n` (F-RPL-02 AC3).
    case selectStop(Int)
    /// The max-speed pin: S-RPL-03 (F-RPL-02 AC2).
    case selectMaxSpeed
    /// S-RPL-03 "Jump to this moment".
    case jumpToPin
    /// A sheet closed: the selection clears.
    case clearSelection
    /// Feedback on an event changed: the pins are read again.
    case reloadDetails
}

/// Kotlin `ReplayEffect`.
public enum ReplayNavigation: Equatable, Sendable {
    /// The trip is still being recorded: show S-DRV-05 (F-RPL-01 AC6).
    case showDriveScreen
    /// Open S-RPL-02 for the event.
    case openEvidence(eventId: String)
}

/// Kotlin `ReplayPresenter`: the shared playback state machine, which also sends the Replay analytics. The app target
/// wraps the Kotlin object; S-RPL-01 draws from it.
@MainActor
public protocol ReplayPresenting: AnyObject {
    var state: ReplayScreenState { get }
    /// The current state first, then every change. It changes on load and on intents, never per frame.
    func states() -> AsyncStream<ReplayScreenState>
    func navigation() -> AsyncStream<ReplayNavigation>
    func dispatch(_ action: ReplayAction)
    /// Monotonic now on the presenter's clock.
    func nowNs() -> Int64
    /// Kotlin `ReplayState.playheadAt(nowNs)`: trip seconds at the marker.
    func playhead(at nowNs: Int64) -> Double
    func close()
}

/// Makes one presenter per Replay screen (`CoreServices.replayPresenter`).
@MainActor
public protocol ReplayPresenterMaking: AnyObject {
    func presenter(tripId: String, source: ReplayOpenSource) -> any ReplayPresenting
    /// S-RPL-02 for one event (`CoreServices.evidencePresenter`).
    func evidencePresenter(tripId: String, eventId: String) -> any EvidencePresenting
    /// S-SCO-04 (`CoreServices.tipDetailPresenter`): the trip's own tip when `tipId` is nil.
    func tipPresenter(tripId: String, tipId: String?, eventId: String?) -> any TipDetailPresenting
}

/// Whether the phone is online, for the map's offline fallback (F-RPL-01 AC5, EC-NET).
@MainActor
public protocol ConnectivityMonitoring: AnyObject {
    /// Emits the current state first, then every change. `true` while a network path is available.
    func onlineUpdates() -> AsyncStream<Bool>
}
