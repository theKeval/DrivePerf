import CoreLocation
import DPPlatform
import DPSharedBridge
import DrivePerfShared
import XCTest

@testable import DrivePerf

/// DRIVE-167 (F-DQ-03): every fix carries its provider and age, alongside the accuracy it already had.
final class FixMetadataTests: XCTestCase {
    private func fix(age: TimeInterval, simulated: Bool = false, accessory: Bool = false) -> CLLocation {
        CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 0.001, longitude: 0.002), altitude: 10, horizontalAccuracy: 4,
            verticalAccuracy: 3, course: 90, courseAccuracy: 5, speed: 12, speedAccuracy: 0.5,
            timestamp: Date().addingTimeInterval(-age),
            sourceInfo: CLLocationSourceInformation(
                softwareSimulationState: simulated, andExternalAccessoryState: accessory))
    }

    func testAgeAndProviderAreStoredWithTheFix() throws {
        let sample = try XCTUnwrap(LocationMapping.sample(from: fix(age: 2), lastAccepted: nil, clock: SystemClock()))

        XCTAssertEqual(sample.provider, .fused)
        XCTAssertEqual(Double(sample.ageMs ?? -1), 2_000, accuracy: 100)
        XCTAssertEqual(sample.hAccM, 4)
    }

    func testSimulatedAndAccessoryFixesAreToldApart() {
        XCTAssertEqual(LocationMapping.provider(of: fix(age: 0, simulated: true)), .simulated)
        XCTAssertEqual(LocationMapping.provider(of: fix(age: 0, accessory: true)), .accessory)
        let plain = CLLocation(latitude: 0, longitude: 0)
        XCTAssertEqual(LocationMapping.provider(of: plain), .fused)
    }

    @MainActor
    func testProviderAndAgeCrossToKotlin() {
        let swift = DPSharedBridge.LocationSample(
            tMonoNs: 1, wallMs: 2, lat: 0, lng: 0, hAccM: 4, provider: .accessory, ageMs: 1_250)
        let kotlin = KotlinMapping.kotlin(swift)
        XCTAssertEqual(kotlin.provider, .accessory)
        XCTAssertEqual(kotlin.ageMs?.int64Value, 1_250)
        XCTAssertEqual(kotlin.hAccM?.doubleValue, 4)

        let bare = KotlinMapping.kotlin(DPSharedBridge.LocationSample(tMonoNs: 1, wallMs: 2, lat: 0, lng: 0))
        XCTAssertNil(bare.provider)
        XCTAssertNil(bare.ageMs)
        for p in [DPSharedBridge.FixProvider.gnss, .fused, .network, .accessory, .simulated] {
            XCTAssertEqual(KotlinMapping.kotlin(p).name.lowercased(), p.rawValue)
        }
    }

    func testAFixFromTheFutureHasNoAge() {
        let future = fix(age: -5)
        XCTAssertNil(LocationMapping.ageMs(of: future, now: Date().wallMs))
        XCTAssertEqual(LocationMapping.ageMs(of: fix(age: 0), now: Date().wallMs).map { $0 < 100 }, true)
    }
}
