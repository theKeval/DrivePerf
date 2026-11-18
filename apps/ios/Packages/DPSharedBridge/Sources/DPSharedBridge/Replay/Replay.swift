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

    /// The analytics value for `replay_played {speed}`.
    public var analyticsValue: String {
        switch self {
        case .auto: "auto"
        case .x1: "1x"
        case .x10: "10x"
        case .x30: "30x"
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

/// Kotlin `Replay`: one trip, ready to draw and play.
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
    /// The marker at `tS`. Never interpolates across a `noGps` gap.
    func frame(at tS: Double) -> ReplayFrame
    /// Trip seconds per wall-clock second.
    func playbackRate(_ speed: PlaybackSpeed) -> Double
    /// Kotlin `Replay.zoomBucketFor`: the simplification bucket for a map `viewWidth` points wide showing
    /// `longitudeDelta` degrees of longitude.
    func zoomBucket(longitudeDelta: Double, viewWidth: Double) -> Int
}

/// Kotlin `Replay.MAX_ZOOM` and the web-Mercator bucket formula, for fakes and previews that have no core.
public enum ReplayZoomMath {
    public static let maxZoom = 22

    public static func bucket(longitudeDelta: Double, viewWidth: Double) -> Int {
        guard longitudeDelta > 0, viewWidth > 0 else { return maxZoom }
        let zoom = log2(360 * viewWidth / (256 * longitudeDelta)).rounded(.down)
        guard zoom.isFinite else { return maxZoom }
        return min(max(Int(zoom), 0), maxZoom)
    }
}

/// Kotlin `ReplayRepository`.
@MainActor
public protocol ReplayLoading: AnyObject {
    /// The replay for a trip, or nil when there is nothing to draw.
    func replay(tripId: String) async -> (any ReplayTrack)?
}

/// The Replay analytics events (S-RPL-01, F-RPL-01, F-RPL-08). Only these keys, with no values beyond them.
public enum ReplayAnalyticsEvent: Equatable, Sendable {
    case opened(source: String)
    case played(speed: PlaybackSpeed)
    case scrubbed
    case completed
    case legendOpened

    public var name: String {
        switch self {
        case .opened: "replay_opened"
        case .played: "replay_played"
        case .scrubbed: "replay_scrubbed"
        case .completed: "replay_completed"
        case .legendOpened: "quality_overlay_legend_opened"
        }
    }

    public var properties: [String: String] {
        switch self {
        case .opened(let source): ["source": source]
        case .played(let speed): ["speed": speed.analyticsValue]
        case .scrubbed, .completed, .legendOpened: [:]
        }
    }
}

/// Sends Replay events through the consent-gated core analytics.
@MainActor
public protocol ReplayAnalytics: AnyObject {
    func track(_ event: ReplayAnalyticsEvent)
}

/// Whether the phone is online, for the map's offline fallback (F-RPL-01 AC5, EC-NET).
@MainActor
public protocol ConnectivityMonitoring: AnyObject {
    /// Emits the current state first, then every change. `true` while a network path is available.
    func onlineUpdates() -> AsyncStream<Bool>
}
