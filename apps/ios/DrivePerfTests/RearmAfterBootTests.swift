import DPSharedBridge
import Foundation
import UserNotifications
import XCTest
@testable import DPPlatform

/// DRIVE-222: after a restart iOS starts background location only once the app has been opened, so the first
/// background wake per boot asks the user to open DrivePerf.
final class OpenedSinceBootTests: XCTestCase {
    private let boot = Date(timeIntervalSince1970: 1_800_000_000)
    private var defaults: UserDefaults!
    private var suite: String!

    override func setUp() {
        super.setUp()
        suite = "dp.test.openedSinceBoot.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: suite)
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suite)
        super.tearDown()
    }

    func testNothingRecordedIsUnknownAndNeverAsks() {
        let opened = OpenedSinceBoot(defaults: defaults)
        XCTAssertEqual(opened.state(bootTime: boot), .unknown)
        XCTAssertFalse(opened.claimNotice(bootTime: boot), "the first run of this build is not evidence of a restart")
    }

    func testForegroundDuringThisBootIsOpened() {
        let opened = OpenedSinceBoot(defaults: defaults)
        opened.recordForeground(bootTime: boot)
        // The kernel's boot time drifts by milliseconds between reads.
        XCTAssertEqual(opened.state(bootTime: boot.addingTimeInterval(0.004)), .opened)
        XCTAssertFalse(opened.claimNotice(bootTime: boot))
    }

    func testARestartWithoutAForegroundIsNotOpened() {
        let opened = OpenedSinceBoot(defaults: defaults)
        opened.recordForeground(bootTime: boot)
        let restart = boot.addingTimeInterval(3 * 86_400)
        XCTAssertEqual(opened.state(bootTime: restart), .notOpened)
    }

    func testTheNoticeIsClaimedOncePerBoot() {
        let opened = OpenedSinceBoot(defaults: defaults)
        opened.recordForeground(bootTime: boot)
        let restart = boot.addingTimeInterval(86_400)
        XCTAssertTrue(opened.claimNotice(bootTime: restart))
        XCTAssertFalse(opened.claimNotice(bootTime: restart.addingTimeInterval(0.003)), "every later wake stays quiet")
        let another = restart.addingTimeInterval(86_400)
        XCTAssertTrue(opened.claimNotice(bootTime: another), "the next restart asks again")
    }

    func testOpeningTheAppEndsTheNotOpenedState() {
        let opened = OpenedSinceBoot(defaults: defaults)
        opened.recordForeground(bootTime: boot)
        let restart = boot.addingTimeInterval(86_400)
        XCTAssertTrue(opened.claimNotice(bootTime: restart))
        opened.recordForeground(bootTime: restart)
        XCTAssertEqual(opened.state(bootTime: restart), .opened)
    }

    private func status(
        _ state: RecorderState, health: AutoDetectHealth = .on, manual: Bool = false
    ) -> RecorderStatus {
        RecorderStatus(
            state: state, tripId: state == .idle ? nil : "t1", isManual: manual, elapsedS: 0, distanceM: 0,
            autoDetectEnabled: health != .off, autoDetectHealth: health)
    }

    func testOnlyAnAutomaticCandidateInTheBackgroundAsks() {
        XCTAssertTrue(OpenedSinceBoot.shouldAsk(runState: .background, status: status(.candidate)))
        XCTAssertTrue(
            OpenedSinceBoot.shouldAsk(runState: .background, status: status(.candidate, health: .needsAttention)))
        XCTAssertFalse(
            OpenedSinceBoot.shouldAsk(runState: .active, status: status(.candidate)), "in use: location can start")
        XCTAssertFalse(
            OpenedSinceBoot.shouldAsk(runState: .inactive, status: status(.candidate)), "a user launch is in use")
        XCTAssertFalse(
            OpenedSinceBoot.shouldAsk(runState: .background, status: status(.recording, manual: true)),
            "a manual start is not auto-detect")
    }

    /// Codex P2 on #96: paused, then a restart, then a wake before the core has started. The recorder's status is the
    /// default `.idle` / `.on` then; the restored pause keeps the core idle, so nothing asks and the once-per-boot
    /// notice is not used up.
    func testAPausedUserIsNotAskedAfterARestartEvenBeforeTheCoreStarts() {
        let opened = OpenedSinceBoot(defaults: defaults)
        opened.recordForeground(bootTime: boot)
        let restart = boot.addingTimeInterval(86_400)
        let beforeCore = RecorderStatus.idle
        XCTAssertEqual(beforeCore.autoDetectHealth, .on, "the default the wake sees before the core attaches")
        XCTAssertFalse(OpenedSinceBoot.shouldAsk(runState: .background, status: beforeCore))
        let restoredPause = status(.idle, health: .paused)
        XCTAssertFalse(OpenedSinceBoot.shouldAsk(runState: .background, status: restoredPause))
        XCTAssertFalse(
            OpenedSinceBoot.shouldAsk(runState: .background, status: status(.candidate, health: .paused)))
        XCTAssertFalse(OpenedSinceBoot.shouldAsk(runState: .background, status: status(.idle, health: .off)))
        XCTAssertTrue(opened.claimNotice(bootTime: restart), "the slot for this boot is still free")
    }

    func testTheNoticeReusesTheWatchdogCopyUnderItsOwnIdentifier() {
        let request = NotificationRearmNotice.request()
        XCTAssertEqual(request.content.title, NotificationWatchdog.title)
        XCTAssertEqual(request.content.body, NotificationWatchdog.body)
        XCTAssertEqual(request.content.categoryIdentifier, NotificationWatchdog.category)
        XCTAssertNotEqual(
            request.identifier, NotificationWatchdog.identifier,
            "a watchdog check-in removes delivered watchdog notices and must not hide this one")
        XCTAssertNil(request.trigger, "delivered at once")
        XCTAssertNil(request.content.sound, "it arrives as a drive starts")
    }
}

