import XCTest

@testable import CaptureCore

final class CaptureCoreTests: XCTestCase {
    private let t0 = Date(timeIntervalSince1970: 1_800_000_000)

    private func fix(_ seconds: Double, lat: Double, accuracy: Double = 5, speed: Double = 10) -> GPSFix {
        GPSFix(
            time: t0.addingTimeInterval(seconds), point: GeoPoint(latitude: lat, longitude: 0), altitude: 0,
            horizontalAccuracy: accuracy, verticalAccuracy: 5, speed: speed, speedAccuracy: 1, course: 0,
            source: .legacy)
    }

    private func observation(_ seconds: Double, _ kind: ActivityKind, _ confidence: ActivityConfidence = .high)
        -> ActivityObservation
    {
        ActivityObservation(start: t0.addingTimeInterval(seconds), kind: kind, confidence: confidence)
    }

    func testHaversineOneDegreeOfLatitude() {
        let distance = GeoPoint(latitude: 0, longitude: 0).distance(to: GeoPoint(latitude: 1, longitude: 0))
        XCTAssertEqual(distance, 111_195, accuracy: 10)
    }

    func testStreamStatsCountsGapsAndRate() {
        var stats = StreamStats(expectedHz: 1, gapThreshold: 3)
        for second in [0.0, 1, 2, 3, 10, 11] { stats.record(at: t0.addingTimeInterval(second)) }
        XCTAssertEqual(stats.count, 6)
        XCTAssertEqual(stats.gapCount, 1)
        XCTAssertEqual(stats.longestGap, 7)
        XCTAssertEqual(stats.delivery, 5.0 / 11.0, accuracy: 0.001)
    }

    func testStartLatencyUsesFirstFixWithin20Metres() {
        var accumulator = TripAccumulator()
        accumulator.ingest(fix(10, lat: 0.001, accuracy: 65))
        accumulator.ingest(fix(12, lat: 0.002, accuracy: 15))
        XCTAssertEqual(accumulator.firstFix?.horizontalAccuracy, 65)
        XCTAssertEqual(accumulator.firstGoodFix?.horizontalAccuracy, 15)

        var record = TripRecord(
            mode: .automatic, wakeTrigger: .significantChange, launchKind: .coldBackground, wakeAt: t0,
            legacyKeeperEnabled: true, lowPowerModeAtStart: false, appStateAtWake: "background", batteryStart: 0.9)
        record.parkingSpot = GeoPoint(latitude: 0, longitude: 0)
        record.motionStartAt = t0.addingTimeInterval(-30)
        record.accumulator = accumulator
        record.batteryEnd = 0.85
        record.endedAt = t0.addingTimeInterval(3600)

        let metrics = TripMetrics(record: record, now: t0)
        XCTAssertEqual(metrics.startLatencyMeters ?? 0, 222.4, accuracy: 0.5)
        XCTAssertEqual(metrics.startLatencySeconds ?? 0, 42, accuracy: 0.01)
        XCTAssertEqual(metrics.firstFixAfterTriggerSeconds ?? 0, 10, accuracy: 0.01)
        XCTAssertEqual(metrics.batteryPercentPerHour ?? 0, 5, accuracy: 0.01)
    }

    func testPoorFixesDoNotAddDistance() {
        var accumulator = TripAccumulator()
        accumulator.ingest(fix(0, lat: 0, accuracy: 10))
        accumulator.ingest(fix(1, lat: 0.5, accuracy: 200))
        XCTAssertEqual(accumulator.distanceMeters, 0)
    }

    func testStationaryJitterAddsNoDistance() {
        var accumulator = TripAccumulator()
        for second in 0..<240 {
            accumulator.ingest(fix(Double(second), lat: second.isMultiple(of: 2) ? 0 : 0.00001))
        }
        XCTAssertEqual(accumulator.distanceMeters, 0)
    }

    func testSustainedSpeedConfirms() {
        var confirmation = DriveConfirmation(startedAt: t0)
        confirmation.observeSpeed(8, at: t0)
        confirmation.observeSpeed(8, at: t0.addingTimeInterval(19))
        XCTAssertEqual(confirmation.decide(now: t0.addingTimeInterval(19)), .pending)
        confirmation.observeSpeed(8, at: t0.addingTimeInterval(20))
        XCTAssertEqual(confirmation.decide(now: t0.addingTimeInterval(20)), .confirmed("sustained speed"))
    }

    func testAutomotivePlusSpeedConfirms() {
        var confirmation = DriveConfirmation(startedAt: t0)
        confirmation.observe(activity: observation(1, .automotive, .medium))
        confirmation.observeSpeed(4, at: t0.addingTimeInterval(2))
        XCTAssertEqual(confirmation.decide(now: t0.addingTimeInterval(2)), .confirmed("automotive plus speed"))
    }

    func testLowConfidenceAutomotiveDoesNotConfirm() {
        var confirmation = DriveConfirmation(startedAt: t0)
        confirmation.observe(activity: observation(1, .automotive, .low))
        confirmation.observeSpeed(4, at: t0.addingTimeInterval(2))
        XCTAssertEqual(confirmation.decide(now: t0.addingTimeInterval(2)), .pending)
    }

    func testWalkingIsRejectedEarly() {
        var confirmation = DriveConfirmation(startedAt: t0)
        confirmation.observe(activity: observation(-300, .walking))
        confirmation.observeSpeed(1.4, at: t0.addingTimeInterval(30))
        XCTAssertEqual(confirmation.decide(now: t0.addingTimeInterval(89)), .pending)
        XCTAssertEqual(confirmation.decide(now: t0.addingTimeInterval(90)), .rejected("activity: walking"))
    }

