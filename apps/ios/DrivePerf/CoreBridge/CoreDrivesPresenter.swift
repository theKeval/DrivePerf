import DPSharedBridge
import DrivePerfShared
import Foundation

/// `DrivesPresenting` over the shared Kotlin `DrivesPresenter` (DRIVE-217, ADR-002 §4), with the score surfaces
/// (DRIVE-77, DRIVE-87, DRIVE-55). Like the other core bridges it
/// exists before the core starts: until `attach` it shows an empty list and drops intents (no trip can be listed
/// before then either). The Replay fixture attaches a presenter over its synthetic trip instead.
@MainActor
final class CoreDrivesPresenter: DrivesPresenting {
    private var presenter: DrivePerfShared.DrivesPresenter?
    private var stateObservers: [UUID: AsyncStream<DrivesScreenState>.Continuation] = [:]
    private var navigationObservers: [UUID: AsyncStream<DrivesNavigation>.Continuation] = [:]
    /// Navigation that arrived while nobody observed it, for the next observer.
    private var pendingNavigation: [DrivesNavigation] = []

    /// The Kotlin state, read now: an intent's effect on it is visible at once.
    var state: DrivesScreenState { Self.swift(presenter?.state.value) }

    /// Connects the started core. Later calls are ignored.
    func attach(_ services: CoreServices) {
        attach(services.drivesPresenter(format: CoreDrivesFormatter(), region: Locale.current.region?.identifier ?? ""))
    }

    /// Connects [presenter] (main-thread scope). Later calls are ignored.
    func attach(_ presenter: DrivePerfShared.DrivesPresenter) {
        guard self.presenter == nil else { return }
        self.presenter = presenter
        let states = UncheckedSendable(presenter.state)
        let effects = UncheckedSendable(presenter.effects)
        Task { [weak self] in
            for await value in states.value {
                guard let self else { return }
                let state = Self.swift(value)
                stateObservers.values.forEach { $0.yield(state) }
            }
        }
        Task { [weak self] in
            for await effect in effects.value {
                // iOS pushes trip detail and the score screens with navigation links, and opens the evidence and
                // tip itself (TripEvidenceSheets), so only Replay and Analysis come back as navigation.
                guard let self, let effect, let navigation = Self.navigation(effect) else { continue }
                if navigationObservers.isEmpty {
                    pendingNavigation.append(navigation)
                } else {
                    navigationObservers.values.forEach { $0.yield(navigation) }
                }
            }
        }
    }

    func states() -> AsyncStream<DrivesScreenState> {
        let id = UUID()
        return AsyncStream { continuation in
            continuation.yield(state)
            stateObservers[id] = continuation
            continuation.onTermination = { [weak self] _ in
                Task { @MainActor in self?.stateObservers[id] = nil }
            }
        }
    }

    func navigation() -> AsyncStream<DrivesNavigation> {
        let id = UUID()
        return AsyncStream { continuation in
            pendingNavigation.forEach { continuation.yield($0) }
            pendingNavigation.removeAll()
            navigationObservers[id] = continuation
            continuation.onTermination = { [weak self] _ in
                Task { @MainActor in self?.navigationObservers[id] = nil }
            }
        }
    }

    func dispatch(_ action: DrivesAction) {
        presenter?.dispatch(intent: Self.kotlin(action))
    }

    // MARK: Kotlin → Swift

    static func navigation(_ effect: any DrivePerfShared.DrivesEffect) -> DrivesNavigation? {
        if let open = effect as? DrivesEffectOpenReplay {
            return .openReplay(tripId: open.tripId, title: open.title, mapSummary: open.mapSummary)
        }
        if let open = effect as? DrivesEffectOpenAnalysis {
            return .openAnalysis(tripId: open.tripId, title: open.title)
        }
        return nil
    }

