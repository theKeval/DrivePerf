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

        permissions.state.location = .always
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
        XCTAssertEqual(DriveFormat.kilometres(9_649), "9.6")
        XCTAssertEqual(DriveFormat.kilometres(0), "0.0")
    }
}

@MainActor
final class TripListViewModelTests: XCTestCase {
    private var calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/Toronto")!
        calendar.firstWeekday = 2
        return calendar
    }()

    /// Thursday 5 Nov 2026, 08:14 local.
    private func date(_ day: Int, _ hour: Int, _ minute: Int = 0, month: Int = 11) -> Date {
        calendar.date(from: DateComponents(year: 2026, month: month, day: day, hour: hour, minute: minute))!
    }

    private func trip(_ id: String, start: Date, minutes: Double, km: Double, status: TripStatus = .finalised)
        -> TripSummary
    {
        TripSummary(
            id: id, source: .auto, status: status, startedAt: start,
            endedAt: start.addingTimeInterval(minutes * 60), distanceM: km * 1000, samples: SampleCounts())
    }

    private func makeModel(_ trips: [TripSummary]) async -> TripListViewModel {
        let recorder = FakeRecorder()
        recorder.tripList = trips
        let now = date(10, 18)
        let model = TripListViewModel(
            history: recorder, calendar: calendar, locale: Locale(identifier: "en_CA"), now: { now })
        let task = Task { await model.observe() }
        while !model.hasLoaded { await Task.yield() }
        task.cancel()
        recorder.finishStreams()
        return model
    }

    func testRowsShowPartOfDayDistanceAndDurationNewestFirst() async {
        let model = await makeModel([
            trip("a", start: date(5, 8, 14), minutes: 30, km: 37.94),
            trip("b", start: date(9, 12, 30), minutes: 12, km: 5.5),
        ])

        XCTAssertEqual(model.latest.map(\.id), ["b", "a"])
        let row = model.latest[1]
        XCTAssertEqual(row.title, "Thursday morning")
        XCTAssertEqual(row.distance, "37.9 km")
        XCTAssertEqual(row.duration, "30 min")
        XCTAssertEqual(model.latest[0].title, "Monday midday")
    }

    func testSectionsAreGroupedByWeek() async {
        let model = await makeModel([
            trip("this", start: date(9, 8), minutes: 20, km: 10),
            trip("last", start: date(5, 8), minutes: 20, km: 10),
            trip("older", start: date(21, 8, month: 10), minutes: 20, km: 10),
        ])

        XCTAssertEqual(model.sections.map(\.title), ["This week", "Last week", "19–25 Oct"])
        XCTAssertEqual(model.sections.first?.meta, "1 drive")
        XCTAssertEqual(model.countText, "3 drives")
    }

    func testShortAndInterruptedTripsCarryANote() async {
        let model = await makeModel([
            trip("s", start: date(9, 8), minutes: 1, km: 0.2, status: .short),
            trip("i", start: date(9, 7), minutes: 10, km: 4, status: .interrupted),
        ])

        XCTAssertEqual(model.latest[0].note, "Short trip · not scored")
        XCTAssertTrue(model.latest[1].note?.hasPrefix("iOS ended DrivePerf in the background.") == true)
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
