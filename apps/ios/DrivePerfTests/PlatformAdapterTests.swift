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
            timestamp: 12.5, userAcceleration: (0, 0, 0), gravity: (0, 0, -1), rotationRate: (Double.pi, 0, 0),
            quaternion: (0, 0, 0, 1))

        XCTAssertEqual(sample.gravityUp, Vec3(x: 0, y: 0, z: 1), "Face up at rest: gravityUp is +z")
        XCTAssertEqual(sample.rotRateDps.x, 180, accuracy: 1e-9)
        XCTAssertEqual(sample.tMonoNs, 12_500_000_000)
        XCTAssertEqual(sample.attitude, Quat(x: 0, y: 0, z: 0, w: 1))
    }

    func testUserAccelerationIsKinematic() {
        // Core Motion reports the negative of the kinematic acceleration (its raw accelerometer reads −1 g face up).
        let sample = MotionNormalizer.normalize(
            timestamp: 0, userAcceleration: (-0.3, 0, 0), gravity: (0, 0, -1), rotationRate: (0, 0, 0),
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

        let sample = LocationMapping.sample(from: location, notBefore: now, lastAccepted: nil, clock: SystemClock())

        XCTAssertEqual(sample?.hAccM, 4)
        XCTAssertNil(sample?.vAccM)
        XCTAssertNil(sample?.altM, "Altitude is meaningless without a vertical accuracy")
        XCTAssertNil(sample?.courseDeg)
        XCTAssertEqual(sample?.speedMps, 12)
        XCTAssertEqual(sample?.description, "LocationSample(<redacted>)", "Never logs a coordinate (ADR-007)")
    }

    func testFixCachedBeforeTheWakeIsDropped() {
        let wake = Date()
        let cached = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 0, longitude: 0), altitude: 0, horizontalAccuracy: 5,
            verticalAccuracy: 5, timestamp: wake.addingTimeInterval(-60))

        XCTAssertNil(LocationMapping.sample(from: cached, notBefore: wake, lastAccepted: nil, clock: SystemClock()))
    }

    func testDuplicateFixFromTheSecondStreamIsDropped() {
        let now = Date()
        let fix = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 0, longitude: 0), altitude: 0, horizontalAccuracy: 5,
            verticalAccuracy: 5, timestamp: now)

        XCTAssertNil(
            LocationMapping.sample(
                from: fix, notBefore: .distantPast, lastAccepted: now.addingTimeInterval(-0.2), clock: SystemClock()))
    }

    // MARK: Activity mapping

    func testAutomaticWinsOverStationaryAtARedLight() {
        XCTAssertEqual(
            ActivityMapping.kind(automotive: true, cycling: false, running: false, walking: false, stationary: true),
            .automotive)
        XCTAssertEqual(
            ActivityMapping.kind(automotive: false, cycling: false, running: false, walking: false, stationary: false),
            .unknown)
    }

    // MARK: Spike change 5: file access decides, not isProtectedDataAvailable

    func testTripFilesAreWritableAndRoundTrip() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("dp-files-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: root) }
        let store = FileTripStore(root: root)

        XCTAssertTrue(store.isWritable())
        let writer = try store.open(tripId: "t1")
        writer.append(
            MotionSample(
                tMonoNs: 1, userAccelG: Vec3(x: 0, y: 0, z: 0), gravityUp: Vec3(x: 0, y: 0, z: 1),
                rotRateDps: Vec3(x: 0, y: 0, z: 0), attitude: nil))
        writer.close()

        XCTAssertEqual(writer.counts.motion, 1)
        XCTAssertGreaterThan(store.sizeBytes(tripId: "t1"), 0)
        store.delete(tripId: "t1")
        XCTAssertEqual(store.sizeBytes(tripId: "t1"), 0)
    }
}
