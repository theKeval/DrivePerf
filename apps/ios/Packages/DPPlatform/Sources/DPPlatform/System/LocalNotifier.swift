import Foundation
import OSLog
import UserNotifications

/// Local notifications for capture (F-CAP-11, S-LIVE-09).
public struct LocalNotifier: Sendable {
    public static let recordingCategory = "driveperf.recording"
    public static let decodedCategory = "driveperf.decoded"

    @MainActor private static var pending: [String: (token: UUID, task: Task<Void, Never>)] = [:]
    private static let logger = Logger(subsystem: "com.driveperf.app", category: "CaptureNotifications")

    public init() {}

    /// Passive, with no sound and one identifier per trip.
    @MainActor
    public func postAutoTripRecording(tripId: String) {
        post(
            tripId: tripId, category: Self.recordingCategory, title: "Recording your drive",
            body: "Tap to open. Stop it any time after you park.")
    }

    @MainActor
    public func postDriveDecoded(tripId: String, smoothness: Int?) {
        post(
            tripId: tripId, category: Self.decodedCategory, title: "Drive decoded.",
            body: smoothness.map { "Smoothness \($0)" } ?? "Open DrivePerf to see your drive.")
    }

    @MainActor
    public func removeTripNotifications(tripId: String) {
        enqueue(tripId: tripId) { center, identifier in
            center.removePendingNotificationRequests(withIdentifiers: [identifier])
            center.removeDeliveredNotifications(withIdentifiers: [identifier])
        }
    }

    public static func identifier(for tripId: String) -> String { "trip-\(tripId)" }

    @MainActor
    private func post(tripId: String, category: String, title: String, body: String) {
        enqueue(tripId: tripId) { center, identifier in
            let authorization = await center.notificationSettings().authorizationStatus
            guard authorization == .authorized || authorization == .provisional || authorization == .ephemeral else { return }
            let content = UNMutableNotificationContent()
            content.title = title
            content.body = body
            content.interruptionLevel = .passive
            content.threadIdentifier = tripId
            content.categoryIdentifier = category
            do {
                try await center.add(UNNotificationRequest(identifier: identifier, content: content, trigger: nil))
            } catch {
                Self.logger.error("Could not post capture notification: \(error.localizedDescription, privacy: .private)")
            }
        }
    }

    @MainActor
    private func enqueue(
        tripId: String, operation: @escaping @MainActor (UNUserNotificationCenter, String) async -> Void
    ) {
        let previous = Self.pending[tripId]?.task
        // Serialize submission and removal so an in-flight recording notice cannot reappear after the trip ends.
        let token = UUID()
        Self.pending[tripId] = (token, Task {
            await previous?.value
            await operation(.current(), Self.identifier(for: tripId))
            if Self.pending[tripId]?.token == token { Self.pending[tripId] = nil }
        })
    }
}
