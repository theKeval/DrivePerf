import CoreLocation
import DPSharedBridge
import Foundation
import Observation

/// The Pro lock for Replay of drives older than 30 days (F-RPL-01 Free/Pro, Gate 2 OQ-06). Entitlements do not exist
/// yet, so the app passes `ReplayProLockNotBuilt` and every replay opens. The paywall work replaces it with a lock that
/// reads the entitlement; S-RPL-01 already shows its Lock state when this returns true.
public protocol ReplayProLock: Sendable {
    func isLocked(tripStartedAt: Date, now: Date) -> Bool
}

/// The seam until entitlements exist: nothing is locked.
public struct ReplayProLockNotBuilt: ReplayProLock {
    public init() {}
    public func isLocked(tripStartedAt: Date, now: Date) -> Bool { false }
}

/// One route stretch ready for MapKit, built when the zoom bucket changes, never per frame.
public struct ReplayPolyline: Identifiable, Sendable {
    public var id: Int
    public var style: ReplaySegmentStyle
    public var coordinates: [CLLocationCoordinate2D]
}

/// S-RPL-01 Trip Replay (F-RPL-01) with the data-quality overlay (F-RPL-08): loads the shared replay, plays it on the
/// display clock, scrubs, and says which overlay span the marker is in.
@MainActor
@Observable
public final class ReplayViewModel {
    public enum Phase: Equatable, Sendable {
        case loading, ready, locked, failed
    }

