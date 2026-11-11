import CoreLocation
import DPPlatform
import DPSharedBridge
import XCTest

/// Pure parts of the platform adapters. The parts that need real sensors run in the on-device self-test.
final class PlatformAdapterTests: XCTestCase {
    // MARK: Spike change 7: CLMonitor names

    func testMonitorNameIsAlphanumeric() {
        XCTAssertTrue(MonitorNaming.isValid(WakeTriggerMonitor.monitorName))
    }

    func testMonitorNamingRejectsTheNameThatCrashedSpikeOne() {
        XCTAssertFalse(MonitorNaming.isValid("parked-spot"))
        XCTAssertFalse(MonitorNaming.isValid("parked spot"))
        XCTAssertFalse(MonitorNaming.isValid(""))
        XCTAssertFalse(MonitorNaming.isValid("parkedSpöt"))
    }

    // MARK: Spike change 8: stale wakes

    func testFreshRegionExitIsAccepted() {
        XCTAssertTrue(
            WakeFilter().accepts(
                source: .region, eventWallMs: 1_000_000, nowWallMs: 1_005_000, processStartWallMs: 0,
                appInForeground: false))
    }

    func testCachedSignificantChangeAfterColdLaunchIsDropped() {
        // Spike 1: every cold launch delivered a cached fix 504–52,106 s old.
        XCTAssertFalse(
            WakeFilter().accepts(
                source: .significantChange, eventWallMs: 0, nowWallMs: 504_000, processStartWallMs: 500_000,
                appInForeground: false))
    }

    func testInitialSignificantChangeWhileOpeningTheAppIsIgnored() {
        XCTAssertFalse(
            WakeFilter().accepts(
                source: .significantChange, eventWallMs: 1_000, nowWallMs: 2_000, processStartWallMs: 0,
                appInForeground: true))
    }

    func testOldRegionEventIsDropped() {
        XCTAssertFalse(
            WakeFilter().accepts(
                source: .region, eventWallMs: 0, nowWallMs: 901_000, processStartWallMs: 0, appInForeground: false))
    }

    // MARK: §2.1 normalised device frame

    func testGravityPointsUpAndRotationIsInDegrees() {
        let sample = MotionNormalizer.normalize(
            tMonoNs: 12_500_000_000, userAcceleration: (0, 0, 0), gravity: (0, 0, -1), rotationRate: (Double.pi, 0, 0),
            quaternion: (1, 0, 0, 0))

        XCTAssertEqual(sample.gravityUp, Vec3(x: 0, y: 0, z: 1), "Face up at rest: gravityUp is +z")
        XCTAssertEqual(sample.rotRateDps.x, 180, accuracy: 1e-9)
        XCTAssertEqual(sample.tMonoNs, 12_500_000_000)
        XCTAssertEqual(sample.attitude, Quat(w: 1, x: 0, y: 0, z: 0))
    }

    func testFinalMotionBatchIsDeliveredOffTheCallersThreadBeforeStopReturns() throws {
        let source = CoreMotionSource()
        try XCTSkipIf(source.capability().maxRateHz == 0, "Needs a real device")
        let log = BatchLog()
        source.start(rateHz: 50) { samples in log.record(onMain: Thread.isMainThread) }
        Thread.sleep(forTimeInterval: 0.2)  // fewer than one full batch

        source.stop()

        let delivered = log.threads
        XCTAssertFalse(delivered.isEmpty, "The partial batch is flushed")
        XCTAssertFalse(delivered.contains(true), "No batch on the main thread")
    }

    func testUserAccelerationIsKinematic() {
        // Core Motion reports the negative of the kinematic acceleration (its raw accelerometer reads −1 g face up).
        let sample = MotionNormalizer.normalize(
            tMonoNs: 0, userAcceleration: (-0.3, 0, 0), gravity: (0, 0, -1), rotationRate: (0, 0, 0),
            quaternion: nil)

        XCTAssertEqual(sample.userAccelG.x, 0.3, accuracy: 1e-12)
        XCTAssertNil(sample.attitude)
    }

