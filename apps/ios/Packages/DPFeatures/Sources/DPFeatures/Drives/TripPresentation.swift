import DPDesignSystem
import DPSharedBridge
import Foundation

// What the trip detail (S-TRP-01, the parts the shell has) and the data-quality sheet (S-DQ-01) show, built from a
// `TripSummary` without any view code, so it is unit-tested. Copy is from docs/design/DRIVE-170 and DRIVE-172.

/// One number with how it was obtained (F-DQ-05): long-press shows `provenance` and `meaning`.
public struct ProvenancedValue: Identifiable, Equatable, Sendable {
    public var id: String { metricKey }
    public var metricKey: String
    /// "Distance"
    public var label: String
    /// "39.0", already at the metric's precision, without "≈".
    public var value: String
    /// "km"; empty for counts.
    public var unit: String
    public var provenance: Provenance
    public var meaning: String
    /// Drawn with the estimated token ("≈", 60 %).
    public var isEstimated: Bool
    /// A supporting line: "≈ 0.4 km estimated".
    public var note: String?
    /// The value lowered the quality badge (S-DQ-01 warn tone).
    public var lowersBadge: Bool = false

    /// "Distance, 39.0 kilometres. Derived."
    public var accessibilityLabel: String {
        let shown = isEstimated ? "about \(value)" : value
        return "\(label), \(shown) \(spokenUnit). \(provenance.label)."
    }

    private var spokenUnit: String {
        switch unit {
        case "km": "kilometres"
        case "km/h": "kilometres per hour"
        case "m": "metres"
        case "min": "minutes"
        case "%": "percent"
        case "Hz": "hertz"
        default: unit
        }
    }
}

/// A banner under the trip detail's title (S-TRP-01 state banners).
public struct TripBanner: Equatable, Sendable {
    public enum Tone: Equatable, Sendable { case info, attention }
    public enum Action: Equatable, Sendable {
        /// "Was this a bus or train?" Yes / No.
        case answer(TripQuestion)
        /// Restore a passenger or not-a-drive trip as a drive.
        case restore
    }

    public var tone: Tone
    public var text: String
    public var action: Action?
}

/// The trip detail's content.
public struct TripDetail: Equatable, Sendable {
    public var title: String
    public var subtitle: String
    public var banners: [TripBanner]
    public var tiles: [ProvenancedValue]
    public var quality: QualityBadge?
    /// The current answer to "What was this?", for the picker's checkmark.
    public var correction: TripCorrection
}

/// One stretch of the S-DQ-01 coverage strip, as fractions of the drive.
public struct CoverageSpan: Equatable, Sendable {
    public var style: OverlayStyle
    public var from: Double
    public var to: Double
}

/// The data-quality sheet's content (S-DQ-01).
public struct DataQualityDetail: Equatable, Sendable {
    public var badge: QualityBadge
    /// "Usable data"
    public var title: String
    /// One honest sentence per badge.
    public var hero: String
    /// The estimated-start explanation, when there is one.
    public var estimatedStart: String?
    public var coverage: [CoverageSpan]
    /// "Scored 31 of 34 min"
    public var scoredLine: String
    /// "Drive coverage, 33 minutes. Start estimated. 41 seconds without GPS."
    public var coverageAccessibility: String
    public var rows: [ProvenancedValue]
    public var legend: [(Provenance, String)]

    public static func == (a: Self, b: Self) -> Bool {
        a.badge == b.badge && a.title == b.title && a.hero == b.hero && a.estimatedStart == b.estimatedStart
            && a.coverage == b.coverage && a.scoredLine == b.scoredLine && a.rows == b.rows
            && a.legend.map(\.0) == b.legend.map(\.0) && a.legend.map(\.1) == b.legend.map(\.1)
    }
}

/// Builds `TripDetail` and `DataQualityDetail`. `catalog` supplies provenance, meaning and precision; a metric
/// missing from it falls back to Derived with one decimal at most, so a stale catalogue never hides a number.
public struct TripPresenter: Sendable {
    public var catalog: ProvenanceCatalog
    public var calendar: Calendar
    public var locale: Locale

    public init(catalog: ProvenanceCatalog, calendar: Calendar = .current, locale: Locale = .current) {
        self.catalog = catalog
        self.calendar = calendar
        self.locale = locale
    }

    // MARK: Trip detail