    func testRunningIsRejectedEarly() {
        var confirmation = DriveConfirmation(startedAt: t0)
        confirmation.observe(activity: observation(0, .running))
        confirmation.observeSpeed(3.5, at: t0.addingTimeInterval(30))
        XCTAssertEqual(confirmation.decide(now: t0.addingTimeInterval(90)), .rejected("activity: running"))
    }

    func testStaleWalkingDoesNotBlockSustainedSpeed() {
        var confirmation = DriveConfirmation(startedAt: t0)
        confirmation.observe(activity: observation(-120, .walking))
        confirmation.observeSpeed(9, at: t0)
        confirmation.observeSpeed(9, at: t0.addingTimeInterval(21))
        XCTAssertEqual(confirmation.decide(now: t0.addingTimeInterval(21)), .confirmed("sustained speed"))
    }

    func testCyclingBlocksSustainedSpeedAndTimesOut() {
        var confirmation = DriveConfirmation(startedAt: t0)
        confirmation.observe(activity: observation(0, .cycling))
        confirmation.observeSpeed(8, at: t0)
        confirmation.observeSpeed(8, at: t0.addingTimeInterval(30))
        XCTAssertEqual(confirmation.decide(now: t0.addingTimeInterval(30)), .pending)
        XCTAssertEqual(confirmation.decide(now: t0.addingTimeInterval(180)), .rejected("timeout, activity: cycling"))
    }

    func testTimeoutWithoutActivity() {
        let confirmation = DriveConfirmation(startedAt: t0)
        XCTAssertEqual(confirmation.decide(now: t0.addingTimeInterval(180)), .rejected("timeout, no driving"))
    }

    func testAutomotiveStartSpansShortStop() {
        let observations = [
            observation(0, .walking),
            observation(100, .automotive),
            observation(200, .stationary),
            observation(260, .automotive),
        ]
        XCTAssertEqual(MotionHistory.automotiveStart(in: observations), t0.addingTimeInterval(100))
    }

    func testAutomotiveStartStopsAtLongStationary() {
        let observations = [
            observation(0, .automotive),
            observation(100, .stationary),
            observation(400, .automotive),
        ]
        XCTAssertEqual(MotionHistory.automotiveStart(in: observations), t0.addingTimeInterval(400))
    }

    func testAutomotiveStartNilWithoutAutomotive() {
        XCTAssertNil(MotionHistory.automotiveStart(in: [observation(0, .walking)]))
    }

    func testTripEndsAfterStopWindow() {
        var detector = TripEndDetector(startedAt: t0, stopWindow: 240)
        detector.observe(speed: 10, activity: nil, at: t0.addingTimeInterval(100))
        detector.observe(speed: 0.2, activity: nil, at: t0.addingTimeInterval(200))
        XCTAssertFalse(detector.hasEnded(now: t0.addingTimeInterval(339)))
        XCTAssertTrue(detector.hasEnded(now: t0.addingTimeInterval(340)))
        XCTAssertEqual(detector.lastMovingAt, t0.addingTimeInterval(100))
    }

    func testRunningAwayDoesNotKeepTripAlive() {
        var detector = TripEndDetector(startedAt: t0, stopWindow: 240)
        detector.observe(speed: 3.2, activity: observation(0, .running), at: t0.addingTimeInterval(200))
        XCTAssertEqual(detector.lastMovingAt, t0)
    }

    func testCSVSkipsTornLastLine() {
        XCTAssertEqual(CSVColumns.rows(csv: "1,2,3\n4,5,6\n7,8", columns: 3), [[1, 2, 3], [4, 5, 6]])
    }

    func testGPXContainsPoints() {
        let table = SampleTable(columns: CSVColumns.gps, rows: [[1_800_000_000, 12.5, 77.5, 900, 5, 5, 1, 1, 0]])
        let gpx = GPXExporter.gpx(name: "t", gps: table)
        XCTAssertTrue(gpx.contains("lat=\"12.5\" lon=\"77.5\""))
        XCTAssertTrue(gpx.contains("Never commit"))
    }

    func testSummaryCarriesNoCoordinates() throws {
        var record = TripRecord(
            mode: .automatic, wakeTrigger: .parkedRegionExit, launchKind: .resumedBackground, wakeAt: t0,
            legacyKeeperEnabled: true, lowPowerModeAtStart: false, appStateAtWake: "background", batteryStart: 0.9)
        record.parkingSpot = GeoPoint(latitude: 12.345678, longitude: 76.54321)
        var accumulator = TripAccumulator()
        accumulator.ingest(fix(1, lat: 12.777777))
        record.accumulator = accumulator
        record.metrics = TripMetrics(record: record, now: t0.addingTimeInterval(60))

        let data = try JSONEncoder().encode(
            SpikeSummary(generatedAt: t0, installs: [], trips: [TripSummary(record)], wakes: []))
        let json = String(decoding: data, as: UTF8.self)
        for forbidden in ["12.345678", "76.54321", "12.777777", "latitude", "longitude", "\"lat\"", "\"lon\""] {
            XCTAssertFalse(json.contains(forbidden), "summary leaked \(forbidden)")
        }
        XCTAssertTrue(json.contains("\"hadParkingSpot\":true"))
    }
}
