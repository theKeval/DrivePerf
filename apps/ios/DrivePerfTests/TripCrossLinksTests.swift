import DPSharedBridge
import DrivePerfShared
import XCTest

@testable import DPFeatures
@testable import DrivePerf

/// DRIVE-87 AC2, DRIVE-55, DRIVE-102, DRIVE-39 AC1 on iOS: the evidence and tip sheets opened from trip detail, the
/// breakdown and Analysis, and the bridge for the new actions, effects and fields. The presenters' own rules are tested
/// in Kotlin (`DrivesCrossLinksTest`, `AnalysisEvidenceLinkTest`).
@MainActor
final class TripCrossLinksTests: XCTestCase {
    private var evidenceMade: [String] = []
    private var tipsMade: [(String?, String?)] = []
    private var evidencePresenter = FakeEvidencePresenter()
    private var tipPresenter = TipFake()

    private func sheets() -> TripEvidenceSheets {
        TripEvidenceSheets(
            evidence: { [unowned self] id in
                evidenceMade.append(id)
                return evidencePresenter
            },
            tip: { [unowned self] tipId, eventId in
                tipsMade.append((tipId, eventId))
                return tipPresenter
            })
    }

    /// Lets the sheets' navigation task drain the fake's events.
    private func settle(until done: () -> Bool) async {
        for _ in 0..<200 where !done() { await Task.yield() }
    }

    func testAnEventOpensItsEvidenceAndClosingClosesThePresenter() {
        let s = sheets()
        XCTAssertNil(s.evidence)
        s.openEvidence("e2")
        XCTAssertEqual(evidenceMade, ["e2"])
        XCTAssertNotNil(s.evidence)
        s.closeEvidence()
        XCTAssertNil(s.evidence)
        XCTAssertTrue(evidencePresenter.closed)
    }

    func testTheEvidenceThanksTheUserTellsTheScreenUnderneathAndCloses() async {
        evidencePresenter.events = [.showEvent("e3"), .toast("Thanks."), .feedbackChanged, .close]
        let s = sheets()
        var changed = 0
        s.onFeedbackChanged = { changed += 1 }
        s.openEvidence("e2")
        await settle { s.evidence == nil }
        XCTAssertNil(s.evidence, "Send closes the sheet")
        XCTAssertEqual(s.toast, "Thanks.")
        XCTAssertEqual(changed, 1)
        s.toastShown()
        XCTAssertNil(s.toast)
    }

    func testTheEvidencesTipIsPushedInsideTheSheet() async {
        evidencePresenter.events = [.openTip(tipId: "brake_light", eventId: "e1")]
        let s = sheets()
        s.openEvidence("e1")
        await settle { s.evidence?.tip != nil }
        XCTAssertNotNil(s.evidence?.tip)
        XCTAssertEqual(tipsMade.map(\.0), ["brake_light"])
        XCTAssertEqual(tipsMade.map(\.1), ["e1"])
        XCTAssertNil(s.tip, "S-SCO-04 from the sheet is the sheet's own push, not trip detail's")
    }

    func testTheTripsTipIsPushedAndSeeTheMomentOpensItsEventOnceItHasGone() {
        tipPresenter.state = TipDetailScreenState(loading: false, tipId: "brake_light", eventId: "e1")
        let s = sheets()
        s.openTip()
        XCTAssertNotNil(s.tip)
        XCTAssertEqual(tipsMade.map(\.0), [nil])
        s.tipFinished()
        XCTAssertNil(s.tip)
        XCTAssertTrue(tipPresenter.closed)
        XCTAssertNil(s.evidence, "the evidence waits for the tip screen to leave")
        s.tipDisappeared()
        XCTAssertEqual(evidenceMade, ["e1"])
        // A second disappearance (Back, say) opens nothing more.
        s.tipDisappeared()
        XCTAssertEqual(evidenceMade, ["e1"])
    }

    func testPoppingTheTipOpensNothing() {
        let s = sheets()
        s.openTip()
        s.tipClosed()
        s.tipDisappeared()
        XCTAssertNil(s.evidence)
        XCTAssertTrue(evidenceMade.isEmpty)
    }

