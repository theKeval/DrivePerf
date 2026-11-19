import Foundation

// Swift mirrors of S-TRP-02 Edit trip, S-TRP-03 Delete trip (DRIVE-131) and the All drives filters (DRIVE-126 AC3) in
// the shared Drives presenter (`TripEdit.kt`, `DrivesFilters.kt`). The names differ from the Kotlin ones so the app
// target, which sees both, needs no qualification. Everything is already formatted by the core.

// MARK: - S-TRP-02 Edit trip

/// Kotlin `TripEditType`: the five answers of the type picker.
public enum TripEditKind: String, Equatable, Sendable, CaseIterable {
    case driver, passenger, transit, taxi, other
}

/// Kotlin `TripEditPage`: the sheet's fields, or one of the pickers pushed inside it.
public enum TripEditPane: Equatable, Sendable {
    case main, type, car
}

/// One row of the type picker: "Passenger" with "Not scored. Stays in your history."
public struct TripTypeOption: Identifiable, Equatable, Sendable {
    public var id: TripEditKind { kind }
    public var kind: TripEditKind
    public var title: String
    public var detail: String
    public var isSelected: Bool
    /// The saved type ("Current" tag).
    public var isCurrent: Bool
    public var accessibilityLabel: String

    public init(kind: TripEditKind, title: String, detail: String, isSelected: Bool, isCurrent: Bool, accessibilityLabel: String) {
        self.kind = kind
        self.title = title
        self.detail = detail
        self.isSelected = isSelected
        self.isCurrent = isCurrent
        self.accessibilityLabel = accessibilityLabel
    }
}

/// One row of the car picker: "GTI" with "2019 Volkswagen Golf".
public struct CarOption: Identifiable, Equatable, Sendable {
    public var id: String
    public var name: String
    public var detail: String?
    /// "Default" tag.
    public var isDefault: Bool
    public var isSelected: Bool
    public var isCurrent: Bool
    public var accessibilityLabel: String

    public init(
        id: String, name: String, detail: String?, isDefault: Bool, isSelected: Bool, isCurrent: Bool, accessibilityLabel: String
    ) {
        self.id = id
        self.name = name
        self.detail = detail
        self.isDefault = isDefault
        self.isSelected = isSelected
        self.isCurrent = isCurrent
        self.accessibilityLabel = accessibilityLabel
    }
}

/// Kotlin `TripEditUi`: what S-TRP-02 draws.
public struct TripEditForm: Equatable, Sendable {
    public var tripId: String
    public var pane: TripEditPane
    /// "Edit trip"
    public var title: String
    public var tripTitle: String
    public var tripSubtitle: String
    /// "37.9 km · 30 min"
    public var tripStats: String
    public var score: ScoreBadge?
    public var kind: TripEditKind
    /// "Passenger"
    public var typeValue: String
    /// "Not scored"
    public var typeDetail: String
    public var types: [TripTypeOption]
    /// "Passenger: not a drive you made. …", while a type other than Driver is chosen.
    public var typeBanner: String?
    public var typeFooter: String
    /// "GTI"; nil without a car.
    public var carValue: String?
    public var carDetail: String?
    public var cars: [CarOption]
    public var carNote: String
    /// "Save to apply. …"
    public var changedBanner: String?
    public var isChanged: Bool
    public var caption: String
    /// "Delete drive"
    public var deleteLabel: String

    /// The car row and picker show only when the user has a car.
    public var hasCars: Bool { !cars.isEmpty }

    public init(
        tripId: String, pane: TripEditPane = .main, title: String = "Edit trip", tripTitle: String, tripSubtitle: String,
        tripStats: String, score: ScoreBadge? = nil, kind: TripEditKind = .driver, typeValue: String, typeDetail: String,
        types: [TripTypeOption] = [], typeBanner: String? = nil, typeFooter: String = "", carValue: String? = nil,
        carDetail: String? = nil, cars: [CarOption] = [], carNote: String = "", changedBanner: String? = nil,
        isChanged: Bool = false, caption: String = "", deleteLabel: String = "Delete drive"
    ) {
        self.tripId = tripId
        self.pane = pane
        self.title = title
        self.tripTitle = tripTitle
        self.tripSubtitle = tripSubtitle
        self.tripStats = tripStats
        self.score = score
        self.kind = kind
        self.typeValue = typeValue
        self.typeDetail = typeDetail
        self.types = types
        self.typeBanner = typeBanner
        self.typeFooter = typeFooter
        self.carValue = carValue
        self.carDetail = carDetail
        self.cars = cars
        self.carNote = carNote
        self.changedBanner = changedBanner
        self.isChanged = isChanged
        self.caption = caption
        self.deleteLabel = deleteLabel
    }
}

/// Kotlin `DeleteTripUi`: S-TRP-03, a real, irreversible deletion (F-HIS-02 AC3).
public struct DeleteTripConfirmation: Equatable, Sendable {
    public var tripId: String
    /// "Delete this drive?"
    public var title: String
    public var message: String
    public var tripTitle: String
    public var tripSubtitle: String
    public var confirm: String
    public var cancel: String
    /// The deletion is running.
    public var isDeleting: Bool
    public var error: String?

    public init(
        tripId: String, title: String, message: String, tripTitle: String, tripSubtitle: String, confirm: String = "Delete",
        cancel: String = "Cancel", isDeleting: Bool = false, error: String? = nil
    ) {
        self.tripId = tripId
        self.title = title
        self.message = message
        self.tripTitle = tripTitle
        self.tripSubtitle = tripSubtitle
        self.confirm = confirm
        self.cancel = cancel
        self.isDeleting = isDeleting
        self.error = error
    }
}

