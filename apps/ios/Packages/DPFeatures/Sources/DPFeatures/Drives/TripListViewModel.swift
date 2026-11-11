import DPSharedBridge
import Foundation
import Observation

/// One row of the drives list (components.md §12, reduced to what the shell has: no route label, score or car yet).
public struct TripRow: Identifiable, Equatable, Sendable {
    public var id: String
    /// "Thursday morning"
    public var title: String
    /// "5 Nov · 8:14 am"
    public var subtitle: String
    /// "37.9 km"
    public var distance: String
    /// "30 min"
    public var duration: String
    /// One line under the card: short, interrupted or manual.
    public var note: String?
    public var accessibilityLabel: String
    /// The compact data-quality badge (components.md §10.1); nil before analysis.
    public var quality: QualityBadge? = nil
    /// "Interrupted" tag (F-CAP-06).
    public var isInterrupted: Bool = false
    /// The open "Was this a bus or train?" question, answered on the card (F-CAP-04).
    public var question: TripQuestion? = nil
    /// Passenger or not a drive: the card is dimmed and doesn't count.
    public var isDimmed: Bool = false
    /// "Passenger" / "Bus or train" chip in place of the stats emphasis.
    public var kindLabel: String? = nil
}

/// Which list All drives shows (S-HIS-01 filter).
public enum DrivesFilter: String, CaseIterable, Identifiable, Sendable {
    case drives, notDrives
    public var id: String { rawValue }
    public var title: String { self == .drives ? "Drives" : "Not drives" }
}

public struct TripSection: Identifiable, Equatable, Sendable {
    public var id: String
    /// "This week", "Last week", "19–25 Oct"
    public var title: String
    /// "3 drives"
    public var meta: String
    public var rows: [TripRow]
}

/// S-HOME-01 "Latest drives" and S-HIS-01 "All drives": processed trips, newest first, grouped by week.
@MainActor
@Observable
public final class TripListViewModel {
    public private(set) var trips: [TripSummary] = []
    public private(set) var hasLoaded = false
    /// Trips corrected to not-a-drive, kept 30 days (F-CAP-04 AC3).
    public private(set) var notDrives: [TripSummary] = []
    public var filter: DrivesFilter = .drives
    private let history: any TripHistory
    private let corrections: (any TripCorrecting)?
    private let calendar: Calendar
    private let now: @MainActor () -> Date
    private let locale: Locale
    public let presenter: TripPresenter

    public init(
        history: any TripHistory, corrections: (any TripCorrecting)? = nil, catalog: ProvenanceCatalog = ProvenanceCatalog(),
        calendar: Calendar = .current, locale: Locale = .current, now: @escaping @MainActor () -> Date = { Date() }
    ) {
        self.history = history
        self.corrections = corrections
        self.calendar = calendar
        self.locale = locale
        self.now = now
        presenter = TripPresenter(catalog: catalog, calendar: calendar, locale: locale)
    }

    /// Drives and not-drives together, for as long as the app runs: a trip corrected from its detail moves from one list
    /// to the other, and the detail must still find it (it shows the "Not a drive" banner and Restore).
    public func observe() async {
        await withTaskGroup(of: Void.self) { group in
            group.addTask { await self.observeDrives() }
            group.addTask { await self.observeNotDrives() }
        }
    }

    func observeDrives() async {
        for await list in history.observeTrips() {
            trips = list.sorted { $0.startedAt > $1.startedAt }
            hasLoaded = true
        }
    }

    /// The "Not drives" filter's list. Returns at once without a corrections port.
    func observeNotDrives() async {
        guard let corrections else { return }
        for await list in corrections.observeNotDrives() {
            notDrives = list.sorted { $0.startedAt > $1.startedAt }
        }
    }

    /// Whether answers and corrections can be made (the port exists).
    public var canCorrect: Bool { corrections != nil }

    /// "Was this a bus or train?" Yes / No.
    public func answer(tripId: String, yes: Bool) async {
        await corrections?.answer(tripId: tripId, yes: yes)
    }

    /// "What was this?" (S-TRP-02 type picker); `.driver` restores a trip as a drive.
    public func correct(tripId: String, to correction: TripCorrection) async {
        await corrections?.correct(tripId: tripId, to: correction)
    }

    /// A listed trip or a not-a-drive trip.
    public func trip(id: String) -> TripSummary? {
        trips.first { $0.id == id } ?? notDrives.first { $0.id == id }
    }

    /// The trip detail (S-TRP-01) for [id], or nil once the trip is gone.
    public func detail(id: String) -> TripDetail? {
        guard let trip = trip(id: id) else { return nil }
        let r = row(trip)
        return presenter.detail(trip, title: r.title, subtitle: r.subtitle)
    }

