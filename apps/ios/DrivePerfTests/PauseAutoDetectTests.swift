import DPSharedBridge
import XCTest
@testable import DPFeatures

/// DRIVE-152 (F-CAP-15): S-HOME-03's rules and view model.
@MainActor
final class PauseAutoDetectTests: XCTestCase {
    private var toronto: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/Toronto")!
        return calendar
    }
    private let english = Locale(identifier: "en_US")

    /// Date formatting puts a narrow no-break space (U+202F) before AM/PM; the app keeps it, the expectations use a
    /// plain space.
    private func plain(_ text: String) -> String { text.replacingOccurrences(of: "\u{202F}", with: " ") }

    private func at(_ year: Int, _ month: Int, _ day: Int, _ hour: Int, _ minute: Int = 0) -> Date {
        toronto.date(from: DateComponents(year: year, month: month, day: day, hour: hour, minute: minute))!
    }

    // MARK: Rules

    func testEachChoiceEndsWhenItSays() {
        let now = at(2026, 11, 18, 19, 30)
        XCTAssertEqual(PauseRules.until(.oneHour, now: now, calendar: toronto), now.addingTimeInterval(3600))
        XCTAssertEqual(PauseRules.until(.untilMorning, now: now, calendar: toronto), at(2026, 11, 19, 6))
        XCTAssertEqual(PauseRules.until(.untilMorning, now: at(2026, 11, 18, 0, 10), calendar: toronto), at(2026, 11, 19, 6))
        XCTAssertNil(PauseRules.until(.untilResumed, now: now, calendar: toronto))
    }

    func testTheMorningEndKeepsLocalSixAcrossAClockChange() {
        // 1 November 2026: clocks go back at 02:00 in Toronto.
        let evening = at(2026, 10, 31, 22)
        let until = PauseRules.until(.untilMorning, now: evening, calendar: toronto)
        XCTAssertEqual(until, at(2026, 11, 1, 6))
        XCTAssertEqual(until?.timeIntervalSince(evening), 9 * 3600)
    }

    func testTheEndIsNamedByItsDay() {
        let now = at(2026, 11, 18, 19, 30)
        func phrase(_ until: Date?) -> String {
            plain(PauseRules.untilPhrase(until, now: now, calendar: toronto, locale: english))
        }
        XCTAssertEqual(phrase(at(2026, 11, 18, 20, 30)), "until 8:30 PM")
        XCTAssertEqual(phrase(at(2026, 11, 19, 6)), "until 6:00 AM tomorrow")
        XCTAssertEqual(phrase(at(2026, 11, 21, 6)), "until Saturday 6:00 AM")
        XCTAssertEqual(phrase(nil), "until you turn it back on")
        XCTAssertEqual(
            plain(PauseRules.untilLine(at(2026, 11, 19, 6), now: now, calendar: toronto, locale: english)),
            "Until 6:00 AM tomorrow")
    }

    func testOptionLinesAndButtonsFollowTheChoice() {
        let now = at(2026, 11, 20, 19, 30)
        XCTAssertEqual(plain(PauseRules.detail(.oneHour, now: now, calendar: toronto, locale: english)), "Until 8:30 PM")
        XCTAssertEqual(
            plain(PauseRules.detail(.untilMorning, now: now, calendar: toronto, locale: english)), "Saturday, 6:00 AM")
        XCTAssertEqual(PauseRules.detail(.untilResumed, now: now, calendar: toronto, locale: english), "Manual drives still work")
        XCTAssertEqual(PauseRules.button(.untilMorning), "Pause until tomorrow")
        XCTAssertEqual(PauseRules.button(.oneHour), "Pause for 1 hour")
        XCTAssertEqual(PauseRules.button(.untilResumed), "Pause auto-detect")
    }

    // MARK: View model

    private func makeModel(_ recorder: PauseRecorder, now: Date) -> PauseAutoDetectViewModel {
        PauseAutoDetectViewModel(recorder: recorder, status: { recorder.status }, now: { now }, calendar: toronto)
    }

    func testPauseSendsTheChoiceAndShowsTheConfirmation() {
        let recorder = PauseRecorder()
        let now = at(2026, 11, 18, 19, 30)
        let model = makeModel(recorder, now: now)
        model.prepare()
        XCTAssertEqual(model.step, .pick)
        XCTAssertEqual(model.choice, .untilMorning, "the sheet opens on until tomorrow morning")
        model.choice = .oneHour
        model.pause()
        XCTAssertEqual(recorder.pauses.count, 1)
        XCTAssertEqual(recorder.pauses.first?.0, .oneHour)
        XCTAssertEqual(recorder.pauses.first?.1, now.addingTimeInterval(3600))
        XCTAssertEqual(model.step, .confirmed(until: now.addingTimeInterval(3600)))
    }

    func testUndoTurnsItBackOnAndShowsTheChoicesAgain() {
        let recorder = PauseRecorder()
        let model = makeModel(recorder, now: at(2026, 11, 18, 19, 30))
        model.prepare()
        model.choice = .untilResumed
        model.pause()
        XCTAssertEqual(recorder.pauses.first?.1, nil, "until I turn it back on has no end")
        model.undo()
        XCTAssertEqual(recorder.undos, 1)
        XCTAssertEqual(model.step, .pick)
    }

    func testOpenedWhilePausedItOffersResume() {
        let recorder = PauseRecorder()
        let until = at(2026, 11, 19, 6)
        recorder.status.autoDetectHealth = .paused
        recorder.status.autoDetectPausedUntil = until
        let model = makeModel(recorder, now: at(2026, 11, 18, 19, 30))
        model.prepare()
        XCTAssertEqual(model.step, .resume)
        XCTAssertTrue(model.isPaused)
        XCTAssertEqual(model.pausedUntil, until)
        model.resume()
        XCTAssertEqual(recorder.resumes, 1)
    }

    func testPauseIsOfferedOnlyWithAutomaticTripsOnAndNoDriveRunning() {
        let recorder = PauseRecorder()
        let model = makeModel(recorder, now: Date())
        XCTAssertTrue(model.isAvailable)
        recorder.status.autoDetectEnabled = false
        XCTAssertFalse(model.isAvailable)
        recorder.status = .recording()
        XCTAssertFalse(model.isAvailable, "not reachable during a driver trip (D1)")
    }

    func testTheAccessoryAndRecordingSettingsShowThePause() async {
        let recorder = FakeRecorder()
        let permissions = FakePermissions()
        permissions.state = PermissionSnapshot(location: .whenInUse, preciseLocation: true, motion: .authorized)
        let capture = CaptureViewModel(recorder: recorder, history: recorder, permissions: permissions, clock: FixedClock())
        await capture.refreshReadiness()
        var paused = RecorderStatus.idle
        paused.autoDetectHealth = .paused
        paused.autoDetectPausedUntil = Date(timeIntervalSince1970: 1_800_000_000)
        capture.apply(paused)
        XCTAssertEqual(
            capture.accessory, .paused(until: Date(timeIntervalSince1970: 1_800_000_000)),
            "a pause shows over a missing permission")
        let preferences = MemoryPreferences()
        preferences.update { $0.autoTripsEnabled = true }
        let settings = RecordingSettingsViewModel(
            preferences: preferences, permissions: permissions, refreshCapture: {},
            pause: PauseAutoDetectViewModel(recorder: recorder, status: { capture.status }))
        XCTAssertEqual(settings.statusText, "Auto-detect paused")
        capture.resumeAutoDetect()
        XCTAssertEqual(recorder.resumes, 1)
    }
}

/// Records the pause calls the view model makes.
@MainActor
private final class PauseRecorder: TripRecording {
    var status = RecorderStatus.idle
    private(set) var pauses: [(AutoDetectPauseLength, Date?)] = []
    private(set) var resumes = 0
    private(set) var undos = 0

    func statuses() -> AsyncStream<RecorderStatus> { AsyncStream { $0.yield(status) } }
    func onLaunch() {}
    func onWake(_ event: WakeEvent) {}
    func stop() {}
    func markPassenger(keep: Bool) {}
    func setAutoDetect(enabled: Bool) {}
    func pauseAutoDetect(_ length: AutoDetectPauseLength, until: Date?) { pauses.append((length, until)) }
    func resumeAutoDetect() { resumes += 1 }
    func undoPauseAutoDetect() { undos += 1 }
}
