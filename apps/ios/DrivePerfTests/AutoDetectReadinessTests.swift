import DPSharedBridge
import XCTest
@testable import DPFeatures

final class AutoDetectReadinessTests: XCTestCase {
    private let healthy = PermissionSnapshot(
        location: .always, preciseLocation: true, motion: .authorized, notifications: .authorized,
        backgroundRefresh: .available)

    func testHealthyPhoneNeedsNothing() {
        XCTAssertEqual(AutoDetectReadiness.reasons(for: healthy), [])
    }

    func testLocationComesFirstAndWhileUsingStillCountsAsPrecise() {
        var snapshot = healthy
        snapshot.location = .whenInUse
        snapshot.motion = .denied
        XCTAssertEqual(AutoDetectReadiness.reasons(for: snapshot), [.locationWhileUsing, .motionOff])

        snapshot.location = .denied
        snapshot.preciseLocation = false
        // No location, so "precise" is not a second problem.
        XCTAssertEqual(AutoDetectReadiness.reasons(for: snapshot), [.locationDenied, .motionOff])
    }

    func testEveryReasonInPriorityOrder() {
        let snapshot = PermissionSnapshot(
            location: .whenInUse, preciseLocation: false, motion: .restricted, notifications: .denied,
            backgroundRefresh: .restricted)
        XCTAssertEqual(
            AutoDetectReadiness.reasons(for: snapshot),
            [.locationWhileUsing, .motionOff, .preciseLocationOff, .backgroundRefreshOff])
    }

    func testMotionNotYetAskedOrUnavailableIsNotAnAttentionReason() {
        var snapshot = healthy
        snapshot.motion = .notDetermined
        XCTAssertEqual(AutoDetectReadiness.reasons(for: snapshot), [])
        snapshot.motion = .unavailable
        XCTAssertEqual(AutoDetectReadiness.reasons(for: snapshot), [])
    }

    func testEveryReasonHasCopy() {
        for reason in AttentionReason.allCases {
            XCTAssertFalse(reason.shortText.isEmpty)
            XCTAssertFalse(reason.explanation.isEmpty)
        }
    }

    @MainActor
    func testStatusRequestStaysPendingUntilConsumed() {
        let requests = CaptureStatusRequests()
        XCTAssertFalse(requests.isPending)
        requests.request()
        XCTAssertTrue(requests.isPending)
        requests.consume()
        XCTAssertFalse(requests.isPending)
    }
}
