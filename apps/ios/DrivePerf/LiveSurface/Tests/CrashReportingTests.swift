import DPPlatform
import DPSharedBridge
import Sentry
import XCTest
@testable import DrivePerf

/// DRIVE-196 (F-ANA-03, ADR-007): Sentry starts only with consent and a DSN, carries only allow-listed tags, and no
/// location reaches it.
@MainActor
final class CrashReportingTests: XCTestCase {
    private final class FakeEngine: CrashReportingEngine {
        private(set) var started: [String] = []
        private(set) var stopped = 0
        private(set) var tags: [[String: String]] = []

        func start(dsn: String) { started.append(dsn) }
        func stop() { stopped += 1 }
        func setTags(_ tags: [String: String]) { self.tags.append(tags) }
    }

    private func bundle(dsn: String) throws -> Bundle {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("dsn-\(UUID().uuidString).bundle")
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        let plist: [String: Any] = ["CFBundleIdentifier": "test.\(UUID().uuidString)", "DPSentryDSN": dsn]
        try PropertyListSerialization.data(fromPropertyList: plist, format: .xml, options: 0)
            .write(to: url.appendingPathComponent("Info.plist"))
        return try XCTUnwrap(Bundle(url: url))
    }

    func testNothingStartsWithoutConsentOrWithoutADSN() throws {
        let engine = FakeEngine()
        let reporter = ConsentGatedCrashReporter(bundle: try bundle(dsn: "https://key@o1.ingest.de.sentry.io/1"), engine: engine)
        reporter.apply(consent: false)
        XCTAssertEqual(reporter.status, .off)

        let noDSN = ConsentGatedCrashReporter(bundle: try bundle(dsn: " "), engine: engine)
        noDSN.apply(consent: true)
        XCTAssertEqual(noDSN.status, .notConfigured)
        XCTAssertFalse(noDSN.hasDSN)
        XCTAssertTrue(engine.started.isEmpty)
    }

    func testConsentStartsOnceWithTheTripTagsAndOptOutStops() throws {
        let engine = FakeEngine()
        let reporter = ConsentGatedCrashReporter(bundle: try bundle(dsn: "https://key@o1.ingest.de.sentry.io/1"), engine: engine)
        reporter.updateTrip(state: .recording, isManual: false) // before consent: kept, not sent

        reporter.apply(consent: true)
        reporter.apply(consent: true)
        XCTAssertEqual(reporter.status, .active)
        XCTAssertEqual(engine.started, ["https://key@o1.ingest.de.sentry.io/1"])
        XCTAssertEqual(engine.tags.last?[CrashTags.inTrip], "true")

        reporter.updateTrip(state: .recording, isManual: false) // unchanged: not sent again
        reporter.updateTrip(state: .idle, isManual: false)
        XCTAssertEqual(engine.tags.count, 2)
        XCTAssertEqual(engine.tags.last, [CrashTags.inTrip: "false", CrashTags.captureMode: "none", CrashTags.tripState: "idle"])

        reporter.apply(consent: false)
        XCTAssertEqual(reporter.status, .off)
        XCTAssertEqual(engine.stopped, 1)
    }

    func testTripTagsMatchAndroid() {
        XCTAssertEqual(
            CrashTags.forTrip(state: .paused, isManual: true),
            [CrashTags.inTrip: "true", CrashTags.captureMode: "manual", CrashTags.tripState: "paused"])
        XCTAssertEqual(CrashTags.forTrip(state: .candidate, isManual: false)[CrashTags.captureMode], "auto")
        XCTAssertEqual(CrashTags.allowed, ["in_trip", "capture_mode", "trip_state", "db_state", "app_build"])
    }

    func testTheScrubberRemovesCoordinatePairsOnly() {
        XCTAssertEqual(CoordinateScrubber.scrub("at 45.50170, -73.56730 ok"), "at [location removed] ok")
        XCTAssertTrue(CoordinateScrubber.containsCoordinates("45.5017;-73.5673"))
        XCTAssertFalse(CoordinateScrubber.containsCoordinates("build 0.1.0 (42), 12.5 km"))
    }

    func testBeforeSendKeepsAllowListedTagsAndDropsLocations() throws {
        let event = Sentry.Event(level: .fatal)
        event.message = SentryMessage(formatted: "Fix rejected at 45.50170, -73.56730")
        let exception = Sentry.Exception(value: "Bad sample 45.501701 -73.567301", type: "IllegalStateException")
        event.exceptions = [exception]
        event.tags = ["in_trip": "true", "street": "Main"]
        event.extra = ["where": "45.5017,-73.5673"]
        event.breadcrumbs = [Breadcrumb(level: .info, category: "state"), Breadcrumb(level: .info, category: "fix")]
        event.breadcrumbs?[0].message = "recording -> finishing"
        event.breadcrumbs?[1].message = "near 45.5017, -73.5673"

        let scrubbed = try XCTUnwrap(SentryCrashEngine.scrub(event))
        XCTAssertEqual(scrubbed.message?.formatted, "Fix rejected at [location removed]")
        XCTAssertEqual(scrubbed.exceptions?.first?.value, "Bad sample [location removed]")
        XCTAssertEqual(scrubbed.tags, ["in_trip": "true"])
        XCTAssertNil(scrubbed.extra)
        XCTAssertEqual(scrubbed.breadcrumbs?.map(\.message), ["recording -> finishing"])
    }
}