    public func detail(_ trip: TripSummary, title: String, subtitle: String) -> TripDetail {
        TripDetail(
            title: title, subtitle: subtitle, banners: banners(trip), tiles: tiles(trip), quality: trip.quality?.badge,
            correction: trip.correction ?? (trip.kind == .passenger ? .passenger : .driver))
    }

    func banners(_ trip: TripSummary) -> [TripBanner] {
        var out: [TripBanner] = []
        if let question = trip.question {
            out.append(TripBanner(tone: .info, text: Self.questionText(question), action: .answer(question)))
        }
        if let correction = trip.correction, correction.isNotADrive {
            out.append(TripBanner(tone: .info, text: notADriveText(correction, at: trip.correctedAt), action: .restore))
        } else if trip.kind == .passenger {
            out.append(
                TripBanner(
                    tone: .info,
                    text: "Marked as passenger. It isn’t scored and doesn’t count toward your baseline, routes or recaps.",
                    action: .restore))
        }
        switch trip.status {
        case .interrupted:
            out.append(TripBanner(tone: .attention, text: interruptedText(trip.endedAt), action: nil))
        case .short:
            out.append(
                TripBanner(
                    tone: .info,
                    text: "Short drive · \(km(trip.distanceM)) km. Short drives give the score less to work with, so "
                        + "expect it to move more from drive to drive.",
                    action: nil))
        case .finalised:
            break
        }
        if let quality = trip.quality, quality.badge == .unscorable {
            out.append(
                TripBanner(
                    tone: .attention,
                    text: "We couldn’t score this one fairly. \(Self.reasonText(quality.reason)). Here’s the route and the "
                        + "stats anyway.",
                    action: nil))
        }
        return out
    }

    static func questionText(_ question: TripQuestion) -> String {
        switch question {
        case .transit: "Was this a bus or train? Yes takes it out of your scores and history."
        case .bike: "Was this a bike ride? Yes takes it out of your scores and history."
        }
    }

    func notADriveText(_ correction: TripCorrection, at: Date?) -> String {
        let what =
            switch correction {
            case .transit: "a bus or train ride"
            case .bike: "a bike ride"
            case .walk: "a walk"
            default: "not a drive"
            }
        let deleted = at.flatMap { calendar.date(byAdding: .day, value: NotDrives.retentionDays, to: $0) }
            .map { " It will be deleted on \($0.formatted(Date.FormatStyle(date: .long, time: .omitted, locale: locale, calendar: calendar)))." }
            ?? ""
        return "Marked as \(what). It’s out of your scores and history and kept under Not drives.\(deleted)"
    }

    func interruptedText(_ at: Date) -> String {
        var style = Date.FormatStyle(date: .omitted, time: .shortened, locale: locale, calendar: calendar)
        style.timeZone = calendar.timeZone
        return "Interrupted at \(at.formatted(style)). iOS ended DrivePerf in the background. Everything before then is "
            + "saved. The missing part is shown as a gap, never scored."
    }

    /// S-TRP-01 stat tiles in PRD order; speed is never first, and tiles without a value are left out.
    func tiles(_ trip: TripSummary) -> [ProvenancedValue] {
        let s = trip.stats
        var out: [ProvenancedValue] = []
        let estimatedPart = s.estimatedDistanceM > 0
        var distance = value("distance", "Distance", trip.distanceM, includesEstimate: estimatedPart)
        if estimatedPart {
            distance.note = DPEstimated.prefix + "\(km(s.estimatedDistanceM)) km estimated"
        }
        out.append(distance)
        out.append(duration("duration", "Duration", trip.duration))
        if let v = s.movingS { out.append(duration("movingTime", "Moving", v)) }
        if let v = s.stoppedS { out.append(duration("stoppedTime", "Stopped", v)) }
        if let v = s.stopCount { out.append(value("stops", "Stops", Double(v))) }
        if let v = s.avgMovingSpeedMps { out.append(value("avgMovingSpeed", "Avg moving speed", v)) }
        if let v = s.maxSpeedMps { out.append(value("maxSpeed", "Max speed", v)) }
        if let v = s.elevGainM { out.append(value("elevationGainLoss", "Climb", v)) }
        if let v = s.elevLossM {
            var descent = value("elevationGainLoss", "Descent", v)
            descent.metricKey = "elevationLoss"
            out.append(descent)
        }
        return out
    }

    // MARK: Data quality (S-DQ-01)

