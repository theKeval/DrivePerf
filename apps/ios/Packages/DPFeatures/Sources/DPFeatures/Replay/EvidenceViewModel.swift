import DPSharedBridge
import Foundation
import Observation

/// S-RPL-02 (F-RPL-04) with S-SCO-03 over it, as a thin wrapper over the shared `EvidencePresenter`. Replay owns it and
/// follows its navigation (the stepper moves the marker, feedback reloads the pins); this keeps its state and the
/// S-SCO-04 tip it pushes.
@MainActor
@Observable
public final class EvidenceViewModel {
    public private(set) var state: EvidenceScreenState
    /// S-SCO-04 while it is pushed.
    public var tip: TipDetailViewModel?

    private let presenter: any EvidencePresenting
    private let tips: (_ tipId: String, _ eventId: String) -> any TipDetailPresenting

    init(presenter: any EvidencePresenting, tips: @escaping (_ tipId: String, _ eventId: String) -> any TipDetailPresenting) {
        self.presenter = presenter
        self.tips = tips
        state = presenter.state
    }

    /// Follows the presenter's state (`.task {}`).
    public func observe() async {
        for await next in presenter.states() { state = next }
    }

    func navigationEvents() -> AsyncStream<EvidenceNavigation> { presenter.navigation() }

    public func send(_ action: EvidenceAction) {
        presenter.dispatch(action)
        state = presenter.state
    }

    func openTip(tipId: String, eventId: String) {
        tip?.close()
        tip = TipDetailViewModel(presenter: tips(tipId, eventId))
    }

    /// The tip was popped.
    public func tipClosed() {
        tip?.close()
        tip = nil
    }

    func close() {
        tip?.close()
        tip = nil
        presenter.close()
    }

    /// Whether the S-SCO-03 small sheet is up; setting false dismisses it.
    public var isNeededSheetShown: Bool {
        get { state.neededSheet != nil }
        set { if !newValue, state.neededSheet != nil { send(.dismissNeeded) } }
    }
}

/// S-SCO-04 (F-SCO-06) over the shared `TipDetailPresenter`. "See the moment" and Back both return to the evidence
/// sheet it was pushed from.
@MainActor
@Observable
public final class TipDetailViewModel {
    public private(set) var state: TipDetailScreenState
    /// The presenter asked to leave: back to the evidence.
    public private(set) var finished = false

    private let presenter: any TipDetailPresenting

    init(presenter: any TipDetailPresenting) {
        self.presenter = presenter
        state = presenter.state
    }

    public func observe() async {
        for await next in presenter.states() { state = next }
    }

    public func observeNavigation() async {
        for await event in presenter.navigation() {
            switch event {
            case .openEvidence, .close: finished = true
            }
        }
    }

    public func send(_ action: TipDetailAction) { presenter.dispatch(action) }

    func close() { presenter.close() }
}
