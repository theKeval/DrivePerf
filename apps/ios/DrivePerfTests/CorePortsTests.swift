import DPPlatform
import DPSharedBridge
import DrivePerfShared
import XCTest
@testable import DrivePerf

/// The Kotlin port adapters only convert types: values, nulls, enums and bytes must survive the trip both ways.
@MainActor
final class CorePortsTests: XCTestCase {
    func testLocationSampleMapsEveryFieldAndKeepsNullsNull() {
        let swift = DPSharedBridge.LocationSample(
            tMonoNs: 7, wallMs: 8, lat: 0.001, lng: 0.002, altM: nil, hAccM: 4, vAccM: nil, speedMps: 12,
            speedAccMps: nil, courseDeg: 90, courseAccDeg: nil)

        let kotlin = KotlinMapping.kotlin(swift)

        XCTAssertEqual(kotlin.tMonoNs, 7)
        XCTAssertEqual(kotlin.wallMs, 8)
        XCTAssertEqual(kotlin.lat, 0.001)
        XCTAssertEqual(kotlin.hAccM?.doubleValue, 4)
        XCTAssertEqual(kotlin.speedMps?.doubleValue, 12)
        XCTAssertEqual(kotlin.courseDeg?.doubleValue, 90)
        XCTAssertNil(kotlin.altM)
        XCTAssertNil(kotlin.vAccM)
        XCTAssertNil(kotlin.speedAccMps)
    }

    func testMotionSampleKeepsUnitsAndAttitudeOrder() {
        let swift = DPSharedBridge.MotionSample(
            tMonoNs: 1, userAccelG: DPSharedBridge.Vec3(x: 0.1, y: 0.2, z: 0.3),
            gravityUp: DPSharedBridge.Vec3(x: 0, y: 0, z: 1), rotRateDps: DPSharedBridge.Vec3(x: 180, y: 0, z: 0),
            attitude: DPSharedBridge.Quat(w: 1, x: 0.1, y: 0.2, z: 0.3))

        let kotlin = KotlinMapping.kotlin(swift)

        XCTAssertEqual(kotlin.userAccelG.y, 0.2)
        XCTAssertEqual(kotlin.gravityUp.z, 1)
        XCTAssertEqual(kotlin.rotRateDps.x, 180)
        XCTAssertEqual(kotlin.attitude?.w, 1)
        XCTAssertEqual(kotlin.attitude?.z, 0.3)
    }

    func testEnumsMapOneToOne() {
        XCTAssertEqual(KotlinMapping.kotlin(DPSharedBridge.WakeKind.parkedExit), .parkedExit)
        XCTAssertEqual(KotlinMapping.kotlin(DPSharedBridge.ActivityType.automotive), .automotive)
        XCTAssertEqual(KotlinMapping.kotlin(DPSharedBridge.ActivityConfidence.medium), .medium)
        XCTAssertEqual(KotlinMapping.kotlin(DPSharedBridge.ThermalState.serious), .serious)
        XCTAssertEqual(KotlinMapping.kotlin(DPSharedBridge.FileResult.unavailable), .unavailable)
        XCTAssertEqual(KotlinMapping.swift(DrivePerfShared.TripFileKind.preview), .preview)
    }

    func testPowerStatusBoxesBattery() {
        let status = KotlinMapping.kotlin(
            DPSharedBridge.PowerStatus(batteryPct: 81, isCharging: true, lowPowerMode: false, thermal: .nominal))
        XCTAssertEqual(status.batteryPct?.intValue, 81)
        XCTAssertNil(
            KotlinMapping.kotlin(
                DPSharedBridge.PowerStatus(batteryPct: nil, isCharging: false, lowPowerMode: true, thermal: .fair)
            ).batteryPct)
    }

    func testBytesRoundTripThroughKotlinByteArray() {
        let data = Data((0..<1000).map { UInt8(truncatingIfNeeded: $0 &* 7) })

        let array = KotlinMapping.kotlin(data)

        XCTAssertEqual(array.size, 1000)
        XCTAssertEqual(KotlinMapping.swift(array), data)
    }

    func testTripFilePortStoresBytesThroughTheKotlinTypes() throws {
        let root = makeTempDirectory()
        defer { try? FileManager.default.removeItem(at: root) }
        let port = KotlinTripFilePort(FileTripStore(root: root))
        let bytes = Data("R0 bytes".utf8)

        XCTAssertEqual(port.append(tripId: "t", kind: .r0, bytes: KotlinMapping.kotlin(bytes)), .ok)
        XCTAssertEqual(port.sync(tripId: "t", kind: .r0), .ok)
        XCTAssertEqual(port.compress(tripId: "t", kind: .r0), .ok)
        let read = try XCTUnwrap(port.read(tripId: "t", kind: .r0))
        XCTAssertEqual(KotlinMapping.swift(read), bytes)
        XCTAssertEqual(port.describe(tripId: "t", kind: .r0, withSha256: true)?.compressed, true)
        XCTAssertEqual(port.tripIds(), ["t"])
        XCTAssertEqual(port.deleteTrip(tripId: "t"), .ok)
        XCTAssertNil(port.read(tripId: "t", kind: .r0))
    }

    func testParkedRegionPortConvertsTheCenter() {
        let spy = SpyParkedRegion()
        let port = KotlinParkedRegionPort(spy)

        port.arm(center: DrivePerfShared.GeoPoint(lat: 0.001, lng: 0.002), radiusM: 120)

        XCTAssertEqual(spy.armed.first?.center, DPSharedBridge.GeoPoint(lat: 0.001, lng: 0.002))
        XCTAssertEqual(spy.armed.first?.radiusM, 120)
    }

    func testClockPortReadsTheSameClock() {
        let port = KotlinClockPort(SystemClock())
        let first = port.monotonicNs()
        XCTAssertGreaterThanOrEqual(port.monotonicNs(), first)
        XCTAssertEqual(Double(port.wallMs()), Date().timeIntervalSince1970 * 1000, accuracy: 1000)
    }
}