    public func dataQuality(_ trip: TripSummary) -> DataQualityDetail? {
        guard let q = trip.quality else { return nil }
        let gapS = q.overlays.filter { $0.style == .shortGap || $0.style == .longGap }.reduce(0) { $0 + $1.t1S - $1.t0S }
        return DataQualityDetail(
            badge: q.badge, title: "\(Self.badgeWord(q.badge)) data", hero: hero(q, gapS: gapS),
            estimatedStart: q.estimatedStartM.flatMap { $0 > 0 ? estimatedStartText($0) : nil },
            coverage: coverage(q), scoredLine: "Scored \(minutes(q.scoredS)) of \(minutes(q.totalS)) min",
            coverageAccessibility: coverageAccessibility(q, gapS: gapS), rows: rows(q), legend: legend())
    }

    static func badgeWord(_ badge: QualityBadge) -> String {
        switch badge {
        case .good: "Good"
        case .usable: "Usable"
        case .limited: "Limited"
        case .unscorable: "Unscorable"
        }
    }

    func hero(_ q: TripQualitySummary, gapS: Double) -> String {
        switch q.badge {
        case .good:
            return "Everything we needed, the whole way. Scores and events are as reliable as a phone gets."
        case .usable:
            if q.reason == "lowImuRate" || q.imuHz < Self.lowImuHz {
                return "Battery saver: sensors at \(Int(q.imuHz.rounded())) Hz. The score is fair, with a little less "
                    + "detail in short events."
            }
            if gapS > 0 {
                return "Mostly solid, with \(Int(gapS.rounded())) s without GPS. The score is fair; that stretch is "
                    + "drawn dotted."
            }
            return "Mostly solid. The score is fair."
        case .limited:
            if let moved = q.leftOut.first(where: { $0.reason == .phoneMoved }) {
                return "Your phone moved around for about \(max(minutes(moved.seconds), 1)) minutes, so we left that "
                    + "part out of the score."
            }
            return "Part of this drive couldn’t be scored fairly, so the score is based on \(minutes(q.scoredS)) of "
                + "\(minutes(q.totalS)) min."
        case .unscorable:
            return "We couldn’t score this one fairly. \(Self.reasonText(q.reason))."
        }
    }

    func estimatedStartText(_ metres: Double) -> String {
        "The first \(DPEstimated.prefix)\(km(metres)) km is estimated. Automatic trips start a moment after you set "
            + "off. We fill in the start from your phone’s location history and draw it dashed. It’s never scored and "
            + "never gets event pins."
    }

    func coverage(_ q: TripQualitySummary) -> [CoverageSpan] {
        guard q.totalS > 0 else { return [] }
        return q.overlays.compactMap { o in
            let from = min(max(o.t0S / q.totalS, 0), 1)
            let to = min(max(o.t1S / q.totalS, 0), 1)
            return to > from ? CoverageSpan(style: o.style, from: from, to: to) : nil
        }
    }

    func coverageAccessibility(_ q: TripQualitySummary, gapS: Double) -> String {
        var parts = ["Drive coverage, \(minutes(q.totalS)) minutes."]
        if q.overlays.contains(where: { $0.style == .estimatedStart }) { parts.append("Start estimated.") }
        if gapS > 0 { parts.append("\(Int(gapS.rounded())) seconds without GPS.") }
        let leftOut = q.leftOut.reduce(0) { $0 + $1.seconds }
        if leftOut > 0 { parts.append("\(max(minutes(leftOut), 1)) minutes left out of the score.") }
        return parts.joined(separator: " ")
    }

