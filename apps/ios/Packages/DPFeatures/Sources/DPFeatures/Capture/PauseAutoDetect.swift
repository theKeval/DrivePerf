import DPSharedBridge
import Foundation
import Observation

/// Which day a pause ends on, seen from now: "until 8:30 PM", "until 6:00 AM tomorrow", "until Saturday 6:00 AM".
public enum PauseDay: Equatable, Sendable {
    case today, tomorrow, later
}

/// Pure rules for S-HOME-03 (F-CAP-15): when each choice ends and how its end is named. The shared core keeps the pause
/// and ends it; these only turn the user's choice into a time in the phone's calendar and time zone.
public enum PauseRules {
    /// "Until tomorrow morning" ends at this local hour (PRD F-CAP-15 AC1: tomorrow 06:00).
    public static let morningHour = 6
    /// The choice the sheet opens with (S-HOME-03 layout).
    public static let defaultLength = AutoDetectPauseLength.untilMorning

    /// When a pause chosen at `now` ends: an hour later; 06:00 on the next calendar day (a day with no 06:00, at a
    /// clock change, takes the next valid time); or never (nil, until the user turns it back on).
    public static func until(_ length: AutoDetectPauseLength, now: Date, calendar: Calendar = .current) -> Date? {
        switch length {
        case .oneHour:
            return now.addingTimeInterval(3600)
        case .untilMorning:
            guard let tomorrow = calendar.date(byAdding: .day, value: 1, to: calendar.startOfDay(for: now)) else {
                return nil
            }
            return calendar.date(bySettingHour: morningHour, minute: 0, second: 0, of: tomorrow)
        case .untilResumed:
            return nil
        }
    }

    /// The day `until` falls on, seen from `now`.
    public static func day(of until: Date, now: Date, calendar: Calendar = .current) -> PauseDay {
        let days = calendar.dateComponents([.day], from: calendar.startOfDay(for: now), to: calendar.startOfDay(for: until))
            .day ?? 0
        switch days {
        case ...0: return .today
        case 1: return .tomorrow
        default: return .later
        }
    }

    /// The end as a phrase: "until 8:30 PM", "until 6:00 AM tomorrow", "until Saturday 6:00 AM", or "until you turn it
    /// back on" when there is none.
    public static func untilPhrase(
        _ until: Date?, now: Date, calendar: Calendar = .current, locale: Locale = .current
    ) -> String {
        guard let until else { return "until you turn it back on" }
        let time = clock(until, calendar: calendar, locale: locale)
        switch day(of: until, now: now, calendar: calendar) {
        case .today: return "until \(time)"
        case .tomorrow: return "until \(time) tomorrow"
        case .later: return "until \(weekday(until, calendar: calendar, locale: locale)) \(time)"
        }
    }

    /// `untilPhrase` starting a line: "Until 6:00 AM tomorrow".
    public static func untilLine(
        _ until: Date?, now: Date, calendar: Calendar = .current, locale: Locale = .current
    ) -> String {
        let phrase = untilPhrase(until, now: now, calendar: calendar, locale: locale)
        return phrase.prefix(1).uppercased(with: locale) + phrase.dropFirst()
    }

    /// The option row's title.
    public static func title(_ length: AutoDetectPauseLength) -> String {
        switch length {
        case .oneHour: "For 1 hour"
        case .untilMorning: "Until tomorrow morning"
        case .untilResumed: "Until I turn it back on"
        }
    }

    /// The option row's second line: "Until 8:30 PM", "Saturday, 6:00 AM", "Manual drives still work".
    public static func detail(
        _ length: AutoDetectPauseLength, now: Date, calendar: Calendar = .current, locale: Locale = .current
    ) -> String {
        guard let until = until(length, now: now, calendar: calendar) else { return "Manual drives still work" }
        let time = clock(until, calendar: calendar, locale: locale)
        return length == .untilMorning ? "\(weekday(until, calendar: calendar, locale: locale)), \(time)" : "Until \(time)"
    }

