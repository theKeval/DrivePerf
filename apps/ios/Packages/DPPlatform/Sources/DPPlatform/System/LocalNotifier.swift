import Foundation
import UserNotifications

/// Local notifications for capture (F-CAP-11, S-LIVE-09). Automatic trips get a passive notification instead of a
/// Live Activity: a Live Activity cannot start while the app is in the background ("Target is not foreground" on
/// all six automatic trips in Spike 1, spike change 9).
public struct LocalNotifier: Sendable {
    public init() {}

    /// S-LIVE-09: passive, no sound, one per trip (thread = trip id).
    public func postAutoTripRecording(tripId: String) {
        let content = UNMutableNotificationContent()
        content.title = "Recording your drive"
        content.body = "Tap to open. Stop it any time after you park."
        content.interruptionLevel = .passive
        content.threadIdentifier = tripId
        UNUserNotificationCenter.current().add(
            UNNotificationRequest(identifier: Self.identifier(for: tripId), content: content, trigger: nil))
    }

    public func removeTripNotifications(tripId: String) {
        let center = UNUserNotificationCenter.current()
        center.removeDeliveredNotifications(withIdentifiers: [Self.identifier(for: tripId)])
        center.removePendingNotificationRequests(withIdentifiers: [Self.identifier(for: tripId)])
    }

    static func identifier(for tripId: String) -> String { "trip-\(tripId)" }
}