    func testTheTripListSendsTheLinksToThePresenter() {
        let presenter = StaticDrivesPresenter()
        let trips = TripListViewModel(presenter: presenter)
        trips.evidenceOpened(tripId: "t", eventId: "e1")
        trips.tipOpened("t")
        trips.tipEvidenceOpened("t")
        XCTAssertEqual(presenter.actions, [.openEvidence(tripId: "t", eventId: "e1"), .openTip(tripId: "t"), .openTipEvidence(tripId: "t")])
    }

    func testTheNewActionsReachKotlin() {
        let evidence = CoreDrivesPresenter.kotlin(.openEvidence(tripId: "t", eventId: "e1")) as? DrivesIntentOpenEvidence
        XCTAssertEqual(evidence?.tripId, "t")
        XCTAssertEqual(evidence?.eventId, "e1")
        XCTAssertEqual((CoreDrivesPresenter.kotlin(.openTip(tripId: "t")) as? DrivesIntentOpenTip)?.tripId, "t")
        XCTAssertEqual((CoreDrivesPresenter.kotlin(.openTipEvidence(tripId: "t")) as? DrivesIntentOpenTipEvidence)?.tripId, "t")
        XCTAssertEqual((CoreAnalysisPresenter.kotlin(.openEvidence("e1")) as? AnalysisIntentOpenEvidence)?.eventId, "e1")
        XCTAssertTrue(CoreAnalysisPresenter.kotlin(.reload) is AnalysisIntentReload)
    }

    func testTheEvidenceAndTipEffectsAreNotNavigationOnIOS() {
        XCTAssertNil(CoreDrivesPresenter.navigation(DrivesEffectOpenEvidence(tripId: "t", eventId: "e1")))
        XCTAssertNil(CoreDrivesPresenter.navigation(DrivesEffectOpenTip(tripId: "t")))
    }

    func testTheSwiftWordsMatchTheSharedCopy() {
        XCTAssertEqual(CoreDrivesPresenter.links, TripLinkWords())
    }

    func testTheMapHeroAndTheTipsMomentReachSwift() {
        let map = TripMapUi(
            widthM: 100, heightM: 200,
            segments: [MapSegment(style: .estimated, xs: doubles([0, 50]), ys: doubles([0, 100]))],
            pins: [MapPin(eventId: "e1", type: .brake, needed: true, at: MapPoint(x: 10, y: 20))],
            stops: [MapPoint(x: 30, y: 40)], maxSpeed: MapPoint(x: 50, y: 60), estimatedStart: true,
            accessibilityLabel: "Map of your drive, 1.0 km. 1 event, 1 stop.")
        let swift = CoreDrivesPresenter.tripMap(map)
        XCTAssertEqual(swift.segments.first?.style, .estimated)
        XCTAssertEqual(swift.segments.first?.points, [AnalysisMapPoint(x: 0, y: 0), AnalysisMapPoint(x: 50, y: 100)])
        XCTAssertEqual(swift.pins, [AnalysisMapPin(eventId: "e1", type: .brake, needed: true, at: AnalysisMapPoint(x: 10, y: 20))])
        XCTAssertEqual(swift.stops, [AnalysisMapPoint(x: 30, y: 40)])
        XCTAssertEqual(swift.maxSpeed, AnalysisMapPoint(x: 50, y: 60))
        XCTAssertTrue(swift.estimatedStart)
        XCTAssertEqual(swift.accessibilityLabel, "Map of your drive, 1.0 km. 1 event, 1 stop.")

        let tip = TipUi(
            tipId: "brake_light", category: .braking, kicker: "One thing for next time", title: "Ease off earlier",
            text: "Next time, ease off earlier.", why: "Why.", eventId: "e1")
        XCTAssertEqual(CoreDrivesPresenter.tip(tip).eventId, "e1")
    }

    private func doubles(_ values: [Double]) -> KotlinDoubleArray {
        let array = KotlinDoubleArray(size: Int32(values.count))
        for (i, v) in values.enumerated() { array.set(index: Int32(i), value: v) }
        return array
    }
}

/// A tip presenter with a settable state, recording close.
@MainActor
private final class TipFake: TipDetailPresenting {
    var state = TipDetailScreenState(loading: false)
    private(set) var closed = false

    func states() -> AsyncStream<TipDetailScreenState> { AsyncStream { $0.finish() } }
    func navigation() -> AsyncStream<TipDetailNavigation> { AsyncStream { $0.finish() } }
    func dispatch(_ action: TipDetailAction) {}
    func close() { closed = true }
}
