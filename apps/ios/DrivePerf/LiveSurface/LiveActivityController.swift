import ActivityKit
import Foundation

/// ActivityKit for manual trips only (spike change 9: a local Live Activity cannot start in the background, so
/// automatic trips use the passive notification). Updates are local and throttled: at most every 5 s, on a 0.1 km
/// step or every 15 s (S-LIVE-01 platform notes).
@MainActor
final class LiveActivityController: LiveSurfacing {
    private var activity: Activity<RecordingActivityAttributes>?
    private var state: RecordingActivityAttributes.ContentState?
    private var lastPushAt = Date.distantPast
    private var lastPushedKm = 0.0

    func start(tripId: String, startedAt: Date) {
        guard ActivityAuthorizationInfo().areActivitiesEnabled, activity == nil else { return }
        let initial = RecordingActivityAttributes.ContentState(
            startedAt: startedAt, distanceKm: 0, hasFix: false, ended: false)
        do {
            activity = try Activity.request(
                attributes: RecordingActivityAttributes(tripId: tripId),
                content: ActivityContent(state: initial, staleDate: Date().addingTimeInterval(60)))
            state = initial
        } catch {
            activity = nil
        }
    }

    func update(distanceM: Double, hasFix: Bool) {
        guard let activity, var state else { return }
        let km = (distanceM / 100).rounded(.down) / 10
        let now = Date()
        let due = now.timeIntervalSince(lastPushAt) >= 15 || (km - lastPushedKm >= 0.1)
        guard now.timeIntervalSince(lastPushAt) >= 5, due || hasFix != state.hasFix else { return }
        state.distanceKm = km
        state.hasFix = hasFix
        self.state = state
        lastPushAt = now
        lastPushedKm = km
        let content = ActivityContent(state: state, staleDate: now.addingTimeInterval(60))
        // `Activity` is not Sendable; it is only touched from this actor and the update call.
        nonisolated(unsafe) let target = activity
        Task { await target.update(content) }
    }

    func end() {
        guard let activity, var state else { return }
        self.activity = nil
        self.state = nil
        state.ended = true
        let content = ActivityContent(state: state, staleDate: nil)
        nonisolated(unsafe) let target = activity
        Task { await target.end(content, dismissalPolicy: .after(Date().addingTimeInterval(15 * 60))) }
    }
}