/// DRIVE-222: session renewal and the location diagnostics log.
final class LocationDiagnosticsTests: XCTestCase {
    func testASessionMadeInTheBackgroundIsReplacedOnceTheAppIsInUse() {
        func needs(_ has: Bool, _ wants: Bool, _ always: Bool, _ createdInUse: Bool, _ inUse: Bool) -> Bool {
            LocationServiceSessions.needsNewSession(
                hasSession: has, wantsAlways: wants, always: always, createdInUse: createdInUse, inUse: inUse)
        }
        XCTAssertTrue(needs(false, false, true, false, false), "none held yet")
        XCTAssertTrue(needs(true, false, true, true, true), "the authorization changed")
        XCTAssertTrue(needs(true, true, true, false, true), "made in the background, now in use")
        XCTAssertFalse(needs(true, true, true, false, false), "still in the background: nothing better to make")
        XCTAssertFalse(needs(true, true, true, true, true), "made in use already")
        XCTAssertFalse(needs(true, true, true, true, false))
    }

    func testDiagnosticFlagsBecomeOneSortedKey() {
        XCTAssertEqual(LocationDiagnosticKey.key(["insufficientlyInUse": false, "authorizationDenied": false]), "none")
        XCTAssertEqual(
            LocationDiagnosticKey.key(["insufficientlyInUse": true, "authorizationDenied": true, "x": false]),
            "authorizationDenied,insufficientlyInUse")
    }

    func testRunStateInUse() {
        XCTAssertTrue(AppRunState.active.isInUse)
        XCTAssertTrue(AppRunState.inactive.isInUse)
        XCTAssertFalse(AppRunState.background.isInUse)
    }

    private func makeDirectory() -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("dp-diag-\(UUID().uuidString)")
        addTeardownBlock { try? FileManager.default.removeItem(at: url) }
        return url
    }

    func testEntriesAreJSONLinesWithTimeEventAndFields() throws {
        let now = Date(timeIntervalSince1970: 1_800_000_000.25)
        let log = LocationDiagnosticsLog(directory: makeDirectory(), now: { now })
        log.record("launch", ["app": "background", "openedSinceBoot": "notOpened"])
        log.record("rearm_notice_posted")
        let entries = log.entries()
        XCTAssertEqual(entries.count, 2)
        XCTAssertEqual(entries[0]["event"] as? String, "launch")
        XCTAssertEqual(entries[0]["app"] as? String, "background")
        XCTAssertEqual(entries[0]["openedSinceBoot"] as? String, "notOpened")
        XCTAssertEqual((entries[0]["t"] as? NSNumber)?.int64Value, 1_800_000_000_250)
        XCTAssertEqual(entries[1]["event"] as? String, "rearm_notice_posted")
        let text = try String(contentsOf: log.url, encoding: .utf8)
        XCTAssertEqual(text.split(separator: "\n").count, 2)
        XCTAssertEqual(log.url.lastPathComponent, "location-diagnostics.jsonl")
    }

    func testTheLogDropsItsOlderHalfPastTheCap() throws {
        let log = LocationDiagnosticsLog(directory: makeDirectory(), maxBytes: 4_096, now: { Date() })
        for index in 0..<200 { log.record("wake", ["n": String(index)]) }
        let size = try XCTUnwrap(FileManager.default.attributesOfItem(atPath: log.url.path)[.size] as? Int)
        XCTAssertLessThanOrEqual(size, 4_096)
        let entries = log.entries()
        XCTAssertEqual(entries.last?["n"] as? String, "199", "the newest line is always kept")
        XCTAssertGreaterThan(entries.count, 10)
        let numbers = entries.compactMap { ($0["n"] as? String).flatMap(Int.init) }
        XCTAssertEqual(numbers, numbers.sorted(), "lines stay in order")
    }

    func testTheLogIsLeftOutOfBackups() throws {
        let log = LocationDiagnosticsLog(directory: makeDirectory())
        log.record("launch")
        let values = try log.url.resourceValues(forKeys: [.isExcludedFromBackupKey])
        XCTAssertEqual(values.isExcludedFromBackup, true)
    }
}
