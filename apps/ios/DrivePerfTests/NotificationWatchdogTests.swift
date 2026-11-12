import Foundation
import UserNotifications
import XCTest
@testable import DPPlatform

final class NotificationWatchdogTests: XCTestCase {
    private let now = Date(timeIntervalSince1970: 1_800_000_000)
    private var utc: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }

    func testCopyAndCategoryMatchTheDesign() {
        let request = NotificationWatchdog.request(fireAt: now.addingTimeInterval(24 * 3600), now: now, calendar: utc)
        XCTAssertEqual(request.identifier, NotificationWatchdog.identifier)
        XCTAssertEqual(request.content.title, "Auto-detect may be paused.")
        XCTAssertEqual(request.content.body, "Open DrivePerf once to switch it back on.")
        XCTAssertEqual(request.content.categoryIdentifier, NotificationWatchdog.category)
    }

    func testFutureTimeUsesAOneShotCalendarTriggerAtThatTime() throws {
        let fireAt = now.addingTimeInterval(24 * 3600)
        let request = NotificationWatchdog.request(fireAt: fireAt, now: now, calendar: utc)
        let trigger = try XCTUnwrap(request.trigger as? UNCalendarNotificationTrigger)
        XCTAssertFalse(trigger.repeats)
        XCTAssertEqual(utc.date(from: trigger.dateComponents), fireAt)
    }

    func testPastOrImminentTimeFiresInASecond() throws {
        for fireAt in [now.addingTimeInterval(-3600), now, now.addingTimeInterval(0.5)] {
            let request = NotificationWatchdog.request(fireAt: fireAt, now: now, calendar: utc)
            let trigger = try XCTUnwrap(request.trigger as? UNTimeIntervalNotificationTrigger)
            XCTAssertEqual(trigger.timeInterval, 1)
            XCTAssertFalse(trigger.repeats)
        }
    }
}

final class BootDetectorTests: XCTestCase {
    private let boot = Date(timeIntervalSince1970: 1_800_000_000)

    func testFirstEverLaunchIsNotABoot() {
        XCTAssertFalse(BootDetector.isNewBoot(previous: nil, current: boot))
    }

    func testSameBootWithClockJitterIsNotANewBoot() {
        XCTAssertFalse(BootDetector.isNewBoot(previous: boot, current: boot.addingTimeInterval(0.8)))
        XCTAssertFalse(BootDetector.isNewBoot(previous: boot, current: boot.addingTimeInterval(-5)))
    }

    func testALaterBootTimeIsARestart() {
        XCTAssertTrue(BootDetector.isNewBoot(previous: boot, current: boot.addingTimeInterval(3 * 3600)))
    }

    func testFirstLaunchSinceBootIsReportedOncePerBoot() {
        let name = "boot-tests-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        let detector = BootDetector()

        XCTAssertFalse(detector.isFirstLaunchSinceBoot(defaults: defaults, bootTime: boot), "first run")
        XCTAssertFalse(detector.isFirstLaunchSinceBoot(defaults: defaults, bootTime: boot), "same boot")
        let rebooted = boot.addingTimeInterval(86_400)
        XCTAssertTrue(detector.isFirstLaunchSinceBoot(defaults: defaults, bootTime: rebooted), "after a restart")
        XCTAssertFalse(detector.isFirstLaunchSinceBoot(defaults: defaults, bootTime: rebooted), "same boot again")
        XCTAssertFalse(detector.isFirstLaunchSinceBoot(defaults: defaults, bootTime: nil), "unknown boot time")
    }
}
