import DPPlatform
import DPSharedBridge
import Foundation
import UserNotifications
import XCTest
@testable import DrivePerf

final class DriveWidgetSnapshotTests: XCTestCase {
    func testRoundTripAndCorruptSnapshotFallback() throws {
        let directory = makeTempDirectory()
        defer { try? FileManager.default.removeItem(at: directory) }
        let url = directory.appendingPathComponent("widget.json")
        XCTAssertEqual(DriveWidgetSnapshot.read(from: url), DriveWidgetSnapshot())

        let snapshot = DriveWidgetSnapshot(
            canRecord: true,
            recording: .init(tripID: "drive", startedAt: Date(timeIntervalSince1970: 100), distanceM: 900, offersLiveActivity: true),
            latestDrive: .init(partOfDay: "Morning", distanceM: 5_000, smoothness: 91, baseline: 84))
        try snapshot.write(to: url)
        let read = DriveWidgetSnapshot.read(from: url)
        XCTAssertEqual(read, snapshot)
        XCTAssertEqual(read.latestDrive?.delta, 7)
        XCTAssertEqual(read.action, .showLive("drive"))

        try Data("broken".utf8).write(to: url)
        XCTAssertEqual(DriveWidgetSnapshot.read(from: url), DriveWidgetSnapshot())
    }

    @MainActor
    func testLatestDriveIncludesFinalizedShortTripWithoutSmoothness() async {
        let now = Date()
        let history = [
            TripSummary(
                id: "short", source: .manual, status: .short, startedAt: now.addingTimeInterval(-60),
                endedAt: now, distanceM: 300, samples: SampleCounts()),
            TripSummary(
                id: "older", source: .manual, status: .finalised, startedAt: now.addingTimeInterval(-1_800),
                endedAt: now.addingTimeInterval(-900), distanceM: 5_000, samples: SampleCounts()),
        ]
        let short = DriveWidgetSnapshot.LatestDrive(partOfDay: "Evening", distanceM: 300, smoothness: nil, baseline: nil)
        let older = DriveWidgetSnapshot.LatestDrive(partOfDay: "Evening", distanceM: 5_000, smoothness: 91, baseline: 84)

        let latest = await TripSurfaceData.latestDrive(in: history) { id in
            id == "short" ? short : older
        }

        XCTAssertEqual(latest, short)
    }

    func testActionAndMissingBaseline() {
        var snapshot = DriveWidgetSnapshot()
        XCTAssertEqual(snapshot.action, .start)
        snapshot.canRecord = false
        XCTAssertEqual(snapshot.action, .fixLocation)
        snapshot.recording = .init(tripID: "drive", startedAt: .now, distanceM: 0, offersLiveActivity: false)
        XCTAssertEqual(snapshot.action, .stop("drive"))
        XCTAssertNil(DriveWidgetSnapshot.LatestDrive(partOfDay: "Evening", distanceM: 9_000, smoothness: 91, baseline: nil).delta)
    }
}

@MainActor
final class SystemTripIntentTests: XCTestCase {
    private let core = FakeSharedCore()
    private let live = SpyLiveSurface()
    private let notifier = SpyNotifier()
    private let clock = SystemClock()
    private lazy var recorder = CoreTripRecorder(liveSurface: live, notifier: notifier)

    private func recording(_ id: String, manual: Bool) -> RecorderStatus {
        RecorderStatus(state: .recording, tripId: id, isManual: manual, elapsedS: 60, distanceM: 700, autoDetectEnabled: true)
    }

    func testStartWithoutPermissionRequestsLocationHelp() async throws {
        recorder.attach(core)
        let needsLocation = try await TripIntentHandler.start(
            recorder: recorder, clock: clock, canRecord: false, expectedTripID: nil, source: "widget")
        XCTAssertTrue(needsLocation)
        XCTAssertEqual(core.commands, ["auto:true"])
    }