/// Kotlin `TripEditMessageUi`: the toast after a save, "Marked as passenger · scores updated" with "Undo"; without
/// `undoLabel`, why an Undo did not go through.
public struct TripEditToast: Equatable, Sendable {
    public var tripId: String
    public var text: String
    public var undoLabel: String?

    public init(tripId: String, text: String, undoLabel: String? = "Undo") {
        self.tripId = tripId
        self.text = text
        self.undoLabel = undoLabel
    }
}

/// Kotlin `TripEditAction`.
public enum TripEditCommand: Equatable, Sendable {
    case open(tripId: String, pane: TripEditPane)
    case showPane(TripEditPane)
    case pickType(TripEditKind)
    case pickCar(vehicleId: String)
    case save
    case close
    case undo
    case dismissMessage
    case askDelete(tripId: String)
    case cancelDelete
    case confirmDelete
}

// MARK: - S-HIS-01 filter chips and S-HIS-02

/// Kotlin `FilterGroup`.
public enum HistoryFilterGroup: String, Equatable, Sendable, CaseIterable {
    case type, car, route, quality
}

/// Kotlin `FilterKey`: one filter choice.
public struct HistoryFilterKey: Hashable, Sendable {
    public var group: HistoryFilterGroup
    public var value: String

    public init(group: HistoryFilterGroup, value: String) {
        self.group = group
        self.value = value
    }
}

/// Kotlin `FilterChipUi`.
public struct HistoryFilterChip: Identifiable, Equatable, Sendable {
    public var id: HistoryFilterKey { key }
    public var key: HistoryFilterKey
    /// "The Commute", "Good data"
    public var label: String
    public var count: Int
    public var isSelected: Bool
    /// The quality glyph to draw before the label.
    public var quality: QualityBadge?
    /// "The Commute, 10 drives, selected" or "Remove filter The Commute".
    public var accessibilityLabel: String

    public init(
        key: HistoryFilterKey, label: String, count: Int, isSelected: Bool, quality: QualityBadge? = nil,
        accessibilityLabel: String
    ) {
        self.key = key
        self.label = label
        self.count = count
        self.isSelected = isSelected
        self.quality = quality
        self.accessibilityLabel = accessibilityLabel
    }
}

/// Kotlin `FilterGroupUi`: "Car" and its chips.
public struct HistoryFilterSection: Identifiable, Equatable, Sendable {
    public var id: HistoryFilterGroup { group }
    public var group: HistoryFilterGroup
    public var title: String
    public var chips: [HistoryFilterChip]

    public init(group: HistoryFilterGroup, title: String, chips: [HistoryFilterChip]) {
        self.group = group
        self.title = title
        self.chips = chips
    }
}

/// Kotlin `FilterSheetUi`: S-HIS-02.
public struct HistoryFilterSheet: Equatable, Sendable {
    /// "Filter drives"
    public var title: String
    /// "All 30 drives" / "2 filters · 8 drives"
    public var subtitle: String
    public var sections: [HistoryFilterSection]
    /// "Show 8 drives" / "No drives match"
    public var showLabel: String
    public var canShow: Bool
    public var resetLabel: String
    public var canReset: Bool

    public init(
        title: String, subtitle: String, sections: [HistoryFilterSection], showLabel: String, canShow: Bool,
        resetLabel: String = "Reset", canReset: Bool
    ) {
        self.title = title
        self.subtitle = subtitle
        self.sections = sections
        self.showLabel = showLabel
        self.canShow = canShow
        self.resetLabel = resetLabel
        self.canReset = canReset
    }
}

/// Kotlin `HistoryFilterUi`: the filters on S-HIS-01.
public struct HistoryFilters: Equatable, Sendable {
    public var activeCount: Int
    /// "Filters"
    public var buttonLabel: String
    /// "Filters, 2 applied"
    public var buttonAccessibilityLabel: String
    public var applied: [HistoryFilterChip]
    public var suggestions: [HistoryFilterChip]
    /// "Clear"
    public var clearLabel: String
    /// "8 drives · filtered"
    public var subtitle: String?
    /// "No drives match."
    public var emptyTitle: String?
    public var emptyBody: String?
    public var sheet: HistoryFilterSheet?

    public var isActive: Bool { activeCount > 0 }

    public init(
        activeCount: Int = 0, buttonLabel: String = "Filters", buttonAccessibilityLabel: String = "Filters",
        applied: [HistoryFilterChip] = [], suggestions: [HistoryFilterChip] = [], clearLabel: String = "Clear",
        subtitle: String? = nil, emptyTitle: String? = nil, emptyBody: String? = nil, sheet: HistoryFilterSheet? = nil
    ) {
        self.activeCount = activeCount
        self.buttonLabel = buttonLabel
        self.buttonAccessibilityLabel = buttonAccessibilityLabel
        self.applied = applied
        self.suggestions = suggestions
        self.clearLabel = clearLabel
        self.subtitle = subtitle
        self.emptyTitle = emptyTitle
        self.emptyBody = emptyBody
        self.sheet = sheet
    }
}

/// Kotlin `HistoryFilterAction`.
public enum HistoryFilterCommand: Equatable, Sendable {
    case openSheet
    case toggleDraft(HistoryFilterKey)
    case resetDraft
    case apply
    case closeSheet
    case toggle(HistoryFilterKey)
    case clear
}