    /// The primary button, whose label follows the choice.
    public static func button(_ length: AutoDetectPauseLength) -> String {
        switch length {
        case .oneHour: "Pause for 1 hour"
        case .untilMorning: "Pause until tomorrow"
        case .untilResumed: "Pause auto-detect"
        }
    }

    private static func clock(_ date: Date, calendar: Calendar, locale: Locale) -> String {
        var style = Date.FormatStyle(date: .omitted, time: .shortened, locale: locale, calendar: calendar)
        style.timeZone = calendar.timeZone
        return date.formatted(style)
    }

    private static func weekday(_ date: Date, calendar: Calendar, locale: Locale) -> String {
        var style = Date.FormatStyle(locale: locale, calendar: calendar).weekday(.wide)
        style.timeZone = calendar.timeZone
        return date.formatted(style)
    }
}

/// S-HOME-03 Pause auto-detect (F-CAP-15). One instance for the app; each surface that opens the sheet (the capture
/// status sheet, Recording settings) calls `prepare()` first. The core keeps the pause: this only sends the user's
/// choice and reads the core's status back.
@MainActor
@Observable
public final class PauseAutoDetectViewModel {
    /// The sheet's three states (S-HOME-03 default, pausedUntil, resume).
    public enum Step: Equatable, Sendable {
        /// Choose how long.
        case pick
        /// Paused: the confirmation with Done and Undo. `until` is nil for "until I turn it back on".
        case confirmed(until: Date?)
        /// Opened while paused: Resume auto-detect or Keep paused.
        case resume
    }

    public private(set) var step = Step.pick
    public var choice = PauseRules.defaultLength

    private let recorder: any TripRecording
    private let status: @MainActor () -> RecorderStatus
    private let now: @MainActor () -> Date
    private let calendar: Calendar

    /// `status` reads the recorder status the views observe (the capture view model's), so the views update with it.
    public init(
        recorder: any TripRecording, status: @escaping @MainActor () -> RecorderStatus,
        now: @escaping @MainActor () -> Date = { Date() }, calendar: Calendar = .current
    ) {
        self.recorder = recorder
        self.status = status
        self.now = now
        self.calendar = calendar
    }

    /// Paused right now (the core's health).
    public var isPaused: Bool { status().autoDetectHealth == .paused }

    /// When the pause ends by itself; nil while not paused or paused until the user turns it back on.
    public var pausedUntil: Date? { isPaused ? status().autoDetectPausedUntil : nil }

    /// Pause or resume is offered while automatic trips are on and no driver trip is running (D1 gate).
    public var isAvailable: Bool { status().autoDetectEnabled && !status().isDriving }

    /// "Until 6:00 AM tomorrow" for the current pause.
    public var pausedUntilLine: String { PauseRules.untilLine(pausedUntil, now: now(), calendar: calendar) }

    /// "until 6:00 AM tomorrow" for the current pause, inside a sentence.
    public var pausedUntilPhrase: String { PauseRules.untilPhrase(pausedUntil, now: now(), calendar: calendar) }

    /// The option row's second line, from now.
    public func detail(_ length: AutoDetectPauseLength) -> String {
        PauseRules.detail(length, now: now(), calendar: calendar)
    }

    /// Resets the sheet as it opens: Resume while paused, otherwise the choices with the default selected.
    public func prepare() {
        step = isPaused ? .resume : .pick
        choice = PauseRules.defaultLength
    }

    /// Pause → the confirmation.
    public func pause() {
        let until = PauseRules.until(choice, now: now(), calendar: calendar)
        recorder.pauseAutoDetect(choice, until: until)
        step = .confirmed(until: until)
    }

    /// Undo on the confirmation: auto-detect is back on, and the choices show again.
    public func undo() {
        recorder.undoPauseAutoDetect()
        step = .pick
    }

    /// Resume auto-detect (the sheet, the Home banner, Recording settings).
    public func resume() {
        recorder.resumeAutoDetect()
    }

    /// "Until 6:00 AM tomorrow" for a confirmed end.
    public func untilLine(_ until: Date?) -> String {
        PauseRules.untilLine(until, now: now(), calendar: calendar)
    }
}
