import DPSharedBridge
import XCTest
@testable import DrivePerf

@MainActor
final class InterimTripRecorderTests: XCTestCase {
    private var clock: TestClock!
    private var location: FakeLocationSource!
    private var motion: FakeMotionSource!
    private var activity: FakeActivitySource!
    private var files: MemoryTripFiles!
    private var triggers: FakeWakeTriggers!
    private var live: SpyLiveSurface!
    private var notifier: SpyNotifier!
    private var root: URL!

    override func setUp() async throws {
        clock = TestClock()
        location = FakeLocationSource()
        motion = FakeMotionSource()
        activity = FakeActivitySource()
        files = MemoryTripFiles()
        triggers = FakeWakeTriggers()
        live = SpyLiveSurface()
        notifier = SpyNotifier()
        root = makeTempDirectory()
    }

    override func tearDown() async throws {
        try? FileManager.default.removeItem(at: root)
    }

    private func makeRecorder() -> InterimTripRecorder {
        let clock = clock!
        let recorder = InterimTripRecorder(
            location: location, motion: motion, activity: activity, files: files, index: TripIndexStore(root: root),
            triggers: triggers, liveSurface: live, notifier: notifier, now: { clock.now })
        recorder.launch()
        return recorder
    }

    private func wake(_ recorder: InterimTripRecorder) {
        let wall = Int64(clock.now.timeIntervalSince1970 * 1000)
        recorder.handle(WakeEvent(kind: .parkedExit, eventWallMs: wall, receivedWallMs: wall))
    }

    /// Drives `seconds` at `speed`, one fix per second, `step` metres apart.
    private func drive(seconds: Int, speed: Double, recorder: InterimTripRecorder, from start: inout Double) {
        for _ in 0..<seconds {
            clock.advance(1)
            start += speed
            location.send(metresNorth: start, speedMps: speed, at: clock.now)
            recorder.evaluate()
        }
    }

    // MARK: Manual trips

    func testManualStartRecordsAndShowsLiveActivity() throws {
        let recorder = makeRecorder()

        try recorder.startManualTrip()

        XCTAssertEqual(recorder.snapshot.phase, .recording)
        XCTAssertEqual(recorder.snapshot.live?.source, .manual)
        XCTAssertTrue(recorder.snapshot.isDriving)
        XCTAssertTrue(location.isRunning)
        XCTAssertTrue(motion.isRunning)
        XCTAssertEqual(motion.rates, [50])
        XCTAssertEqual(live.started.count, 1)
    }

    func testManualStopSavesTripReArmsParkedRegionAndEndsLiveActivity() async throws {
        let recorder = makeRecorder()
        try recorder.startManualTrip()
        var position = 0.0
        drive(seconds: 200, speed: 15, recorder: recorder, from: &position)
        motion.send(count: 100)

        let summary = await recorder.stopTrip()

        XCTAssertEqual(summary?.status, .finalised)
        XCTAssertEqual(summary?.source, .manual)
        XCTAssertEqual(summary?.distanceM ?? 0, 3000, accuracy: 30)
        XCTAssertEqual(summary?.samples.motion, 100)
        XCTAssertEqual(summary?.samples.location, 200)
        XCTAssertEqual(recorder.snapshot.phase, .idle)
        XCTAssertEqual(recorder.snapshot.lastFinished, summary)
        XCTAssertEqual(recorder.history.map(\.id), [summary?.id])
        XCTAssertEqual(triggers.armedSpots.count, 1)
        XCTAssertEqual(triggers.armedSpots.first?.radiusM, 120)
        XCTAssertEqual(live.ended, 1)
        XCTAssertFalse(location.isRunning)
        XCTAssertFalse(motion.isRunning)
    }

    func testShortManualTripIsKeptAsShort() async throws {
        let recorder = makeRecorder()
        try recorder.startManualTrip()
        clock.advance(30)

        let summary = await recorder.stopTrip()

        XCTAssertEqual(summary?.status, .short)
        XCTAssertEqual(recorder.history.count, 1)
    }

    func testStartFailsWhileStorageIsLockedBeforeFirstUnlock() {
        files.writable = false
        let recorder = makeRecorder()

        XCTAssertThrowsError(try recorder.startManualTrip()) { error in
            XCTAssertEqual(error as? RecorderError, .storageLocked)
        }
        XCTAssertEqual(recorder.snapshot.phase, .idle)
    }

    // MARK: Automatic trips

    func testWakeIsIgnoredWithoutAutoDetectOrAlways() {
        let recorder = makeRecorder()
        recorder.updateAuthorization(always: false, autoDetectEnabled: true)

        wake(recorder)

        XCTAssertEqual(recorder.snapshot.phase, .idle)
        XCTAssertEqual(recorder.snapshot.autoDetect, .needsAttention(reason: .locationWhileUsing))
    }