    /// The generic `Presenter<S, …>` reaches Swift with an optional state; the presenter always has one.
    static func swift(_ s: DrivePerfShared.DrivesState?) -> DrivesScreenState {
        guard let s else { return DrivesScreenState() }
        return DrivesScreenState(
            hasLoaded: s.loaded, trips: s.trips.map(row), latest: s.latest.map(row), notDrives: s.notDrives.map(row),
            sections: s.sections.map {
                TripSection(id: $0.id, title: $0.title, meta: $0.meta, rows: $0.rows.map(row), accessibilityLabel: $0.accessibilityLabel)
            },
            canCorrect: s.canCorrect, detail: s.detail.map(detail), quality: s.quality.map(quality),
            countText: s.countText, sinceText: s.sinceText,
            corrections: s.corrections.map {
                CorrectionChoice(correction: KotlinMapping.swift($0.correction), title: $0.title)
            },
            explainer: s.explainer.map(explainer))
    }

    static func row(_ r: TripRowUi) -> TripRow {
        TripRow(
            id: r.id, title: r.title, subtitle: r.subtitle, distance: r.distance, duration: r.duration, note: r.note,
            accessibilityLabel: r.accessibilityLabel, quality: r.quality.map(KotlinMapping.swift), qualityWord: r.qualityWord,
            isInterrupted: r.interrupted, question: question(r.question), isDimmed: r.dimmed, kindLabel: r.kindLabel,
            score: r.score.map(badge), car: r.car)
    }

    static func detail(_ d: TripDetailUi) -> TripDetail {
        TripDetail(
            id: d.id, title: d.title, subtitle: d.subtitle, banners: d.banners.map(banner), tiles: d.tiles.map(value),
            quality: d.quality.map(KotlinMapping.swift), qualityWord: d.qualityWord,
            correction: KotlinMapping.swift(d.correction),
            meta: d.meta.map { TripMeta(car: $0.car, isPassenger: $0.passenger, source: $0.source, timeRange: $0.timeRange) },
            scoresHidden: d.scoresHidden, score: d.score.map(score), breakdown: d.breakdown.map(breakdown),
            events: d.events.map(events), cleanest: d.cleanest, tip: d.tip.map(tip), canAnalyse: d.canAnalyse,
            map: d.map.map(tripMap), links: links)
    }

    /// The S-TRP-01 map hero (DRIVE-55), in the S-ANL-01 map card's types.
    static func tripMap(_ m: TripMapUi) -> TripMap {
        TripMap(
            widthM: m.widthM, heightM: m.heightM,
            segments: m.segments.map { s in
                let xs = CoreAnalysisTimeline.doubles(s.xs)
                let ys = CoreAnalysisTimeline.doubles(s.ys)
                return AnalysisMapSegment(
                    style: CoreAnalysisPresenter.swift(s.style), points: zip(xs, ys).map { AnalysisMapPoint(x: $0, y: $1) })
            },
            pins: m.pins.map {
                AnalysisMapPin(
                    eventId: $0.eventId, type: KotlinMapping.swift($0.type), needed: $0.needed,
                    at: AnalysisMapPoint(x: $0.at.x, y: $0.at.y))
            },
            stops: m.stops.map { AnalysisMapPoint(x: $0.x, y: $0.y) },
            maxSpeed: m.maxSpeed.map { AnalysisMapPoint(x: $0.x, y: $0.y) },
            estimatedStart: m.estimatedStart, accessibilityLabel: m.accessibilityLabel)
    }

    /// The shared `TripLinkCopy` (decision 3).
    static let links = TripLinkWords(
        estimatedStart: TripLinkCopy.shared.ESTIMATED_START, seeTheMoment: TripLinkCopy.shared.SEE_THE_MOMENT,
        openEvidence: TripLinkCopy.shared.OPEN_EVIDENCE, openTip: TripLinkCopy.shared.OPEN_TIP,
        showEvents: TripLinkCopy.shared.SHOW_EVENTS, hideEvents: TripLinkCopy.shared.HIDE_EVENTS)

