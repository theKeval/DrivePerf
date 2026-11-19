import DPSharedBridge
import DrivePerfShared
import XCTest

@testable import DPFeatures
@testable import DrivePerf

/// The iOS side of the shared Drives presenter (DRIVE-217): the Kotlin → Swift mapping of its state, intents and
/// effects, and the iOS date formatting it uses. The presenter's own cases (rows, tiles, banners, S-DQ-01, corrections)
/// are tested once, in `shared/presentation` jvmTest.
@MainActor
final class TripQualityTests: XCTestCase {
    private var calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/Toronto")!
        calendar.firstWeekday = 2
        return calendar
    }()

    /// Wall time in Toronto as epoch milliseconds.
    private func ms(_ day: Int, _ hour: Int, _ minute: Int = 0, month: Int = 11) -> Int64 {
        let date = calendar.date(from: DateComponents(year: 2026, month: month, day: day, hour: hour, minute: minute))!
        return Int64(date.timeIntervalSince1970 * 1000)
    }

    private lazy var format = CoreDrivesFormatter(calendar: calendar, locale: Locale(identifier: "en_CA"))

    /// A presenter over fixed Kotlin trips, seen through the bridge, at Tuesday 10 Nov 2026 18:00.
    private func bridge(_ trips: [DrivePerfShared.Trip]) async -> CoreDrivesPresenter {
        let presenter = DrivePerfShared.DrivesPresenter.companion.onMain(
            trips: trips, units: .metric, format: format, clock: KotlinClockPort(WallClock(ms: ms(10, 18))))
        let bridge = CoreDrivesPresenter()
        bridge.attach(presenter)
        while !bridge.state.hasLoaded { await Task.yield() }
        return bridge
    }

    // MARK: Formatting

    func testFormatterUsesTheUsersCalendarAndLocale() {
        let t = ms(5, 8, 14)
        XCTAssertEqual(format.platform, .ios)
        XCTAssertEqual(format.weekday(epochMs: t), "Thursday")
        XCTAssertEqual(format.month(epochMs: t), "Nov")
        XCTAssertEqual(format.dayOfMonth(epochMs: t), 5)
        XCTAssertEqual(format.hour(epochMs: t), 8)
        XCTAssertTrue(format.time(epochMs: t).hasPrefix("8:14"), format.time(epochMs: t))
        XCTAssertEqual(format.longDate(epochMs: t), "November 5, 2026")
        XCTAssertTrue(format.dayAndMonth(epochMs: t).contains("November"))
        XCTAssertEqual(format.weekStart(epochMs: t), ms(2, 0), "weeks start on the calendar's first weekday (Monday)")
    }

    // MARK: Kotlin state → Swift

    func testRowsAndWeeksComeFromTheSharedPresenter() async {
        let model = TripListViewModel(presenter: await bridge([
            kotlinTrip("a", start: ms(5, 8, 14), minutes: 30, km: 37.94),
            kotlinTrip("b", start: ms(9, 12, 30), minutes: 12, km: 5.5),
            kotlinTrip("older", start: ms(21, 8, month: 10), minutes: 20, km: 10),
        ]))

        XCTAssertEqual(model.latest.map(\.id), ["b", "a", "older"])
        XCTAssertEqual(model.latest[1].title, "Thursday morning")
        XCTAssertTrue(model.latest[1].subtitle.hasPrefix("5 Nov · 8:14"), model.latest[1].subtitle)
        XCTAssertEqual(model.latest[1].distance, "37.9 km")
        XCTAssertEqual(model.latest[1].duration, "30 min")
        XCTAssertEqual(model.latest[1].accessibilityLabel, "Thursday morning, 37.9 km, 30 minutes")
        XCTAssertEqual(model.latest[0].title, "Monday midday")
        XCTAssertEqual(model.sections.map(\.title), ["This week", "Last week", "19–25 Oct"])
        XCTAssertEqual(model.sections.first?.meta, "1 drive")
        XCTAssertEqual(model.countText, "3 drives")
        XCTAssertNotNil(model.sinceText)
        XCTAssertFalse(model.canCorrect)
        XCTAssertEqual(model.corrections.map(\.correction), [.driver, .passenger, .transit, .bike, .walk, .other, .taxi])
    }

    func testDetailAndQualityMapEveryField() async throws {
        let quality = DrivePerfShared.TripQuality(
            badge: .limited, reason: .lowScoredShare, placement: .mounted, imuHz: 50, gnssCoveragePct: 96.6,
            alignmentSettledS: KotlinDouble(value: 40), estimatedStartM: KotlinDouble(value: 429), scoredS: 1_860,
            excludedS: 120, totalS: 1_980,
            spans: [
                DrivePerfShared.TripSegment(kind: .estimated, t0S: 0, t1S: 49, reason: .estimatedStart, labelKey: nil),
                DrivePerfShared.TripSegment(kind: .excluded, t0S: 900, t1S: 1_080, reason: .phoneMoved, labelKey: nil),
            ])
        let bridge = await bridge([
            kotlinTrip("i", start: ms(9, 7), minutes: 33, km: 38.95, status: .interrupted, quality: quality, suspect: .cycling)
        ])
        let model = TripListViewModel(presenter: bridge)
        let row = try XCTUnwrap(model.latest.first)
        XCTAssertEqual(row.quality, .limited)
        XCTAssertEqual(row.qualityWord, "Limited")
        XCTAssertTrue(row.isInterrupted)
        XCTAssertEqual(row.question, .bike)
        XCTAssertTrue(row.note?.hasPrefix("iOS ended DrivePerf in the background. Saved up to") == true)
        XCTAssertTrue(row.accessibilityLabel.hasSuffix("Limited data"))

        XCTAssertNil(model.detail(id: "i"))
        model.open("i")
        let detail = try XCTUnwrap(model.detail(id: "i"), "the sheet opens in the same frame")
        XCTAssertEqual(detail.title, "Monday morning")
        XCTAssertEqual(detail.qualityWord, "Limited")
        XCTAssertEqual(detail.correction, .driver)
        XCTAssertEqual(detail.banners.first?.action, .answer(.bike))
        XCTAssertEqual(detail.banners[1].tone, .attention)
        XCTAssertTrue(detail.banners[1].text.contains("iOS ended DrivePerf in the background."))
        let distance = detail.tiles[0]
        XCTAssertEqual(distance.value, "39.0")
        XCTAssertEqual(distance.accessibilityLabel, "Distance, 39.0 kilometres. Derived.")
        XCTAssertEqual(distance.provenanceMeaning, "Calculated from measured data, the same way every time.")

        model.openQuality("i")
        let sheet = try XCTUnwrap(model.dataQuality(id: "i"))
        XCTAssertEqual(sheet.title, "Limited data")
        XCTAssertEqual(sheet.coverage.map(\.style), [.estimatedStart, .excluded])
        XCTAssertEqual(sheet.coverage[0].to, 49.0 / 1_980, accuracy: 1e-9)
        XCTAssertEqual(sheet.rows.last?.isEstimated, true)
        XCTAssertEqual(sheet.legend.map(\.0), [.observed, .derived, .estimated])
        XCTAssertTrue(sheet.coverageAccessibility.hasPrefix("Drive coverage, 33 minutes."))
        model.closeQuality()
        XCTAssertNil(model.dataQuality(id: "i"))
        XCTAssertNil(model.detail(id: "other"))
    }

    func testReplayIsHandedOverAsARequest() async {
        let model = TripListViewModel(presenter: await bridge([kotlinTrip("t", start: ms(10, 17, 42), minutes: 30, km: 19.9)]))
        let observing = Task { await model.observe() }
        defer { observing.cancel() }
        model.open("t")
        model.requestReplay("t")
        while model.replayRequest == nil { await Task.yield() }
        XCTAssertEqual(
            model.replayRequest,
            TripListViewModel.ReplayRequest(tripId: "t", title: "Tuesday evening", mapSummary: "Map of your drive, 19.9 km."))
        XCTAssertNotNil(model.detail(id: "t"), "the detail stays under Replay")
        model.replayOpened()
        XCTAssertNil(model.replayRequest)
    }

    func testIntentsReachThePresenter() {
        let presenter = StaticDrivesPresenter()
        let model = TripListViewModel(presenter: presenter)
        model.open("a")
        model.openQuality("a")
        model.closeQuality()
        model.answer(tripId: "a", yes: true)
        model.correct(tripId: "a", to: .walk)
        model.requestReplay("a")
        XCTAssertEqual(presenter.actions, [
            .open(tripId: "a"), .openQuality(tripId: "a"), .closeQuality, .answer(tripId: "a", yes: true),
            .correct(tripId: "a", to: .walk), .openReplay(tripId: "a"),
        ])
        XCTAssertEqual(
            CoreDrivesPresenter.kotlin(.correct(tripId: "a", to: .bike)) as? DrivesIntentCorrect,
            DrivesIntentCorrect(tripId: "a", to: .bike))
    }

    func testBeforeTheCoreStartsTheListIsEmpty() {
        let bridge = CoreDrivesPresenter()
        XCTAssertFalse(bridge.state.hasLoaded)
        bridge.dispatch(.open(tripId: "a"))
        XCTAssertNil(bridge.state.detail)
    }

    // MARK: DRIVE-172 provenance catalogue

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

    // MARK: Fixtures

    private func kotlinTrip(
        _ id: String, start: Int64, minutes: Double, km: Double, status: DrivePerfShared.TripStatus = .finalised,
        quality: DrivePerfShared.TripQuality? = nil, suspect: DrivePerfShared.Suspect = .none
    ) -> DrivePerfShared.Trip {
        DrivePerfShared.Trip(
            id: id, status: status, kind: .driver, source: .auto, sourceDetail: nil, vehicleId: nil, startedAt: start,
            startTzOffsetMin: -300, startEstimated: false, endedAt: KotlinLong(value: start + Int64(minutes * 60_000)),
            endTzOffsetMin: nil, tzId: nil, partOfDay: nil, fromLabel: nil, toLabel: nil, labelOverride: nil, context: nil,
            distanceM: KotlinDouble(value: km * 1000), estimatedDistanceM: nil, durationS: KotlinDouble(value: minutes * 60),
            movingS: nil, stoppedS: nil, stopCount: nil, avgMovingSpeedMps: nil, maxSpeedMps: nil, maxSpeedAtS: nil,
            elevGainM: nil, elevLossM: nil, elevMinM: nil, elevMaxM: nil, bbox: nil, stops: [], cleanest: nil,
            quality: quality, interrupted: nil, suspect: suspect, suspectAnswer: nil, correction: nil, correctedAt: nil,
            captureMeta: nil, pipelineVersion: nil, rawState: .full, createdAt: 1, updatedAt: 1, deletedAt: nil)
    }
}

/// A wall clock stopped at [ms].
struct WallClock: ClockBridge {
    let ms: Int64
    func monotonicNs() -> Int64 { 0 }
    func wallMs() -> Int64 { ms }
}
