import Foundation
import OSLog
import UserNotifications

/// DRIVE-222 (F-CAP-14 AC3 on iOS): the S-LIVE-10 "Auto-detect may be paused" notice, posted at the first wake after
/// a restart while the app has not been opened since. iOS cannot start background location for the app until it is
/// opened once (`OpenedSinceBoot`), and the 24 h watchdog never fires in that state because every wake checks in.
///
/// Same copy and category as the watchdog, so a tap opens the capture status sheet, but its own identifier: the core's
/// watchdog schedule removes a delivered watchdog notice at every check-in, which would hide this one at the next
/// wake. It is removed when the app comes to the foreground. It never asks for notification permission.
public final class NotificationRearmNotice: @unchecked Sendable {
    public static let identifier = "driveperf.rearm_after_boot"

    private static let logger = Logger(subsystem: "com.driveperf.app", category: "RearmNotice")

    public init() {}

    public func post() {
        Task {
            do {
                try await UNUserNotificationCenter.current().add(Self.request())
            } catch {
                Self.logger.error("Could not post the re-arm notice: \(error.localizedDescription, privacy: .private)")
            }
        }
    }

    public func clear() {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [Self.identifier])
        center.removeDeliveredNotifications(withIdentifiers: [Self.identifier])
    }

    /// Delivered at once (no trigger). Pure, so it is unit-tested.
    static func request() -> UNNotificationRequest {
        let content = UNMutableNotificationContent()
        content.title = NotificationWatchdog.title
        content.body = NotificationWatchdog.body
        content.categoryIdentifier = NotificationWatchdog.category
        // No sound is set: it arrives as a drive starts.
        content.interruptionLevel = .active
        return UNNotificationRequest(identifier: identifier, content: content, trigger: nil)
    }
}
