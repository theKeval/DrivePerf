import DPSharedBridge
import DrivePerfShared
import XCTest

@testable import DPFeatures
@testable import DrivePerf

/// DRIVE-170 (badge and S-DQ-01), DRIVE-172 (provenance and precision), DRIVE-100 (question and corrections) and
/// DRIVE-109 (interrupted trips) on iOS.
@MainActor
final class TripQualityTests: XCTestCase {
    private var calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/Toronto")!
        return calendar
    }()

    private lazy var presenter = TripPresenter(
        catalog: KotlinMapping.provenanceCatalog(), calendar: calendar, locale: Locale(identifier: "en_CA"))

    private let start = Date(timeIntervalSince1970: 1_762_340_040)

    private func quality(
        _ badge: DPSharedBridge.QualityBadge, reason: String? = nil, imuHz: Double = 50, estimatedStartM: Double? = 429,
        leftOut: [DPSharedBridge.LeftOut] = [], overlays: [DPSharedBridge.TrackOverlay] = []
    ) -> DPSharedBridge.TripQualitySummary {
        DPSharedBridge.TripQualitySummary(
            badge: badge, reason: reason, phone: leftOut.isEmpty ? .mounted : .moved, imuHz: imuHz, gnssCoveragePct: 96.6,
            alignmentSettledS: 40, estimatedStartM: estimatedStartM, scoredS: 1_860, excludedS: 120, totalS: 1_980,
            leftOut: leftOut, overlays: overlays)
    }

    private func trip(
        _ id: String = "t", status: DPSharedBridge.TripStatus = .finalised, quality: DPSharedBridge.TripQualitySummary? = nil,
        kind: DPSharedBridge.TripKind = .driver, correction: DPSharedBridge.TripCorrection? = nil,
        question: DPSharedBridge.TripQuestion? = nil, estimatedM: Double = 0
    ) -> DPSharedBridge.TripSummary {
        DPSharedBridge.TripSummary(
            id: id, source: .auto, status: status, startedAt: start, endedAt: start.addingTimeInterval(1_980),
            distanceM: 38_950, samples: DPSharedBridge.SampleCounts(), kind: kind, correction: correction,
            correctedAt: correction.map { _ in start }, question: question, quality: quality,
            stats: DPSharedBridge.TripStats(movingS: 1_700, stopCount: 2, maxSpeedMps: 28.6, estimatedDistanceM: estimatedM))
    }

    // MARK: DRIVE-172 provenance and precision

    func testCatalogueComesFromTheSharedCore() {
        let catalog = KotlinMapping.provenanceCatalog()
        XCTAssertEqual(catalog.metrics.count, DrivePerfShared.Metric.allCases.count)
        XCTAssertEqual(catalog["distance"]?.format(38_950), "39.0")
        XCTAssertEqual(catalog["longG"]?.format(-0.425), "\u{2212}0.43")
        XCTAssertEqual(catalog["maxSpeed"]?.format(28.6), "103")
        XCTAssertEqual(catalog["estimatedStart"]?.provenance, .estimated)
        XCTAssertEqual(catalog["distance"]?.provenance(includesEstimate: true), .estimated)
        XCTAssertEqual(catalog.legend[.derived], "Calculated from measured data, the same way every time.")
    }

    func testTilesCarryProvenanceAndMarkAnEstimatedDistance() {
        let tiles = presenter.tiles(trip(estimatedM: 429))
        let distance = tiles[0]
        XCTAssertEqual(distance.label, "Distance")
        XCTAssertEqual(distance.value, "39.0")
        XCTAssertEqual(distance.unit, "km")
        XCTAssertEqual(distance.provenance, .estimated)
        XCTAssertTrue(distance.isEstimated)
        XCTAssertEqual(distance.note, "\u{2248}\u{2009}0.4 km estimated")
        XCTAssertEqual(tiles.map(\.label), ["Distance", "Duration", "Moving", "Stops", "Max speed"])
        XCTAssertEqual(tiles[1].value, "33")
        XCTAssertEqual(tiles[4].value, "103")
        XCTAssertEqual(tiles[4].provenance, .derived)
        XCTAssertEqual(presenter.tiles(trip())[0].provenance, .derived)
        XCTAssertEqual(presenter.tiles(trip())[0].accessibilityLabel, "Distance, 39.0 kilometres. Derived.")
    }

    // MARK: DRIVE-170 S-DQ-01

    func testDataQualitySheetShowsEveryAc2Row() throws {
        let q = quality(
            .limited, reason: "lowScoredShare", leftOut: [DPSharedBridge.LeftOut(reason: .phoneMoved, seconds: 180)],
            overlays: [
                DPSharedBridge.TrackOverlay(style: .estimatedStart, t0S: 0, t1S: 49), DPSharedBridge.TrackOverlay(style: .excluded, t0S: 900, t1S: 1_080),
                DPSharedBridge.TrackOverlay(style: .shortGap, t0S: 1_899, t1S: 1_940),
            ])
        let detail = try XCTUnwrap(presenter.dataQuality(trip(quality: q)))

        XCTAssertEqual(detail.title, "Limited data")
        XCTAssertEqual(detail.hero, "Your phone moved around for about 3 minutes, so we left that part out of the score.")
        XCTAssertEqual(detail.rows.map(\.label), [
            "Phone", "Sensor rate", "GPS coverage", "Phone alignment", "Left out of the score", "Estimated start",
        ])
        XCTAssertEqual(detail.rows[0].value, "Moved")
        XCTAssertEqual(detail.rows[1].value, "50")
        XCTAssertEqual(detail.rows[1].provenance, .observed)
        XCTAssertEqual(detail.rows[2].value, "97")
        XCTAssertEqual(detail.rows[2].unit, "%")
        XCTAssertEqual(detail.rows[4].value, "3")
        XCTAssertEqual(detail.rows[4].note, "Phone moved 3 min")
        XCTAssertTrue(detail.rows[4].lowersBadge)
        XCTAssertEqual(detail.rows[5].value, "0.4")
        XCTAssertTrue(detail.rows[5].isEstimated)
        XCTAssertEqual(detail.scoredLine, "Scored 31 of 33 min")
        XCTAssertEqual(detail.coverage.map(\.style), [.estimatedStart, .excluded, .shortGap])
        XCTAssertEqual(detail.coverage[0].to, 49.0 / 1_980, accuracy: 1e-9)
        XCTAssertEqual(
            detail.coverageAccessibility,
            "Drive coverage, 33 minutes. Start estimated. 41 seconds without GPS. 3 minutes left out of the score.")
        XCTAssertEqual(detail.legend.map(\.0), [.observed, .derived, .estimated])
        XCTAssertNotNil(detail.estimatedStart)
    }

    func testEachBadgeHasItsOwnSentence() throws {
        XCTAssertTrue(try XCTUnwrap(presenter.dataQuality(trip(quality: quality(.good)))).hero.hasPrefix("Everything"))
        let usable = presenter.dataQuality(trip(quality: quality(.usable, overlays: [DPSharedBridge.TrackOverlay(style: .shortGap, t0S: 10, t1S: 51)])))
        XCTAssertEqual(usable?.hero, "Mostly solid, with 41 s without GPS. The score is fair; that stretch is drawn dotted.")
        XCTAssertTrue(presenter.dataQuality(trip(quality: quality(.usable, imuHz: 25)))?.hero.hasPrefix("Battery saver: sensors at 25 Hz") == true)
        XCTAssertEqual(
            presenter.dataQuality(trip(quality: quality(.unscorable, reason: "noImu")))?.hero,
            "We couldn’t score this one fairly. No motion sensor data reached the app.")
        XCTAssertNil(presenter.dataQuality(trip()))
    }

    // MARK: DRIVE-100 and DRIVE-109 banners and rows

    func testBannersAskTheQuestionAndExplainInterruptions() {
        let asked = presenter.banners(trip(question: .transit))
        XCTAssertEqual(asked.first?.action, .answer(.transit))

        let interrupted = presenter.banners(trip(status: .interrupted))
        XCTAssertEqual(interrupted.first?.tone, .attention)
        XCTAssertTrue(interrupted.first?.text.contains("The missing part is shown as a gap, never scored.") == true)

        let bus = presenter.banners(trip(kind: .transit, correction: .transit))
        XCTAssertEqual(bus.first?.action, .restore)
        XCTAssertTrue(bus.first?.text.contains("deleted on December 5, 2025") == true, bus.first?.text ?? "")
    }

    func testListRowsCarryTheBadgeTagsAndQuestion() async {
        let recorder = FakeRecorder()
        recorder.tripList = [
            trip("a", status: .interrupted, quality: quality(.usable)), trip("b", kind: .passenger),
            trip("c", question: .bike),
        ]
        let model = TripListViewModel(history: recorder, calendar: calendar, locale: Locale(identifier: "en_CA"))
        let task = Task { await model.observe() }
        while !model.hasLoaded { await Task.yield() }
        task.cancel()

        let rows = Dictionary(uniqueKeysWithValues: model.latest.map { ($0.id, $0) })
        XCTAssertEqual(rows["a"]?.quality, .usable)
        XCTAssertEqual(rows["a"]?.isInterrupted, true)
        XCTAssertTrue(rows["a"]?.accessibilityLabel.hasSuffix("Usable data") == true)
        XCTAssertEqual(rows["b"]?.kindLabel, "Passenger")
        XCTAssertEqual(rows["b"]?.isDimmed, true)
        XCTAssertEqual(rows["c"]?.question, .bike)
        XCTAssertFalse(model.canCorrect)
    }

    func testAnswersAndCorrectionsGoToTheCore() async {
        let recorder = FakeRecorder()
        let corrections = FakeCorrections()
        corrections.list = [trip("bus", kind: .transit, correction: .transit)]
        let model = TripListViewModel(history: recorder, corrections: corrections)
        XCTAssertTrue(model.canCorrect)

        await model.answer(tripId: "t1", yes: true)
        await model.correct(tripId: "t2", to: .walk)
        XCTAssertEqual(corrections.answers.map(\.0), ["t1"])
        XCTAssertEqual(corrections.answers.map(\.1), [true])
        XCTAssertEqual(corrections.corrections.map(\.1), [.walk])

        let task = Task { await model.observeNotDrives() }
        while model.notDrives.isEmpty { await Task.yield() }
        task.cancel()
        XCTAssertEqual(model.notDriveRows.first?.kindLabel, "Bus or train")
        XCTAssertEqual(model.detail(id: "bus")?.correction, .transit)
    }

    // MARK: Kotlin → Swift

    func testKotlinTripsMapQualityCorrectionsAndQuestion() throws {
        let spans = [
            DrivePerfShared.TripSegment(kind: .estimated, t0S: 0, t1S: 49, reason: .estimatedStart, labelKey: nil),
            DrivePerfShared.TripSegment(kind: .excluded, t0S: 100, t1S: 160, reason: .phoneMoved, labelKey: nil),
            DrivePerfShared.TripSegment(kind: .gap, t0S: 400, t1S: 640, reason: .noGnss, labelKey: "gap.labelled"),
        ]
        let kq = DrivePerfShared.TripQuality(
            badge: .limited, reason: .lowScoredShare, placement: .mounted, imuHz: 50, gnssCoveragePct: 90,
            alignmentSettledS: KotlinDouble(value: 40), estimatedStartM: KotlinDouble(value: 429), scoredS: 500,
            excludedS: 109, totalS: 700, spans: spans)
        let kt = DrivePerfShared.Trip(
            id: "k", status: .finalised, kind: .driver, source: .auto, sourceDetail: nil, vehicleId: nil,
            startedAt: 1_000_000, startTzOffsetMin: 0, startEstimated: true, endedAt: KotlinLong(value: 1_700_000),
            endTzOffsetMin: nil, tzId: nil, partOfDay: nil, fromLabel: nil, toLabel: nil, labelOverride: nil,
            context: nil, distanceM: KotlinDouble(value: 9_000), estimatedDistanceM: KotlinDouble(value: 429),
            durationS: KotlinDouble(value: 700), movingS: nil, stoppedS: nil, stopCount: KotlinInt(value: 3),
            avgMovingSpeedMps: nil, maxSpeedMps: nil, maxSpeedAtS: nil, elevGainM: nil, elevLossM: nil,
            elevMinM: nil, elevMaxM: nil, bbox: nil, stops: [], cleanest: nil, quality: kq, interrupted: nil,
            suspect: .transit, suspectAnswer: nil, correction: nil, correctedAt: nil, captureMeta: nil,
            pipelineVersion: nil, rawState: .full, createdAt: 1, updatedAt: 1, deletedAt: nil)

        let s = try XCTUnwrap(KotlinMapping.summary(kt))
        XCTAssertEqual(s.question, .transit)
        XCTAssertEqual(s.kind, .driver)
        XCTAssertEqual(s.stats.stopCount, 3)
        XCTAssertEqual(s.stats.estimatedDistanceM, 429)
        XCTAssertTrue(s.stats.startEstimated)
        let q = try XCTUnwrap(s.quality)
        XCTAssertEqual(q.badge, .limited)
        XCTAssertEqual(q.reason, "lowScoredShare")
        XCTAssertEqual(q.phone, .moved)
        XCTAssertEqual(q.leftOut, [DPSharedBridge.LeftOut(reason: .phoneMoved, seconds: 60)])
        XCTAssertEqual(q.overlays.map(\.style), [.estimatedStart, .excluded, .longGap])
    }
}

@MainActor
final class FakeCorrections: DPSharedBridge.TripCorrecting {
    var list: [DPSharedBridge.TripSummary] = []
    private(set) var answers: [(String, Bool)] = []
    private(set) var corrections: [(String, DPSharedBridge.TripCorrection)] = []

    func observeNotDrives() -> AsyncStream<[DPSharedBridge.TripSummary]> {
        AsyncStream { $0.yield(list) }
    }

    func answer(tripId: String, yes: Bool) async { answers.append((tripId, yes)) }

    func correct(tripId: String, to correction: DPSharedBridge.TripCorrection) async {
        corrections.append((tripId, correction))
    }
}
