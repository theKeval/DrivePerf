import DPSharedBridge
import DrivePerfShared
import XCTest
@testable import DrivePerf

/// The recorder the features use, over the shared core: commands reach the core, statuses and trips come back, and
/// the iOS surfaces follow the trip.
@MainActor
final class CoreTripRecorderTests: XCTestCase {
    private let live = SpyLiveSurface()
    private let notifier = SpyNotifier()
    private let assertions = SpyAssertions()
    private let core = FakeSharedCore()

    private func makeRecorder(attach: Bool = true) -> CoreTripRecorder {
        let recorder = CoreTripRecorder(
            liveSurface: live, notifier: notifier, assertions: assertions, autoDetectEnabled: false)
        if attach { recorder.attach(core) }
        return recorder
    }

    private func status(
        _ state: DPSharedBridge.RecorderState, id: String? = "t1", manual: Bool = true, distance: Double = 0
    ) -> DPSharedBridge.RecorderStatus {
        DPSharedBridge.RecorderStatus(
            state: state, tripId: id, isManual: manual, elapsedS: 12, distanceM: distance, autoDetectEnabled: false)
    }

    /// Lets the recorder's observing tasks run.
    private func settle() async {
        for _ in 0..<5 { await Task.yield() }
    }

    func testBeforeTheCoreStartsCommandsAreDroppedAndTheChoiceIsKept() {
        let recorder = makeRecorder(attach: false)
        recorder.onWake(DPSharedBridge.WakeEvent(kind: .manual, tMonoNs: 1, wallMs: 1))
        recorder.setAutoDetect(enabled: true)
        XCTAssertEqual(recorder.status.state, .idle)
        XCTAssertTrue(recorder.status.autoDetectEnabled)
        XCTAssertEqual(assertions.begun, 0, "no trip can start before the core")
        recorder.attach(core)
        XCTAssertEqual(core.commands, ["auto:true"], "the stored choice reaches the core when it attaches")
    }

    func testCommandsReachTheCore() {
        let recorder = makeRecorder()
        recorder.onLaunch()
        recorder.onWake(DPSharedBridge.WakeEvent(kind: .parkedExit, tMonoNs: 1, wallMs: 1))
        recorder.stop()
        recorder.markPassenger(keep: false)
        XCTAssertEqual(core.commands, ["auto:false", "launch", "wake:parkedExit", "stop", "passenger:false"])
        XCTAssertEqual(assertions.begun, 1)
    }

    func testAManualTripShowsTheLiveActivityUntilItEnds() async {
        let recorder = makeRecorder()
        core.send(status(.recording))
        core.send(status(.recording, distance: 150))
        await settle()
        XCTAssertEqual(recorder.status.distanceM, 150)
        XCTAssertEqual(live.started, ["t1"])
        XCTAssertEqual(live.updates.last, 150)
        XCTAssertGreaterThan(assertions.ended, 0, "the wake assertion ends once the trip moves")
        XCTAssertTrue(notifier.posted.isEmpty, "manual trips use the Live Activity, not the notification")

        core.send(.idle)
        await settle()
        XCTAssertEqual(live.ended, 1)
        XCTAssertEqual(recorder.status.state, .idle)
    }

    func testAnAutomaticTripPostsOneNotificationOnceConfirmed() async {
        _ = makeRecorder()
        core.send(status(.candidate, manual: false))
        await settle()
        XCTAssertTrue(notifier.posted.isEmpty, "a candidate is not shown")
        core.send(status(.recording, manual: false))
        core.send(status(.paused, manual: false))
        core.send(status(.recording, manual: false, distance: 900))
        await settle()
        XCTAssertEqual(notifier.posted, ["t1"])
        XCTAssertTrue(live.started.isEmpty)
        core.send(.idle)
        await settle()
        XCTAssertEqual(notifier.removed, ["t1"])
    }

