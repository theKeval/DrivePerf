import DrivePerfShared
import Foundation

/// iOS dates and times for the shared Drives presenter (DRIVE-217): `Date.FormatStyle` and `Calendar` in the user's
/// locale, calendar and time zone. Everything else on the Drives screens is formatted by the core.
final class CoreDrivesFormatter: NSObject, DrivePerfShared.DrivesFormatter, @unchecked Sendable {
    private let calendar: Calendar
    private let locale: Locale

    init(calendar: Calendar = .current, locale: Locale = .current) {
        self.calendar = calendar
        self.locale = locale
    }

    var platform: DrivesPlatform { .ios }

    private func date(_ epochMs: Int64) -> Date { Date(timeIntervalSince1970: TimeInterval(epochMs) / 1000) }

    private var style: Date.FormatStyle {
        Date.FormatStyle(locale: locale, calendar: calendar, timeZone: calendar.timeZone)
    }

    /// "8:14 am"
    func time(epochMs: Int64) -> String {
        var style = Date.FormatStyle(date: .omitted, time: .shortened, locale: locale, calendar: calendar)
        style.timeZone = calendar.timeZone
        return date(epochMs).formatted(style)
    }

    /// "December 5, 2025"
    func longDate(epochMs: Int64) -> String {
        var style = Date.FormatStyle(date: .long, time: .omitted, locale: locale, calendar: calendar)
        style.timeZone = calendar.timeZone
        return date(epochMs).formatted(style)
    }

    /// "5 November"
    func dayAndMonth(epochMs: Int64) -> String { date(epochMs).formatted(style.day().month(.wide)) }

    /// "Thursday"
    func weekday(epochMs: Int64) -> String { date(epochMs).formatted(style.weekday(.wide)) }

    /// "Nov"
    func month(epochMs: Int64) -> String { date(epochMs).formatted(style.month(.abbreviated)) }

    func dayOfMonth(epochMs: Int64) -> Int32 { Int32(calendar.component(.day, from: date(epochMs))) }

    func hour(epochMs: Int64) -> Int32 { Int32(calendar.component(.hour, from: date(epochMs))) }

    func weekStart(epochMs: Int64) -> Int64 {
        let start = calendar.dateInterval(of: .weekOfYear, for: date(epochMs))?.start ?? calendar.startOfDay(for: date(epochMs))
        return Int64((start.timeIntervalSince1970 * 1000).rounded())
    }
}