    static func quality(_ q: QualitySheetUi) -> DataQualityDetail {
        DataQualityDetail(
            badge: KotlinMapping.swift(q.badge), title: q.title, hero: q.hero, estimatedStart: q.estimatedStart,
            coverage: q.coverage.map { CoverageSpan(style: KotlinMapping.swift($0.style), from: $0.from, to: $0.to) },
            scoredLine: q.scoredLine, coverageAccessibility: q.coverageDescription, rows: q.rows.map(value),
            legend: q.legend.map { (KotlinMapping.swift($0.provenance), $0.meaning) })
    }

    static func value(_ v: DrivePerfShared.ProvenancedValue) -> DPSharedBridge.ProvenancedValue {
        DPSharedBridge.ProvenancedValue(
            metricKey: v.key, label: v.label, value: v.value, unit: v.unit, provenance: KotlinMapping.swift(v.provenance),
            provenanceMeaning: v.provenance.meaning, meaning: v.meaning, isEstimated: v.estimated, note: v.note,
            lowersBadge: v.lowersBadge, accessibilityLabel: v.accessibilityLabel)
    }

    static func banner(_ b: TripBannerUi) -> TripBanner {
        let action: TripBanner.Action? =
            if let answer = b.action as? BannerActionAnswer, let question = question(answer.question) {
                .answer(question)
            } else if b.action is BannerActionRestore {
                .restore
            } else {
                nil
            }
        return TripBanner(tone: b.attention ? .attention : .info, text: b.text, action: action)
    }

    static func question(_ suspect: DrivePerfShared.Suspect?) -> TripQuestion? {
        switch suspect {
        case .transit: .transit
        case .cycling: .bike
        default: nil
        }
    }

    // MARK: Scores (DRIVE-77, DRIVE-87, DRIVE-113)

    static func tone(_ t: DrivePerfShared.ComparisonTone) -> ScoreComparisonTone {
        switch t {
        case .better: .better
        case .lower: .lower
        case .same: .same
        case .calibrating: .calibrating
        }
    }

    static func badge(_ b: ScoreBadgeUi) -> DPSharedBridge.ScoreBadge {
        DPSharedBridge.ScoreBadge(
            score: b.score, delta: b.delta, tone: tone(b.tone), isNewRecord: b.newRecord, notScoredText: b.notScoredText)
    }

    static func comparison(_ c: ComparisonUi) -> ScoreComparison {
        ScoreComparison(
            tone: tone(c.tone), glyph: c.glyph, text: c.text, calibratingK: Int(c.calibratingK), calibratingOf: Int(c.calibratingOf))
    }

    static func event(_ e: EventRowUi) -> ScoreEventRow {
        ScoreEventRow(
            id: e.id, type: KotlinMapping.swift(e.type), label: e.label, time: e.time, meta: e.meta, isNeeded: e.needed,
            isExcluded: e.excluded, tag: e.tag, accessibilityLabel: e.accessibilityLabel)
    }

    static func contributor(_ c: ContributorUi) -> ScoreContributor {
        ScoreContributor(
            id: KotlinMapping.swift(c.id), label: c.label, band: c.band.map(KotlinMapping.swift), bandGlyph: c.bandGlyph,
            bandText: c.bandText, reason: c.reason, events: c.events.map(event), accessibilityLabel: c.accessibilityLabel)
    }

    static func score(_ s: ScoreUi) -> ScoreSummary {
        ScoreSummary(
            smoothness: s.smoothness.map { Int(truncating: $0) }, baseline: s.baseline.map { Int(truncating: $0) },
            comparison: s.comparison.map(comparison), recordLine: s.recordLine, basedOn: s.basedOn, paceNote: s.paceNote,
            notScoredReason: s.notScoredReason, contributors: s.contributors.map(contributor),
            accessibilityLabel: s.accessibilityLabel)
    }

