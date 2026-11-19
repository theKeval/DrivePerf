import DPSharedBridge
import XCTest

@testable import DPFeatures
@testable import DrivePerf

/// DRIVE-34, DRIVE-39, DRIVE-203 on iOS: the pins, readouts and sheets S-RPL-01 adds over the shared presenters. The
/// presenters' own rules are tested in Kotlin (`ReplayPinsTest`, `EvidencePresenterTest`, `TipDetailPresenterTest`).
@MainActor
final class ReplayPinsTests: XCTestCase {
    /// The real shared presenter over the synthetic fixture: the overlay reaches Swift with its words.
    func testTheSyntheticFixtureCarriesPinsTicksAndStats() async {
        let presenter = SyntheticReplayPresenters().presenter(tripId: "syn", source: .tripDetail)
        var loaded: ReplayScreenState?
        for await state in presenter.states() where state.overlay != nil {
            loaded = state
            break
        }
        let overlay = try? XCTUnwrap(loaded?.overlay)
        XCTAssertEqual(overlay?.eventPins.map(\.id), ["syn-brake", "syn-corner"], "the event in the GPS gap is not pinned")
        XCTAssertEqual(overlay?.ticks.count, 3, "every event ticks the scrubber")
        XCTAssertEqual(overlay?.eventPins.last?.needed, true)
        XCTAssertEqual(overlay?.stopPins.map(\.number), [1])
        XCTAssertEqual(overlay?.summary, "103 km/h max · 1 stop · 2 events")
        XCTAssertEqual(overlay?.elevationText, "↗ 64 m ↘ 58 m")
        XCTAssertEqual(overlay?.distanceText(at: 1_800), "19.9")
        XCTAssertEqual(overlay?.crossed(from: 359, to: 361), ["syn-brake"])

        presenter.dispatch(.selectStop(1))
        XCTAssertEqual(presenter.state.selection, .stop(1))
        XCTAssertEqual(presenter.state.pinSheet?.value, "34")
        XCTAssertEqual(presenter.state.pinSheet?.provenance, .derived)
        presenter.dispatch(.jumpToPin)
        XCTAssertNil(presenter.state.pinSheet)
        XCTAssertEqual(presenter.playhead(at: presenter.nowNs()), 900)
        presenter.close()
    }

    func testAnEventTapOpensTheEvidenceAndTheSheetDrivesTheReplay() async {
        let presenter = FakeReplayPresenter()
        presenter.state = ReplayScreenState(loading: false, track: FakeReplayTrack(), overlay: FakeOverlay())
        presenter.navigationEvents = [.openEvidence(eventId: "e1")]
        let evidence = FakeEvidencePresenter()
        evidence.events = [.showEvent("e2"), .feedbackChanged, .toast("Counted again"), .close]
        let model = ReplayViewModel(
            presenter: presenter,
            sheets: ReplaySheetSource(evidence: { _ in evidence }, tip: { _, _ in FakeTipPresenter() }))

        model.selectEvent("e1")
        XCTAssertEqual(presenter.actions.last, .selectEvent("e1"))
        await model.observeNavigation()
        XCTAssertNotNil(model.evidence)
        // The sheet's navigation runs in its own task: wait for it to finish.
        for _ in 0..<50 where model.evidence != nil { try? await Task.sleep(for: .milliseconds(10)) }
        XCTAssertTrue(presenter.actions.contains(.showEvent("e2")))
        XCTAssertTrue(presenter.actions.contains(.reloadDetails))
        XCTAssertEqual(model.toast, "Counted again")
        XCTAssertNil(model.evidence, "Close closes the sheet")
        XCTAssertEqual(presenter.actions.last, .clearSelection)
        XCTAssertTrue(evidence.closed)
    }

    func testReadoutsComeFromTheOverlay() {
        let presenter = FakeReplayPresenter()
        presenter.state = ReplayScreenState(loading: false, track: FakeReplayTrack(), overlay: FakeOverlay())
        presenter.playheadS = 100
        let model = ReplayViewModel(presenter: presenter)
        model.frameTick()
        XCTAssertEqual(model.readoutDistance, "1.0")
        XCTAssertEqual(model.readoutSpeed, "54")
        presenter.playheadS = 220
        model.frameTick()
        XCTAssertEqual(model.readoutSpeed, "—", "no speed in a GPS gap")
    }

