import DPSharedBridge
import DrivePerfShared
import XCTest

@testable import DPFeatures
@testable import DrivePerf

/// The iOS side of S-TRP-02 Edit trip, S-TRP-03 Delete trip (DRIVE-131) and the All drives filters (DRIVE-126 AC3):
/// the Kotlin ↔ Swift mapping and the view model. The presenter's own cases are tested once, in `shared/presentation`
/// jvmTest (`TripEditTest`, `DrivesFiltersTest`).
@MainActor
final class TripEditTests: XCTestCase {
    private var calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/Toronto")!
        calendar.firstWeekday = 2
        return calendar
    }()

    private func ms(_ day: Int, _ hour: Int) -> Int64 {
        Int64(calendar.date(from: DateComponents(year: 2026, month: 11, day: day, hour: hour))!.timeIntervalSince1970 * 1000)
    }

    private func trip(_ id: String, day: Int, kind: DrivePerfShared.TripKind = .driver) -> DrivePerfShared.Trip {
        let start = ms(day, 8)
        return DrivePerfShared.Trip(
            id: id, status: .finalised, kind: kind, source: .auto, sourceDetail: nil, vehicleId: nil, startedAt: start,
            startTzOffsetMin: -300, startEstimated: false, endedAt: KotlinLong(value: start + 600_000), endTzOffsetMin: nil,
            tzId: nil, partOfDay: nil, fromLabel: nil, toLabel: nil, labelOverride: nil, context: nil,
            distanceM: KotlinDouble(value: 5_000), estimatedDistanceM: nil, durationS: KotlinDouble(value: 600), movingS: nil,
            stoppedS: nil, stopCount: nil, avgMovingSpeedMps: nil, maxSpeedMps: nil, maxSpeedAtS: nil, elevGainM: nil,
            elevLossM: nil, elevMinM: nil, elevMaxM: nil, bbox: nil, stops: [], cleanest: nil, quality: nil, interrupted: nil,
            suspect: .none, suspectAnswer: nil, correction: nil, correctedAt: nil, captureMeta: nil, pipelineVersion: nil,
            rawState: .full, createdAt: 1, updatedAt: 1, deletedAt: nil)
    }

    /// A presenter over fixed Kotlin trips (no edit ports, as in the UI-test fixture), seen through the bridge.
    private func bridge(_ trips: [DrivePerfShared.Trip]) async -> CoreDrivesPresenter {
        let presenter = DrivePerfShared.DrivesPresenter.companion.onMain(
            trips: trips, units: .metric,
            format: CoreDrivesFormatter(calendar: calendar, locale: Locale(identifier: "en_CA")),
            clock: KotlinClockPort(WallClock(ms: ms(10, 18))))
        let bridge = CoreDrivesPresenter()
        bridge.attach(presenter)
        while !bridge.state.hasLoaded { await Task.yield() }
        return bridge
    }

    // MARK: Filters through the shared presenter

    func testATypeFilterNarrowsAllDrivesAndShowsItsChip() async {
        let model = TripListViewModel(presenter: await bridge([
            trip("a", day: 10), trip("p", day: 9, kind: .passenger), trip("b", day: 8),
        ]))
        XCTAssertEqual(model.historyFilters, HistoryFilters())
        XCTAssertFalse(model.canEdit, "the fixture has no edit ports")

        let passenger = HistoryFilterKey(group: .type, value: "passenger")
        model.filter(.toggle(passenger))

        XCTAssertEqual(model.sections.flatMap { $0.rows.map(\.id) }, ["p"])
        XCTAssertEqual(model.latest.map(\.id), ["a", "p", "b"], "the Drives tab is not filtered")
        XCTAssertEqual(model.historyFilters.activeCount, 1)
        XCTAssertEqual(model.historyFilters.applied.map(\.label), ["Passenger"])
        XCTAssertEqual(model.historyFilters.applied.first?.accessibilityLabel, "Remove filter Passenger")
        XCTAssertEqual(model.historyFilters.subtitle, "1 drive · filtered")

        model.filter(.openSheet)
        let sheet = try? XCTUnwrap(model.historyFilters.sheet)
        XCTAssertEqual(sheet?.title, "Filter drives")
        XCTAssertEqual(sheet?.sections.map(\.group), [.type, .quality])
        XCTAssertEqual(sheet?.sections.first?.chips.map(\.label),
            ["Driver", "Passenger", "Bus or train", "Taxi or rideshare", "Not a drive"])
        XCTAssertEqual(sheet?.sections.first?.chips.map(\.count), [2, 1, 0, 0, 0])
        XCTAssertEqual(sheet?.showLabel, "Show 1 drive")

        model.filter(.resetDraft)
        model.filter(.apply)
        XCTAssertNil(model.historyFilters.sheet)
        XCTAssertFalse(model.historyFilters.isActive)
        XCTAssertEqual(model.sections.flatMap { $0.rows.map(\.id) }, ["a", "p", "b"])
    }

    // MARK: Swift → Kotlin

    func testEditAndFilterCommandsReachThePresenter() {
        let presenter = StaticDrivesPresenter()
        let model = TripListViewModel(presenter: presenter)
        let car = HistoryFilterKey(group: .car, value: "gti")
        model.edit(.open(tripId: "a", pane: .type))
        model.edit(.pickType(.taxi))
        model.edit(.save)
        model.edit(.askDelete(tripId: "a"))
        model.edit(.confirmDelete)
        model.filter(.toggle(car))
        model.filter(.clear)
        XCTAssertEqual(presenter.actions, [
            .edit(.open(tripId: "a", pane: .type)), .edit(.pickType(.taxi)), .edit(.save), .edit(.askDelete(tripId: "a")),
            .edit(.confirmDelete), .history(.toggle(car)), .history(.clear),
        ])
    }

    func testCommandsMapToTheKotlinIntents() {
        XCTAssertEqual(
            CoreDrivesPresenter.kotlin(.edit(.open(tripId: "a", pane: .car))) as? DrivesIntentEdit,
            DrivesIntentEdit(action: TripEditActionOpen(tripId: "a", page: .car)))
        XCTAssertEqual(
            CoreDrivesPresenter.kotlin(.edit(.pickType(.taxi))) as? DrivesIntentEdit,
            DrivesIntentEdit(action: TripEditActionPickType(type: .taxi)))
        XCTAssertEqual(
            CoreDrivesPresenter.kotlin(.edit(.pickCar(vehicleId: "ioniq"))) as? DrivesIntentEdit,
            DrivesIntentEdit(action: TripEditActionPickCar(vehicleId: "ioniq")))
        XCTAssertEqual(
            CoreDrivesPresenter.kotlin(.edit(.undo)) as? DrivesIntentEdit, DrivesIntentEdit(action: TripEditActionUndo.shared))
        XCTAssertEqual(
            CoreDrivesPresenter.kotlin(.history(.toggleDraft(HistoryFilterKey(group: .quality, value: "good")))) as? DrivesIntentHistory,
            DrivesIntentHistory(action: HistoryFilterActionToggleDraft(key: FilterKey(group: .quality, value: "good"))))
        XCTAssertEqual(
            CoreDrivesPresenter.kotlin(.history(.apply)) as? DrivesIntentHistory,
            DrivesIntentHistory(action: HistoryFilterActionApply.shared))
        XCTAssertEqual(KotlinMapping.kotlin(.taxi), DrivePerfShared.TripCorrection.taxi)
        XCTAssertEqual(KotlinMapping.swift(DrivePerfShared.TripCorrection.taxi), .taxi)
        XCTAssertFalse(DPSharedBridge.TripCorrection.taxi.isNotADrive)
    }

    // MARK: Kotlin → Swift

    func testTheDeleteDialogAndTheDeletedNavigationMap() {
        let dialog = CoreDrivesPresenter.deleteConfirmation(DeleteTripUi(
            tripId: "a", title: "Delete this drive?", message: "This can’t be undone.", tripTitle: "The Commute",
            tripSubtitle: "Tuesday morning · 10 Nov", confirm: "Delete", cancel: "Cancel", deleting: true, error: nil))
        XCTAssertEqual(dialog, DeleteTripConfirmation(
            tripId: "a", title: "Delete this drive?", message: "This can’t be undone.", tripTitle: "The Commute",
            tripSubtitle: "Tuesday morning · 10 Nov", isDeleting: true))
        XCTAssertEqual(
            CoreDrivesPresenter.navigation(DrivesEffectTripDeleted(tripId: "a", message: "Drive deleted")),
            .tripDeleted(tripId: "a", message: "Drive deleted"))
    }

    func testTheViewModelReadsTheEditStateForItsTrip() {
        let form = TripEditForm(tripId: "a", tripTitle: "The Commute", tripSubtitle: "Tuesday morning · 10 Nov",
            tripStats: "5.0 km · 10 min", typeValue: "Driver", typeDetail: "Scored")
        let presenter = StaticDrivesPresenter(state: DrivesScreenState(
            canEdit: true, edit: form, editMessage: TripEditToast(tripId: "a", text: "Marked as passenger · scores updated")))
        let model = TripListViewModel(presenter: presenter)
        XCTAssertTrue(model.canEdit)
        XCTAssertEqual(model.editForm(id: "a"), form)
        XCTAssertNil(model.editForm(id: "b"))
        XCTAssertEqual(model.editToast?.undoLabel, "Undo")
        XCTAssertNil(model.deleteConfirmation)
        XCTAssertNil(model.deletedTrip)
    }
}