    /// The data-quality sheet (S-DQ-01) for [id].
    public func dataQuality(id: String) -> DataQualityDetail? {
        trip(id: id).flatMap(presenter.dataQuality)
    }

    /// The "Not drives" rows, newest first.
    public var notDriveRows: [TripRow] { notDrives.map(row) }

    public var latest: [TripRow] { trips.prefix(5).map(row) }

    public var sections: [TripSection] {
        let grouped = Dictionary(grouping: trips) { calendar.dateInterval(of: .weekOfYear, for: $0.startedAt)?.start }
        return grouped.keys.compactMap { $0 }.sorted(by: >).map { weekStart in
            let items = (grouped[weekStart] ?? []).sorted { $0.startedAt > $1.startedAt }
            return TripSection(
                id: "\(weekStart.timeIntervalSince1970)", title: weekTitle(weekStart),
                meta: items.count == 1 ? "1 drive" : "\(items.count) drives", rows: items.map(row))
        }
    }

    /// S-HIS-01 subtitle and footer.
    public var countText: String { trips.count == 1 ? "1 drive" : "\(trips.count) drives" }

    func weekTitle(_ weekStart: Date) -> String {
        guard let thisWeek = calendar.dateInterval(of: .weekOfYear, for: now())?.start else { return "" }
        if calendar.isDate(weekStart, inSameDayAs: thisWeek) { return "This week" }
        if let last = calendar.date(byAdding: .weekOfYear, value: -1, to: thisWeek),
            calendar.isDate(weekStart, inSameDayAs: last)
        {
            return "Last week"
        }
        let end = calendar.date(byAdding: .day, value: 6, to: weekStart) ?? weekStart
        let startDay = calendar.component(.day, from: weekStart)
        let endDay = calendar.component(.day, from: end)
        let sameMonth = calendar.component(.month, from: weekStart) == calendar.component(.month, from: end)
        return sameMonth
            ? "\(startDay)–\(endDay) \(month(end))"
            : "\(startDay) \(month(weekStart)) – \(endDay) \(month(end))"
    }

    /// "Nov"
    func month(_ date: Date) -> String {
        date.formatted(Date.FormatStyle(locale: locale, calendar: calendar, timeZone: calendar.timeZone).month(.abbreviated))
    }

    func row(_ trip: TripSummary) -> TripRow {
        let title = "\(weekday(trip.startedAt)) \(partOfDay(trip.startedAt))"
        var timeStyle = Date.FormatStyle(date: .omitted, time: .shortened, locale: locale, calendar: calendar)
        timeStyle.timeZone = calendar.timeZone
        let minutes = max(Int((trip.duration / 60).rounded()), trip.duration > 0 ? 1 : 0)
        let km = "\(DriveFormat.kilometres(trip.distanceM)) km"
        let note: String? =
            switch trip.status {
            case .short: "Short trip · not scored"
            case .interrupted: "iOS ended DrivePerf in the background. Saved up to \(trip.endedAt.formatted(timeStyle))."
            case .finalised: trip.source == .manual ? "Started by you" : nil
            }
        let kindLabel = Self.kindLabel(trip)
        let qualityWord = trip.quality.map { "\(TripPresenter.badgeWord($0.badge)) data" }
        return TripRow(
            id: trip.id, title: title,
            subtitle: "\(calendar.component(.day, from: trip.startedAt)) \(month(trip.startedAt)) · "
                + trip.startedAt.formatted(timeStyle),
            distance: km, duration: "\(minutes) min", note: note,
            accessibilityLabel: [title, "\(km)", "\(minutes) minutes", kindLabel, note, qualityWord].compactMap { $0 }
                .joined(separator: ", "),
            quality: trip.quality?.badge, isInterrupted: trip.status == .interrupted, question: trip.question,
            isDimmed: kindLabel != nil, kindLabel: kindLabel)
    }

    static func kindLabel(_ trip: TripSummary) -> String? {
        switch trip.correction {
        case .transit: return "Bus or train"
        case .bike: return "Bike ride"
        case .walk: return "Walk"
        case .other: return "Not a drive"
        case .passenger: return "Passenger"
        case .driver, nil: return trip.kind == .passenger ? "Passenger" : nil
        }
    }

    func weekday(_ date: Date) -> String {
        date.formatted(Date.FormatStyle(locale: locale, calendar: calendar, timeZone: calendar.timeZone).weekday(.wide))
    }

    /// "Part of day" words used across Stories and lists: morning, midday, afternoon, evening, night.
    func partOfDay(_ date: Date) -> String {
        switch calendar.component(.hour, from: date) {
        case 5..<12: "morning"
        case 12..<14: "midday"
        case 14..<17: "afternoon"
        case 17..<21: "evening"
        default: "night"
        }
    }
}
