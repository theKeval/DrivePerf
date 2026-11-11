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
    private let history: any TripHistory
    private let calendar: Calendar
    private let now: @MainActor () -> Date
    private let locale: Locale

    public init(
        history: any TripHistory, calendar: Calendar = .current, locale: Locale = .current,
        now: @escaping @MainActor () -> Date = { Date() }
    ) {
        self.history = history
        self.calendar = calendar
        self.locale = locale
        self.now = now
    }

    public func observe() async {
        for await list in history.observeTrips() {
            trips = list.sorted { $0.startedAt > $1.startedAt }
            hasLoaded = true
        }
    }

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
        return TripRow(
            id: trip.id, title: title,
            subtitle: "\(calendar.component(.day, from: trip.startedAt)) \(month(trip.startedAt)) · "
                + trip.startedAt.formatted(timeStyle),
            distance: km, duration: "\(minutes) min", note: note,
            accessibilityLabel: [title, "\(km)", "\(minutes) minutes", note].compactMap { $0 }.joined(separator: ", "))
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
