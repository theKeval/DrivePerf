import DPSharedBridge
import XCTest
@testable import DPFeatures

/// DRIVE-85 / DRIVE-115 / DRIVE-120: the in-drive lock, the content hold (S-DRV-05), the passenger escape (S-DRV-03)
/// and the auto-end note, on the view model with a fake recorder and a controllable clock.
@MainActor
final class InDriveLockTests: XCTestCase {
    private final class Clock {
        var date = Date(timeIntervalSince1970: 1_800_000_000)
        func advance(_ seconds: TimeInterval) { date = date.addingTimeInterval(seconds) }
    }

    private let clock = Clock()
    private var opened: [HeldContent] = []

    private func makeModel(_ recorder: FakeRecorder = FakeRecorder()) -> CaptureViewModel {
        let clock = clock
        let model = CaptureViewModel(
            recorder: recorder, history: recorder, permissions: FakePermissions(), clock: FixedClock(),
            now: { clock.date }, finishTimeout: .milliseconds(100), startTimeout: .milliseconds(300))
        model.onOpenContent = { [weak self] in self?.opened.append($0) }
        return model
    }

    private func trip(
        _ id: String = "t", source: TripSource = .manual, status: TripStatus = .finalised, endedAt: Date? = nil
    ) -> TripSummary {
        TripSummary(
            id: id, source: source, status: status, startedAt: clock.date.addingTimeInterval(-3_000),
            endedAt: endedAt ?? clock.date, distanceM: 9_000, samples: SampleCounts())
    }

    private func drive(_ model: CaptureViewModel, _ recorder: FakeRecorder, isManual: Bool = true) {
        recorder.publish(.recording(isManual: isManual))
        model.apply(.recording(isManual: isManual))
    }

    // MARK: Passenger (DRIVE-120)

    func testThePassengerSheetNeedsATripAndCancelClosesIt() {
        let recorder = FakeRecorder()
        let model = makeModel(recorder)
        model.requestPassenger()
        XCTAssertFalse(model.isPassengerSheetPresented, "nothing to mark when no trip is recording")

        drive(model, recorder)
        model.requestPassenger()
        XCTAssertTrue(model.isPassengerSheetPresented)
        model.askDiscard()
        XCTAssertEqual(model.passengerStep, .confirmDiscard)
        model.cancelPassenger()
        XCTAssertFalse(model.isPassengerSheetPresented)
        XCTAssertEqual(model.passengerStep, .choose)
        XCTAssertTrue(recorder.passengerMarks.isEmpty)
        XCTAssertEqual(model.cover, .recording, "Cancel changes nothing")
    }

    func testKeepingAsAPassengerSavesTheTripAndLiftsTheLock() async {
        let recorder = FakeRecorder()
        let saved = trip(source: .manual)
        recorder.passengerResult = saved
        let model = makeModel(recorder)
        drive(model, recorder)

        model.requestPassenger()
        await model.keepAsPassenger()
        model.apply(.idle)

        XCTAssertEqual(recorder.passengerMarks, [true])
        XCTAssertEqual(model.cover, .finished(.passenger(saved)))
        XCTAssertFalse(model.isPassengerSheetPresented)
        XCTAssertFalse(model.isContentLocked(now: clock.date), "the user is not driving: no hold after a passenger trip")
    }

    func testDontSaveDeletesTheTripAndLiftsTheLock() async {
        let recorder = FakeRecorder()
        let model = makeModel(recorder)
        drive(model, recorder)

        model.requestPassenger()
        model.askDiscard()
        await model.discardTrip()
        model.apply(.idle)

        XCTAssertEqual(recorder.passengerMarks, [false])
        XCTAssertEqual(model.cover, .finished(.discarded))
        XCTAssertFalse(model.isContentLocked(now: clock.date))
    }

    func testPassengerKeepOpensContentThatWasHeld() async {
        let recorder = FakeRecorder()
        let model = makeModel(recorder)
        drive(model, recorder)
        model.requestContent(.replay)
        XCTAssertEqual(model.cover, .held)

        await model.keepAsPassenger()

        XCTAssertEqual(opened, [.replay])
        XCTAssertNil(model.heldContent)
    }

    // MARK: Content lock (DRIVE-115)

    func testContentOpenedDuringATripShowsTheHeldScreenAndNothingOpens() {
        let recorder = FakeRecorder()
        let model = makeModel(recorder)
        drive(model, recorder)

        model.requestContent(.story)

        XCTAssertEqual(model.cover, .held)
        XCTAssertEqual(model.heldContent, .story)
        XCTAssertTrue(opened.isEmpty)
        clock.advance(600)
        model.tick(now: clock.date)
        XCTAssertTrue(opened.isEmpty, "never opens while driving")
    }