    func testWakeRecordsFromTheWakeAndConfirmsOnSustainedSpeedWithoutLiveActivity() {
        let recorder = makeRecorder()
        recorder.updateAuthorization(always: true, autoDetectEnabled: true)
        let wokeAt = clock.now

        wake(recorder)

        XCTAssertEqual(recorder.snapshot.phase, .candidate)
        XCTAssertFalse(recorder.snapshot.isDriving)
        XCTAssertTrue(location.isRunning, "GNSS starts at the wake")
        XCTAssertEqual(location.notBeforeWallMs, Int64(wokeAt.timeIntervalSince1970 * 1000))
        motion.send(count: 50)
        var position = 0.0
        drive(seconds: 22, speed: 10, recorder: recorder, from: &position)

        XCTAssertEqual(recorder.snapshot.phase, .recording)
        XCTAssertEqual(recorder.snapshot.live?.startedAt, wokeAt, "The trip starts at the wake")
        XCTAssertEqual(notifier.posted.count, 1)
        XCTAssertTrue(live.started.isEmpty, "No local Live Activity for automatic trips")
        let tripId = recorder.snapshot.live?.id ?? ""
        XCTAssertEqual(files.writers[tripId]?.counts.motion, 50, "Candidate samples are kept")
    }

    func testCandidateTimesOutAndDeletesSamples() {
        let recorder = makeRecorder()
        recorder.updateAuthorization(always: true, autoDetectEnabled: true)
        wake(recorder)
        let tripId = recorder.snapshot.live?.id ?? ""

        clock.advance(181)
        recorder.evaluate()

        XCTAssertEqual(recorder.snapshot.phase, .idle)
        XCTAssertEqual(files.deleted, [tripId])
        XCTAssertTrue(recorder.history.isEmpty)
    }

    func testWalkingCandidateIsRejectedAfterNinetySeconds() {
        let recorder = makeRecorder()
        recorder.updateAuthorization(always: true, autoDetectEnabled: true)
        wake(recorder)
        activity.send(.walking, at: clock.now)
        var position = 0.0
        drive(seconds: 91, speed: 1.4, recorder: recorder, from: &position)

        XCTAssertEqual(recorder.snapshot.phase, .idle)
    }

    func testAutomaticTripEndsAfterStopTimerAtLastMovement() {
        let recorder = makeRecorder()
        recorder.updateAuthorization(always: true, autoDetectEnabled: true)
        wake(recorder)
        var position = 0.0
        drive(seconds: 120, speed: 12, recorder: recorder, from: &position)
        let lastMoving = clock.now
        drive(seconds: 241, speed: 0, recorder: recorder, from: &position)

        XCTAssertEqual(recorder.snapshot.phase, .idle)
        let trip = recorder.history.first
        XCTAssertEqual(trip?.source, .automatic)
        XCTAssertEqual(trip?.endedAt, lastMoving)
    }

    func testShortAutomaticTripIsDiscarded() {
        let recorder = makeRecorder()
        recorder.updateAuthorization(always: true, autoDetectEnabled: true)
        wake(recorder)
        var position = 0.0
        drive(seconds: 25, speed: 8, recorder: recorder, from: &position)
        XCTAssertEqual(recorder.snapshot.phase, .recording)
        drive(seconds: 241, speed: 0, recorder: recorder, from: &position)

        XCTAssertEqual(recorder.snapshot.phase, .idle)
        XCTAssertTrue(recorder.history.isEmpty)
    }

    func testActivityHistoryIsBoundedByThePreviousTripEnd() async throws {
        let recorder = makeRecorder()
        try recorder.startManualTrip()
        clock.advance(300)
        _ = await recorder.stopTrip()
        let endedAt = clock.now
        recorder.updateAuthorization(always: true, autoDetectEnabled: true)
        clock.advance(120)

        wake(recorder)

        XCTAssertEqual(activity.queriedFromWallMs, Int64(endedAt.timeIntervalSince1970 * 1000))
    }

    // MARK: Recovery (§1.7)

    func testRecentCheckpointResumesTheSameTripAfterRelaunch() throws {
        let first = makeRecorder()
        try first.startManualTrip()
        let tripId = first.snapshot.live?.id
        clock.advance(10)
        first.evaluate()

        clock.advance(60)
        let relaunched = makeRecorder()

        XCTAssertEqual(relaunched.snapshot.phase, .recording)
        XCTAssertEqual(relaunched.snapshot.live?.id, tripId)
    }

    func testStaleCheckpointBecomesInterruptedTrip() throws {
        let first = makeRecorder()
        try first.startManualTrip()
        clock.advance(10)
        first.evaluate()

        clock.advance(3600)
        let relaunched = makeRecorder()

        XCTAssertEqual(relaunched.snapshot.phase, .idle)
        XCTAssertEqual(relaunched.history.first?.status, .interrupted)
    }
}
