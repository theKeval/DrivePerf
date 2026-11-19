import DPSharedBridge
import XCTest
@testable import DPFeatures

@MainActor
final class CaptureViewModelTests: XCTestCase {
    private func makeModel(
        _ recorder: FakeRecorder = FakeRecorder(), permissions: FakePermissions = FakePermissions(),
        now: Date = Date(timeIntervalSince1970: 1_800_000_000)
    ) -> CaptureViewModel {
        CaptureViewModel(
            recorder: recorder, history: recorder, permissions: permissions, clock: FixedClock(), now: { now },
            finishTimeout: .milliseconds(100), startTimeout: .milliseconds(300))
    }

    func testStartWithoutLocationAsksForSettingsAndDoesNotRecord() async {
        let recorder = FakeRecorder()
        let permissions = FakePermissions()
        permissions.state.location = .denied
        let model = makeModel(recorder, permissions: permissions)

        await model.start()

        XCTAssertEqual(model.startProblem, .locationDenied)
        XCTAssertTrue(recorder.wakes.isEmpty)
    }

    func testTheInDriveScreenReportsWhenItShowsAndHides() {
        let recorder = FakeRecorder()
        let model = makeModel(recorder)

        model.setInDriveScreenVisible(true)
        model.setInDriveScreenVisible(false)

        XCTAssertEqual(recorder.inDriveScreenVisible, [true, false])
    }

    func testStartSendsAManualWakeStampedWithTheClock() async {
        let recorder = FakeRecorder()
        let permissions = FakePermissions()
        permissions.state.location = .whenInUse
        let model = makeModel(recorder, permissions: permissions)

        await model.start()

        XCTAssertEqual(
            recorder.wakes,
            [WakeEvent(kind: .manual, tMonoNs: 42_000_000_000, wallMs: 1_800_000_000_000, sourceDetail: "app")])
        XCTAssertNil(model.startProblem)
    }

    func testStartThatDoesNotRecordReportsLockedStorage() async {
        let recorder = FakeRecorder()
        recorder.canStart = false
        let permissions = FakePermissions()
        permissions.state.location = .whenInUse
        let model = makeModel(recorder, permissions: permissions)

        await model.start()

        XCTAssertEqual(model.startProblem, .storageLocked)
    }

    func testAStartReportedLaterByTheRecorderIsNotAnError() async {
        let recorder = FakeRecorder()
        recorder.startsLater = true
        let permissions = FakePermissions()
        permissions.state.location = .whenInUse
        let model = makeModel(recorder, permissions: permissions)

        await model.start()

        XCTAssertNil(model.startProblem, "the shared core reports the new trip after onWake returns")
        XCTAssertEqual(recorder.status.state, .recording)
    }

    func testAStartTheRecorderReportsAsFailedIsLockedStorage() async {
        let recorder = FakeRecorder()
        recorder.startsLater = true
        recorder.failsStart = true
        let permissions = FakePermissions()
        permissions.state.location = .whenInUse
        let model = makeModel(recorder, permissions: permissions)

        let started = ContinuousClock.now
        await model.start()

        XCTAssertEqual(model.startProblem, .storageLocked)
        XCTAssertLessThan(ContinuousClock.now - started, .milliseconds(250), "reported at once, not by the timeout")
    }

    func testADriverTripLocksTheAppToTheInDriveScreen() {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let model = makeModel(now: now)

        model.apply(.recording(elapsedS: 90))

        XCTAssertEqual(model.cover, .recording)
        XCTAssertEqual(model.tripStartedAt, now.addingTimeInterval(-90), "Start time derived from elapsedS")
        guard case .recording = model.accessory else { return XCTFail("Accessory should show recording") }
    }

    func testACandidateDoesNotLockTheApp() {
        let model = makeModel()

        model.apply(.recording(isManual: false, state: .candidate))

        XCTAssertNil(model.cover)
    }

    func testStopNeedsConfirmationThenShowsTheSavedTrip() async {
        let recorder = FakeRecorder()
        let saved = TripSummary(
            id: "t", source: .manual, status: .finalised, startedAt: Date(), endedAt: Date(), distanceM: 3000,
            samples: SampleCounts())
        recorder.stopResult = saved
        let model = makeModel(recorder)
        recorder.publish(.recording())
        model.apply(.recording())

        model.requestStop()
        XCTAssertTrue(model.isEndSheetPresented)
        model.keepRecording()
        XCTAssertFalse(model.isEndSheetPresented)
        XCTAssertEqual(model.cover, .recording, "Keep recording changes nothing")

        model.requestStop()
        await model.endDrive()

        XCTAssertEqual(model.cover, .finished(.saved(saved, autoEnded: false)))
        model.dismissFinished()
        XCTAssertNil(model.cover)
    }

    func testADiscardedTripShowsTheFinishedStateWithoutATrip() async {
        let recorder = FakeRecorder()
        let model = makeModel(recorder)
        recorder.publish(.recording())
        model.apply(.recording())

        await model.endDrive()

        XCTAssertEqual(model.cover, .finished(.saved(nil, autoEnded: false)))
    }

    func testATripEndingOnItsOwnShowsTheFinishedState() async {
        let recorder = FakeRecorder()
        let model = makeModel(recorder)
        model.apply(.recording(isManual: false))
        let saved = TripSummary(
            id: "t", source: .auto, status: .finalised, startedAt: Date(), endedAt: Date(), distanceM: 9000,
            samples: SampleCounts())
        recorder.publishTrips([saved])

        model.apply(.idle)
        for _ in 0..<50 where model.cover == .recording || model.cover == .decoding { await Task.yield() }

        XCTAssertEqual(model.cover, .finished(.saved(saved, autoEnded: false)))
    }