    func rows(_ q: TripQualitySummary) -> [ProvenancedValue] {
        var phone = ProvenancedValue(
            metricKey: "phoneState", label: "Phone", value: Self.phoneWord(q.phone), unit: "", provenance: .derived,
            meaning: "From how steady the motion sensors were while the car moved.", isEstimated: false)
        phone.lowersBadge = q.phone == .moved
        var rate = value("imuRate", "Sensor rate", q.imuHz)
        rate.lowersBadge = q.reason == "lowImuRate" || q.reason == "noImu"
        var gps = value("gnssCoverage", "GPS coverage", q.gnssCoveragePct)
        gps.lowersBadge = q.reason == "lowGnss" || q.reason == "noPreciseLocation"
        var alignment = ProvenancedValue(
            metricKey: "alignment", label: "Phone alignment",
            value: q.alignmentSettledS.map { "Settled after \(Int($0.rounded())) s" } ?? "Didn’t settle", unit: "",
            provenance: .derived, meaning: "How long it took to work out which way your phone faces in the car.",
            isEstimated: false)
        alignment.lowersBadge = q.reason == "notAligned"
        let leftOutS = q.leftOut.reduce(0) { $0 + $1.seconds }
        var leftOut = duration("excludedTime", "Left out of the score", leftOutS)
        if leftOutS > 0 {
            leftOut.note = q.leftOut.map { "\(Self.reasonWord($0.reason)) \(max(minutes($0.seconds), 1)) min" }
                .joined(separator: " · ")
        } else {
            leftOut.value = "Nothing"
            leftOut.unit = ""
        }
        leftOut.lowersBadge = q.reason == "lowScoredShare" || q.reason == "tooLittleScoredTime"
        var out = [phone, rate, gps, alignment, leftOut]
        if let m = q.estimatedStartM, m > 0 {
            var start = value("estimatedStart", "Estimated start", m)
            start.isEstimated = true
            out.append(start)
        }
        return out
    }

    func legend() -> [(Provenance, String)] {
        Provenance.allCases.map { ($0, catalog.legend[$0] ?? Self.fallbackLegend($0)) }
    }

    static func fallbackLegend(_ p: Provenance) -> String {
        switch p {
        case .observed: "Measured directly by a sensor or the phone."
        case .derived: "Calculated from measured data, the same way every time."
        case .estimated: "Inferred or filled in where data was missing."
        }
    }

    static func phoneWord(_ p: PhoneState) -> String {
        switch p {
        case .mounted: "Mounted"
        case .loose: "Loose"
        case .moved: "Moved"
        case .unknown: "Not sure"
        }
    }

    static func reasonWord(_ r: SpanReason) -> String {
        switch r {
        case .phoneMoved: "Phone moved"
        case .notAligned: "Lining up"
        case .lowImuRate: "Slow sensors"
        case .imuGap: "Sensor gap"
        case .appKilled: "App stopped"
        case .lowBattery: "Low battery"
        case .thermal: "Phone too warm"
        case .userPause: "Paused"
        case .noGnss, .tunnel: "No GPS"
        case .estimatedStart: "Estimated start"
        }
    }

    static func reasonText(_ reason: String?) -> String {
        switch reason {
        case "noImu": "No motion sensor data reached the app"
        case "noPreciseLocation": "Precise location was off"
        case "notAligned": "We couldn’t work out which way your phone faced"
        case "lowScoredShare": "Too much of the drive had to be left out"
        case "tooLittleScoredTime": "Too little of the drive could be scored"
        case "lowGnss": "GPS was weak for much of the drive"
        case "noGyro": "This phone has no gyroscope"
        case "lowImuRate": "The sensors ran slower than usual"
        default: "Not enough good data"
        }
    }

    // MARK: Values

    private static let lowImuHz = 40.0

    /// A tile for [metricKey] at its catalogue precision.
    func value(_ metricKey: String, _ label: String, _ si: Double, includesEstimate: Bool = false) -> ProvenancedValue {
        let info = catalog[metricKey]
            ?? MetricInfo(key: metricKey, provenance: .derived, meaning: "", unit: nil, decimals: 1, perSi: 1)
        let provenance = info.provenance(includesEstimate: includesEstimate)
        return ProvenancedValue(
            metricKey: metricKey, label: label, value: info.format(si), unit: info.unit ?? "", provenance: provenance,
            meaning: info.meaning, isEstimated: provenance == .estimated)
    }

    /// Durations in whole minutes, "1 h 12 min" from an hour (brand.md).
    func duration(_ metricKey: String, _ label: String, _ seconds: Double) -> ProvenancedValue {
        var v = value(metricKey, label, seconds)
        let m = minutes(seconds)
        if m >= 60 {
            v.value = "\(m / 60) h \(String(format: "%02d", m % 60))"
        } else {
            v.value = "\(m)"
        }
        v.unit = "min"
        return v
    }

    func minutes(_ seconds: Double) -> Int { Int((max(seconds, 0) / 60).rounded(.toNearestOrAwayFromZero)) }

    /// Distance in km at 0.1 km, the catalogue's precision.
    func km(_ metres: Double) -> String {
        (catalog["distance"] ?? MetricInfo(key: "distance", provenance: .derived, meaning: "", unit: "km", decimals: 1, perSi: 0.001))
            .format(metres)
    }
}