    static func breakdown(_ b: BreakdownUi) -> ScoreBreakdown {
        ScoreBreakdown(
            basisLine: b.basisLine,
            options: b.options.map { ScoreBasisOption(title: $0.title, detail: $0.detail, isSelected: $0.selected, value: $0.value) },
            optionsFooter: b.optionsFooter, runsTitle: b.runsTitle, runs: b.runs.map { Int(truncating: $0) },
            runsSummary: b.runsSummary, contributorsFooter: b.contributorsFooter, notScoredText: b.notScoredText,
            notScoredFooter: b.notScoredFooter)
    }

    static func events(_ e: TripEventsUi) -> TripEvents {
        TripEvents(
            title: e.title,
            counts: e.counts.map { ScoreEventCount(type: KotlinMapping.swift($0.type), label: $0.label, count: Int($0.count)) },
            rows: e.rows.map(event), emptyTitle: e.emptyTitle, emptyBody: e.emptyBody, note: e.note)
    }

    static func tip(_ t: TipUi) -> ScoreTip {
        ScoreTip(
            tipId: t.tipId, category: KotlinMapping.swift(t.category), kicker: t.kicker, title: t.title, text: t.text, why: t.why,
            eventId: t.eventId)
    }

    static func explainer(_ h: HowScoresWorkUi) -> HowScoresWork {
        HowScoresWork(
            title: h.title, exampleScore: Int(h.exampleScore), exampleBaseline: Int(h.exampleBaseline),
            exampleAccessibility: h.exampleAccessibility, heroTitle: h.heroTitle, heroBody: h.heroBody, notchKey: h.notchKey,
            sections: h.sections.map {
                ExplainerSection(kind: sectionKind($0.kind), title: $0.title, paragraphs: $0.paragraphs, note: $0.note)
            },
            thresholds: h.thresholds.map { ExplainerThreshold(type: KotlinMapping.swift($0.type), label: $0.label, text: $0.text) },
            provenance: h.provenance.map {
                ExplainerProvenance(provenance: KotlinMapping.swift($0.provenance), label: $0.label, meaning: $0.meaning)
            },
            versionLine: h.versionLine)
    }

    static func sectionKind(_ k: DrivePerfShared.ExplainerSectionKind) -> DPSharedBridge.ExplainerSectionKind {
        switch k {
        case .text: .text
        case .events: .events
        case .quality: .quality
        case .provenance: .provenance
        }
    }

    // MARK: Swift → Kotlin

    static func kotlin(_ action: DrivesAction) -> any DrivePerfShared.DrivesIntent {
        switch action {
        case .open(let tripId): DrivesIntentOpen(tripId: tripId)
        case .closeDetail: DrivesIntentCloseDetail.shared
        case .openQuality(let tripId): DrivesIntentOpenQuality(tripId: tripId)
        case .closeQuality: DrivesIntentCloseQuality.shared
        case .openReplay(let tripId): DrivesIntentOpenReplay(tripId: tripId)
        case .openAnalysis(let tripId): DrivesIntentOpenAnalysis(tripId: tripId)
        case .showBreakdown(let tripId): DrivesIntentShowBreakdown(tripId: tripId)
        case .openHowScoresWork(let source):
            DrivesIntentOpenHowScoresWork(source: source == .breakdown ? DrivePerfShared.ExplainerSource.breakdown : .tripDetail)
        case .tapContributor(let id): DrivesIntentTapContributor(contributor: contributorID(id))
        case .answer(let tripId, let yes): DrivesIntentAnswer(tripId: tripId, yes: yes)
        case .correct(let tripId, let to): DrivesIntentCorrect(tripId: tripId, to: KotlinMapping.kotlin(to))
        case .openEvidence(let tripId, let eventId): DrivesIntentOpenEvidence(tripId: tripId, eventId: eventId)
        case .openTip(let tripId): DrivesIntentOpenTip(tripId: tripId)
        case .openTipEvidence(let tripId): DrivesIntentOpenTipEvidence(tripId: tripId)
        }
    }

    static func contributorID(_ id: ScoreContributorID) -> DrivePerfShared.ContributorId {
        switch id {
        case .braking: .braking
        case .acceleration: .acceleration
        case .cornering: .cornering
        case .consistency: .consistency
        }
    }
}