    func testAccessoryFollowsAutoDetectAndLocationAccess() async {
        let permissions = FakePermissions()
        let model = makeModel(permissions: permissions)

        permissions.state = Self.healthy
        await model.refreshReadiness()
        XCTAssertEqual(model.accessory, .ready)

        permissions.state.location = .whenInUse
        await model.refreshReadiness()
        XCTAssertEqual(model.accessory, .attention(.locationWhileUsing))

        var manual = RecorderStatus.idle
        manual.autoDetectEnabled = false
        model.apply(manual)
        XCTAssertEqual(model.accessory, .manual)
    }

    private static let healthy = PermissionSnapshot(
        location: .always, preciseLocation: true, motion: .authorized, notifications: .authorized,
        backgroundRefresh: .available)

    func testAccessoryNeedsAttentionForMotionPreciseLocationAndBackgroundRefresh() async {
        let permissions = FakePermissions()
        let model = makeModel(permissions: permissions)

        permissions.state = Self.healthy
        permissions.state.motion = .denied
        await model.refreshReadiness()
        XCTAssertEqual(model.accessory, .attention(.motionOff))

        permissions.state = Self.healthy
        permissions.state.preciseLocation = false
        await model.refreshReadiness()
        XCTAssertEqual(model.accessory, .attention(.preciseLocationOff))

        permissions.state = Self.healthy
        permissions.state.backgroundRefresh = .denied
        await model.refreshReadiness()
        XCTAssertEqual(model.accessory, .attention(.backgroundRefreshOff))
        XCTAssertEqual(model.attentionReasons, [.backgroundRefreshOff])

        permissions.state = Self.healthy
        await model.refreshReadiness()
        XCTAssertEqual(model.accessory, .ready)
        XCTAssertTrue(model.attentionReasons.isEmpty)
    }

    func testLastCheckInComesFromTheRecorderStatus() {
        let model = makeModel()
        XCTAssertNil(model.lastCheckIn)

        var status = RecorderStatus.idle
        status.lastCheckIn = Date(timeIntervalSince1970: 1_799_999_000)
        model.apply(status)
        XCTAssertEqual(model.lastCheckIn, Date(timeIntervalSince1970: 1_799_999_000))
    }

    func testShortTripBannerUnderFiveHundredMetresOrTwoMinutes() {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let model = makeModel(now: now)
        model.apply(.recording(distanceM: 400))
        XCTAssertTrue(model.isShortTrip(now: now.addingTimeInterval(600)))
        model.apply(.recording(distanceM: 4000))
        XCTAssertTrue(model.isShortTrip(now: now.addingTimeInterval(60)))
        XCTAssertFalse(model.isShortTrip(now: now.addingTimeInterval(600)))
    }

    func testDriveFormatting() {
        XCTAssertEqual(DriveFormat.elapsed(864), "14:24")
        XCTAssertEqual(DriveFormat.elapsed(3729), "1:02:09")
        XCTAssertEqual(DriveFormat.elapsed(-5), "0:00")
        XCTAssertEqual(DisplaySettingsModel().distance(9_649), "9.6 km")
        XCTAssertEqual(DisplaySettingsModel().distance(0), "0.0 km")
    }
}

@MainActor
final class ProfileViewModelTests: XCTestCase {
    func testCrashReportToggleStartsAndStopsTheReporter() {
        let crash = FakeCrashReporter()
        let preferences = MemoryPreferences()
        let model = ProfileViewModel(dependencies: makeDependencies(preferences: preferences, crash: crash))
        XCTAssertFalse(model.sendCrashReports)

        model.setSendCrashReports(true)
        XCTAssertTrue(preferences.preferences.sendCrashReports)
        XCTAssertEqual(model.crashStatus, .notConfigured)

        model.setSendCrashReports(false)
        XCTAssertEqual(crash.consents, [true, false])
        XCTAssertEqual(model.crashStatusText, "Off")
    }

    func testProfileShowsChoicesSavedAfterItWasCreated() {
        let crash = FakeCrashReporter()
        let preferences = MemoryPreferences()
        let model = ProfileViewModel(dependencies: makeDependencies(preferences: preferences, crash: crash))
        XCTAssertFalse(model.sendCrashReports)

        // What onboarding does on a fresh install, after AppModel has built the Profile model.
        preferences.update {
            $0.shareUsageStatistics = true
            $0.sendCrashReports = true
        }
        crash.apply(consent: true)
        model.refresh()

        XCTAssertTrue(model.shareUsageStatistics)
        XCTAssertTrue(model.sendCrashReports)
        XCTAssertEqual(model.crashStatus, .notConfigured)
    }

    func testSelfTestStreamsSteps() async {
        let model = ProfileViewModel(dependencies: makeDependencies())

        await model.runSelfTest()

        XCTAssertEqual(model.selfTestSteps.first?.state, .passed("ok"))
        XCTAssertEqual(model.selfTestSummary, "All checks passed")
        XCTAssertFalse(model.isSelfTestRunning)
    }

    func testShellStartsInOnboardingUntilItIsFinished() {
        let preferences = MemoryPreferences()
        XCTAssertEqual(AppModel(dependencies: makeDependencies(preferences: preferences)).route, .onboarding)
        preferences.update { $0.onboardingCompleted = true }
        XCTAssertEqual(AppModel(dependencies: makeDependencies(preferences: preferences)).route, .shell)
    }
}