    func testStartAwaitsCoreAndPresentsLiveActivity() async throws {
        recorder.attach(core)
        let intent = Task {
            try await TripIntentHandler.start(
                recorder: recorder, clock: clock, canRecord: true, expectedTripID: nil, source: "widget")
        }
        for _ in 0..<100 where !recorder.isStartingFromIntent { await Task.yield() }
        XCTAssertTrue(recorder.isStartingFromIntent)
        core.send(recording("manual", manual: true))
        let needsLocation = try await intent.value
        XCTAssertFalse(needsLocation)
        XCTAssertEqual(core.commands, ["auto:true", "wake:manual"])
        XCTAssertEqual(live.started, ["manual"])
        XCTAssertTrue(notifier.posted.isEmpty)
    }

    func testShowAttachesOnceToMatchingAutoTripAndStopIsScoped() async throws {
        recorder.attach(core)
        core.send(recording("auto", manual: false))
        _ = await recorder.waitForStatus { $0.tripId == "auto" }
        XCTAssertTrue(live.started.isEmpty)
        XCTAssertEqual(notifier.posted, ["auto"])

        for id in ["stale", "auto", "auto"] {
            let needsLocation = try await TripIntentHandler.start(
                recorder: recorder, clock: clock, canRecord: true, expectedTripID: id, source: "control_center")
            XCTAssertFalse(needsLocation)
        }
        XCTAssertEqual(live.started, ["auto"])
        await TripIntentHandler.stop(recorder: recorder, expectedTripID: "stale")
        XCTAssertEqual(core.commands, ["auto:true"])
        await TripIntentHandler.stop(recorder: recorder, expectedTripID: "auto")
        XCTAssertEqual(core.commands, ["auto:true", "stop"])

        core.send(.idle)
        _ = await recorder.waitForStatus { $0.state == .idle }
        XCTAssertEqual(notifier.removed, ["auto"])
        XCTAssertEqual(live.ended, 1)
    }

    func testRecordingRouteReentersCaptureOnlyForMatchingTrip() async {
        recorder.attach(core)
        core.send(recording("active", manual: false))
        _ = await recorder.waitForStatus { $0.tripId == "active" }
        let router = TripSurfaceRouter()
        await router.open(.recording("old"), recorder: recorder)
        XCTAssertEqual(router.rootRevision, 0)
        await router.open(.recording("active"), recorder: recorder)
        XCTAssertEqual(router.rootRevision, 1)
        await router.open(.decoded("old"), recorder: recorder)
        XCTAssertEqual(router.rootRevision, 2)
    }
}

final class TripNotificationRouteTests: XCTestCase {
    func testRoutesOnlyDefaultActionsFromCapture() {
        func route(category: String, action: String = UNNotificationDefaultActionIdentifier, request: String = "trip-42") -> TripNotificationRoute? {
            TripNotificationRoute(actionID: action, requestID: request, category: category, tripID: "42")
        }

        XCTAssertEqual(route(category: LocalNotifier.recordingCategory), .recording("42"))
        XCTAssertEqual(route(category: ""), .recording("42"))
        XCTAssertEqual(route(category: LocalNotifier.decodedCategory), .decoded("42"))
        XCTAssertNil(route(category: "unrelated"))
        XCTAssertNil(route(category: "", action: UNNotificationDismissActionIdentifier))
        XCTAssertNil(route(category: "", request: "trip-17"))
    }

    func testWatchdogNotificationOpensTheStatusSheetWithoutATripID() {
        let watchdog = TripNotificationRoute(
            actionID: UNNotificationDefaultActionIdentifier, requestID: AutoDetectWatchdog.identifier,
            category: AutoDetectWatchdog.category, tripID: "")
        XCTAssertEqual(watchdog, .watchdog)
        XCTAssertNil(
            TripNotificationRoute(
                actionID: UNNotificationDismissActionIdentifier, requestID: AutoDetectWatchdog.identifier,
                category: AutoDetectWatchdog.category, tripID: ""))
    }
}
