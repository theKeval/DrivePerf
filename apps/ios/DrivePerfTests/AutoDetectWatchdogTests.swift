import DPPlatform
import Foundation
import XCTest

final class WatchdogPolicyTests: XCTestCase {
    private let t0 = Date(timeIntervalSince1970: 1_800_000_000)
    private let hour: TimeInterval = 3600

    func testFirstCheckInSchedulesTwentyFourHoursAhead() {
        let record = WatchdogPolicy.afterCheckIn(WatchdogRecord(), now: t0)
        XCTAssertEqual(record.lastCheckIn, t0)
        XCTAssertEqual(record.scheduledFireAt, t0.addingTimeInterval(24 * hour))
        XCTAssertNil(record.lastFiredAt)
    }

    func testEachCheckInPushesTheNoticeOut() {
        var record = WatchdogPolicy.afterCheckIn(WatchdogRecord(), now: t0)
        record = WatchdogPolicy.afterCheckIn(record, now: t0.addingTimeInterval(10 * hour))
        XCTAssertEqual(record.scheduledFireAt, t0.addingTimeInterval(34 * hour))
        XCTAssertNil(record.lastFiredAt, "It never fired: the check-in came before it was due")
    }

    func testANoticeThatWasDueIsCountedAsFiredAndTheNextOneWaitsSeventyTwoHours() {
        let first = WatchdogPolicy.afterCheckIn(WatchdogRecord(), now: t0)  // due at +24 h
        // The user ignores it, then opens the app at +30 h: it fired at +24 h.
        let reopened = WatchdogPolicy.afterCheckIn(first, now: t0.addingTimeInterval(30 * hour))
        XCTAssertEqual(reopened.lastFiredAt, t0.addingTimeInterval(24 * hour))
        // Silence would end at +54 h, but a second notice may not come before +96 h.
        XCTAssertEqual(reopened.scheduledFireAt, t0.addingTimeInterval(96 * hour))
    }

    func testAfterSeventyTwoHoursTheTwentyFourHourRuleApplies() {
        let first = WatchdogPolicy.afterCheckIn(WatchdogRecord(), now: t0)
        // Reopened a week later: the old notice fired at +24 h, long before the 72 h gap matters.
        let week = t0.addingTimeInterval(7 * 24 * hour)
        let reopened = WatchdogPolicy.afterCheckIn(first, now: week)
        XCTAssertEqual(reopened.scheduledFireAt, week.addingTimeInterval(24 * hour))
    }

    func testCancelKeepsLastCheckInAndClearsThePendingNotice() {
        let armed = WatchdogPolicy.afterCheckIn(WatchdogRecord(), now: t0)
        let cancelled = WatchdogPolicy.afterCancel(armed, now: t0.addingTimeInterval(hour))
        XCTAssertEqual(cancelled.lastCheckIn, t0)
        XCTAssertNil(cancelled.scheduledFireAt)
        XCTAssertNil(cancelled.lastFiredAt)

        let late = WatchdogPolicy.afterCancel(armed, now: t0.addingTimeInterval(30 * hour))
        XCTAssertEqual(late.lastFiredAt, t0.addingTimeInterval(24 * hour))
    }
}

final class AutoDetectWatchdogTests: XCTestCase {
    private actor Recorder: WatchdogScheduling {
        enum Call: Equatable { case schedule(Date), cancel }
        private(set) var calls: [Call] = []
        func schedule(fireAt: Date) { calls.append(.schedule(fireAt)) }
        func cancel() { calls.append(.cancel) }
    }

    private final class Clock: @unchecked Sendable {
        var date: Date
        init(_ date: Date) { self.date = date }
    }

    private func makeDefaults() -> UserDefaults {
        let name = "watchdog-tests-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        addTeardownBlock { UserDefaults(suiteName: name)?.removePersistentDomain(forName: name) }
        return defaults
    }

    func testCheckInRecordsTimeAndSchedulesTheNotice() async throws {
        let clock = Clock(Date(timeIntervalSince1970: 1_800_000_000))
        let scheduler = Recorder()
        let watchdog = AutoDetectWatchdog(defaults: makeDefaults(), scheduler: scheduler, now: { clock.date })

        watchdog.checkIn()

        XCTAssertEqual(watchdog.record.lastCheckIn, clock.date)
        let expected = clock.date.addingTimeInterval(24 * 3600)
        try await waitFor { await scheduler.calls == [.schedule(expected)] }
        let last = await MainActor.run { watchdog.lastCheckIn }
        XCTAssertEqual(last, clock.date)
    }

    func testCancelClearsTheNoticeButKeepsTheCheckIn() async throws {
        let clock = Clock(Date(timeIntervalSince1970: 1_800_000_000))
        let scheduler = Recorder()
        let watchdog = AutoDetectWatchdog(defaults: makeDefaults(), scheduler: scheduler, now: { clock.date })

        watchdog.checkIn()
        watchdog.cancel()

        XCTAssertEqual(watchdog.record.lastCheckIn, clock.date)
        XCTAssertNil(watchdog.record.scheduledFireAt)
        try await waitFor {
            let calls = await scheduler.calls
            return calls.count == 2 && calls.last == .cancel
        }
    }

    func testRecordSurvivesANewInstance() {
        let defaults = makeDefaults()
        let date = Date(timeIntervalSince1970: 1_800_000_000)
        AutoDetectWatchdog(defaults: defaults, scheduler: Recorder(), now: { date }).checkIn()
        let reopened = AutoDetectWatchdog(defaults: defaults, scheduler: Recorder(), now: { date })
        XCTAssertEqual(reopened.record.lastCheckIn, date)
    }

    func testNotificationCopy() {
        XCTAssertEqual(AutoDetectWatchdog.title, "Auto-detect may be paused.")
        XCTAssertEqual(AutoDetectWatchdog.body, "Open DrivePerf once to switch it back on.")
    }

    private func waitFor(_ condition: @escaping () async -> Bool) async throws {
        for _ in 0..<200 {
            if await condition() { return }
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTFail("Condition not met in time")
    }
}
