import Foundation
import UserNotifications
import XCTest
@testable import DPPlatform

/// F-CAP-15: the "Auto-detect is back on" notice at the end of a pause.
final class NotificationResumeNoticeTests: XCTestCase {
    private let now = Date(timeIntervalSince1970: 1_800_000_000)
    private var utc: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }

    func testCopyAndIdentifier() {
        let request = NotificationResumeNotice.request(fireAt: now.addingTimeInterval(3600), now: now, calendar: utc)
        XCTAssertEqual(request.identifier, NotificationResumeNotice.identifier)
        XCTAssertNotEqual(request.identifier, NotificationWatchdog.identifier, "the two notices never replace each other")
        XCTAssertEqual(request.content.title, "Auto-detect is back on")
        XCTAssertNil(request.content.sound, "it may arrive while driving")
    }

    func testTheNoticeFiresAtThePauseEnd() throws {
        let fireAt = now.addingTimeInterval(10 * 3600)
        let request = NotificationResumeNotice.request(fireAt: fireAt, now: now, calendar: utc)
        let trigger = try XCTUnwrap(request.trigger as? UNCalendarNotificationTrigger)
        XCTAssertFalse(trigger.repeats)
        XCTAssertEqual(utc.date(from: trigger.dateComponents), fireAt)
    }

    func testAnEndAlreadyPastFiresInASecond() throws {
        let request = NotificationResumeNotice.request(fireAt: now.addingTimeInterval(-60), now: now, calendar: utc)
        let trigger = try XCTUnwrap(request.trigger as? UNTimeIntervalNotificationTrigger)
        XCTAssertEqual(trigger.timeInterval, 1)
    }
}
