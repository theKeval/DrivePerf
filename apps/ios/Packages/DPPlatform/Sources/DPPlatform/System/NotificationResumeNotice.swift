import DPSharedBridge
import Foundation
import OSLog
import UserNotifications

/// The F-CAP-15 "Auto-detect is back on" notice on iOS: one `UNUserNotificationCenter` request at the end of a pause.
/// The shared core decides when: it schedules the notice when the user pauses until a time, again at every launch
/// while the pause lasts, and cancels it when they resume early or undo. It does not cancel it when the pause ends on
/// time. The app may not be running then, so the notice is what tells the user; auto-detect itself comes back at the
/// next wake, launch or foreground. A fixed identifier means a new schedule replaces the pending one. It never asks
/// for notification permission: without it the notice is not delivered, and Home still shows the state.
public final class NotificationResumeNotice: AutoDetectResumeNoticeBridge, @unchecked Sendable {
    public static let identifier = "driveperf.auto_detect_resumed"
    public static let title = "Auto-detect is back on"
    public static let body = "DrivePerf will record your drives automatically again."

    private static let logger = Logger(subsystem: "com.driveperf.app", category: "ResumeNotice")

    private let now: @Sendable () -> Date
    private let lock = NSLock()
    /// The core calls from its own thread: serialise the notification centre calls so a cancel never overtakes an
    /// earlier schedule.
    private var tail: Task<Void, Never>?

    public init(now: @escaping @Sendable () -> Date = { Date() }) {
        self.now = now
    }

    public func schedule(atWallMs: Int64) {
        let fireAt = Date(wallMs: atWallMs)
        let scheduledAt = now()
        enqueue {
            let center = UNUserNotificationCenter.current()
            center.removePendingNotificationRequests(withIdentifiers: [Self.identifier])
            do {
                try await center.add(Self.request(fireAt: fireAt, now: scheduledAt))
            } catch {
                Self.logger.error("Could not schedule the resume notice: \(error.localizedDescription, privacy: .private)")
            }
        }
    }

    public func cancel() {
        enqueue {
            let center = UNUserNotificationCenter.current()
            center.removePendingNotificationRequests(withIdentifiers: [Self.identifier])
            center.removeDeliveredNotifications(withIdentifiers: [Self.identifier])
        }
    }

    /// The request for a notice at `fireAt`. A time that has already passed (or is about to) fires in a second, because
    /// a calendar trigger in the past never fires. Pure, so it is unit-tested.
    static func request(fireAt: Date, now: Date, calendar: Calendar = .current) -> UNNotificationRequest {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        // No sound is set: it may arrive while driving.
        content.interruptionLevel = .active
        let trigger: UNNotificationTrigger
        if fireAt.timeIntervalSince(now) <= 1 {
            trigger = UNTimeIntervalNotificationTrigger(timeInterval: 1, repeats: false)
        } else {
            let components = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: fireAt)
            trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
        }
        return UNNotificationRequest(identifier: identifier, content: content, trigger: trigger)
    }

    private func enqueue(_ work: @escaping @Sendable () async -> Void) {
        lock.withLock {
            let previous = tail
            tail = Task {
                await previous?.value
                await work()
            }
        }
    }
}
