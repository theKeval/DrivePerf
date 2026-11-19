import CoreLocation
import DPSharedBridge
import Foundation
import Observation

/// One route stretch ready for MapKit, built when the zoom bucket changes, never per frame.
public struct ReplayPolyline: Identifiable, Sendable {
    public var id: Int
    public var style: ReplaySegmentStyle
    public var coordinates: [CLLocationCoordinate2D]
}

/// S-RPL-01 Trip Replay (F-RPL-01) with the data-quality overlay (F-RPL-08), as a thin wrapper over the shared
/// `ReplayPresenter` (ADR-002 section 4). The presenter owns playback, scrubbing, the end of the trip, the Pro gate
/// and the analytics; this owns the frame loop (each display frame reads the playhead, then the core's `frameAt` and
/// `spanAt`), the map's zoom buckets and the offline switch.
@MainActor
@Observable
public final class ReplayViewModel {
    public enum Phase: Equatable, Sendable {
        case loading, ready, locked, failed
    }

    /// The presenter's state; it changes on load and on intents only.
    public private(set) var state = ReplayScreenState()
    /// Trip seconds at the marker, read from the presenter's playhead each frame.
    public private(set) var tS: Double = 0
    /// The core's `frameAt(tS)`; iOS adds no interpolation of its own.
    public private(set) var frame: ReplayFrame?
    /// The core's `spanAt(tS)` for the chip (F-RPL-08 AC2), or nil on an observed stretch.
    public private(set) var activeSpan: ReplaySpan?
    /// False with no network path: the plain canvas replaces the map (AC5).
    public private(set) var isOnline = true
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
    /// The presenter asked for S-DRV-05: the trip is being recorded (AC6).
    public private(set) var driveScreenRequested = false
    /// Event pins pulsing now, by event ID, with when the pulse started (F-RPL-03 AC2).
    public private(set) var pulses: [String: Date] = [:]
    /// S-RPL-02 while it is open.
    public private(set) var evidence: EvidenceViewModel?
    /// Short confirmations from the sheets ("Counted again · Smoothness recomputed").
    public private(set) var toast: String?

    private let presenter: any ReplayPresenting
    private let connectivity: (any ConnectivityMonitoring)?
    private let display: DisplaySettingsModel
    private let sheets: ReplaySheetSource?
    private var segmentCache: [Int: [ReplaySegment]] = [:]
    private var evidenceTask: Task<Void, Never>?

    /// How much wider than the route the fitted map is.
    static let fitPadding = 1.3

    public init(
        presenter: any ReplayPresenting, connectivity: (any ConnectivityMonitoring)? = nil,
        display: DisplaySettingsModel? = nil, sheets: ReplaySheetSource? = nil
    ) {
        self.presenter = presenter
        self.connectivity = connectivity
        self.display = display ?? DisplaySettingsModel()
        self.sheets = sheets
        apply(presenter.state)
    }

    isolated deinit {
        evidenceTask?.cancel()
        evidence?.close()
        presenter.close()
    }

    // MARK: Observing

    /// Follows the presenter's state (`.task {}`).
    public func observe() async {
        for await next in presenter.states() { apply(next) }
    }

    /// Follows the presenter's one-shot navigation (`.task {}`).
    public func observeNavigation() async {
        for await event in presenter.navigation() {
            switch event {
            case .showDriveScreen: driveScreenRequested = true
            case .openEvidence(let eventId): openEvidence(eventId)
            }
        }
    }

    /// Follows the network: offline shows the plain canvas.
    public func observeConnectivity() async {
        guard let connectivity else { return }
        for await online in connectivity.onlineUpdates() { isOnline = online }
    }

    func apply(_ next: ReplayScreenState) {
        let newTrack = next.track.map { ObjectIdentifier($0) } != state.track.map { ObjectIdentifier($0) }
        state = next
        if newTrack, let track = next.track { prepare(track) }
        refresh(at: presenter.nowNs())
    }

    /// AC1: a new replay is fitted, drawn whole, and its marker sits at the start.
    private func prepare(_ track: any ReplayTrack) {
        segmentCache = [:]
        zoomBucket = track.zoomBucket(
            longitudeDelta: max(track.bbox.maxLng - track.bbox.minLng, 1e-4) * Self.fitPadding, viewWidth: 402)
        segments = segments(for: zoomBucket)
        let present = Set(segments.map(\.style)).union(track.spans.map(\.style))
        stylesPresent = ReplaySegmentStyle.allCases.filter(present.contains)
    }

    public var phase: Phase {
        if state.track != nil { return .ready }
        switch state.missing {
        case .locked: return .locked
        case .notFound, .recording: return .failed
        case nil: return .loading
        }
    }

    public var isPlaying: Bool { state.playing }
    public var isScrubbing: Bool { state.scrubbing }
    public var speed: PlaybackSpeed { state.speed }
    public var durationS: Double { state.track?.durationS ?? 0 }
    public var spans: [ReplaySpan] { state.track?.spans ?? [] }
    public var bounds: ReplayBounds? { state.track?.bbox }
    public var summaryOnly: Bool { state.track?.summaryOnly ?? false }

    // MARK: Frame loop (AC4)

