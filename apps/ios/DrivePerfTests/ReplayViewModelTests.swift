import DPSharedBridge
import XCTest

@testable import DPFeatures
@testable import DrivePerf

/// DRIVE-200 (F-RPL-01) and DRIVE-61 (F-RPL-08) on iOS: S-RPL-01's view model is a thin wrapper over the shared
/// `ReplayPresenter` (its state machine is tested in `ReplayPresenterTest`). These check what the wrapper adds: the
/// frame loop reads the playhead and the core's frame and span, intents go to the presenter, the map's zoom buckets,
/// the offline switch, the phases and the drive-screen effect.
@MainActor
final class ReplayViewModelTests: XCTestCase {
    private var presenter: FakeReplayPresenter!

    override func setUp() {
        presenter = FakeReplayPresenter()
    }

    private func loaded(connectivity: (any ConnectivityMonitoring)? = nil) -> ReplayViewModel {
        presenter.state = ReplayScreenState(loading: false, track: FakeReplayTrack())
        return ReplayViewModel(presenter: presenter, connectivity: connectivity)
    }

    // MARK: AC1

    func testALoadedReplayIsDrawnWholeWithTheMarkerAtThePlayhead() {
        let model = loaded()
        XCTAssertEqual(model.phase, .ready)
        XCTAssertFalse(model.isPlaying)
        XCTAssertEqual(model.tS, 0)
        XCTAssertEqual(model.frame?.lat, FakeReplayTrack.lat(0))
        XCTAssertEqual(model.bounds, FakeReplayTrack.bounds)
        XCTAssertEqual(model.segments.map(\.style), [.estimated, .observed, .noGps, .observed, .excluded, .observed])
        XCTAssertEqual(model.polylines.count, model.segments.count)
        XCTAssertEqual(model.stylesPresent, [.observed, .estimated, .noGps, .excluded])
    }

    func testTheReplayArrivesThroughThePresentersState() async {
        presenter.updates = [ReplayScreenState(loading: false, track: FakeReplayTrack())]
        let model = ReplayViewModel(presenter: presenter)
        XCTAssertEqual(model.phase, .loading)
        await model.observe()
        XCTAssertEqual(model.phase, .ready)
        XCTAssertFalse(model.segments.isEmpty)
    }

    func testPhasesFollowWhyThereIsNoReplay() {
        presenter.state = ReplayScreenState(loading: false, missing: .notFound)
        XCTAssertEqual(ReplayViewModel(presenter: presenter).phase, .failed)
        presenter.state = ReplayScreenState(loading: false, missing: .locked)
        XCTAssertEqual(ReplayViewModel(presenter: presenter).phase, .locked, "the shared Pro gate shows the Lock state")
    }

    func testARecordingTripAsksForTheDriveScreen() async {
        presenter.navigationEvents = [.showDriveScreen]
        let model = ReplayViewModel(presenter: presenter)
        await model.observeNavigation()
        XCTAssertTrue(model.driveScreenRequested)
    }

    func testThePresenterIsClosedWithTheScreen() {
        var model: ReplayViewModel? = loaded()
        XCTAssertNotNil(model)
        model = nil
        XCTAssertTrue(presenter.closed)
    }

    // MARK: AC2 to AC4: intents and the frame loop

    func testEachFrameReadsThePlayheadThenTheCoresFrameAndTicks() {
        let model = loaded()
        presenter.playheadS = 120
        presenter.now = 42
        model.frameTick()
        XCTAssertEqual(model.tS, 120)
        XCTAssertEqual(model.frame?.lat ?? 0, FakeReplayTrack.lat(120), accuracy: 1e-12)
        XCTAssertEqual(presenter.actions.last, .tick(42))
    }

    func testPlayPauseAndSpeedGoToThePresenter() {
        let model = loaded()
        model.togglePlay()
        XCTAssertEqual(presenter.actions.last, .play)
        XCTAssertTrue(model.isPlaying)
        model.togglePlay()
        XCTAssertEqual(presenter.actions.last, .pause)
        model.select(.x30)
        XCTAssertEqual(presenter.actions.last, .setSpeed(.x30))
        let count = presenter.actions.count
        model.select(.x30)
        XCTAssertEqual(presenter.actions.count, count, "the selected speed is not sent again")
    }

