import DPSharedBridge
import DrivePerfShared
import Foundation

/// `AnalysisPresenterMaking` over `CoreServices.analysisPresenter` (S-ANL-01, DRIVE-45, ADR-002 section 4). Like the
/// other core bridges it exists before the core starts; an Analysis opened before `attach` finds nothing.
@MainActor
final class CoreAnalysisPresenters: AnalysisPresenterMaking {
    private var services: CoreServices?

    func attach(_ services: CoreServices) {
        self.services = services
    }

    func presenter(tripId: String) -> any AnalysisPresenting {
        guard let services else { return UnavailableAnalysisPresenter(tripId: tripId) }
        let region = Locale.current.region?.identifier ?? ""
        return CoreAnalysisPresenter(services.analysisPresenter(tripId: tripId, source: .tripDetail, region: region))
    }
}

/// One Kotlin `AnalysisPresenter` (main-thread scope) as `AnalysisPresenting`. The timeline and map are wrapped once
/// per Kotlin object, so a state change that keeps them never converts their points again.
@MainActor
final class CoreAnalysisPresenter: AnalysisPresenting {
    private let presenter: DrivePerfShared.AnalysisPresenter
    private var timeline: (kotlin: DrivePerfShared.AnalysisTimeline, swift: CoreAnalysisTimeline)?
    private var map: (kotlin: DrivePerfShared.AnalysisMap, swift: CoreAnalysisMap)?
    private var gg: (kotlin: DrivePerfShared.GgView, swift: AnalysisGgView)?
    private static let copy = CoreAnalysisPresenter.screenCopy()

    init(_ presenter: DrivePerfShared.AnalysisPresenter) {
        self.presenter = presenter
    }

    var state: AnalysisScreenState { swift(presenter.state.value) }

