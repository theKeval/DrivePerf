import ActivityKit
import Foundation
import OSLog

/// Local ActivityKit updates for manual drives and automatic drives opened through "Show live drive".
@MainActor
final class LiveActivityController: LiveSurfacing {
    private let carName: @MainActor () -> String?
    private let logger = Logger(subsystem: "com.driveperf.app", category: "LiveActivity")
    private var activity: Activity<RecordingActivityAttributes>?
    private var state: RecordingActivityAttributes.ContentState?
    private var lastPushAt = Date.distantPast
    private var lastPushedTenths = 0

    init(carName: @escaping @MainActor () -> String? = { nil }) {
        self.carName = carName
    }

    var activitiesEnabled: Bool { ActivityAuthorizationInfo().areActivitiesEnabled }
    var hasEnded: Bool { activity?.activityState == .ended || activity?.activityState == .dismissed }

    func start(tripId: String, startedAt: Date) {
        _ = present(tripId: tripId, startedAt: startedAt, distanceM: 0)
    }

    func present(tripId: String, startedAt: Date, distanceM: Double) -> Bool {
        if activity?.attributes.tripId == tripId { return true }
        guard activitiesEnabled, activity == nil else { return false }
        let now = Date()
        let tenths = Int((max(0, distanceM) / 100).rounded(.down))
        let initial = RecordingActivityAttributes.ContentState(
            startedAt: startedAt, distanceKm: Double(tenths) / 10, hasFix: distanceM > 0, ended: false,
            carName: carName().map { String($0.unicodeScalars.prefix(80)) })
        do {
            activity = try Activity.request(
                attributes: RecordingActivityAttributes(tripId: tripId),
                content: ActivityContent(state: initial, staleDate: now.addingTimeInterval(60)))
            state = initial
            lastPushAt = now
            lastPushedTenths = tenths
            return true
        } catch {
            logger.notice("Could not start Live Activity: \(error.localizedDescription, privacy: .private)")
            return false
        }
    }

    func resume(tripId: String) -> Bool {
        guard let existing = Activity<RecordingActivityAttributes>.activities.first(where: {
            $0.attributes.tripId == tripId && !$0.content.state.ended && $0.activityState != .dismissed
        }) else { return false }
        activity = existing
        state = existing.content.state
        lastPushAt = Date()
        lastPushedTenths = Int((existing.content.state.distanceKm * 10).rounded())
        return true
    }

    func update(distanceM: Double, hasFix: Bool) {
        guard let activity, var state, !hasEnded else { return }
        let tenths = Int((max(0, distanceM) / 100).rounded(.down))
        let now = Date()
        let secondsSincePush = now.timeIntervalSince(lastPushAt)
        guard secondsSincePush >= 5, tenths != lastPushedTenths || secondsSincePush >= 15 || hasFix != state.hasFix else {
            return
        }
        state.distanceKm = Double(tenths) / 10
        state.hasFix = hasFix
        self.state = state
        lastPushAt = now
        lastPushedTenths = tenths
        let content = ActivityContent(state: state, staleDate: now.addingTimeInterval(60))
        nonisolated(unsafe) let target = activity
        Task { await target.update(content) }
    }

    func end() {
        guard let activity, var state else { return }
        defer {
            self.activity = nil
            self.state = nil
        }
        guard !hasEnded else { return }
        state.ended = true
        let content = ActivityContent(state: state, staleDate: nil)
        nonisolated(unsafe) let target = activity
        Task { await target.end(content, dismissalPolicy: .after(Date().addingTimeInterval(15 * 60))) }
    }
}