    func testScrubbingMovesTheMarkerInTheSameFrame() {
        let model = loaded()
        model.scrub(to: 300)
        XCTAssertEqual(presenter.actions, [.scrubStart, .scrubTo(300)])
        XCTAssertEqual(model.tS, 300)
        XCTAssertEqual(model.frame?.lat ?? 0, FakeReplayTrack.lat(300), accuracy: 1e-12)
        model.scrub(to: 310)
        XCTAssertEqual(presenter.actions.suffix(1), [.scrubTo(310)], "one ScrubStart per gesture")
        XCTAssertEqual(presenter.actions.filter { $0 == .scrubStart }.count, 1)
        model.endScrub()
        XCTAssertEqual(presenter.actions.last, .scrubEnd)
    }

    func testAccessibilityStepsAreOneOffSeeks() {
        let model = loaded()
        presenter.playheadS = 100
        model.frameTick()
        model.step(by: 30)
        XCTAssertEqual(presenter.actions.last, .scrubTo(130))
    }

    func testTheLegendIsForwarded() {
        let model = loaded()
        model.legendOpened()
        XCTAssertEqual(presenter.actions.last, .openLegend)
    }

    // MARK: F-RPL-08: the marker and chip are the core's

    func testTheMarkerIsTheCoresFrameRightUpToASpanBoundary() {
        let model = loaded()
        model.scrub(to: 29.75)
        XCTAssertEqual(model.frame?.style, .estimated)
        XCTAssertNil(model.frame?.speedMps)
        XCTAssertEqual(model.spanLabel, "Estimated start")
        model.scrub(to: 30)
        XCTAssertEqual(model.frame?.style, .observed)
        XCTAssertEqual(model.frame?.speedMps, 15)
        XCTAssertNil(model.spanLabel)
    }

    func testInAGapTheMarkerHidesAndSpeedIsADash() {
        let model = loaded()
        model.scrub(to: 220)
        XCTAssertEqual(model.spanLabel, "No GPS")
        XCTAssertTrue(model.isMarkerHidden)
        XCTAssertEqual(model.speedText, "\u{2014}")
        model.scrub(to: 420)
        XCTAssertEqual(model.spanLabel, "Phone moved \u{2014} not scored")
        XCTAssertFalse(model.isMarkerHidden)
    }

    func testTheCoreLabelWinsOverTheStyleText() {
        let track = FakeReplayTrack()
        track.spans[2].label = "Not scored"
        presenter.state = ReplayScreenState(loading: false, track: track)
        let model = ReplayViewModel(presenter: presenter)
        model.scrub(to: 420)
        XCTAssertEqual(model.spanLabel, "Not scored")
    }

    // MARK: AC5 and zoom

    func testNoNetworkSwitchesToThePlainCanvas() async {
        let model = loaded(connectivity: FakeConnectivity(states: [true, false]))
        XCTAssertTrue(model.isOnline)
        await model.observeConnectivity()
        XCTAssertFalse(model.isOnline)
    }

    func testZoomBucketsMatchTheCoreFormula() {
        // Kotlin Replay.zoomBucketFor: floor(log2(360 * width / (256 * delta))), 0...22.
        XCTAssertEqual(ReplayZoomMath.bucket(longitudeDelta: 360, viewWidth: 256), 0)
        XCTAssertEqual(ReplayZoomMath.bucket(longitudeDelta: 0.35, viewWidth: 402), 10)
        XCTAssertEqual(ReplayZoomMath.bucket(longitudeDelta: 0, viewWidth: 402), 22)
        XCTAssertEqual(ReplayZoomMath.bucket(longitudeDelta: 1e-9, viewWidth: 402), 22)
    }

    func testACameraChangeAsksTheCoreForThatBucketOnce() {
        let track = FakeReplayTrack()
        presenter.state = ReplayScreenState(loading: false, track: track)
        let model = ReplayViewModel(presenter: presenter)
        model.cameraSettled(longitudeDelta: 360, viewWidth: 256 * 32)
        model.cameraSettled(longitudeDelta: 0.01, viewWidth: 402)
        model.cameraSettled(longitudeDelta: 360, viewWidth: 256 * 32)
        XCTAssertEqual(model.zoomBucket, 5)
        XCTAssertEqual(track.bucketsAsked.filter { $0 == 5 }.count, 1)
    }