    public let tripId: String
    public private(set) var phase = Phase.loading
    public private(set) var isPlaying = false
    public private(set) var speed = PlaybackSpeed.auto
    /// Trip seconds at the marker.
    public private(set) var tS: Double = 0
    public private(set) var frame: ReplayFrame?
    public private(set) var isScrubbing = false
    /// False with no network path: the plain canvas replaces the map (AC5).
    public private(set) var isOnline = true
    public private(set) var durationS: Double = 0
    public private(set) var spans: [ReplaySpan] = []
    public private(set) var bounds: ReplayBounds?
    /// Raw data was removed (EC-STO): the summary route only.
    public private(set) var summaryOnly = false
    /// The route for the current zoom bucket.
    public private(set) var segments: [ReplaySegment] = [] {
        didSet {
            polylines = segments.enumerated().map { index, segment in
                ReplayPolyline(
                    id: index, style: segment.style,
                    coordinates: segment.points.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lng) })
            }
        }
    }
    /// `segments` as map coordinates.
    public private(set) var polylines: [ReplayPolyline] = []
    public private(set) var zoomBucket = ReplayZoomMath.maxZoom
    /// The styles this trip draws, for the legend and its order.
    public private(set) var stylesPresent: [ReplaySegmentStyle] = []

    private let loader: any ReplayLoading
    private let analytics: (any ReplayAnalytics)?
    private let connectivity: (any ConnectivityMonitoring)?
    private let display: DisplaySettingsModel
    private let lock: any ReplayProLock
    private let tripStartedAt: Date?
    private let source: String
    private let now: @MainActor () -> Date
    private var track: (any ReplayTrack)?
    private var table: ReplayFrameTable?
    private var segmentCache: [Int: [ReplaySegment]] = [:]
    private var rate: Double = 1
    private var lastTick: TimeInterval?
    private var resumeAfterScrub = false
    private var didScrub = false
    private var opened = false

    /// The longest display-clock step one tick may play, so a stall or a return from the background never leaps.
    static let maxTickS: TimeInterval = 0.25

    public init(
        tripId: String, loader: any ReplayLoading, analytics: (any ReplayAnalytics)? = nil,
        connectivity: (any ConnectivityMonitoring)? = nil, display: DisplaySettingsModel? = nil,
        lock: any ReplayProLock = ReplayProLockNotBuilt(), tripStartedAt: Date? = nil, source: String = "trip_detail",
        now: @escaping @MainActor () -> Date = { Date() }
    ) {
        self.tripId = tripId
        self.loader = loader
        self.analytics = analytics
        self.connectivity = connectivity
        self.display = display ?? DisplaySettingsModel()
        self.lock = lock
        self.tripStartedAt = tripStartedAt
        self.source = source
        self.now = now
    }

    // MARK: Loading

    /// AC1: fits the route, draws all of it, and puts the marker at the start, paused.
    public func load() async {
        if !opened {
            opened = true
            analytics?.track(.opened(source: source))
        }
        // Coming back to the screen keeps the position; only a first load or Try again fetches.
        guard phase != .ready else { return }
        if let tripStartedAt, lock.isLocked(tripStartedAt: tripStartedAt, now: now()) {
            phase = .locked
            return
        }
        phase = .loading
        guard let track = await loader.replay(tripId: tripId) else {
            phase = .failed
            return
        }
        let table = await Self.sample(track)
        apply(track, table: table)
    }

    /// Samples the marker off the main actor: one core call per sample, once.
    private nonisolated static func sample(_ track: any ReplayTrack) async -> ReplayFrameTable {
        await Task.detached(priority: .userInitiated) {
            ReplayFrameTable(durationS: track.durationS) { track.frame(at: $0) }
        }.value
    }

    func apply(_ track: any ReplayTrack, table: ReplayFrameTable) {
        self.track = track
        self.table = table
        durationS = track.durationS
        spans = track.spans.sorted { $0.t0S < $1.t0S }
        bounds = track.bbox
        summaryOnly = track.summaryOnly
        rate = track.playbackRate(speed)
        segmentCache = [:]
        // Until the map reports its camera: the fitted route on a phone-wide map.
        zoomBucket = track.zoomBucket(
            longitudeDelta: max(track.bbox.maxLng - track.bbox.minLng, 1e-4) * Self.fitPadding, viewWidth: 402)
        segments = segments(for: zoomBucket)
        let present = Set(segments.map(\.style)).union(spans.map(\.style))
        stylesPresent = ReplaySegmentStyle.allCases.filter(present.contains)
        tS = 0
        isPlaying = false
        lastTick = nil
        frame = table.frame(at: 0)
        phase = .ready
    }

    /// Follows the network: offline shows the plain canvas.
    public func observeConnectivity() async {
        guard let connectivity else { return }
        for await online in connectivity.onlineUpdates() { isOnline = online }
    }

    /// How much wider than the route the fitted map is.
    static let fitPadding = 1.3

    /// The camera settled: simplify for its zoom bucket (cached per bucket).
    public func cameraSettled(longitudeDelta: Double, viewWidth: Double) {
        guard let track else { return }
        let bucket = track.zoomBucket(longitudeDelta: longitudeDelta, viewWidth: viewWidth)
        guard bucket != zoomBucket else { return }
        zoomBucket = bucket
        segments = segments(for: bucket)
    }

    private func segments(for bucket: Int) -> [ReplaySegment] {
        if let cached = segmentCache[bucket] { return cached }
        let value = track?.segments(zoomBucket: bucket) ?? []
        segmentCache[bucket] = value
        return value
    }

    // MARK: Playback (AC2, AC4)

    public func togglePlay() {
        isPlaying ? pause() : play()
    }

    /// Plays from the marker; at the end it restarts.
    public func play() {
        guard phase == .ready, !isScrubbing, !isPlaying else { return }
        if tS >= durationS { seek(0) }
        isPlaying = true
        lastTick = nil
        analytics?.track(.played(speed: speed))
    }

    /// Stops in place.
    public func pause() {
        isPlaying = false
        lastTick = nil
    }

    /// Changes the rate at once, playing or not.
    public func select(_ speed: PlaybackSpeed) {
        guard speed != self.speed else { return }
        self.speed = speed
        rate = track?.playbackRate(speed) ?? 1
        if isPlaying { analytics?.track(.played(speed: speed)) }
    }

    /// One display frame (TimelineView `.animation`): `time` is the display clock in seconds. Allocation-free.
    public func tick(at time: TimeInterval) {
        guard isPlaying, let table else { return }
        guard let last = lastTick else {
            lastTick = time
            return
        }
        lastTick = time
        let dt = min(max(time - last, 0), Self.maxTickS)
        var next = tS + dt * rate
        var finished = false
        if next >= durationS {
            next = durationS
            finished = true
        }
        tS = next
        frame = table.frame(at: next)
        if finished {
            pause()
            analytics?.track(.completed)
        }
    }

    // MARK: Scrubbing (AC3)

    /// The thumb was grabbed: playback holds, and resumes on release if it was playing.
    public func beginScrub() {
        guard phase == .ready, !isScrubbing else { return }
        resumeAfterScrub = isPlaying
        isPlaying = false
        lastTick = nil
        isScrubbing = true
        didScrub = false
    }

    /// The marker and readouts follow the thumb in the same frame.
    public func scrub(to tS: Double) {
        guard phase == .ready else { return }
        if !isScrubbing { beginScrub() }
        didScrub = true
        seek(tS)
    }

    /// Released: back to the previous play state.
    public func endScrub() {
        guard isScrubbing else { return }
        isScrubbing = false
        if didScrub { analytics?.track(.scrubbed) }
        if resumeAfterScrub, tS < durationS {
            isPlaying = true
            lastTick = nil
        }
        resumeAfterScrub = false
    }

    /// VoiceOver and keyboard: ±30 s.
    public func step(by seconds: Double) {
        guard phase == .ready else { return }
        seek(tS + seconds)
        analytics?.track(.scrubbed)
    }

    private func seek(_ value: Double) {
        let clamped = min(max(value, 0), durationS)
        tS = clamped
        frame = table?.frame(at: clamped)
    }

    // MARK: Overlay (F-RPL-08)

    /// The span the marker is in, if any.
    public var activeSpan: ReplaySpan? {
        spans.first { $0.contains(tS, durationS: durationS) }
    }

    /// The small label while the marker is inside a span (AC2): "Estimated start", "No GPS", "Phone moved — not scored".
    public var spanLabel: String? {
        activeSpan.flatMap { $0.label ?? Self.label($0.style) }
    }

    /// The marker hides in a GPS gap and reappears after it.
    public var isMarkerHidden: Bool {
        frame?.style == .noGps
    }

    public static func label(_ style: ReplaySegmentStyle) -> String? {
        switch style {
        case .observed: nil
        case .estimated: "Estimated start"
        case .noGps: "No GPS"
        case .excluded: "Phone moved \u{2014} not scored"
        }
    }

    /// The legend's words for each style.
    public static func legendLabel(_ style: ReplaySegmentStyle) -> String {
        switch style {
        case .observed: "Measured"
        case .estimated: "Estimated"
        case .noGps: "No GPS"
        case .excluded: "Not scored"
        }
    }

    public func legendOpened() {
        analytics?.track(.legendOpened)
    }

    // MARK: Text

    /// "12:05" or "1:02:05".
    public static func clock(_ seconds: Double) -> String {
        let total = Int(max(seconds, 0).rounded(.down))
        let h = total / 3600
        let m = (total % 3600) / 60
        let s = total % 60
        return h > 0 ? String(format: "%d:%02d:%02d", h, m, s) : String(format: "%d:%02d", m, s)
    }

    /// The speed at the marker in the user's units; "—" in a GPS gap or with no speed (EC-GPS).
    public var speedText: String {
        guard let mps = frame?.speedMps, frame?.style != .noGps else { return "\u{2014}" }
        let info = display.metric("maxSpeed")
        return "\(info.format(mps)) \(info.unit ?? "")".trimmingCharacters(in: .whitespaces)
    }

    /// "Replay position, 12 min of 33 min. Speed 92 km/h."
    public var scrubberAccessibilityValue: String {
        let at = Int((tS / 60).rounded(.down))
        let of = Int((durationS / 60).rounded(.up))
        return "\(at) min of \(of) min. Speed \(speedText)."
    }

    /// Fraction of the trip played, for the scrubber.
    public var progress: Double {
        durationS > 0 ? tS / durationS : 0
    }
}
