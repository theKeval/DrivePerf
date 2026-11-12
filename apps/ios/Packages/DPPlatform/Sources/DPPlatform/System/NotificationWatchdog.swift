import DPSharedBridge
import Foundation
import OSLog
import UserNotifications

/// The F-CAP-14 watchdog on iOS. iOS cannot tell the app it was force-quit, so the shared core pre-schedules a local
/// notification 24 h after every check-in (the DTS pattern, A3 §1.5): while auto-detect keeps being woken, each
/// check-in pushes it out and it never shows; once the wakes stop it fires and tells the user to open DrivePerf. The
/// core owns the rules (24 h, at most once per 72 h, never while auto-detect is off); this adapter only schedules what
/// it is told, as one `UNUserNotificationCenter` request with a fixed identifier, so a new schedule replaces the
/// pending one. It never asks for notification permission: without it the request is simply not delivered, and Home
/// and the status sheet still say what is wrong.
public final class NotificationWatchdog: AutoDetectWatchdogBridge, @unchecked Sendable {
    public static let identifier = "driveperf.watchdog"
    public static let category = "driveperf.capture_problem"
    public static let title = "Auto-detect may be paused."
    public static let body = "Open DrivePerf once to switch it back on."

    private static let logger = Logger(subsystem: "com.driveperf.app", category: "Watchdog")

    private let now: @Sendable () -> Date
    private let lock = NSLock()
    /// The core calls from its own thread: serialise the notification centre calls so a cancel never overtakes an
    /// earlier schedule.
    private var tail: Task<Void, Never>?

    public init(now: @escaping @Sendable () -> Date = { Date() }) {
        self.now = now
    }

    public func schedule(fireAtWallMs: Int64) {
        let fireAt = Date(wallMs: fireAtWallMs)
        let scheduledAt = now()
        enqueue {
            let center = UNUserNotificationCenter.current()
            // A notice that already fired is stale now: the app is awake, so auto-detect is on.
            center.removeDeliveredNotifications(withIdentifiers: [Self.identifier])
            center.removePendingNotificationRequests(withIdentifiers: [Self.identifier])
            do {
                try await center.add(Self.request(fireAt: fireAt, now: scheduledAt))
            } catch {
                Self.logger.error("Could not schedule the watchdog: \(error.localizedDescription, privacy: .private)")
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

    /// The request for a notice at `fireAt`. A time that has already passed (or is about to) fires in a second,
    /// because a calendar trigger in the past never fires. Pure, so it is unit-tested.
    static func request(fireAt: Date, now: Date, calendar: Calendar = .current) -> UNNotificationRequest {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.categoryIdentifier = category
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
