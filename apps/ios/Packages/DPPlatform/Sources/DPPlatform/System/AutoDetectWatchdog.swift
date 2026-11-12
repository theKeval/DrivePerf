import DPSharedBridge
import Foundation
import OSLog
import UserNotifications

/// What the watchdog remembers between launches: timestamps only (F-CAP-14 privacy).
public struct WatchdogRecord: Equatable, Codable, Sendable {
    /// The last time auto-detect armed itself or was woken.
    public var lastCheckIn: Date?
    /// When the pending watchdog notification is set to fire; nil when none is scheduled.
    public var scheduledFireAt: Date?
    /// When the notification last fired, inferred at the next check-in (iOS tells the app nothing when it is not
    /// running). Drives the "at most once per 72 h" rule.
    public var lastFiredAt: Date?

    public init(lastCheckIn: Date? = nil, scheduledFireAt: Date? = nil, lastFiredAt: Date? = nil) {
        self.lastCheckIn = lastCheckIn
        self.scheduledFireAt = scheduledFireAt
        self.lastFiredAt = lastFiredAt
    }
}

/// The watchdog's timing rules, pure so they are unit-tested (F-CAP-14.1).
public enum WatchdogPolicy {
    /// No check-in for this long and the notification fires.
    public static let silence: TimeInterval = 24 * 3600
    /// The notification never fires twice within this window, so a user who ignores it is not nagged.
    public static let minimumGap: TimeInterval = 72 * 3600

    /// The record after a check-in at `now`: a notification that was due before `now` is counted as fired, then the
    /// next one is set 24 h ahead and never earlier than 72 h after the last one.
    public static func afterCheckIn(_ record: WatchdogRecord, now: Date) -> WatchdogRecord {
        var next = record
        if let due = record.scheduledFireAt, due <= now { next.lastFiredAt = due }
        next.lastCheckIn = now
        var fire = now.addingTimeInterval(silence)
        if let fired = next.lastFiredAt { fire = max(fire, fired.addingTimeInterval(minimumGap)) }
        next.scheduledFireAt = fire
        return next
    }

    /// The record after the watchdog was switched off (auto-detect off, Always lost): nothing pending, but a
    /// notification that was already due stays counted as fired.
    public static func afterCancel(_ record: WatchdogRecord, now: Date) -> WatchdogRecord {
        var next = record
        if let due = record.scheduledFireAt, due <= now { next.lastFiredAt = due }
        next.scheduledFireAt = nil
        return next
    }
}

/// The notification centre as the watchdog uses it, so tests do not need the system one.
public protocol WatchdogScheduling: Sendable {
    /// Replaces any pending watchdog notification with one at `fireAt`.
    func schedule(fireAt: Date) async
    func cancel() async
}

/// F-CAP-14 on iOS: iOS cannot tell the app it was force-quit, so a local notification is pre-scheduled 24 h ahead at
/// every check-in (the DTS pattern, A3 §1.5). If auto-detect keeps running, the next check-in pushes it further out and
/// it never shows; if the app is no longer woken, it fires and tells the user to open DrivePerf. At most once per 72 h.
/// Tapping it opens the capture status sheet, which re-arms (opening the app is a check-in).
public final class AutoDetectWatchdog: @unchecked Sendable {
    public static let identifier = "driveperf.watchdog"
    public static let category = "driveperf.capture_problem"
    public static let title = "Auto-detect may be paused."
    public static let body = "Open DrivePerf once to switch it back on."

    private static let recordKey = "driveperf.watchdog.record"
    private static let logger = Logger(subsystem: "com.driveperf.app", category: "Watchdog")

    private let defaults: UserDefaults
    private let scheduler: any WatchdogScheduling
    private let now: @Sendable () -> Date
    private let lock = NSLock()
    /// Serialises the notification centre calls so a cancel never overtakes an earlier schedule.
    private let queue = SerialAsync()

    public init(
        defaults: UserDefaults = .standard, scheduler: any WatchdogScheduling = NotificationWatchdogScheduler(),
        now: @escaping @Sendable () -> Date = { Date() }
    ) {
        self.defaults = defaults
        self.scheduler = scheduler
        self.now = now
    }

    public var record: WatchdogRecord {
        guard let data = defaults.data(forKey: Self.recordKey),
            let record = try? JSONDecoder().decode(WatchdogRecord.self, from: data)
        else { return WatchdogRecord() }
        return record
    }

    /// Auto-detect armed itself or was woken: remember it and push the watchdog 24 h out. Callable from any thread.
    public func checkIn() {
        let fireAt = lock.withLock { () -> Date in
            let next = WatchdogPolicy.afterCheckIn(record, now: now())
            save(next)
            return next.scheduledFireAt ?? now().addingTimeInterval(WatchdogPolicy.silence)
        }
        let scheduler = scheduler
        queue.run { await scheduler.schedule(fireAt: fireAt) }
    }

    /// Auto-detect is off or cannot run: nothing to watch. The last check-in is kept for the status sheet.
    public func cancel() {
        lock.withLock { save(WatchdogPolicy.afterCancel(record, now: now())) }
        let scheduler = scheduler
        queue.run { await scheduler.cancel() }
    }

    private func save(_ record: WatchdogRecord) {
        do {
            defaults.set(try JSONEncoder().encode(record), forKey: Self.recordKey)
        } catch {
            Self.logger.error("Could not save the watchdog record: \(error.localizedDescription, privacy: .private)")
        }
    }
}

// Declared here, not on the class, so the main-actor protocol does not pull the whole watchdog onto the main actor:
// `checkIn()` is called from wake callbacks on any thread.
extension AutoDetectWatchdog: AutoDetectCheckIns {
    @MainActor public var lastCheckIn: Date? { record.lastCheckIn }
}

/// Runs async work one item at a time, in the order it was submitted.
private final class SerialAsync: @unchecked Sendable {
    private let lock = NSLock()
    private var tail: Task<Void, Never>?

    func run(_ work: @escaping @Sendable () async -> Void) {
        lock.withLock {
            let previous = tail
            tail = Task {
                await previous?.value
                await work()
            }
        }
    }
}

/// `WatchdogScheduling` on `UNUserNotificationCenter`. It never asks for permission: without notification access the
/// request is simply not delivered, and the Capture status sheet and Home still say what is wrong.
public struct NotificationWatchdogScheduler: WatchdogScheduling {
    private static let logger = Logger(subsystem: "com.driveperf.app", category: "Watchdog")

    public init() {}

    public func schedule(fireAt: Date) async {
        let center = UNUserNotificationCenter.current()
        // A notice that already fired is stale now: the app is open, so auto-detect is back on.
        center.removeDeliveredNotifications(withIdentifiers: [AutoDetectWatchdog.identifier])
        center.removePendingNotificationRequests(withIdentifiers: [AutoDetectWatchdog.identifier])
        let content = UNMutableNotificationContent()
        content.title = AutoDetectWatchdog.title
        content.body = AutoDetectWatchdog.body
        content.categoryIdentifier = AutoDetectWatchdog.category
        content.interruptionLevel = .active
        let components = Calendar.current.dateComponents(
            [.year, .month, .day, .hour, .minute, .second], from: fireAt)
        let request = UNNotificationRequest(
            identifier: AutoDetectWatchdog.identifier, content: content,
            trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: false))
        do {
            try await center.add(request)
        } catch {
            Self.logger.error("Could not schedule the watchdog: \(error.localizedDescription, privacy: .private)")
        }
    }

    public func cancel() async {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [AutoDetectWatchdog.identifier])
        center.removeDeliveredNotifications(withIdentifiers: [AutoDetectWatchdog.identifier])
    }
}
