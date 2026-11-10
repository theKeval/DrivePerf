import DPSharedBridge
import XCTest
@testable import DrivePerf

/// §1.5 and §1.6 rules used by the interim recorder.
final class TripRulesTests: XCTestCase {
    private let config = TripRulesConfig()
    private let t0 = Date(timeIntervalSince1970: 1_800_000_000)

    private func activity(_ kind: ActivityKind, _ confidence: ActivityConfidence = .high, at offset: TimeInterval = 0)
        -> ActivitySample
    {
        ActivitySample(startWallMs: Int64((t0.timeIntervalSince1970 + offset) * 1000), kind: kind, confidence: confidence)
    }

    func testSustainedSpeedForTwentySecondsConfirms() {
        var decider = CandidateDecider(wokeAt: t0, config: config)
        for second in 0...20 {
            decider.observe(speedMps: 8, accuracyM: 5, at: t0.addingTimeInterval(TimeInterval(second)))
        }
        XCTAssertEqual(decider.decide(now: t0.addingTimeInterval(20)), .confirmed)
    }

    func testASlowerFixRestartsTheSustainedSpeedWindow() {
        var decider = CandidateDecider(wokeAt: t0, config: config)
        for second in 0...12 { decider.observe(speedMps: 8, accuracyM: 5, at: t0.addingTimeInterval(TimeInterval(second))) }
        decider.observe(speedMps: 5, accuracyM: 5, at: t0.addingTimeInterval(13))
        for second in 14...25 { decider.observe(speedMps: 8, accuracyM: 5, at: t0.addingTimeInterval(TimeInterval(second))) }
        XCTAssertEqual(decider.decide(now: t0.addingTimeInterval(25)), .pending)
    }

    func testAutomotiveActivityPlusFreshSpeedConfirms() {
        var decider = CandidateDecider(wokeAt: t0, config: config)
        decider.observe(activity(.automotive, .medium))
        decider.observe(speedMps: 3, accuracyM: 10, at: t0.addingTimeInterval(2))
        XCTAssertEqual(decider.decide(now: t0.addingTimeInterval(4)), .confirmed)
    }

    func testMotionAloneNeverConfirms() {
        var decider = CandidateDecider(wokeAt: t0, config: config)
        decider.observe(activity(.automotive, .high))
        XCTAssertEqual(decider.decide(now: t0.addingTimeInterval(30)), .pending)
    }

    func testNoGoodFixNeverConfirms() {
        var decider = CandidateDecider(wokeAt: t0, config: config)
        for second in 0...25 {
            decider.observe(speedMps: 10, accuracyM: 80, at: t0.addingTimeInterval(TimeInterval(second)))
        }
        XCTAssertEqual(decider.decide(now: t0.addingTimeInterval(25)), .pending)
    }

    func testCyclingBlocksTheSustainedSpeedRule() {
        var decider = CandidateDecider(wokeAt: t0, config: config)
        decider.observe(activity(.cycling, .high))
        for second in 0...25 { decider.observe(speedMps: 8, accuracyM: 5, at: t0.addingTimeInterval(TimeInterval(second))) }
        XCTAssertEqual(decider.decide(now: t0.addingTimeInterval(25)), .pending)
    }

    func testWalkingIsRejectedOnlyAfterNinetySeconds() {
        var decider = CandidateDecider(wokeAt: t0, config: config)
        decider.observe(activity(.walking, .high))
        decider.observe(speedMps: 1.4, accuracyM: 5, at: t0.addingTimeInterval(5))
        XCTAssertEqual(decider.decide(now: t0.addingTimeInterval(60)), .pending, "Still walking to the car")
        XCTAssertEqual(decider.decide(now: t0.addingTimeInterval(90)), .rejected)
    }

    func testCandidateTimesOutAfterThreeMinutes() {
        let decider = CandidateDecider(wokeAt: t0, config: config)
        XCTAssertEqual(decider.decide(now: t0.addingTimeInterval(179)), .pending)
        XCTAssertEqual(decider.decide(now: t0.addingTimeInterval(180)), .timedOut)
    }

    func testStopDetectorIgnoresWalkingAwayFromTheCar() {
        var stop = StopDetector(startedAt: t0, stopWindowS: 240, movingSpeedMps: 2)
        stop.observe(speedMps: 10, at: t0.addingTimeInterval(10))
        stop.observe(activity(.walking, at: 20))
        stop.observe(speedMps: 2.5, at: t0.addingTimeInterval(30))
        XCTAssertEqual(stop.lastMovingAt, t0.addingTimeInterval(10))
        XCTAssertFalse(stop.hasEnded(now: t0.addingTimeInterval(249)))
        XCTAssertTrue(stop.hasEnded(now: t0.addingTimeInterval(250)))
    }

    func testDistanceIgnoresInaccurateFixesAndJitter() {
        var distance = DistanceAccumulator(config: config)
        func fix(_ north: Double, accuracy: Double) -> LocationSample {
            LocationSample(tMonoNs: 0, wallMs: 0, lat: north / 111_195, lng: 0, hAccM: accuracy)
        }
        distance.ingest(fix(0, accuracy: 5))
        distance.ingest(fix(3, accuracy: 5))  // under 5 m: jitter
        distance.ingest(fix(500, accuracy: 60))  // inaccurate
        distance.ingest(fix(100, accuracy: 5))

        XCTAssertEqual(distance.distanceM, 100, accuracy: 0.5)
        XCTAssertEqual(distance.fixCount, 4)
    }

    func testShortTripsAreKeptWhenManualAndDiscardedWhenAutomatic() {
        XCTAssertEqual(classifyTrip(source: .manual, distanceM: 300, durationS: 600, config: config), .keep(.short))
        XCTAssertEqual(classifyTrip(source: .automatic, distanceM: 300, durationS: 600, config: config), .discard)
        XCTAssertEqual(classifyTrip(source: .automatic, distanceM: 5000, durationS: 100, config: config), .discard)
        XCTAssertEqual(classifyTrip(source: .automatic, distanceM: 5000, durationS: 600, config: config), .keep(.finalised))
    }
}
