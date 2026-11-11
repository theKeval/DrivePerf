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
    private var parked: SpyParkedRegion!
    private var live: SpyLiveSurface!
    private var notifier: SpyNotifier!
    private var root: URL!

    override func setUp() async throws {
        clock = TestClock()
        location = FakeLocationSource()
        motion = FakeMotionSource()
        activity = FakeActivitySource()
        files = MemoryTripFiles()
        parked = SpyParkedRegion()
        live = SpyLiveSurface()
        notifier = SpyNotifier()
        root = makeTempDirectory()
    }

    override func tearDown() async throws {
        try? FileManager.default.removeItem(at: root)
    }

    private func makeRecorder(autoDetect: Bool = true) -> InterimTripRecorder {
        let clock = clock!
        let recorder = InterimTripRecorder(
            location: location, motion: motion, activity: activity, files: files, parkedRegion: parked,
            index: TripIndexStore(root: root), liveSurface: live, notifier: notifier, autoDetectEnabled: autoDetect,
            now: { clock.now })
        recorder.onLaunch()
        return recorder
    }

    private func wake(_ recorder: InterimTripRecorder, ago seconds: TimeInterval = 0) {
        recorder.onWake(clock.wake(.parkedExit, ago: seconds))
    }

    private func startManual(_ recorder: InterimTripRecorder) {
        recorder.onWake(clock.wake(.manual))
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

    func testManualWakeRecordsAndShowsLiveActivity() {
        let recorder = makeRecorder()

        startManual(recorder)

        XCTAssertEqual(recorder.status.state, .recording)
        XCTAssertTrue(recorder.status.isManual)
        XCTAssertTrue(recorder.status.isDriving)
        XCTAssertTrue(location.isRunning)
        XCTAssertTrue(motion.isRunning)
        XCTAssertEqual(motion.rates, [50])
        XCTAssertEqual(live.started.count, 1)
    }

    func testManualStopSavesTripReArmsParkedRegionAndEndsLiveActivity() {
        let recorder = makeRecorder()
        startManual(recorder)
        let tripId = recorder.status.tripId ?? ""
        var position = 0.0
        drive(seconds: 200, speed: 15, recorder: recorder, from: &position)
        XCTAssertEqual(recorder.status.elapsedS, 200)
        XCTAssertEqual(recorder.status.distanceM, 3000, accuracy: 30)
        motion.send(count: 100)

        recorder.stop()

        let summary = recorder.history.first
        XCTAssertEqual(summary?.id, tripId)
        XCTAssertEqual(summary?.status, .finalised)
        XCTAssertEqual(summary?.source, .manual)
        XCTAssertEqual(summary?.distanceM ?? 0, 3000, accuracy: 30)
        XCTAssertEqual(summary?.samples.motion, 100)
        XCTAssertEqual(summary?.samples.location, 200)
        XCTAssertEqual(files.r0Lines(tripId: tripId, prefix: "M"), 100)
        XCTAssertEqual(files.r0Lines(tripId: tripId, prefix: "L"), 200)
        XCTAssertEqual(files.describe(tripId: tripId, kind: .r0, withSha256: false)?.compressed, true)
        XCTAssertEqual(recorder.status, .idle)
        XCTAssertEqual(recorder.history.count, 1)
        XCTAssertEqual(parked.armed.count, 1)
        XCTAssertEqual(parked.armed.first?.radiusM, 120)
        XCTAssertEqual(live.ended, 1)
        XCTAssertFalse(location.isRunning)
        XCTAssertFalse(motion.isRunning)
    }

    func testShortManualTripIsKeptAsShort() {
        let recorder = makeRecorder()
        startManual(recorder)
        clock.advance(30)

        recorder.stop()

        XCTAssertEqual(recorder.history.first?.status, .short)
        XCTAssertEqual(recorder.history.count, 1)
    }

    func testNothingStartsWhileTheTripFileIsUnavailableBeforeFirstUnlock() {
        files.available = false
        let recorder = makeRecorder()

        startManual(recorder)
        wake(recorder)

        XCTAssertEqual(recorder.status.state, .idle)
        XCTAssertFalse(location.isRunning)
        XCTAssertTrue(files.tripIds().isEmpty)
    }

    func testPassengerDiscardDeletesTheTrip() {
        let recorder = makeRecorder()
        startManual(recorder)
        let tripId = recorder.status.tripId ?? ""
        clock.advance(300)

        recorder.markPassenger(keep: false)

        XCTAssertEqual(recorder.status.state, .idle)
        XCTAssertTrue(recorder.history.isEmpty)
        XCTAssertEqual(files.deletedTrips, [tripId])
        XCTAssertEqual(live.ended, 1)
    }

    // MARK: Automatic trips

    func testWakeIsIgnoredWhileAutoDetectIsOffButManualStillWorks() {
        let recorder = makeRecorder(autoDetect: false)

        wake(recorder)
        XCTAssertEqual(recorder.status.state, .idle)
        XCTAssertFalse(recorder.status.autoDetectEnabled)

        startManual(recorder)
        XCTAssertEqual(recorder.status.state, .recording)
    }

    func testStaleWakeIsDropped() {
        let recorder = makeRecorder()

        wake(recorder, ago: 1000)

        XCTAssertEqual(recorder.status.state, .idle)
    }

    func testWakeRecordsFromTheWakeAndConfirmsOnSustainedSpeedWithoutLiveActivity() {
        let recorder = makeRecorder()
        let wokeAt = clock.now

        wake(recorder)

        XCTAssertEqual(recorder.status.state, .candidate)
        XCTAssertFalse(recorder.status.isDriving)
        XCTAssertFalse(recorder.status.isManual)
        XCTAssertTrue(location.isRunning, "GNSS starts at the wake")
        let tripId = recorder.status.tripId ?? ""
        // A fix cached from before the wake is dropped (spike change 8).
        location.send(metresNorth: 0, speedMps: 0, at: wokeAt.addingTimeInterval(-60))
        motion.send(count: 50)
        var position = 0.0
        drive(seconds: 22, speed: 10, recorder: recorder, from: &position)

        XCTAssertEqual(recorder.status.state, .recording)
        XCTAssertEqual(recorder.status.elapsedS, 22, "The trip starts at the wake")
        XCTAssertEqual(notifier.posted.count, 1)
        XCTAssertTrue(live.started.isEmpty, "No local Live Activity for automatic trips")
        XCTAssertEqual(files.r0Lines(tripId: tripId, prefix: "M"), 50, "Candidate samples are kept")
        XCTAssertEqual(files.r0Lines(tripId: tripId, prefix: "L"), 22)
    }

    func testCandidateTimesOutAndDeletesSamples() {
        let recorder = makeRecorder()
        wake(recorder)
        let tripId = recorder.status.tripId ?? ""

        clock.advance(181)
        recorder.evaluate()

        XCTAssertEqual(recorder.status.state, .idle)
        XCTAssertEqual(files.deletedTrips, [tripId])
        XCTAssertTrue(files.tripIds().isEmpty)
        XCTAssertTrue(recorder.history.isEmpty)
    }

    func testWalkingCandidateIsRejectedAfterNinetySeconds() {
        let recorder = makeRecorder()
        wake(recorder)
        activity.send(.walking, at: clock.now)
        var position = 0.0
        drive(seconds: 91, speed: 1.4, recorder: recorder, from: &position)

        XCTAssertEqual(recorder.status.state, .idle)
    }

    func testAutomaticTripEndsAfterStopTimerAtLastMovement() {
        let recorder = makeRecorder()
        wake(recorder)
        var position = 0.0
        drive(seconds: 120, speed: 12, recorder: recorder, from: &position)
        let lastMoving = clock.now
        drive(seconds: 241, speed: 0, recorder: recorder, from: &position)

        XCTAssertEqual(recorder.status.state, .idle)
        let trip = recorder.history.first
        XCTAssertEqual(trip?.source, .auto)
        XCTAssertEqual(trip?.endedAt, lastMoving)
    }

    func testShortAutomaticTripIsDiscarded() {
        let recorder = makeRecorder()
        wake(recorder)
        var position = 0.0
        drive(seconds: 25, speed: 8, recorder: recorder, from: &position)
        XCTAssertEqual(recorder.status.state, .recording)
        drive(seconds: 241, speed: 0, recorder: recorder, from: &position)

        XCTAssertEqual(recorder.status.state, .idle)
        XCTAssertTrue(recorder.history.isEmpty)
    }

    func testActivityHistoryIsBoundedByThePreviousTripEnd() {
        let recorder = makeRecorder()
        startManual(recorder)
        clock.advance(300)
        recorder.stop()
        let endedAt = clock.now
        clock.advance(120)

        wake(recorder)

        XCTAssertEqual(activity.queriedFromWallMs, Int64(endedAt.timeIntervalSince1970 * 1000))
    }

    // MARK: Recovery (§1.7)

    func testRecentCheckpointResumesTheSameTripAfterRelaunch() {
        let first = makeRecorder()
        startManual(first)
        let tripId = first.status.tripId
        clock.advance(10)
        first.evaluate()

        clock.advance(60)
        let relaunched = makeRecorder()

        XCTAssertEqual(relaunched.status.state, .recording)
        XCTAssertEqual(relaunched.status.tripId, tripId)
    }

    func testStaleCheckpointBecomesInterruptedTrip() {
        let first = makeRecorder()
        startManual(first)
        clock.advance(10)
        first.evaluate()

        clock.advance(3600)
        let relaunched = makeRecorder()

        XCTAssertEqual(relaunched.status.state, .idle)
        XCTAssertEqual(relaunched.history.first?.status, .interrupted)
    }
}
