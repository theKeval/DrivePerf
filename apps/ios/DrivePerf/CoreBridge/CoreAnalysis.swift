import DPSharedBridge
import DrivePerfShared
import Foundation

/// `AnalysisPresenterMaking` over `CoreServices.analysisPresenter` (S-ANL-01, ADR-002 section 4). A stub until
/// DRIVE-45. Like the other core bridges it exists before the core starts; an Analysis opened before `attach` finds
/// nothing.
@MainActor
final class CoreAnalysisPresenters: AnalysisPresenterMaking {
    private var services: CoreServices?

    func attach(_ services: CoreServices) {
        self.services = services
    }

    func presenter(tripId: String) -> any AnalysisPresenting {
        guard let services else { return UnavailableAnalysisPresenter(tripId: tripId) }
        return CoreAnalysisPresenter(services.analysisPresenter(tripId: tripId))
    }
}

/// One Kotlin `AnalysisPresenter` (main-thread scope) as `AnalysisPresenting`.
@MainActor
final class CoreAnalysisPresenter: AnalysisPresenting {
    private let presenter: DrivePerfShared.AnalysisPresenter

    init(_ presenter: DrivePerfShared.AnalysisPresenter) {
        self.presenter = presenter
    }

    var state: AnalysisScreenState { Self.swift(presenter.state.value) }

    func states() -> AsyncStream<AnalysisScreenState> {
        let flow = UncheckedSendable(presenter.state)
        return AsyncStream { continuation in
            let task = Task { @MainActor in
                for await value in flow.value { continuation.yield(Self.swift(value)) }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func navigation() -> AsyncStream<AnalysisNavigation> {
        let flow = UncheckedSendable(presenter.effects)
        return AsyncStream { continuation in
            let task = Task { @MainActor in
                for await effect in flow.value where effect is AnalysisEffectClose {
                    continuation.yield(.close)
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func dispatch(_ action: AnalysisAction) {
        switch action {
        case .back: presenter.dispatch(intent: AnalysisIntentBack.shared)
        }
    }

    func close() { presenter.close() }

    /// The generic `Presenter<S, …>` reaches Swift with an optional state; the presenter always has one.
    private static func swift(_ value: DrivePerfShared.AnalysisState?) -> AnalysisScreenState {
        guard let value else { return AnalysisScreenState() }
        return AnalysisScreenState(tripId: value.tripId, loading: value.loading)
    }
}

/// Before the core has started there is no trip to analyse.
@MainActor
final class UnavailableAnalysisPresenter: AnalysisPresenting {
    let state: AnalysisScreenState

    init(tripId: String) { state = AnalysisScreenState(tripId: tripId, loading: false) }

    func states() -> AsyncStream<AnalysisScreenState> { AsyncStream { [state] in $0.yield(state); $0.finish() } }
    func navigation() -> AsyncStream<AnalysisNavigation> { AsyncStream { $0.finish() } }
    func dispatch(_ action: AnalysisAction) {}
    func close() {}
}
