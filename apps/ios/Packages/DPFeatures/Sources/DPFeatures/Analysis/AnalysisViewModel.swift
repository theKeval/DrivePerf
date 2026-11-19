import DPSharedBridge
import Foundation
import Observation

/// S-ANL-01/02/03 (F-RPL-05, F-RPL-06) as a thin wrapper over the shared `AnalysisPresenter` (ADR-002 section 4). The
/// presenter owns loading, the window, zoom, axis, channels, persistence and analytics; this keeps only the cursor,
/// so a drag redraws the lanes, the map marker and the G-G dot in the same frame (AC2) and reaches the presenter once,
/// when the finger lifts.
@MainActor
@Observable
public final class AnalysisViewModel {
    /// The presenter's state; it changes on load and on intents only.
    public private(set) var state: AnalysisScreenState
    /// The cursor in trip seconds.
    public private(set) var cursorS: Double = 0
    /// The event the drag last snapped to (drives the selection haptic).
    public private(set) var snappedEventId: String?
    /// A short message ("Keep at least one channel on").
    public var message: String?

    private let presenter: any AnalysisPresenting
    /// Set once the screen observes this model. SwiftUI may build and drop extra `AnalysisView` values (and their
    /// models) while `@State` keeps the first; only the model that ran owns the presenter and closes it.
    private var owns = false

    public init(presenter: any AnalysisPresenting) {
        self.presenter = presenter
        state = presenter.state
        cursorS = state.cursorS
    }

    isolated deinit {
        if owns { presenter.close() }
    }

    /// Follows the presenter's state (`.task {}`).
    public func observe() async {
        owns = true
        for await next in presenter.states() { apply(next) }
    }

    /// One-shot navigation; messages are kept here for the toast.
    public func navigation() -> AsyncStream<AnalysisNavigation> { presenter.navigation() }

    func apply(_ next: AnalysisScreenState) {
        if next.cursorS != state.cursorS { cursorS = next.cursorS }
        state = next
    }

    /// The readout at the cursor, for the row, the bubbles, the map marker and the G-G dot.
    public var readout: AnalysisCursorReadout? { state.timeline?.readout(cursorS) }

    // MARK: Gestures

    /// The finger at `x` points of a `width`-point timeline: within 8 pt of a tick it snaps to its peak.
    public func drag(x: Double, width: Double) {
        guard let timeline = state.timeline, width > 0 else { return }
        let snap = timeline.snap(x / width, tolerance: Self.snapPoints / width)
        cursorS = snap.tS
        if snap.eventId != snappedEventId { snappedEventId = snap.eventId }
    }

    public func dragEnded() {
        snappedEventId = nil
        presenter.dispatch(.moveCursor(cursorS))
    }

    /// The map was tapped at `x`, `y` metres (the map's frame): the cursor snaps to the nearest track point.
    public func tapMap(x: Double, y: Double) {
        guard let map = state.map else { return }
        jump(to: map.nearestTime(x: x, y: y))
    }

    public func jump(to tS: Double) {
        cursorS = tS
        presenter.dispatch(.moveCursor(tS))
    }

    public func send(_ action: AnalysisAction) {
        presenter.dispatch(action)
    }

    /// The pinch gesture reports a total scale; the presenter takes steps.
    private var lastPinch = 1.0

    public func pinchChanged(_ scale: Double) {
        guard scale > 0 else { return }
        let step = scale / lastPinch
        lastPinch = scale
        if abs(step - 1) > 0.01 { presenter.dispatch(.pinch(step)) }
    }

    public func pinchEnded() { lastPinch = 1 }

    static let snapPoints = 8.0
}
