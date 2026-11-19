import DPSharedBridge
import DrivePerfShared
import Foundation

/// `DrivesPresenting` over the shared Kotlin `DrivesPresenter` (DRIVE-217, ADR-002 §4). Like the other core bridges it
/// exists before the core starts: until `attach` it shows an empty list and drops intents (no trip can be listed
/// before then either). The Replay fixture attaches a presenter over its synthetic trip instead.
@MainActor
final class CoreDrivesPresenter: DrivesPresenting {
    private var presenter: DrivePerfShared.DrivesPresenter?
    private var stateObservers: [UUID: AsyncStream<DrivesScreenState>.Continuation] = [:]
    private var navigationObservers: [UUID: AsyncStream<DrivesNavigation>.Continuation] = [:]

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
                guard let self, let open = effect as? DrivesEffectOpenReplay else { continue }
                let navigation = DrivesNavigation.openReplay(tripId: open.tripId, title: open.title, mapSummary: open.mapSummary)
                navigationObservers.values.forEach { $0.yield(navigation) }
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

    /// The generic `Presenter<S, …>` reaches Swift with an optional state; the presenter always has one.
    static func swift(_ s: DrivePerfShared.DrivesState?) -> DrivesScreenState {
        guard let s else { return DrivesScreenState() }
        return DrivesScreenState(
            hasLoaded: s.loaded, trips: s.trips.map(row), latest: s.latest.map(row), notDrives: s.notDrives.map(row),
            sections: s.sections.map { TripSection(id: $0.id, title: $0.title, meta: $0.meta, rows: $0.rows.map(row)) },
            canCorrect: s.canCorrect, detail: s.detail.map(detail), quality: s.quality.map(quality),
            countText: s.countText, sinceText: s.sinceText,
            corrections: s.corrections.map {
                CorrectionChoice(correction: KotlinMapping.swift($0.correction), title: $0.title)
            })
    }

    static func row(_ r: TripRowUi) -> TripRow {
        TripRow(
            id: r.id, title: r.title, subtitle: r.subtitle, distance: r.distance, duration: r.duration, note: r.note,
            accessibilityLabel: r.accessibilityLabel, quality: r.quality.map(KotlinMapping.swift), qualityWord: r.qualityWord,
            isInterrupted: r.interrupted, question: question(r.question), isDimmed: r.dimmed, kindLabel: r.kindLabel)
    }

    static func detail(_ d: TripDetailUi) -> TripDetail {
        TripDetail(
            id: d.id, title: d.title, subtitle: d.subtitle, banners: d.banners.map(banner), tiles: d.tiles.map(value),
            quality: d.quality.map(KotlinMapping.swift), qualityWord: d.qualityWord,
            correction: KotlinMapping.swift(d.correction))
    }

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

    // MARK: Swift → Kotlin

    static func kotlin(_ action: DrivesAction) -> any DrivePerfShared.DrivesIntent {
        switch action {
        case .open(let tripId): DrivesIntentOpen(tripId: tripId)
        case .closeDetail: DrivesIntentCloseDetail.shared
        case .openQuality(let tripId): DrivesIntentOpenQuality(tripId: tripId)
        case .closeQuality: DrivesIntentCloseQuality.shared
        case .openReplay(let tripId): DrivesIntentOpenReplay(tripId: tripId)
        case .answer(let tripId, let yes): DrivesIntentAnswer(tripId: tripId, yes: yes)
        case .correct(let tripId, let to): DrivesIntentCorrect(tripId: tripId, to: KotlinMapping.kotlin(to))
        }
    }
}
