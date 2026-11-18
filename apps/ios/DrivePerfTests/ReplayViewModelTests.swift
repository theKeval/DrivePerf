import DPSharedBridge
import XCTest

@testable import DPFeatures
@testable import DrivePerf

/// DRIVE-200 (F-RPL-01) and DRIVE-61 (F-RPL-08) on iOS: S-RPL-01's view model against the shared Replay contract.
@MainActor
final class ReplayViewModelTests: XCTestCase {
    private var loader: FakeReplayLoader!
    private var analytics: FakeReplayAnalytics!

    override func setUp() {
        loader = FakeReplayLoader(track: FakeReplayTrack())
        analytics = FakeReplayAnalytics()
    }

    private func model(
        connectivity: (any ConnectivityMonitoring)? = nil, lock: any ReplayProLock = ReplayProLockNotBuilt()
    ) -> ReplayViewModel {
        ReplayViewModel(
            tripId: "t1", loader: loader, analytics: analytics, connectivity: connectivity, lock: lock,
            tripStartedAt: Date(timeIntervalSince1970: 0), source: "trip_detail",
            now: { Date(timeIntervalSince1970: 3_600) })
    }

    private func loaded() async -> ReplayViewModel {
        let model = model()
        await model.load()
        return model
    }

    // MARK: AC1

    func testOpensFittedWithTheWholeRouteAndTheMarkerAtTheStartPaused() async {
        let model = await loaded()
        XCTAssertEqual(model.phase, .ready)
        XCTAssertFalse(model.isPlaying)
        XCTAssertEqual(model.tS, 0)
        XCTAssertEqual(model.frame?.lat, FakeReplayTrack.lat(0))
        XCTAssertEqual(model.bounds, FakeReplayTrack.bounds)
        XCTAssertEqual(model.speed, .auto)
        XCTAssertEqual(model.segments.map(\.style), [.estimated, .observed, .noGps, .observed, .excluded, .observed])
        XCTAssertEqual(model.polylines.count, model.segments.count)
        XCTAssertEqual(model.stylesPresent, [.observed, .estimated, .noGps, .excluded])
        XCTAssertEqual(analytics.events, [.opened(source: "trip_detail")])
    }

    func testALoadThatFindsNothingShowsTheErrorAndTryAgainLoads() async {
        loader.track = nil
        let model = model()
        await model.load()
        XCTAssertEqual(model.phase, .failed)
        loader.track = FakeReplayTrack()
        await model.load()
        XCTAssertEqual(model.phase, .ready)
        XCTAssertEqual(analytics.events, [.opened(source: "trip_detail")], "opened is sent once per screen")
    }

    func testTheProLockSeamShowsTheLockWithoutLoading() async {
        let model = model(lock: AlwaysLocked())
        await model.load()
        XCTAssertEqual(model.phase, .locked)
        XCTAssertEqual(loader.calls, 0)
    }

    func testNothingIsLockedUntilEntitlementsExist() {
        XCTAssertFalse(ReplayProLockNotBuilt().isLocked(tripStartedAt: .distantPast, now: Date()))
    }

    // MARK: AC2, AC4

    func testPlayAdvancesOnTheDisplayClockAtTheAutoRate() async {
        let model = await loaded()
        model.play()
        XCTAssertTrue(model.isPlaying)
        model.tick(at: 100)
        XCTAssertEqual(model.tS, 0, "the first tick only sets the clock")
        model.tick(at: 100.1)
        // Auto plays 600 s in 60 s: 10 trip seconds per second.
        XCTAssertEqual(model.tS, 1, accuracy: 1e-9)
        XCTAssertEqual(model.frame?.lat ?? 0, FakeReplayTrack.lat(1), accuracy: 1e-9)
        XCTAssertEqual(analytics.events.last, .played(speed: .auto))
    }

    func testPauseStopsInPlace() async {
        let model = await loaded()
        model.play()
        model.tick(at: 0)
        model.tick(at: 0.2)
        model.pause()
        let at = model.tS
        model.tick(at: 5)
        XCTAssertFalse(model.isPlaying)
        XCTAssertEqual(model.tS, at)
    }

    func testSpeedChangesTheRateAtOnce() async {
        let model = await loaded()
        model.play()
        model.tick(at: 0)
        model.select(.x30)
        model.tick(at: 0.1)
        XCTAssertEqual(model.tS, 3, accuracy: 1e-9)
        model.select(.x1)
        model.tick(at: 0.2)
        XCTAssertEqual(model.tS, 3.1, accuracy: 1e-9)
        XCTAssertEqual(analytics.events.suffix(2), [.played(speed: .x30), .played(speed: .x1)])
    }