    func testAStateChangeKeepsTheRoute() {
        let track = FakeReplayTrack()
        presenter.state = ReplayScreenState(loading: false, track: track)
        let model = ReplayViewModel(presenter: presenter)
        let asked = track.bucketsAsked.count
        model.togglePlay()
        XCTAssertEqual(track.bucketsAsked.count, asked, "playing does not rebuild the route")
    }
}

// MARK: - Fakes

/// A presenter that records intents and answers the playhead it is given. It has no state machine of its own: it only
/// flips `playing` and `scrubbing` so the wrapper's reads can be checked.
@MainActor
final class FakeReplayPresenter: ReplayPresenting {
    var state = ReplayScreenState()
    var updates: [ReplayScreenState] = []
    var navigationEvents: [ReplayNavigation] = []
    var playheadS: Double = 0
    var now: Int64 = 0
    private(set) var actions: [ReplayAction] = []
    private(set) var closed = false

    func states() -> AsyncStream<ReplayScreenState> {
        let values = [state] + updates
        if let last = updates.last { state = last }
        return AsyncStream { continuation in
            values.forEach { continuation.yield($0) }
            continuation.finish()
        }
    }

    func navigation() -> AsyncStream<ReplayNavigation> {
        let events = navigationEvents
        return AsyncStream { continuation in
            events.forEach { continuation.yield($0) }
            continuation.finish()
        }
    }

    func dispatch(_ action: ReplayAction) {
        actions.append(action)
        switch action {
        case .play: state.playing = true
        case .pause: state.playing = false
        case .setSpeed(let speed): state.speed = speed
        case .scrubStart: state.scrubbing = true
        case .scrubTo(let tS): playheadS = tS
        case .scrubEnd: state.scrubbing = false
        case .tick, .openLegend: break
        }
    }

    func nowNs() -> Int64 { now }
    func playhead(at nowNs: Int64) -> Double { playheadS }
    func close() { closed = true }
}

/// 600 s along a straight line north: estimated 0–30 s, a GPS gap 200–240 s, excluded 400–460 s.
final class FakeReplayTrack: ReplayTrack, @unchecked Sendable {
    static let bounds = ReplayBounds(minLat: 0, minLng: 0, maxLat: lat(600), maxLng: 0.001)
    static func lat(_ t: Double) -> Double { t * 1e-4 }

    let tripId = "t1"
    let bbox = FakeReplayTrack.bounds
    let durationS: Double = 600
    let summaryOnly = false
    var spans = [
        ReplaySpan(style: .estimated, t0S: 0, t1S: 30),
        ReplaySpan(style: .noGps, t0S: 200, t1S: 240),
        ReplaySpan(style: .excluded, t0S: 400, t1S: 460),
    ]
    private(set) var bucketsAsked: [Int] = []

    func segments(zoomBucket: Int) -> [ReplaySegment] {
        bucketsAsked.append(zoomBucket)
        func run(_ style: ReplaySegmentStyle, _ t0: Double, _ t1: Double) -> ReplaySegment {
            ReplaySegment(style: style, points: [t0, t1].map { ReplayPoint(tS: $0, lat: Self.lat($0), lng: 0) })
        }
        return [
            run(.estimated, 0, 30), run(.observed, 30, 200), run(.noGps, 200, 240), run(.observed, 240, 400),
            run(.excluded, 400, 460), run(.observed, 460, 600),
        ]
    }

    func frame(at tS: Double) -> ReplayFrame {
        let style = span(at: tS)?.style ?? .observed
        let t = style == .noGps ? 200 : tS
        let speed: Double? = style == .noGps || style == .estimated ? nil : 15
        return ReplayFrame(tS: tS, lat: Self.lat(t), lng: 0, speedMps: speed, bearingDeg: 0, style: style)
    }

    func span(at tS: Double) -> ReplaySpan? {
        spans.first { $0.contains(tS, durationS: durationS) }
    }

    func zoomBucket(longitudeDelta: Double, viewWidth: Double) -> Int {
        ReplayZoomMath.bucket(longitudeDelta: longitudeDelta, viewWidth: viewWidth)
    }
}

@MainActor
final class FakeConnectivity: ConnectivityMonitoring {
    let states: [Bool]
    init(states: [Bool]) { self.states = states }

    func onlineUpdates() -> AsyncStream<Bool> {
        AsyncStream { continuation in
            states.forEach { continuation.yield($0) }
            continuation.finish()
        }
    }
}
