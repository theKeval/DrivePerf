import DPPlatform
import DPSharedBridge
import UserNotifications
import XCTest
@testable import DrivePerf

/// DRIVE-137 (F-CAP-11): the automatic trip's notice is counted when shown and when tapped, the manual fallback is not,
/// and the tap opens the in-drive screen like any recording notice.
@MainActor
final class AutoTripNoticeTests: XCTestCase {
    private let notifier = SpyNotifier()
    private let core = FakeSharedCore()
    private var events: [AutoTripNoticeEvent] = []

    private func makeRecorder() -> CoreTripRecorder {
        let recorder = CoreTripRecorder(liveSurface: nil, notifier: notifier, autoDetectEnabled: true)
        recorder.trackNotice = { [weak self] in self?.events.append($0) }
        recorder.attach(core)
        return recorder
    }

    private func status(
        _ state: DPSharedBridge.RecorderState, id: String = "t1", manual: Bool
    ) -> DPSharedBridge.RecorderStatus {
        DPSharedBridge.RecorderStatus(
            state: state, tripId: id, isManual: manual, elapsedS: 12, distanceM: 100, autoDetectEnabled: true)
    }

    private func waitUntil(_ message: String, _ condition: () -> Bool) async {
        let deadline = ContinuousClock.now + .seconds(2)
        while !condition() {
            if ContinuousClock.now >= deadline { return XCTFail("timed out waiting for: \(message)") }
            try? await Task.sleep(for: .milliseconds(5))
        }
    }

    func testAnAutomaticTripsNoticeIsCountedOnceWhenShown() async {
        let recorder = makeRecorder()
        core.send(status(.candidate, manual: false))
        core.send(status(.recording, manual: false))
        core.send(status(.paused, manual: false))
        await waitUntil("the trip is paused") { recorder.status.state == .paused }
        XCTAssertEqual(notifier.posted, ["t1"])
        XCTAssertEqual(events, [.posted])
    }

    func testTheManualFallbackNoticeIsNotCounted() async {
        // No Live Activity surface, so a manual trip falls back to the same notice.
        let recorder = makeRecorder()
        core.send(status(.recording, manual: true))
        await waitUntil("the trip records") { recorder.status.state == .recording }
        XCTAssertEqual(notifier.posted, ["t1"])
        XCTAssertEqual(events, [])
    }

    func testTheAutomaticCategoryRoutesToItsOwnCase() {
        func route(_ category: String) -> TripNotificationRoute? {
            TripNotificationRoute(
                actionID: UNNotificationDefaultActionIdentifier, requestID: LocalNotifier.identifier(for: "42"),
                category: category, tripID: "42")
        }
        XCTAssertEqual(route(LocalNotifier.autoRecordingCategory), .autoRecording("42"))
        XCTAssertEqual(route(LocalNotifier.recordingCategory), .recording("42"))
        XCTAssertNotEqual(LocalNotifier.autoRecordingCategory, LocalNotifier.recordingCategory)
    }

    func testATapOnTheAutomaticNoticeIsCountedAndOpensTheDrive() async {
        let recorder = makeRecorder()
        core.send(status(.recording, manual: false))
        await waitUntil("the trip records") { recorder.status.state == .recording }
        let router = TripSurfaceRouter()
        let before = router.rootRevision

        await router.open(.autoRecording("t1"), recorder: recorder)
        XCTAssertEqual(events, [.posted, .tapped])
        XCTAssertEqual(router.rootRevision, before + 1, "the shell returns to its root, where the in-drive screen covers it")

        await router.open(.recording("t1"), recorder: recorder)
        XCTAssertEqual(events, [.posted, .tapped], "the manual notice's tap is not counted")
    }

    func testATapThatColdLaunchesTheAppIsCountedOnceAnalyticsIsAttached() async {
        // AppDelegate forwards the tap before AppEnvironment.finishLaunch has set trackNotice.
        let recorder = CoreTripRecorder(liveSurface: nil, notifier: notifier, autoDetectEnabled: true)
        let router = TripSurfaceRouter()
        let opening = Task { await router.open(.autoRecording("t1"), recorder: recorder) }
        for _ in 0..<20 { await Task.yield() } // let the tap reach the recorder
        XCTAssertEqual(events, [])

        recorder.trackNotice = { [weak self] in self?.events.append($0) }
        XCTAssertEqual(events, [.tapped], "the held tap is sent when analytics is attached")
        recorder.attach(core)
        core.send(status(.recording, manual: false))
        await opening.value
        await waitUntil("the notice is posted") { events.count == 2 }
        XCTAssertEqual(events, [.tapped, .posted])
    }

    func testEventNamesMatchTheTicket() {
        XCTAssertEqual(AutoTripNoticeEvent.allCases.map(\.rawValue), ["auto_trip_notification_posted", "auto_trip_notification_tapped"])
        XCTAssertEqual(NoticeAnalyticsEvent(.tapped).name, "auto_trip_notification_tapped")
        XCTAssertTrue(NoticeAnalyticsEvent(.posted).properties.isEmpty)
    }
}