    // MARK: §2.1, §2.2 location mapping

    func testLocationMappingKeepsValidFieldsAndNilsInvalidOnes() {
        let now = Date()
        let location = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 0.001, longitude: 0.002), altitude: 10,
            horizontalAccuracy: 4, verticalAccuracy: -1, course: -1, courseAccuracy: -1, speed: 12, speedAccuracy: 0.5,
            timestamp: now)

        let clock = SystemClock()
        let before = clock.monotonicNs()
        let sample = LocationMapping.sample(from: location, lastAccepted: nil, clock: clock)

        XCTAssertEqual(sample?.hAccM, 4)
        XCTAssertNil(sample?.vAccM)
        XCTAssertNil(sample?.altM, "Altitude is meaningless without a vertical accuracy")
        XCTAssertNil(sample?.courseDeg)
        XCTAssertEqual(sample?.speedMps, 12)
        XCTAssertEqual(sample?.description, "LocationSample(<redacted>)", "Never logs a coordinate (ADR-007)")
        XCTAssertEqual(Double(sample?.tMonoNs ?? 0), Double(before), accuracy: 5e7, "Fix time on the monotonic clock")
    }

    // MARK: §2.2 one monotonic clock

    func testFixTimestampIsConvertedToTheMonotonicClock() {
        let clock = SystemClock()
        let fix = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 0, longitude: 0), altitude: 0, horizontalAccuracy: 5,
            verticalAccuracy: 5, timestamp: Date().addingTimeInterval(-60))

        let sample = LocationMapping.sample(from: fix, lastAccepted: nil, clock: clock)

        XCTAssertEqual(Double(clock.monotonicNs() - (sample?.tMonoNs ?? 0)), 60e9, accuracy: 1e8)
    }

    func testMotionUptimeIsConvertedToTheMonotonicClock() {
        let clock = SystemClock()
        let uptime = ProcessInfo.processInfo.systemUptime

        XCTAssertEqual(Double(clock.monotonicNs(atUptime: uptime)), Double(clock.monotonicNs()), accuracy: 5e7)
    }

    func testDuplicateFixFromTheSecondStreamIsDropped() {
        let now = Date()
        let fix = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 0, longitude: 0), altitude: 0, horizontalAccuracy: 5,
            verticalAccuracy: 5, timestamp: now)

        XCTAssertNil(
            LocationMapping.sample(from: fix, lastAccepted: now.addingTimeInterval(-0.2), clock: SystemClock()))
    }

    // MARK: Activity mapping

    func testAutomaticWinsOverStationaryAtARedLight() {
        XCTAssertEqual(
            ActivityMapping.type(automotive: true, cycling: false, running: false, walking: false, stationary: true),
            .automotive)
        XCTAssertEqual(
            ActivityMapping.type(automotive: false, cycling: false, running: false, walking: false, stationary: false),
            .unknown)
    }

    // MARK: TripFileStore (spike change 5: file access decides, not isProtectedDataAvailable)

    private func makeStore() -> (FileTripStore, URL) {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("dp-files-\(UUID().uuidString)")
        return (FileTripStore(root: root), root)
    }

    func testAppendSyncCompressAndReadRoundTrip() throws {
        let (store, root) = makeStore()
        defer { try? FileManager.default.removeItem(at: root) }
        let first = Data("hello ".utf8)
        let second = Data(repeating: 7, count: 50_000)

        XCTAssertTrue(store.isUnlockedSinceBoot())
        XCTAssertEqual(store.append(tripId: "t1", kind: .r0, bytes: first), .ok)
        XCTAssertEqual(store.append(tripId: "t1", kind: .r0, bytes: second), .ok)
        XCTAssertEqual(store.sync(tripId: "t1", kind: .r0), .ok)
        XCTAssertEqual(store.describe(tripId: "t1", kind: .r0, withSha256: false)?.sizeBytes, Int64(50_006))

        XCTAssertEqual(store.compress(tripId: "t1", kind: .r0), .ok)
        let stored = try XCTUnwrap(store.describe(tripId: "t1", kind: .r0, withSha256: true))
        XCTAssertTrue(stored.compressed)
        XCTAssertLessThan(stored.sizeBytes, 1000)
        XCTAssertEqual(stored.sha256?.count, 64)
        XCTAssertEqual(store.read(tripId: "t1", kind: .r0), first + second, "Reads stay uncompressed")
    }

    func testCompressedFileIsGzip() throws {
        let (store, root) = makeStore()
        defer { try? FileManager.default.removeItem(at: root) }
        XCTAssertEqual(store.write(tripId: "t1", kind: .r1, bytes: Data("abc".utf8)), .ok)
        XCTAssertEqual(store.compress(tripId: "t1", kind: .r1), .ok)

        let gz = try Data(contentsOf: root.appendingPathComponent("trips/t1/r1.bin.gz"))
        XCTAssertEqual(Array(gz.prefix(2)), [0x1F, 0x8B], "RFC 1952 magic")
    }

    func testR0IsExcludedFromBackupAndProtectedUnlessOpen() throws {
        let (store, root) = makeStore()
        defer { try? FileManager.default.removeItem(at: root) }
        XCTAssertEqual(store.append(tripId: "t1", kind: .r0, bytes: Data([1])), .ok)

        let url = root.appendingPathComponent("trips/t1/r0.bin")
        XCTAssertEqual(try url.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup, true)
        XCTAssertEqual(FileTripStore.protection(.r0), .completeUnlessOpen)
        XCTAssertEqual(FileTripStore.protection(.preview), .completeUntilFirstUserAuthentication)
    }

    func testWriteReplacesAndDeleteTripRemovesEverything() {
        let (store, root) = makeStore()
        defer { try? FileManager.default.removeItem(at: root) }
        XCTAssertEqual(store.write(tripId: "t1", kind: .preview, bytes: Data([1, 2])), .ok)
        XCTAssertEqual(store.write(tripId: "t1", kind: .preview, bytes: Data([3])), .ok)
        XCTAssertEqual(store.read(tripId: "t1", kind: .preview), Data([3]))
        XCTAssertEqual(store.append(tripId: "t2", kind: .r0, bytes: Data([1])), .ok)
        XCTAssertEqual(store.tripIds(), ["t1", "t2"])
        XCTAssertGreaterThan(store.freeSpaceBytes(), 0)

        XCTAssertEqual(store.delete(tripId: "t1", kind: .preview), .ok)
        XCTAssertNil(store.read(tripId: "t1", kind: .preview))
        XCTAssertEqual(store.delete(tripId: "t1", kind: .preview), .ok, "Deleting a missing file succeeds")
        XCTAssertEqual(store.deleteTrip(tripId: "t2"), .ok)
        XCTAssertNil(store.describe(tripId: "t2", kind: .r0, withSha256: false))
    }

    func testFileErrorsMapToPortCodes() {
        XCTAssertEqual(FileTripStore.result(for: CocoaError(.fileWriteOutOfSpace)), .diskFull)
        XCTAssertEqual(FileTripStore.result(for: CocoaError(.fileWriteNoPermission)), .unavailable)
        XCTAssertEqual(FileTripStore.result(for: NSError(domain: NSPOSIXErrorDomain, code: Int(EPERM))), .unavailable)
        XCTAssertEqual(FileTripStore.result(for: CocoaError(.fileReadCorruptFile)), .ioError)
    }
}

/// Which thread each motion batch arrived on.
private final class BatchLog: @unchecked Sendable {
    private let lock = NSLock()
    private var onMain: [Bool] = []

    func record(onMain value: Bool) { lock.withLock { onMain.append(value) } }
    var threads: [Bool] { lock.withLock { onMain } }
}