    func states() -> AsyncStream<AnalysisScreenState> {
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

    func navigation() -> AsyncStream<AnalysisNavigation> {
        let flow = UncheckedSendable(presenter.effects)
        return AsyncStream { continuation in
            let task = Task { @MainActor in
                for await effect in flow.value {
                    if effect is AnalysisEffectClose {
                        continuation.yield(.close)
                    } else if let message = effect as? AnalysisEffectMessage {
                        continuation.yield(.message(message.text))
                    } else if let replay = effect as? AnalysisEffectOpenReplay {
                        continuation.yield(.openReplay(tripId: replay.tripId))
                    }
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func dispatch(_ action: AnalysisAction) {
        presenter.dispatch(intent: Self.kotlin(action))
    }

    func close() { presenter.close() }

    // MARK: Kotlin → Swift

    /// The generic `Presenter<S, …>` reaches Swift with an optional state; the presenter always has one.
    private func swift(_ value: DrivePerfShared.AnalysisState?) -> AnalysisScreenState {
        guard let value else { return AnalysisScreenState() }
        return AnalysisScreenState(
            tripId: value.tripId, loading: value.loading, loadingText: value.loadingText,
            missing: value.unavailable != nil, summaryOnly: value.summaryOnly, axis: Self.swift(value.axis),
            zoomLabel: value.zoomLabel, canZoomIn: value.canZoomIn, canZoomOut: value.canZoomOut, cursorS: value.cursorS,
            channelOptions: value.channelOptions.map(Self.swift), timeline: wrap(value.timeline), map: wrap(value.map),
            stats: value.stats.map { AnalysisStatTile(title: $0.title, value: $0.value, detail: $0.detail) },
            gg: wrap(value.gg), ggExpanded: value.ggExpanded, ggFilter: value.ggFilter.map(KotlinMapping.swift),
            copy: Self.copy)
    }

    private func wrap(_ t: DrivePerfShared.AnalysisTimeline?) -> (any AnalysisTimelineView)? {
        guard let t else { return nil }
        if let timeline, timeline.kotlin === t { return timeline.swift }
        let swift = CoreAnalysisTimeline(t)
        timeline = (t, swift)
        return swift
    }

    private func wrap(_ m: DrivePerfShared.AnalysisMap?) -> (any AnalysisMapView)? {
        guard let m else { return nil }
        if let map, map.kotlin === m { return map.swift }
        let swift = CoreAnalysisMap(m)
        map = (m, swift)
        return swift
    }

    private func wrap(_ g: DrivePerfShared.GgView?) -> AnalysisGgView? {
        guard let g else { return nil }
        if let gg, gg.kotlin === g { return gg.swift }
        let swift = Self.swift(g)
        gg = (g, swift)
        return swift
    }

    static func swift(_ g: DrivePerfShared.GgView) -> AnalysisGgView {
        AnalysisGgView(
            dots: g.dots.map { AnalysisGgDot(at: AnalysisGPoint(latG: $0.latG, lonG: $0.lonG), weight: $0.weight, emphasised: $0.emphasised) },
            envelope: g.envelope.map(swift),
            pins: g.pins.map { AnalysisGgPin(eventId: $0.eventId, type: KotlinMapping.swift($0.type), needed: $0.needed, at: swift($0.at)) },
            rings: g.rings.map { AnalysisGgRing(g: $0.g, label: $0.label) },
            scaleG: g.scaleG, caption: g.caption, filterCaption: g.filterCaption,
            filters: g.filters.map { AnalysisGgChip(type: $0.type.map(KotlinMapping.swift), label: $0.label, selected: $0.selected) },
            peaks: g.peaks.map {
                AnalysisGgPeak(type: KotlinMapping.swift($0.type), title: $0.title, detail: $0.detail, value: $0.value, eventId: $0.eventId)
            },
            partBanner: g.partBanner, accessibilityLabel: g.accessibilityLabel)
    }

    static func swift(_ p: DrivePerfShared.GgPoint) -> AnalysisGPoint { AnalysisGPoint(latG: p.latG, lonG: p.lonG) }

    static func swift(_ o: DrivePerfShared.ChannelOption) -> AnalysisChannelOption {
        AnalysisChannelOption(
            channel: swift(o.channel), title: o.title, description: o.description_, on: o.on, pro: o.pro,
            enabled: o.enabled, accessibilityLabel: o.accessibilityLabel)
    }

    static func swift(_ c: DrivePerfShared.AnalysisChannel) -> AnalysisChannelKind {
        switch c {
        case .speed: .speed
        case .lonG: .lonG
        case .latG: .latG
        case .elevation: .elevation
        case .jerk: .jerk
        case .quality: .quality
        }
    }

    static func kotlin(_ c: AnalysisChannelKind) -> DrivePerfShared.AnalysisChannel {
        switch c {
        case .speed: .speed
        case .lonG: .lonG
        case .latG: .latG
        case .elevation: .elevation
        case .jerk: .jerk
        case .quality: .quality
        }
    }

    static func swift(_ a: DrivePerfShared.AnalysisAxis) -> AnalysisAxisKind { a == .distance ? .distance : .time }

    static func kotlin(_ a: AnalysisAxisKind) -> DrivePerfShared.AnalysisAxis { a == .distance ? .distance : .time }

    static func swift(_ s: DrivePerfShared.SegmentStyle) -> ReplaySegmentStyle {
        switch s {
        case .observed: .observed
        case .estimated: .estimated
        case .noGps: .noGps
        case .excluded: .excluded
        }
    }

    static func kotlin(_ action: AnalysisAction) -> any DrivePerfShared.AnalysisIntent {
        switch action {
        case .back: AnalysisIntentBack.shared
        case .setAxis(let axis): AnalysisIntentSetAxis(axis: kotlin(axis))
        case .zoomIn: AnalysisIntentZoomIn.shared
        case .zoomOut: AnalysisIntentZoomOut.shared
        case .resetZoom: AnalysisIntentResetZoom.shared
        case .pinch(let factor): AnalysisIntentPinch(factor: factor)
        case .moveCursor(let tS): AnalysisIntentMoveCursor(tS: tS)
        case .toggle(let channel): AnalysisIntentToggleChannel(channel: kotlin(channel))
        case .expandGg: AnalysisIntentExpandGg.shared
        case .collapseGg: AnalysisIntentCollapseGg.shared
        case .filterGg(let type): AnalysisIntentFilterGg(type: type.map(KotlinMapping.kotlin))
        case .showEvent(let id): AnalysisIntentShowEvent(eventId: id)
        case .openReplay: AnalysisIntentOpenReplay.shared
        }
    }

    /// The shared copy, read once (Kotlin `AnalysisCopy`).
    private static func screenCopy() -> AnalysisScreenCopy {
        let k = AnalysisCopy.shared
        var c = AnalysisScreenCopy()
        c.title = k.TITLE
        c.ggTitle = k.GG_TITLE
        c.channelsTitle = k.CHANNELS_TITLE
        c.channelsSub = k.CHANNELS_SUB
        c.channelsPro = k.CHANNELS_PRO_SECTION
        c.channelsFooter = k.CHANNELS_FOOTER
        c.axisTime = k.AXIS_TIME
        c.axisDistance = k.AXIS_DISTANCE
        c.footnote = k.FOOTNOTE
        c.notFound = k.NOT_FOUND
        c.summaryOnly = k.SUMMARY_ONLY
        c.noMap = k.NO_MAP
        c.ggEmpty = k.GG_EMPTY
        c.ggFooter = k.GG_FOOTER
        c.ggHow = [k.GG_HOW_1, k.GG_HOW_2]
        c.ggHowTitle = k.GG_HOW_TITLE
        c.ggPeaksTitle = k.GG_PEAKS_TITLE
        c.ggAxes = k.GG_AXES
        return c
    }
}

/// A Kotlin `AnalysisTimeline`: the lanes are converted once; the lookups call the core per frame.
@MainActor
final class CoreAnalysisTimeline: AnalysisTimelineView {
    private let timeline: DrivePerfShared.AnalysisTimeline
    let axis: AnalysisAxisKind
    let lanes: [AnalysisLane]
    let bands: [AnalysisBand]
    let ticks: [AnalysisTick]
    let axisTicks: [AnalysisAxisTick]
    let legend: [AnalysisLegendItem]
    let summary: String

    init(_ t: DrivePerfShared.AnalysisTimeline) {
        timeline = t
        axis = CoreAnalysisPresenter.swift(t.axis)
        lanes = t.lanes.map { lane in
            AnalysisLane(
                channel: CoreAnalysisPresenter.swift(lane.channel), title: lane.title, rangeLabel: lane.rangeLabel,
                xs: Self.doubles(lane.path?.xs), ys: Self.doubles(lane.path?.ys), hasData: !lane.empty,
                dashedUntilX: lane.dashedUntilX, zeroY: lane.zeroY?.doubleValue)
        }
        bands = t.bands.map { AnalysisBand(style: CoreAnalysisPresenter.swift($0.style), x0: $0.x0, x1: $0.x1, label: $0.label) }
        ticks = t.ticks.map {
            AnalysisTick(
                eventId: $0.eventId, type: KotlinMapping.swift($0.type), needed: $0.needed, x: $0.x, tPeakS: $0.tPeakS,
                accessibilityLabel: $0.accessibilityLabel)
        }
        axisTicks = t.axisTicks.map { AnalysisAxisTick(x: $0.x, label: $0.label) }
        legend = t.legend.map { AnalysisLegendItem(kind: Self.swift($0.key), label: $0.label) }
        summary = t.summary
    }

    func timeAt(_ x: Double) -> Double { timeline.timeAt(x: x) }

    func xOf(_ tS: Double) -> Double { timeline.xOf(tS: tS) }

    func snap(_ x: Double, tolerance: Double) -> (tS: Double, eventId: String?) {
        let snap = timeline.snap(x: x, tolerance: tolerance)
        return (snap.tS, snap.eventId)
    }

    func readout(_ tS: Double) -> AnalysisCursorReadout {
        let r = timeline.readout(tS: tS)
        return AnalysisCursorReadout(
            tS: r.tS, x: r.x, headline: r.headline, chip: r.chip,
            values: r.values.map { AnalysisLaneValue(channel: CoreAnalysisPresenter.swift($0.channel), text: $0.text, y: $0.y) },
            marker: r.marker.map { AnalysisMapPoint(x: $0.x, y: $0.y) }, gg: r.gg.map(CoreAnalysisPresenter.swift),
            accessibilityValue: r.accessibilityValue)
    }

    func tableRows() -> [AnalysisTableRow] {
        timeline.tableRows().map { AnalysisTableRow(time: $0.time, speed: $0.speed, lonG: $0.lonG, latG: $0.latG, note: $0.note) }
    }

    static func doubles(_ a: KotlinDoubleArray?) -> [Double] {
        guard let a else { return [] }
        return (0..<Int(a.size)).map { a.get(index: Int32($0)) }
    }

    static func swift(_ k: DrivePerfShared.LegendKey) -> AnalysisLegendKind {
        switch k {
        case .speed: .speed
        case .lonG: .lonG
        case .latG: .latG
        case .elevation: .elevation
        case .jerk: .jerk
        case .estimated: .estimated
        case .noGps: .noGps
        case .notScored: .notScored
        }
    }
}

/// A Kotlin `AnalysisMap`: segments and pins are converted once.
@MainActor
final class CoreAnalysisMap: AnalysisMapView {
    private let map: DrivePerfShared.AnalysisMap
    let widthM: Double
    let heightM: Double
    let segments: [AnalysisMapSegment]
    let pins: [AnalysisMapPin]

    init(_ m: DrivePerfShared.AnalysisMap) {
        map = m
        widthM = m.widthM
        heightM = m.heightM
        segments = m.segments.map { s in
            let xs = CoreAnalysisTimeline.doubles(s.xs)
            let ys = CoreAnalysisTimeline.doubles(s.ys)
            return AnalysisMapSegment(
                style: CoreAnalysisPresenter.swift(s.style), points: zip(xs, ys).map { AnalysisMapPoint(x: $0, y: $1) })
        }
        pins = m.pins.map {
            AnalysisMapPin(
                eventId: $0.eventId, type: KotlinMapping.swift($0.type), needed: $0.needed,
                at: AnalysisMapPoint(x: $0.at.x, y: $0.at.y))
        }
    }

    func nearestTime(x: Double, y: Double) -> Double { map.nearestTime(x: x, y: y) }
}

/// Before the core has started there is no trip to analyse.
@MainActor
final class UnavailableAnalysisPresenter: AnalysisPresenting {
    let state: AnalysisScreenState

    init(tripId: String) { state = AnalysisScreenState(tripId: tripId, loading: false, missing: true) }

    func states() -> AsyncStream<AnalysisScreenState> { AsyncStream { [state] in $0.yield(state); $0.finish() } }
    func navigation() -> AsyncStream<AnalysisNavigation> { AsyncStream { $0.finish() } }
    func dispatch(_ action: AnalysisAction) {}
    func close() {}
}