    func testAStalledFrameNeverLeaps() async {
        let model = await loaded()
        model.play()
        model.tick(at: 0)
        model.tick(at: 30)
        XCTAssertEqual(model.tS, ReplayViewModel.maxTickS * 10, accuracy: 1e-9)
    }

    func testTheEndPausesAndCompletesAndPlayRestarts() async {
        let model = await loaded()
        model.select(.x30)
        model.play()
        model.scrub(to: 599.9)
        model.endScrub()
        model.tick(at: 0)
        model.tick(at: 0.2)
        XCTAssertEqual(model.tS, 600)
        XCTAssertFalse(model.isPlaying)
        XCTAssertEqual(analytics.events.last, .completed)
        model.play()
        XCTAssertEqual(model.tS, 0)
        XCTAssertTrue(model.isPlaying)
    }

    // MARK: AC3

    func testReleasingTheScrubberResumesPlaying() async {
        let model = await loaded()
        model.play()
        model.beginScrub()
        XCTAssertFalse(model.isPlaying)
        XCTAssertTrue(model.isScrubbing)
        model.scrub(to: 300)
        XCTAssertEqual(model.tS, 300)
        XCTAssertEqual(model.frame?.lat ?? 0, FakeReplayTrack.lat(300), accuracy: 1e-9)
        model.endScrub()
        XCTAssertTrue(model.isPlaying)
        XCTAssertFalse(model.isScrubbing)
        XCTAssertEqual(analytics.events.last, .scrubbed)
    }

    func testReleasingTheScrubberStaysPausedWhenItWasPaused() async {
        let model = await loaded()
        model.scrub(to: 120)
        model.endScrub()
        XCTAssertFalse(model.isPlaying)
        XCTAssertEqual(model.tS, 120)
    }

    func testScrubbingClampsToTheTripAndStepsThirtySeconds() async {
        let model = await loaded()
        model.scrub(to: -5)
        XCTAssertEqual(model.tS, 0)
        model.scrub(to: 5_000)
        XCTAssertEqual(model.tS, 600)
        model.endScrub()
        model.step(by: -30)
        XCTAssertEqual(model.tS, 570)
    }

    // MARK: F-RPL-08

    func testTheSpanLabelFollowsTheMarker() async {
        let model = await loaded()
        model.scrub(to: 10)
        XCTAssertEqual(model.spanLabel, "Estimated start")
        model.scrub(to: 100)
        XCTAssertNil(model.spanLabel)
        model.scrub(to: 210)
        XCTAssertEqual(model.spanLabel, "No GPS")
        XCTAssertTrue(model.isMarkerHidden, "the marker hides in a GPS gap")
        XCTAssertEqual(model.speedText, "\u{2014}")
        model.scrub(to: 420)
        XCTAssertEqual(model.spanLabel, "Phone moved \u{2014} not scored")
        XCTAssertFalse(model.isMarkerHidden)
    }

    func testTheLegendIsCounted() async {
        let model = await loaded()
        model.legendOpened()
        XCTAssertEqual(analytics.events.last, .legendOpened)
    }

    // MARK: AC5

    func testNoNetworkSwitchesToThePlainCanvas() async {
        let model = model(connectivity: FakeConnectivity(states: [true, false]))
        await model.load()
        XCTAssertTrue(model.isOnline)
        await model.observeConnectivity()
        XCTAssertFalse(model.isOnline)
    }

    // MARK: Frames and zoom

    func testTheFrameTableNeverInterpolatesAcrossAGap() {
        let track = FakeReplayTrack()
        let table = ReplayFrameTable(durationS: track.durationS) { track.frame(at: $0) }
        let before = table.frame(at: 199.25)
        XCTAssertEqual(before.style, .observed)
        XCTAssertEqual(before.lat, FakeReplayTrack.lat(199.25), accuracy: 1e-9)
        let edge = table.frame(at: 199.75)
        XCTAssertEqual(edge.lat, FakeReplayTrack.lat(199.5), accuracy: 1e-9, "no line drawn into the gap")
        let inside = table.frame(at: 220.2)
        XCTAssertEqual(inside.style, .noGps)
        XCTAssertEqual(inside.lat, FakeReplayTrack.lat(200), accuracy: 1e-9, "held at the last fix")
        let after = table.frame(at: 240.25)
        XCTAssertEqual(after.style, .observed)
        XCTAssertEqual(after.lat, FakeReplayTrack.lat(240.25), accuracy: 1e-9, "jumps to the far side")
    }

    func testTheFrameTableTurnsTheShortWayRound() {
        let table = ReplayFrameTable(durationS: 1, step: 1) { t in
            ReplayFrame(tS: t, lat: 0, lng: 0, bearingDeg: t == 0 ? 350 : 10)
        }
        XCTAssertEqual(table.frame(at: 0.5).bearingDeg ?? -1, 0, accuracy: 1e-9)
    }