    func testTheHeldScreenStepsBackToTheDriveScreenSoStopStaysReachable() {
        let recorder = FakeRecorder()
        let model = makeModel(recorder)
        drive(model, recorder)
        model.requestContent(.story)

        clock.advance(DriveLock.heldReturnAfter - 1)
        model.tick(now: clock.date)
        XCTAssertEqual(model.cover, .held)
        clock.advance(1)
        model.tick(now: clock.date)

        XCTAssertEqual(model.cover, .recording)
        XCTAssertEqual(model.heldContent, .story, "the content keeps waiting")
    }

    func testTheHeldScreenNamesWhatWaitsExceptPaywallsAndShares() {
        XCTAssertEqual(HeldContent.replay.noun, "Replay")
        XCTAssertEqual(HeldContent.analysis.noun, "Analysis")
        XCTAssertEqual(HeldContent.paywall.noun, "Story")
        XCTAssertEqual(HeldContent.share.noun, "Story")
        XCTAssertFalse(HeldContent.paywall.isReopened)
        XCTAssertFalse(HeldContent.share.isReopened)
        XCTAssertTrue(HeldContent.story.isReopened)
    }

    func testAfterATripTheLockHoldsUntilTheSixtySecondCalmHasPassed() async {
        let recorder = FakeRecorder()
        recorder.stopResult = trip()
        let model = makeModel(recorder)
        drive(model, recorder)

        await model.endDrive()
        model.apply(.idle)

        XCTAssertTrue(model.isContentLocked(now: clock.date))
        XCTAssertTrue(model.isContentLocked(now: clock.date.addingTimeInterval(59)))
        XCTAssertFalse(model.isContentLocked(now: clock.date.addingTimeInterval(60)))
    }

    func testContentRequestedAfterATripWaitsAndOpensOnceParkedForAMinute() async {
        let recorder = FakeRecorder()
        recorder.stopResult = trip()
        let model = makeModel(recorder)
        drive(model, recorder)
        await model.endDrive()
        model.apply(.idle)

        model.requestContent(.story)
        XCTAssertEqual(model.cover, .held)
        clock.advance(59)
        model.tick(now: clock.date)
        XCTAssertTrue(opened.isEmpty)
        clock.advance(1)
        model.tick(now: clock.date)

        XCTAssertEqual(opened, [.story])
        XCTAssertNil(model.cover)
        XCTAssertFalse(model.isContentLocked(now: clock.date))
    }

    func testStillMovingAfterTheTripRestartsTheCalmCount() async {
        let recorder = FakeRecorder()
        recorder.stopResult = trip()
        let model = makeModel(recorder)
        model.setMoving(true)
        drive(model, recorder)
        await model.endDrive()
        model.apply(.idle)

        clock.advance(500)
        XCTAssertTrue(model.isContentLocked(now: clock.date), "still automotive: held however long it takes")
        model.setMoving(false)
        clock.advance(59)
        XCTAssertTrue(model.isContentLocked(now: clock.date))
        model.setMoving(true)
        clock.advance(30)
        model.setMoving(false)
        clock.advance(59)
        XCTAssertTrue(model.isContentLocked(now: clock.date), "movement in between restarted the minute")
        clock.advance(1)
        XCTAssertFalse(model.isContentLocked(now: clock.date))
    }

    func testAHoldThatRanItsMinuteIsNotRevivedByALaterAutomotiveReading() async {
        let recorder = FakeRecorder()
        recorder.stopResult = trip()
        let model = makeModel(recorder)
        drive(model, recorder)
        await model.endDrive()
        model.apply(.idle)

        clock.advance(DriveLock.calmInterval + 5)
        model.setMoving(true)

        XCTAssertFalse(model.isContentLocked(now: clock.date), "no tick ran, yet the minute had passed")
    }

    func testAPaywallHeldByTheLockIsDroppedNotReopened() async {
        let recorder = FakeRecorder()
        recorder.stopResult = trip()
        let model = makeModel(recorder)
        drive(model, recorder)
        model.requestContent(.paywall)
        await model.endDrive()
        model.apply(.idle)

        clock.advance(60)
        model.tick(now: clock.date)

        XCTAssertTrue(opened.isEmpty)
        XCTAssertNil(model.heldContent)
    }

