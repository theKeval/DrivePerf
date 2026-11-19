import DPSharedBridge
import Foundation
import Observation

/// S-HOME-01 "Latest drives", S-HIS-01 "All drives", S-TRP-01 trip detail, S-SCO-01 Smoothness breakdown, S-SCO-02
/// How scores work and S-DQ-01 data quality: a thin wrapper over the shared Drives presenter (DRIVE-217, ADR-002 §4).
/// The state is the presenter's; this only forwards intents and turns the presenter's Replay and Analysis hand-offs
/// into `replayRequest` and `analysisRequest` for the trip detail.
@MainActor
@Observable
public final class TripListViewModel {
    /// What the presenter asked to open in Replay; the trip detail opens it and calls `replayOpened()`.
    public struct ReplayRequest: Equatable, Sendable {
        public var tripId: String
        /// The trip's title, shown under "Replay".
        public var title: String
        /// "Map of your drive, 19.9 km."
        public var mapSummary: String
    }

    /// What the presenter asked to open in Analysis; the trip detail opens it and calls `analysisOpened()`.
    public struct AnalysisRequest: Equatable, Sendable {
        public var tripId: String
        /// The trip's title, shown under "Analysis".
        public var title: String
    }

    public private(set) var state: DrivesScreenState
    public private(set) var replayRequest: ReplayRequest?
    public private(set) var analysisRequest: AnalysisRequest?
    private let presenter: any DrivesPresenting

    public init(presenter: any DrivesPresenting) {
        self.presenter = presenter
        state = presenter.state
    }

    /// Follows the presenter for as long as the app runs, so a trip corrected from its detail is found in Not drives.
    public func observe() async {
        await withTaskGroup(of: Void.self) { group in
            group.addTask { await self.observeStates() }
            group.addTask { await self.observeNavigation() }
        }
    }

    private func observeStates() async {
        for await value in presenter.states() { state = value }
    }

    private func observeNavigation() async {
        for await navigation in presenter.navigation() {
            switch navigation {
            case .openReplay(let tripId, let title, let mapSummary):
                replayRequest = ReplayRequest(tripId: tripId, title: title, mapSummary: mapSummary)
            case .openAnalysis(let tripId, let title):
                analysisRequest = AnalysisRequest(tripId: tripId, title: title)
            }
        }
    }

    public var hasLoaded: Bool { state.hasLoaded }
    public var trips: [TripRow] { state.trips }
    public var latest: [TripRow] { state.latest }
    public var sections: [TripSection] { state.sections }
    /// Trips marked as not a drive, kept 30 days (F-CAP-04 AC3).
    public var notDrives: [TripRow] { state.notDrives }
    /// Whether answers and corrections can be made.
    public var canCorrect: Bool { state.canCorrect }
    /// S-HIS-01 subtitle: "3 drives".
    public var countText: String { state.countText }
    /// S-HIS-01 footer.
    public var sinceText: String? { state.sinceText }
    public var corrections: [CorrectionChoice] { state.corrections }

    /// "I was a passenger"
    public func correctionTitle(_ correction: TripCorrection) -> String {
        state.corrections.first { $0.correction == correction }?.title ?? ""
    }

    /// Whether [id] is listed as a drive or a not-a-drive trip.
    public func contains(_ id: String) -> Bool {
        state.trips.contains { $0.id == id } || state.notDrives.contains { $0.id == id }
    }

    /// The trip detail (S-TRP-01) for [id], once `open(id)` has run and while the trip exists.
    public func detail(id: String) -> TripDetail? {
        state.detail?.id == id ? state.detail : nil
    }

    /// The data-quality sheet (S-DQ-01) over [id]'s detail, once `openQuality(id)` has run.
    public func dataQuality(id: String) -> DataQualityDetail? {
        state.detail?.id == id ? state.quality : nil
    }

    public func open(_ id: String) { send(.open(tripId: id)) }
    public func openQuality(_ id: String) { send(.openQuality(tripId: id)) }
    public func closeQuality() { send(.closeQuality) }

    /// Replay on the trip detail (DRIVE-200): the presenter answers with `replayRequest`.
    public func requestReplay(_ id: String) { send(.openReplay(tripId: id)) }

    /// The trip detail opened the Replay in `replayRequest`.
    public func replayOpened() { replayRequest = nil }

    /// Analysis on the trip detail: the presenter answers with `analysisRequest`.
    public func requestAnalysis(_ id: String) { send(.openAnalysis(tripId: id)) }

    /// The trip detail opened the Analysis in `analysisRequest`.
    public func analysisOpened() { analysisRequest = nil }

    /// S-SCO-01 is showing [id] (`score_viewed {surface: breakdown}`).
    public func showBreakdown(_ id: String) { send(.showBreakdown(tripId: id)) }

    /// S-SCO-02 opened from [source] (`scores_explainer_viewed`).
    public func explainerOpened(from source: ExplainerSource) { send(.openHowScoresWork(source: source)) }

    /// A contributor's events were opened on S-SCO-01 (`contributor_tapped`).
    public func contributorTapped(_ id: ScoreContributorID) { send(.tapContributor(id)) }

    /// S-SCO-02's content.
    public var explainer: HowScoresWork? { state.explainer }

    /// "Was this a bus or train?" Yes / No.
    public func answer(tripId: String, yes: Bool) { send(.answer(tripId: tripId, yes: yes)) }

    /// "What was this?" (S-TRP-02 type picker); `.driver` restores a trip as a drive.
    public func correct(tripId: String, to correction: TripCorrection) { send(.correct(tripId: tripId, to: correction)) }

    /// Sends [action] and reads the new state at once, so a sheet opens in the same frame.
    private func send(_ action: DrivesAction) {
        presenter.dispatch(action)
        state = presenter.state
    }
}

/// A presenter with a fixed state, for previews, tests and the moments before the core has started. It records what
/// it is sent.
@MainActor
public final class StaticDrivesPresenter: DrivesPresenting {
    public var state: DrivesScreenState
    public private(set) var actions: [DrivesAction] = []

    public init(state: DrivesScreenState = DrivesScreenState()) {
        self.state = state
    }

    public func states() -> AsyncStream<DrivesScreenState> {
        AsyncStream { $0.yield(state) }
    }

    public func navigation() -> AsyncStream<DrivesNavigation> {
        AsyncStream { _ in }
    }

    public func dispatch(_ action: DrivesAction) {
        actions.append(action)
    }
}
