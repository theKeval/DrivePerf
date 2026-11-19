import DPSharedBridge
import DrivePerfShared
import Foundation

/// `ReplayPresenterMaking` over `CoreServices.replayPresenter` (DRIVE-200, ADR-002 section 4). Like the other core
/// bridges it exists before the core starts; a Replay opened before `attach` finds nothing.
@MainActor
final class CoreReplayPresenters: ReplayPresenterMaking {
    private var services: CoreServices?

    func attach(_ services: CoreServices) {
        self.services = services
    }

    func presenter(tripId: String, source: ReplayOpenSource) -> any ReplayPresenting {
        guard let services else { return UnavailableReplayPresenter() }
        // DRIVE-34, DRIVE-203: the overload with the pins, stats and elevation.
        return CoreReplayPresenter(
            services.replayPresenter(
                tripId: tripId, source: CoreReplayPresenter.kotlin(source), region: Self.region,
                format: CoreReplayTimeFormat()))
    }

    func evidencePresenter(tripId: String, eventId: String) -> any EvidencePresenting {
        guard let services else { return UnavailableEvidencePresenter(eventId: eventId) }
        return CoreEvidencePresenter(
            services.evidencePresenter(tripId: tripId, eventId: eventId, region: Self.region, format: CoreReplayTimeFormat()))
    }

    func tipPresenter(tripId: String, tipId: String?, eventId: String?) -> any TipDetailPresenting {
        guard let services else { return UnavailableTipPresenter() }
        return CoreTipDetailPresenter(
            services.tipDetailPresenter(tripId: tripId, tipId: tipId, eventId: eventId, region: Self.region))
    }

    /// The default units' region until the user chooses (as the Drives presenter uses).
    private static var region: String { Locale.current.region?.identifier ?? "" }
}

/// One Kotlin `ReplayPresenter` (main-thread scope) as `ReplayPresenting`. The playhead and the end of the trip stay in
/// the core: Swift reads `playheadAt` and sends `Tick` each frame.
@MainActor
final class CoreReplayPresenter: ReplayPresenting {
    private let presenter: DrivePerfShared.ReplayPresenter
    /// One Swift track per Kotlin `Replay`, so a state change never rebuilds the route.
    private var cached: (replay: DrivePerfShared.Replay, track: CoreReplayTrack)?
    /// One Swift overlay per Kotlin `ReplayOverlay`, so playback never rebuilds the pins.
    private var cachedOverlay: (overlay: DrivePerfShared.ReplayOverlay, swift: CoreReplayOverlay)?

    init(_ presenter: DrivePerfShared.ReplayPresenter) {
        self.presenter = presenter
    }

    /// The generic `Presenter<S, …>` reaches Swift with an optional state; the presenter always has one.
    var state: ReplayScreenState { swift(presenter.state.value) }

