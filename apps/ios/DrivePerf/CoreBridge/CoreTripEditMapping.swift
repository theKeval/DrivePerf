import DPSharedBridge
import DrivePerfShared
import Foundation

// Kotlin ↔ Swift for S-TRP-02 Edit trip, S-TRP-03 Delete trip (DRIVE-131) and the All drives filters (DRIVE-126 AC3),
// kept out of `CoreDrivesPresenter.swift`, which only calls these.

extension CoreDrivesPresenter {
    // MARK: Kotlin → Swift

    static func editForm(_ e: TripEditUi) -> TripEditForm {
        TripEditForm(
            tripId: e.tripId, pane: pane(e.page), title: e.title, tripTitle: e.tripTitle, tripSubtitle: e.tripSubtitle,
            tripStats: e.tripStats, score: e.score.map(badge), kind: kind(e.type), typeValue: e.typeValue,
            typeDetail: e.typeDetail,
            types: e.types.map {
                TripTypeOption(
                    kind: kind($0.type), title: $0.title, detail: $0.detail, isSelected: $0.selected, isCurrent: $0.current,
                    accessibilityLabel: $0.accessibilityLabel)
            },
            typeBanner: e.typeBanner, typeFooter: e.typeFooter, carValue: e.carValue, carDetail: e.carDetail,
            cars: e.cars.map {
                CarOption(
                    id: $0.id, name: $0.name, detail: $0.detail, isDefault: $0.isDefault, isSelected: $0.selected,
                    isCurrent: $0.current, accessibilityLabel: $0.accessibilityLabel)
            },
            carNote: e.carNote, changedBanner: e.changedBanner, isChanged: e.changed, caption: e.caption,
            deleteLabel: e.deleteLabel)
    }

    static func deleteConfirmation(_ d: DeleteTripUi) -> DeleteTripConfirmation {
        DeleteTripConfirmation(
            tripId: d.tripId, title: d.title, message: d.message, tripTitle: d.tripTitle, tripSubtitle: d.tripSubtitle,
            confirm: d.confirm, cancel: d.cancel, isDeleting: d.deleting, error: d.error)
    }

    static func history(_ h: HistoryFilterUi) -> HistoryFilters {
        HistoryFilters(
            activeCount: Int(h.activeCount), buttonLabel: h.buttonLabel, buttonAccessibilityLabel: h.buttonAccessibilityLabel,
            applied: h.applied.map(chip), suggestions: h.suggestions.map(chip), clearLabel: h.clearLabel, subtitle: h.subtitle,
            emptyTitle: h.emptyTitle, emptyBody: h.emptyBody,
            sheet: h.sheet.map { sheet in
                HistoryFilterSheet(
                    title: sheet.title, subtitle: sheet.subtitle,
                    sections: sheet.groups.map { HistoryFilterSection(group: group($0.group), title: $0.title, chips: $0.chips.map(chip)) },
                    showLabel: sheet.showLabel, canShow: sheet.canShow, resetLabel: sheet.resetLabel, canReset: sheet.canReset)
            })
    }

    static func chip(_ c: FilterChipUi) -> HistoryFilterChip {
        HistoryFilterChip(
            key: key(c.key), label: c.label, count: Int(c.count), isSelected: c.selected,
            quality: c.quality.map(KotlinMapping.swift), accessibilityLabel: c.accessibilityLabel)
    }

    static func key(_ k: FilterKey) -> HistoryFilterKey {
        HistoryFilterKey(group: group(k.group), value: k.value)
    }

    static func group(_ g: DrivePerfShared.FilterGroup) -> HistoryFilterGroup {
        switch g {
        case .type: .type
        case .car: .car
        case .route: .route
        case .quality: .quality
        }
    }

    static func kind(_ t: DrivePerfShared.TripEditType) -> TripEditKind {
        switch t {
        case .driver: .driver
        case .passenger: .passenger
        case .transit: .transit
        case .taxi: .taxi
        case .other: .other
        }
    }

    static func pane(_ p: DrivePerfShared.TripEditPage) -> TripEditPane {
        switch p {
        case .main: .main
        case .type: .type
        case .car: .car
        }
    }

    // MARK: Swift → Kotlin

    static func kotlin(_ command: TripEditCommand) -> any DrivePerfShared.TripEditAction {
        switch command {
        case .open(let tripId, let pane): TripEditActionOpen(tripId: tripId, page: kotlin(pane))
        case .showPane(let pane): TripEditActionShowPage(page: kotlin(pane))
        case .pickType(let kind): TripEditActionPickType(type: kotlin(kind))
        case .pickCar(let vehicleId): TripEditActionPickCar(vehicleId: vehicleId)
        case .save: TripEditActionSave.shared
        case .close: TripEditActionClose.shared
        case .undo: TripEditActionUndo.shared
        case .dismissMessage: TripEditActionDismissMessage.shared
        case .askDelete(let tripId): TripEditActionAskDelete(tripId: tripId)
        case .cancelDelete: TripEditActionCancelDelete.shared
        case .confirmDelete: TripEditActionConfirmDelete.shared
        }
    }

    static func kotlin(_ command: HistoryFilterCommand) -> any DrivePerfShared.HistoryFilterAction {
        switch command {
        case .openSheet: HistoryFilterActionOpenSheet.shared
        case .toggleDraft(let key): HistoryFilterActionToggleDraft(key: kotlin(key))
        case .resetDraft: HistoryFilterActionResetDraft.shared
        case .apply: HistoryFilterActionApply.shared
        case .closeSheet: HistoryFilterActionCloseSheet.shared
        case .toggle(let key): HistoryFilterActionToggle(key: kotlin(key))
        case .clear: HistoryFilterActionClear.shared
        }
    }

    static func kotlin(_ key: HistoryFilterKey) -> FilterKey {
        let group: DrivePerfShared.FilterGroup =
            switch key.group {
            case .type: .type
            case .car: .car
            case .route: .route
            case .quality: .quality
            }
        return FilterKey(group: group, value: key.value)
    }

    static func kotlin(_ pane: TripEditPane) -> DrivePerfShared.TripEditPage {
        switch pane {
        case .main: .main
        case .type: .type
        case .car: .car
        }
    }

    static func kotlin(_ kind: TripEditKind) -> DrivePerfShared.TripEditType {
        switch kind {
        case .driver: .driver
        case .passenger: .passenger
        case .transit: .transit
        case .taxi: .taxi
        case .other: .other
        }
    }
}
