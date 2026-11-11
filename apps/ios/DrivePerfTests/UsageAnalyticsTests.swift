import DPSharedBridge
import DrivePerfShared
import XCTest
@testable import DrivePerf

/// DRIVE-70: the consent gate (shared Kotlin) behind the iOS adapter. No product events exist yet; these prove that
/// nothing reaches the vendor sink before the user's choice, and that a decline drops what was held.
@MainActor
final class UsageAnalyticsTests: XCTestCase {
    private final class TestEvent: NSObject, DrivePerfShared.AnalyticsEvent {
        let name: String
        let properties: [String: String] = [:]
        init(_ name: String) { self.name = name }
    }

    private final class RecordingSink: NSObject, DrivePerfShared.AnalyticsSink, @unchecked Sendable {
        private(set) var calls: [String] = []
        func setCollectionEnabled(enabled: Bool) { calls.append("enabled=\(enabled)") }
        func log(event: any DrivePerfShared.AnalyticsEvent) { calls.append("log:\(event.name)") }
        func resetId() { calls.append("reset") }
    }

    private final class RecordingAnalytics: NSObject, DrivePerfShared.Analytics {
        private(set) var calls: [String] = []
        func track(event: any DrivePerfShared.AnalyticsEvent) { calls.append("track:\(event.name)") }
        func setUsageConsent(allowed: Bool) { calls.append("consent=\(allowed)") }
        func resetId() { calls.append("reset") }
    }

    func testNothingReachesTheSinkBeforeTheChoice() {
        let sink = RecordingSink()
        let gate = ConsentGatedAnalytics(sink: sink, bufferLimit: 100)

        gate.track(event: TestEvent("onboarding_started"))

        XCTAssertEqual(sink.calls, [])
    }

    func testDecliningDropsWhatWasHeld() {
        let sink = RecordingSink()
        let gate = ConsentGatedAnalytics(sink: sink, bufferLimit: 100)
        gate.track(event: TestEvent("onboarding_started"))

        gate.setUsageConsent(allowed: false)
        gate.track(event: TestEvent("later"))

        XCTAssertEqual(sink.calls, ["enabled=false"])
    }

    func testGrantingSendsWhatWasHeld() {
        let sink = RecordingSink()
        let gate = ConsentGatedAnalytics(sink: sink, bufferLimit: 100)
        gate.track(event: TestEvent("onboarding_started"))

        gate.setUsageConsent(allowed: true)

        XCTAssertEqual(sink.calls, ["enabled=true", "log:onboarding_started"])
    }

    func testAnAnswerGivenBeforeTheCoreStartedIsAppliedOnAttach() {
        let adapter = CoreUsageAnalytics()
        let core = RecordingAnalytics()

        adapter.setUsageConsent(true)
        XCTAssertEqual(core.calls, [])
        adapter.attach(core)

        XCTAssertEqual(core.calls, ["consent=true"])
    }

    func testChangesAfterAttachGoStraightThrough() {
        let adapter = CoreUsageAnalytics()
        let core = RecordingAnalytics()
        adapter.attach(core)

        adapter.setUsageConsent(false)
        adapter.resetID()

        XCTAssertEqual(core.calls, ["consent=false", "reset"])
    }

    func testStubSinkSendsNothing() {
        let stub = StubAnalyticsSink()

        stub.setCollectionEnabled(enabled: true)
        stub.log(event: TestEvent("a"))
        stub.resetId()
    }
}
