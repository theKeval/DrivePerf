import DPSharedBridge
import XCTest

@testable import DPFeatures
@testable import DrivePerf

/// DRIVE-45 (F-RPL-05) and DRIVE-50 (F-RPL-06) on iOS: S-ANL-01's view model is a thin wrapper over the shared
/// `AnalysisPresenter` (its rules are tested in `AnalysisPresenterTest`). These check what the wrapper adds: the cursor
/// moves at once on a drag and reaches the presenter when the finger lifts, ticks snap, the map tap, pinch steps, and
/// that every other control is passed through.
@MainActor
final class AnalysisViewModelTests: XCTestCase {
    private var presenter: FakeAnalysisPresenter!

    override func setUp() {
        presenter = FakeAnalysisPresenter()
    }

    func testADragMovesTheCursorAtOnceAndCommitsItWhenTheFingerLifts() {
        let model = AnalysisViewModel(presenter: presenter)
        model.drag(x: 100, width: 400)
        XCTAssertEqual(model.cursorS, 50, accuracy: 1e-9)
        XCTAssertNil(model.snappedEventId)
        XCTAssertTrue(presenter.actions.isEmpty)
        model.dragEnded()
        XCTAssertEqual(presenter.actions, [.moveCursor(50)])
        XCTAssertEqual(model.readout?.headline, "0:50 elapsed")
    }

    func testADragNearATickSnapsToItsPeak() {
        let model = AnalysisViewModel(presenter: presenter)
        model.drag(x: 204, width: 400)
        XCTAssertEqual(model.cursorS, 100)
        XCTAssertEqual(model.snappedEventId, "e1")
        model.dragEnded()
        XCTAssertNil(model.snappedEventId)
    }

    func testTappingTheMapJumpsToTheNearestTrackPoint() {
        let model = AnalysisViewModel(presenter: presenter)
        model.tapMap(x: 0, y: 1_500)
        XCTAssertEqual(model.cursorS, 150)
        XCTAssertEqual(presenter.actions, [.moveCursor(150)])
    }

    func testAPinchSendsStepsNotTotals() {
        let model = AnalysisViewModel(presenter: presenter)
        model.pinchChanged(2)
        model.pinchChanged(4)
        model.pinchChanged(4.001)
        model.pinchEnded()
        model.pinchChanged(0.5)
        XCTAssertEqual(presenter.actions, [.pinch(2), .pinch(2), .pinch(0.5)])
    }

    func testAPresenterCursorMoveReachesTheDrawnCursor() async {
        var moved = presenter.state
        moved.cursorS = 120
        presenter.updates = [moved]
        let model = AnalysisViewModel(presenter: presenter)
        await model.observe()
        XCTAssertEqual(model.cursorS, 120)
        model.send(.expandGg)
        XCTAssertEqual(presenter.actions, [.expandGg])
    }

    func testWithoutATimelineNothingMoves() {
        presenter.state = AnalysisScreenState(tripId: "t1", loading: true)
        let model = AnalysisViewModel(presenter: presenter)
        model.drag(x: 10, width: 100)
        model.tapMap(x: 1, y: 1)
        XCTAssertEqual(model.cursorS, 0)
        XCTAssertTrue(presenter.actions.isEmpty)
    }
}

/// A 200 s timeline on a linear time axis with one braking tick at 100 s, and a straight 2 km map northwards.
@MainActor
private final class FakeTimeline: AnalysisTimelineView {
    let axis = AnalysisAxisKind.time
    let lanes = [AnalysisLane(channel: .speed, title: "Speed", rangeLabel: "0–120 km/h", xs: [0, 1], ys: [0.3, 0.3])]
    let bands: [AnalysisBand] = []
    let ticks = [AnalysisTick(eventId: "e1", type: .brake, needed: false, x: 0.5, tPeakS: 100, accessibilityLabel: "Hard braking")]
    let axisTicks = [AnalysisAxisTick(x: 0, label: "0:00")]
    let legend = [AnalysisLegendItem(kind: .speed, label: "Speed")]
    let summary = "Timeline"

    func timeAt(_ x: Double) -> Double { x * 200 }
    func xOf(_ tS: Double) -> Double { tS / 200 }

    func snap(_ x: Double, tolerance: Double) -> (tS: Double, eventId: String?) {
        abs(x - 0.5) <= tolerance ? (100, "e1") : (timeAt(x), nil)
    }

    func readout(_ tS: Double) -> AnalysisCursorReadout {
        AnalysisCursorReadout(tS: tS, x: xOf(tS), headline: "0:\(Int(tS)) elapsed")
    }

    func tableRows() -> [AnalysisTableRow] { [] }
}

@MainActor
private final class FakeMap: AnalysisMapView {
    let widthM = 0.0
    let heightM = 2_000.0
    let segments: [AnalysisMapSegment] = []
    let pins: [AnalysisMapPin] = []

    func nearestTime(x: Double, y: Double) -> Double { y / 10 }
}

@MainActor
private final class FakeAnalysisPresenter: AnalysisPresenting {
    var state = AnalysisScreenState(tripId: "t1", loading: false, timeline: FakeTimeline(), map: FakeMap())
    var updates: [AnalysisScreenState] = []
    var actions: [AnalysisAction] = []

    func states() -> AsyncStream<AnalysisScreenState> {
        AsyncStream { [updates] continuation in
            for u in updates { continuation.yield(u) }
            continuation.finish()
        }
    }

    func navigation() -> AsyncStream<AnalysisNavigation> { AsyncStream { $0.finish() } }

    func dispatch(_ action: AnalysisAction) { actions.append(action) }

    func close() {}
}