    func testATripEndingOnItsOwnKeepsTheHeldScreenUpWhileContentWaits() {
        let recorder = FakeRecorder()
        let model = makeModel(recorder)
        drive(model, recorder, isManual: false)
        model.requestContent(.story)

        model.apply(.idle)

        XCTAssertEqual(model.cover, .held)
        XCTAssertTrue(model.isContentLocked(now: clock.date))
    }

    func testUnlockedContentOpensAtOnce() {
        let model = makeModel()
        model.requestContent(.story)
        XCTAssertEqual(opened, [.story])
        XCTAssertNil(model.cover)
    }

    // MARK: Decoding (DRIVE-85 AC2)

    func testStopShowsDecodingUntilTheTripIsListed() async {
        let recorder = FakeRecorder()
        let model = makeModel(recorder)
        drive(model, recorder)
        let saved = trip()

        let ending = Task { await model.endDrive() }
        for _ in 0..<20 where model.cover != .decoding { await Task.yield() }
        XCTAssertEqual(model.cover, .decoding, "S-DRV-04 shows while the trip is finalised")
        recorder.publishTrips([saved])
        await ending.value

        XCTAssertEqual(model.cover, .finished(.saved(saved, autoEnded: false)))
    }

    func testBackToDrivesWhileDecodingDoesNotPopTheSavedScreenUpLater() async {
        let recorder = FakeRecorder()
        let model = makeModel(recorder)
        drive(model, recorder)

        let ending = Task { await model.endDrive() }
        for _ in 0..<20 where model.cover != .decoding { await Task.yield() }
        model.dismissFinished()
        recorder.publishTrips([trip()])
        await ending.value

        XCTAssertNil(model.cover)
    }

    // MARK: Auto end (DRIVE-85 AC4)

    func testAManualTripEndedByTheStopTimerSaysSo() async {
        let recorder = FakeRecorder()
        let model = makeModel(recorder)
        drive(model, recorder)
        let saved = trip(endedAt: clock.date.addingTimeInterval(-DriveLock.manualStopTimeout))
        recorder.publishTrips([saved])

        model.apply(.idle)
        for _ in 0..<50 where model.cover == .recording || model.cover == .decoding { await Task.yield() }

        XCTAssertEqual(model.cover, .finished(.saved(saved, autoEnded: true)))
    }

    func testATripTheUserStoppedIsNeverCalledAutoEnded() async {
        let recorder = FakeRecorder()
        recorder.stopResult = trip(endedAt: clock.date.addingTimeInterval(-1_200))
        let model = makeModel(recorder)
        drive(model, recorder)

        await model.endDrive()

        guard case .finished(.saved(_, let autoEnded)) = model.cover else { return XCTFail("saved screen expected") }
        XCTAssertFalse(autoEnded)
    }

    func testOnlyAFinalisedManualTripEndedLongBeforeCountsAsAutoEnded() {
        let now = clock.date
        let longAgo = now.addingTimeInterval(-900)
        XCTAssertTrue(DriveLock.endedOnStopTimer(trip(endedAt: longAgo), finalisedAt: now))
        XCTAssertFalse(DriveLock.endedOnStopTimer(trip(endedAt: now.addingTimeInterval(-30)), finalisedAt: now))
        XCTAssertFalse(DriveLock.endedOnStopTimer(trip(source: .auto, endedAt: longAgo), finalisedAt: now))
        XCTAssertFalse(DriveLock.endedOnStopTimer(trip(status: .interrupted, endedAt: longAgo), finalisedAt: now))
    }

    // MARK: Hooks

    func testThereIsNoCarNameUntilGarageProvidesOne() {
        XCTAssertNil(makeModel().carName)
    }

    func testMovementFollowsTheNewestAutomotiveSample() {
        func sample(_ type: ActivityType, _ confidence: ActivityConfidence, at wallMs: Int64) -> ActivitySample {
            ActivitySample(tMonoNs: 0, wallMs: wallMs, type: type, confidence: confidence)
        }
        XCTAssertNil(ActivityMovementMonitor.isMoving([]))
        XCTAssertEqual(ActivityMovementMonitor.isMoving([sample(.automotive, .high, at: 1)]), true)
        XCTAssertEqual(ActivityMovementMonitor.isMoving([sample(.automotive, .low, at: 1)]), false)
        XCTAssertEqual(
            ActivityMovementMonitor.isMoving([sample(.stationary, .high, at: 9), sample(.automotive, .high, at: 2)]),
            false)
    }
}