    func testPinsPulseOnlyWhilePlaying() {
        let presenter = FakeReplayPresenter()
        presenter.state = ReplayScreenState(loading: false, track: FakeReplayTrack(), overlay: FakeOverlay())
        let model = ReplayViewModel(presenter: presenter)
        presenter.playheadS = 150
        model.frameTick()
        XCTAssertTrue(model.pulses.isEmpty, "a paused seek past a pin does not pulse it")
        model.togglePlay()
        presenter.playheadS = 160
        model.frameTick()
        presenter.playheadS = 170
        model.frameTick()
        XCTAssertNotNil(model.pulses["e1"])
    }

    func testTheFocusIsTheSelectedPinnedEvent() {
        let presenter = FakeReplayPresenter()
        presenter.state = ReplayScreenState(
            loading: false, track: FakeReplayTrack(), overlay: FakeOverlay(), selection: .event("e1"))
        XCTAssertEqual(ReplayViewModel(presenter: presenter).focus?.id, "e1")
        presenter.state.selection = .stop(1)
        XCTAssertNil(ReplayViewModel(presenter: presenter).focus)
    }

    func testTheEvidenceModelForwardsActionsAndPushesTheTip() async {
        let evidence = FakeEvidencePresenter()
        let tip = FakeTipPresenter()
        let model = EvidenceViewModel(presenter: evidence, tips: { _, _ in tip })
        model.send(.askNeeded)
        XCTAssertEqual(evidence.actions, [.askNeeded])
        model.openTip(tipId: "brake_light", eventId: "e1")
        XCTAssertNotNil(model.tip)
        model.tipClosed()
        XCTAssertNil(model.tip)
        XCTAssertTrue(tip.closed)
        evidence.state.neededSheet = NeededSheetInfo(
            step: .ask, title: "Was it needed?", body: "", primary: "Yes", secondary: "No", score: nil, capNote: "")
        await model.observe()
        XCTAssertTrue(model.isNeededSheetShown)
        model.isNeededSheetShown = false
        XCTAssertEqual(evidence.actions.last, .dismissNeeded)
    }
}

/// One braking event at 165 s, one stop at 50 s; distance is 10 m per second, speed is 10 m/s.
final class FakeOverlay: ReplayOverlayReading, @unchecked Sendable {
    let eventPins = [
        ReplayEventPinInfo(
            id: "e1", kind: .brake, tPeakS: 165, lat: FakeReplayTrack.lat(165), lng: 0, needed: false, pinned: true,
            accessibilityLabel: "Hard braking."),
    ]
    var ticks: [ReplayEventPinInfo] { eventPins }
    let stopPins = [ReplayStopPinInfo(number: 1, atS: 50, durationS: 20, lat: FakeReplayTrack.lat(50), lng: 0)]
    var stopTicks: [ReplayStopPinInfo] { stopPins }
    let stopCount = 1
    let maxSpeed: ReplayMaxSpeedPinInfo? = nil
    let summary = "1 stop · 1 event"
    let noEventsText: String? = nil
    let elevationText: String? = nil
    let elevation: [ReplayElevationSample] = []
    let mapSummary = "Map of your drive, 6.0 km. 1 event, 1 stop."
    let speedUnit = "km/h"
    let distanceUnit = "km"

    func distanceText(at tS: Double) -> String { String(format: "%.1f", tS * 10 / 1000) }
    func speedText(_ speedMps: Double?) -> String { speedMps.map { String(Int(($0 * 3.6).rounded())) } ?? "—" }
    func elevationFraction(at tS: Double) -> Double { 0.5 }
    func crossed(from fromS: Double, to toS: Double) -> [String] {
        toS > fromS ? eventPins.filter { $0.tPeakS > fromS && $0.tPeakS <= toS }.map(\.id) : []
    }
}

@MainActor
final class FakeEvidencePresenter: EvidencePresenting {
    var state = EvidenceScreenState(eventId: "e1", loading: false)
    var events: [EvidenceNavigation] = []
    private(set) var actions: [EvidenceAction] = []
    private(set) var closed = false

    func states() -> AsyncStream<EvidenceScreenState> {
        let value = state
        return AsyncStream { $0.yield(value); $0.finish() }
    }

    func navigation() -> AsyncStream<EvidenceNavigation> {
        let values = events
        return AsyncStream { continuation in
            values.forEach { continuation.yield($0) }
            continuation.finish()
        }
    }

    func dispatch(_ action: EvidenceAction) { actions.append(action) }
    func close() { closed = true }
}

@MainActor
final class FakeTipPresenter: TipDetailPresenting {
    let state = TipDetailScreenState(loading: false)
    private(set) var closed = false

    func states() -> AsyncStream<TipDetailScreenState> { AsyncStream { $0.finish() } }
    func navigation() -> AsyncStream<TipDetailNavigation> { AsyncStream { $0.finish() } }
    func dispatch(_ action: TipDetailAction) {}
    func close() { closed = true }
}