    /// One display frame: the playhead on the presenter's clock, the core's frame and span there, then a tick so the
    /// presenter can stop at the end.
    public func frameTick() {
        let now = presenter.nowNs()
        refresh(at: now)
        presenter.dispatch(.tick(now))
    }

    private func refresh(at now: Int64) {
        guard let track = state.track else { return }
        let t = presenter.playhead(at: now)
        if state.playing, let overlay = state.overlay {
            let passed = overlay.crossed(from: tS, to: t)
            if !passed.isEmpty {
                let at = Date()
                for id in passed { pulses[id] = at }
            }
        }
        tS = t
        frame = track.frame(at: t)
        activeSpan = track.span(at: t)
    }

    // MARK: Intents (AC2, AC3)

    public func togglePlay() {
        send(state.playing ? .pause : .play)
    }

    public func select(_ speed: PlaybackSpeed) {
        guard speed != state.speed else { return }
        send(.setSpeed(speed))
    }

    public func beginScrub() { send(.scrubStart) }

    /// The marker follows the thumb in the same frame.
    public func scrub(to tS: Double) {
        if !state.scrubbing { send(.scrubStart) }
        send(.scrubTo(tS))
    }

    public func endScrub() { send(.scrubEnd) }

    /// VoiceOver and keyboard: ±30 s, a one-off seek.
    public func step(by seconds: Double) { send(.scrubTo(tS + seconds)) }

    public func legendOpened() { send(.openLegend) }

    // MARK: Pins and sheets (DRIVE-34, DRIVE-39, DRIVE-203)

    public var overlay: (any ReplayOverlayReading)? { state.overlay }
    public var selection: ReplaySelectionInfo? { state.selection }
    public var pinSheet: ReplayPinSheetInfo? { state.pinSheet }

    /// An event pin: pause there and open S-RPL-02.
    public func selectEvent(_ id: String) { send(.selectEvent(id)) }
    public func selectStop(_ number: Int) { send(.selectStop(number)) }
    public func selectMaxSpeed() { send(.selectMaxSpeed) }
    /// S-RPL-03 "Jump to this moment".
    public func jumpToPin() { send(.jumpToPin) }
    /// S-RPL-03 closed.
    public func clearSelection() { send(.clearSelection) }

    public func toastShown() { toast = nil }

    /// Where the map centres while the evidence sheet is open (DRIVE-39 AC5).
    public var focus: ReplayEventPinInfo? {
        guard case .event(let id) = state.selection else { return nil }
        return state.overlay?.eventPins.first { $0.id == id }
    }

    private func openEvidence(_ eventId: String) {
        guard let sheets else { return }
        closeEvidenceSession()
        let model = EvidenceViewModel(presenter: sheets.evidence(eventId), tips: sheets.tip)
        evidence = model
        evidenceTask = Task { [weak self] in
            for await event in model.navigationEvents() {
                guard let self else { return }
                self.onEvidence(event)
            }
        }
    }

    private func onEvidence(_ event: EvidenceNavigation) {
        switch event {
        case .showEvent(let id): send(.showEvent(id))
        case .feedbackChanged: send(.reloadDetails)
        case .openTip(let tipId, let eventId): evidence?.openTip(tipId: tipId, eventId: eventId)
        case .toast(let text): toast = text
        case .close: closeEvidence()
        }
    }

    /// S-RPL-02 closed: the selection clears.
    public func closeEvidence() {
        closeEvidenceSession()
        send(.clearSelection)
    }

    private func closeEvidenceSession() {
        evidenceTask?.cancel()
        evidenceTask = nil
        evidence?.close()
        evidence = nil
    }

    /// The presenter updates its state at once; the marker is redrawn from it now rather than on the next frame.
    private func send(_ action: ReplayAction) {
        presenter.dispatch(action)
        apply(presenter.state)
    }

    /// The camera settled: simplify for its zoom bucket (cached per bucket).
    public func cameraSettled(longitudeDelta: Double, viewWidth: Double) {
        guard let track = state.track else { return }
        let bucket = track.zoomBucket(longitudeDelta: longitudeDelta, viewWidth: viewWidth)
        guard bucket != zoomBucket else { return }
        zoomBucket = bucket
        segments = segments(for: bucket)
    }

    private func segments(for bucket: Int) -> [ReplaySegment] {
        if let cached = segmentCache[bucket] { return cached }
        let value = state.track?.segments(zoomBucket: bucket) ?? []
        segmentCache[bucket] = value
        return value
    }

    // MARK: Overlay (F-RPL-08)

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

    // MARK: Text

    /// "12:05" or "1:02:05".
    public static func clock(_ seconds: Double) -> String {
        let total = Int(max(seconds, 0).rounded(.down))
        let h = total / 3600
        let m = (total % 3600) / 60
        let s = total % 60
        return h > 0 ? String(format: "%d:%02d:%02d", h, m, s) : String(format: "%d:%02d", m, s)
    }

    /// The readout's speed number (the shared overlay's, in the user's units), "—" with no measured speed.
    public var readoutSpeed: String {
        guard let overlay = state.overlay else { return speedText }
        return overlay.speedText(frame?.style == .noGps ? nil : frame?.speedMps)
    }

    /// The readout's distance so far, scaled to the trip's own distance (F-RPL-02 AC1).
    public var readoutDistance: String? { state.overlay?.distanceText(at: tS) }

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