    func states() -> AsyncStream<ReplayScreenState> {
        let flow = UncheckedSendable(presenter.state)
        return AsyncStream { continuation in
            let task = Task { @MainActor [weak self] in
                for await value in flow.value {
                    guard let self else { break }
                    continuation.yield(swift(value))
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func navigation() -> AsyncStream<ReplayNavigation> {
        let flow = UncheckedSendable(presenter.effects)
        return AsyncStream { continuation in
            let task = Task { @MainActor in
                for await effect in flow.value {
                    if effect is ReplayEffectShowDriveScreen {
                        continuation.yield(.showDriveScreen)
                    } else if let open = effect as? ReplayEffectOpenEvidence {
                        continuation.yield(.openEvidence(eventId: open.eventId))
                    }
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func dispatch(_ action: ReplayAction) {
        presenter.dispatch(intent: Self.kotlin(action))
    }

    func nowNs() -> Int64 { presenter.nowNs() }

    func playhead(at nowNs: Int64) -> Double {
        presenter.state.value?.playheadAt(nowNs: nowNs) ?? 0
    }

    func close() { presenter.close() }

    private func swift(_ value: DrivePerfShared.ReplayState?) -> ReplayScreenState {
        guard let value else { return ReplayScreenState() }
        return ReplayScreenState(
            loading: value.loading, track: track(value.replay), missing: value.unavailable.map(Self.swift),
            playing: value.playing, speed: CoreReplayTrack.swift(value.speed), scrubbing: value.scrubbing,
            overlay: overlay(value.overlay), selection: value.selection.flatMap(Self.swift),
            pinSheet: value.pinSheet.map(Self.swift))
    }

    private func overlay(_ overlay: DrivePerfShared.ReplayOverlay?) -> (any ReplayOverlayReading)? {
        guard let overlay else { return nil }
        if let cachedOverlay, cachedOverlay.overlay === overlay { return cachedOverlay.swift }
        let swift = CoreReplayOverlay(overlay)
        cachedOverlay = (overlay, swift)
        return swift
    }

    static func swift(_ selection: any DrivePerfShared.ReplaySelection) -> ReplaySelectionInfo? {
        if let event = selection as? ReplaySelectionEvent { return .event(event.eventId) }
        if let stop = selection as? ReplaySelectionStop { return .stop(Int(stop.number)) }
        if selection is ReplaySelectionMaxSpeed { return .maxSpeed }
        return nil
    }

    static func swift(_ sheet: DrivePerfShared.ReplayPinSheet) -> ReplayPinSheetInfo {
        ReplayPinSheetInfo(
            kind: sheet.kind == .stop ? .stop : .maxSpeed, title: sheet.title, subtitle: sheet.subtitle,
            value: sheet.value, unit: sheet.unit, provenance: CoreEvidenceMapping.swift(sheet.provenance),
            rows: sheet.rows.map { ReplayPinSheetInfo.Row(label: $0.label, value: $0.value) }, note: sheet.note,
            accessibilityLabel: sheet.accessibilityLabel)
    }

    private func track(_ replay: DrivePerfShared.Replay?) -> (any ReplayTrack)? {
        guard let replay else { return nil }
        if let cached, cached.replay === replay { return cached.track }
        let track = CoreReplayTrack(replay)
        cached = (replay, track)
        return track
    }

    static func kotlin(_ source: ReplayOpenSource) -> DrivePerfShared.ReplaySource {
        switch source {
        case .tripDetail: .tripDetail
        case .story: .story
        }
    }

    static func swift(_ reason: DrivePerfShared.ReplayUnavailable) -> ReplayMissingReason {
        switch reason {
        case .notFound: .notFound
        case .recording: .recording
        case .locked: .locked
        }
    }

    static func kotlin(_ action: ReplayAction) -> any DrivePerfShared.ReplayIntent {
        switch action {
        case .play: ReplayIntentPlay.shared
        case .pause: ReplayIntentPause.shared
        case .setSpeed(let speed): ReplayIntentSetSpeed(speed: CoreReplayTrack.kotlin(speed))
        case .scrubStart: ReplayIntentScrubStart.shared
        case .scrubTo(let tS): ReplayIntentScrubTo(tS: tS)
        case .scrubEnd: ReplayIntentScrubEnd.shared
        case .tick(let nowNs): ReplayIntentTick(nowNs: nowNs)
        case .openLegend: ReplayIntentOpenLegend.shared
        case .selectEvent(let id): ReplayIntentSelectEvent(eventId: id)
        case .showEvent(let id): ReplayIntentShowEvent(eventId: id)
        case .selectStop(let number): ReplayIntentSelectStop(number: Int32(number))
        case .selectMaxSpeed: ReplayIntentSelectMaxSpeed.shared
        case .jumpToPin: ReplayIntentJumpToPin.shared
        case .clearSelection: ReplayIntentClearSelection.shared
        case .reloadDetails: ReplayIntentReloadDetails.shared
        }
    }
}

/// Before the core has started there is no replay to show.
@MainActor
final class UnavailableReplayPresenter: ReplayPresenting {
    let state = ReplayScreenState(loading: false, missing: .notFound)
    func states() -> AsyncStream<ReplayScreenState> { AsyncStream { $0.yield(state); $0.finish() } }
    func navigation() -> AsyncStream<ReplayNavigation> { AsyncStream { $0.finish() } }
    func dispatch(_ action: ReplayAction) {}
    func nowNs() -> Int64 { 0 }
    func playhead(at nowNs: Int64) -> Double { 0 }
    func close() {}
}

/// One Kotlin `Replay` as a `ReplayTrack`. The Kotlin object is immutable, so it is safe to read from any thread.
final class CoreReplayTrack: ReplayTrack, @unchecked Sendable {
    private let replay: DrivePerfShared.Replay
    let tripId: String
    let bbox: DPSharedBridge.ReplayBounds
    let durationS: Double
    let spans: [DPSharedBridge.ReplaySpan]
    let summaryOnly: Bool

    init(_ replay: DrivePerfShared.Replay) {
        self.replay = replay
        tripId = replay.tripId
        bbox = DPSharedBridge.ReplayBounds(
            minLat: replay.bbox.minLat, minLng: replay.bbox.minLng, maxLat: replay.bbox.maxLat,
            maxLng: replay.bbox.maxLng)
        durationS = replay.durationS
        spans = replay.spans.map(Self.swift)
        summaryOnly = replay.summaryOnly
    }

    func segments(zoomBucket: Int) -> [DPSharedBridge.ReplaySegment] {
        replay.segments(zoomBucket: Int32(zoomBucket)).map { segment in
            DPSharedBridge.ReplaySegment(
                style: Self.style(segment.style),
                points: segment.points.map {
                    DPSharedBridge.ReplayPoint(tS: $0.tS, lat: $0.lat, lng: $0.lng, speedMps: $0.speedMps?.doubleValue)
                })
        }
    }

    func frame(at tS: Double) -> DPSharedBridge.ReplayFrame {
        let f = replay.frameAt(tS: tS)
        return DPSharedBridge.ReplayFrame(
            tS: f.tS, lat: f.lat, lng: f.lng, speedMps: f.speedMps?.doubleValue, bearingDeg: f.bearingDeg?.doubleValue,
            style: Self.style(f.style))
    }

    func span(at tS: Double) -> DPSharedBridge.ReplaySpan? {
        replay.spanAt(tS: tS).map(Self.swift)
    }

    func zoomBucket(longitudeDelta: Double, viewWidth: Double) -> Int {
        Int(DrivePerfShared.Replay.companion.zoomBucketFor(longitudeDeltaDeg: longitudeDelta, viewWidthPoints: viewWidth))
    }

    static func swift(_ span: DrivePerfShared.ReplaySpan) -> DPSharedBridge.ReplaySpan {
        DPSharedBridge.ReplaySpan(style: style(span.style), t0S: span.t0S, t1S: span.t1S, label: span.label)
    }

    static func style(_ style: DrivePerfShared.SegmentStyle) -> ReplaySegmentStyle {
        switch style {
        case .observed: .observed
        case .estimated: .estimated
        case .noGps: .noGps
        case .excluded: .excluded
        }
    }

    static func swift(_ speed: DrivePerfShared.PlaybackSpeed) -> DPSharedBridge.PlaybackSpeed {
        switch speed {
        case .auto: .auto
        case .x1: .x1
        case .x10: .x10
        case .x30: .x30
        }
    }

    static func kotlin(_ speed: DPSharedBridge.PlaybackSpeed) -> DrivePerfShared.PlaybackSpeed {
        switch speed {
        case .auto: .auto
        case .x1: .x1
        case .x10: .x10
        case .x30: .x30
        }
    }
}