    func testZoomBucketsFollowTheVisibleSpan() {
        XCTAssertEqual(ReplayZoom.bucket(longitudeDelta: 360), 0)
        XCTAssertEqual(ReplayZoom.bucket(longitudeDelta: 0.35), 10)
        XCTAssertEqual(ReplayZoom.bucket(longitudeDelta: 0), 20)
        XCTAssertEqual(ReplayZoom.bucket(longitudeDelta: 1e-9), 20)
    }

    func testAZoomChangeAsksTheCoreForThatBucketOnce() async {
        let model = await loaded()
        let track = loader.track as? FakeReplayTrack
        model.setZoomBucket(5)
        model.setZoomBucket(12)
        model.setZoomBucket(5)
        XCTAssertEqual(model.zoomBucket, 5)
        XCTAssertEqual(track?.bucketsAsked.filter { $0 == 5 }.count, 1)
    }

    // MARK: Analytics keys

    func testEventsCarryOnlyTheirKeys() {
        XCTAssertEqual(ReplayAnalyticsEvent.opened(source: "trip_detail").name, "replay_opened")
        XCTAssertEqual(ReplayAnalyticsEvent.opened(source: "trip_detail").properties, ["source": "trip_detail"])
        XCTAssertEqual(ReplayAnalyticsEvent.played(speed: .x10).properties, ["speed": "10x"])
        XCTAssertEqual(ReplayAnalyticsEvent.scrubbed.name, "replay_scrubbed")
        XCTAssertEqual(ReplayAnalyticsEvent.completed.name, "replay_completed")
        XCTAssertEqual(ReplayAnalyticsEvent.legendOpened.name, "quality_overlay_legend_opened")
        XCTAssertEqual(ReplayAnalyticsEvent.completed.properties, [:])
    }

    // MARK: The synthetic fake keeps the contract

    func testTheSyntheticReplayKeepsStyleBoundariesAndTheAutoRate() {
        let replay = SyntheticReplay(tripId: "s", durationS: 600, estimatedS: 30)
        XCTAssertEqual(replay.playbackRate(.auto), 10)
        let segments = replay.segments(zoomBucket: 8)
        XCTAssertEqual(segments.first?.style, .estimated)
        XCTAssertTrue(segments.contains { $0.style == .noGps })
        XCTAssertTrue(segments.contains { $0.style == .excluded })
        for (a, b) in zip(segments, segments.dropFirst()) {
            XCTAssertEqual(a.points.last?.tS, b.points.first?.tS, "segments meet, so styles never leave a hole")
        }
        XCTAssertEqual(replay.frame(at: 220).style, .noGps)
        XCTAssertEqual(replay.frame(at: 100).style, .observed)
        XCTAssertEqual(replay.frame(at: 10).style, .estimated)
    }
}

// MARK: - Fakes

/// 600 s along a straight line north: estimated 0–30 s, a GPS gap 200–240 s, excluded 400–460 s.
final class FakeReplayTrack: ReplayTrack, @unchecked Sendable {
    static let bounds = ReplayBounds(minLat: 0, minLng: 0, maxLat: lat(600), maxLng: 0.001)
    static func lat(_ t: Double) -> Double { t * 1e-4 }

    let tripId = "t1"
    let bbox = FakeReplayTrack.bounds
    let durationS: Double = 600
    let summaryOnly = false
    let spans = [
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
        let style = spans.first { $0.contains(tS) }?.style ?? .observed
        let t = style == .noGps ? 200 : tS
        return ReplayFrame(tS: tS, lat: Self.lat(t), lng: 0, speedMps: style == .noGps ? nil : 15, bearingDeg: 0, style: style)
    }

    func playbackRate(_ speed: PlaybackSpeed) -> Double {
        switch speed {
        case .auto: durationS / 60
        case .x1: 1
        case .x10: 10
        case .x30: 30
        }
    }
}

@MainActor
final class FakeReplayLoader: ReplayLoading {
    var track: (any ReplayTrack)?
    private(set) var calls = 0

    init(track: (any ReplayTrack)?) { self.track = track }

    func replay(tripId: String) async -> (any ReplayTrack)? {
        calls += 1
        return track
    }
}

@MainActor
final class FakeReplayAnalytics: ReplayAnalytics {
    private(set) var events: [ReplayAnalyticsEvent] = []
    func track(_ event: ReplayAnalyticsEvent) { events.append(event) }
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

struct AlwaysLocked: ReplayProLock {
    func isLocked(tripStartedAt: Date, now: Date) -> Bool { true }
}