    func testTripsFromTheCoreReachTheList() async {
        let recorder = makeRecorder()
        let trip = TripSummary(
            id: "t9", source: .manual, status: .short, startedAt: Date(timeIntervalSince1970: 0),
            endedAt: Date(timeIntervalSince1970: 60), distanceM: 300, samples: SampleCounts())
        var lists = recorder.observeTrips().makeAsyncIterator()
        _ = await lists.next()
        core.send([trip])
        let next = await lists.next()
        XCTAssertEqual(next, [trip])
        XCTAssertEqual(recorder.history, [trip])
    }

    // MARK: Mapping

    func testKotlinStatusMapsToTheSwiftMirror() {
        let kotlin = DrivePerfShared.RecorderStatus(
            state: .paused, tripId: "t1", isManual: true, elapsedS: 61.7, distanceM: 1200, autoDetectEnabled: false,
            startFailed: false)
        XCTAssertEqual(
            KotlinMapping.swift(kotlin),
            DPSharedBridge.RecorderStatus(
                state: .paused, tripId: "t1", isManual: true, elapsedS: 61, distanceM: 1200, autoDetectEnabled: false))
    }

    func testKotlinTripsMapToListRows() {
        func trip(_ status: DrivePerfShared.TripStatus, _ source: DrivePerfShared.TripSource, distance: Double) -> DrivePerfShared.Trip {
            DrivePerfShared.Trip(
                id: "t1", status: status, kind: .driver, source: source, sourceDetail: nil, vehicleId: nil,
                startedAt: 1_000_000, startTzOffsetMin: 0, startEstimated: false, endedAt: KotlinLong(value: 1_600_000),
                endTzOffsetMin: nil, tzId: nil, partOfDay: nil, fromLabel: nil, toLabel: nil, labelOverride: nil,
                context: nil, distanceM: KotlinDouble(value: distance), estimatedDistanceM: nil,
                durationS: KotlinDouble(value: 600), movingS: nil, stoppedS: nil, stopCount: nil,
                avgMovingSpeedMps: nil, maxSpeedMps: nil, maxSpeedAtS: nil, elevGainM: nil, elevLossM: nil,
                elevMinM: nil, elevMaxM: nil, bbox: nil, stops: [], cleanest: nil, quality: nil, interrupted: nil,
                suspect: .none, suspectAnswer: nil, captureMeta: nil, pipelineVersion: nil, rawState: .full,
                createdAt: 1, updatedAt: 1, deletedAt: nil)
        }
        let row = KotlinMapping.summary(trip(.finalised, .auto, distance: 4200))
        XCTAssertEqual(row?.status, .finalised)
        XCTAssertEqual(row?.source, .auto)
        XCTAssertEqual(row?.distanceM, 4200)
        XCTAssertEqual(row?.duration, 600)
        XCTAssertEqual(KotlinMapping.summary(trip(.finalised, .manual, distance: 300))?.status, .short)
        XCTAssertEqual(KotlinMapping.summary(trip(.interrupted, .manual, distance: 300))?.status, .interrupted)
        XCTAssertNil(KotlinMapping.summary(trip(.recording, .auto, distance: 0)))
    }

    func testBytesCrossInBulk() {
        let data = Data((0..<100_000).map { UInt8(truncatingIfNeeded: $0 &* 7) })
        XCTAssertEqual(KotlinMapping.swift(KotlinMapping.kotlin(data)), data)
        XCTAssertEqual(KotlinMapping.swift(KotlinMapping.kotlin(Data())), Data())
    }
}

final class DeviceIdentityTests: XCTestCase {
    func testDeviceIdsAreTimeOrderedVersion7() {
        let id = DeviceIdentity.uuidV7(now: Date(timeIntervalSince1970: 1_794_268_800))
        XCTAssertEqual(id.count, 36)
        XCTAssertEqual(Array(id)[14], "7")
        XCTAssertTrue("89ab".contains(Array(id)[19]))
        XCTAssertTrue(id.hasPrefix("01"), "48-bit milliseconds lead the ID")
        XCTAssertLessThan(id, DeviceIdentity.uuidV7(now: Date(timeIntervalSince1970: 1_794_268_801)))
    }
}
