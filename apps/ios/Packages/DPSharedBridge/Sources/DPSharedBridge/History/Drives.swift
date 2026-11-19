import Foundation

// Swift mirrors of the shared Drives presenter's state, intents and effects (`com.driveperf.presentation.drives`,
// DRIVE-217, ADR-002 §4). Everything is already formatted by the core; the app target maps the Kotlin values into
// these (`CoreDrivesPresenter`). Features never see Kotlin.

/// One trip card (components.md §12).
public struct TripRow: Identifiable, Equatable, Sendable {
    public var id: String
    /// The route label ("The Commute") when the trip has one, else "Thursday morning".
    public var title: String
    /// "Thursday morning · 5 Nov" under a route label, else "5 Nov · 8:14 am".
    public var subtitle: String
    /// "37.9 km"
    public var distance: String
    /// "30 min"
    public var duration: String
    /// One line under the card: short, interrupted or started by you.
    public var note: String?
    public var accessibilityLabel: String
    /// The compact data-quality badge (components.md §10.1); nil before analysis.
    public var quality: QualityBadge?
    /// "Usable"
    public var qualityWord: String?
    /// "Interrupted" tag (F-CAP-06).
    public var isInterrupted: Bool
    /// The open "Was this a bus or train?" question, answered on the card (F-CAP-04).
    public var question: TripQuestion?
    /// Passenger or not a drive: the card is dimmed and doesn't count.
    public var isDimmed: Bool
    /// "Passenger" / "Bus or train" chip in place of the stats emphasis.
    public var kindLabel: String?
    /// The score badge; nil when the score is hidden or before analysis.
    public var score: ScoreBadge?
    /// The car chip, only when the user has more than one car.
    public var car: String?

    public init(
        id: String, title: String, subtitle: String, distance: String, duration: String, note: String? = nil,
        accessibilityLabel: String = "", quality: QualityBadge? = nil, qualityWord: String? = nil,
        isInterrupted: Bool = false, question: TripQuestion? = nil, isDimmed: Bool = false, kindLabel: String? = nil,
        score: ScoreBadge? = nil, car: String? = nil
    ) {
        self.id = id
        self.title = title
        self.subtitle = subtitle
        self.distance = distance
        self.duration = duration
        self.note = note
        self.accessibilityLabel = accessibilityLabel
        self.quality = quality
        self.qualityWord = qualityWord
        self.isInterrupted = isInterrupted
        self.question = question
        self.isDimmed = isDimmed
        self.kindLabel = kindLabel
        self.score = score
        self.car = car
    }
}

/// A week of All drives (S-HIS-01).
public struct TripSection: Identifiable, Equatable, Sendable {
    public var id: String
    /// "This week", "Last week", "19–25 Oct"
    public var title: String
    /// "3 drives · avg 84"
    public var meta: String
    public var rows: [TripRow]
    /// "This week, 3 drives, average 84"
    public var accessibilityLabel: String

    public init(id: String, title: String, meta: String, rows: [TripRow], accessibilityLabel: String? = nil) {
        self.id = id
        self.title = title
        self.meta = meta
        self.rows = rows
        self.accessibilityLabel = accessibilityLabel ?? "\(title), \(meta)"
    }
}

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
    /// The provenance's legend line: "Calculated from measured data, the same way every time."
    public var provenanceMeaning: String
    /// The metric's own line.
    public var meaning: String
    /// Drawn with the estimated token ("≈", 60 %).
    public var isEstimated: Bool
    /// A supporting line: "≈ 0.4 km estimated".
    public var note: String?
    /// The value lowered the quality badge (S-DQ-01 warn tone).
    public var lowersBadge: Bool
    /// "Distance, 39.0 kilometres. Derived."
    public var accessibilityLabel: String

    public init(
        metricKey: String, label: String, value: String, unit: String, provenance: Provenance,
        provenanceMeaning: String = "", meaning: String = "", isEstimated: Bool = false, note: String? = nil,
        lowersBadge: Bool = false, accessibilityLabel: String = ""
    ) {
        self.metricKey = metricKey
        self.label = label
        self.value = value
        self.unit = unit
        self.provenance = provenance
        self.provenanceMeaning = provenanceMeaning
        self.meaning = meaning
        self.isEstimated = isEstimated
        self.note = note
        self.lowersBadge = lowersBadge
        self.accessibilityLabel = accessibilityLabel
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

    public init(tone: Tone, text: String, action: Action? = nil) {
        self.tone = tone
        self.text = text
        self.action = action
    }
}

/// The trip detail's content (S-TRP-01).
public struct TripDetail: Equatable, Sendable {
    public var id: String
    public var title: String
    public var subtitle: String
    public var banners: [TripBanner]
    public var tiles: [ProvenancedValue]
    public var quality: QualityBadge?
    /// "Usable"
    public var qualityWord: String?
    /// The current answer to "What was this?", for the picker's checkmark.
    public var correction: TripCorrection
    public var meta: TripMeta?
    /// Passenger or not a drive: no score, contributors, events or tip; a banner says why (F-RPL-07 AC4).
    public var scoresHidden: Bool
    /// The Smoothness card; nil while hidden or before the score has been read.
    public var score: ScoreSummary?
    public var breakdown: ScoreBreakdown?
    public var events: TripEvents?
    /// "9.5 km with no abrupt inputs."
    public var cleanest: String?
    public var tip: ScoreTip?
    /// False once the detailed data was removed to save space.
    public var canAnalyse: Bool

    public init(
        id: String, title: String, subtitle: String, banners: [TripBanner] = [], tiles: [ProvenancedValue] = [],
        quality: QualityBadge? = nil, qualityWord: String? = nil, correction: TripCorrection = .driver,
        meta: TripMeta? = nil, scoresHidden: Bool = false, score: ScoreSummary? = nil, breakdown: ScoreBreakdown? = nil,
        events: TripEvents? = nil, cleanest: String? = nil, tip: ScoreTip? = nil, canAnalyse: Bool = true
    ) {
        self.id = id
        self.title = title
        self.subtitle = subtitle
        self.banners = banners
        self.tiles = tiles
        self.quality = quality
        self.qualityWord = qualityWord
        self.correction = correction
        self.meta = meta
        self.scoresHidden = scoresHidden
        self.score = score
        self.breakdown = breakdown
        self.events = events
        self.cleanest = cleanest
        self.tip = tip
        self.canAnalyse = canAnalyse
    }
}

/// One stretch of the S-DQ-01 coverage strip, as fractions of the drive.
public struct CoverageSpan: Equatable, Sendable {
    public var style: OverlayStyle
    public var from: Double
    public var to: Double

    public init(style: OverlayStyle, from: Double, to: Double) {
        self.style = style
        self.from = from
        self.to = to
    }
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
    /// "What the labels mean": each provenance and its line.
    public var legend: [(Provenance, String)]

    public init(
        badge: QualityBadge, title: String, hero: String, estimatedStart: String?, coverage: [CoverageSpan],
        scoredLine: String, coverageAccessibility: String, rows: [ProvenancedValue], legend: [(Provenance, String)]
    ) {
        self.badge = badge
        self.title = title
        self.hero = hero
        self.estimatedStart = estimatedStart
        self.coverage = coverage
        self.scoredLine = scoredLine
        self.coverageAccessibility = coverageAccessibility
        self.rows = rows
        self.legend = legend
    }

    public static func == (a: Self, b: Self) -> Bool {
        a.badge == b.badge && a.title == b.title && a.hero == b.hero && a.estimatedStart == b.estimatedStart
            && a.coverage == b.coverage && a.scoredLine == b.scoredLine
            && a.coverageAccessibility == b.coverageAccessibility && a.rows == b.rows
            && a.legend.map(\.0) == b.legend.map(\.0) && a.legend.map(\.1) == b.legend.map(\.1)
    }
}

/// One "What was this?" choice (S-TRP-02 type picker).
public struct CorrectionChoice: Equatable, Sendable {
    public var correction: TripCorrection
    /// "I was a passenger"
    public var title: String

    public init(correction: TripCorrection, title: String) {
        self.correction = correction
        self.title = title
    }
}

/// Kotlin `DrivesState`: what S-HOME-01, S-HIS-01, S-TRP-01 and S-DQ-01 draw.
public struct DrivesScreenState: Equatable, Sendable {
    /// The first trip list has arrived.
    public var hasLoaded: Bool
    /// Drives, newest first.
    public var trips: [TripRow]
    /// The Drives tab's "Latest drives".
    public var latest: [TripRow]
    /// Trips marked as not a drive, kept 30 days (F-CAP-04 AC3).
    public var notDrives: [TripRow]
    /// Drives grouped by calendar week, newest first.
    public var sections: [TripSection]
    /// Answers and corrections are possible.
    public var canCorrect: Bool
    /// The open trip detail, if any.
    public var detail: TripDetail?
    /// The open data-quality sheet, if any.
    public var quality: DataQualityDetail?
    /// "3 drives"
    public var countText: String
    /// "That’s every drive since 5 November."; nil without drives.
    public var sinceText: String?
    /// "What was this?" choices, in order.
    public var corrections: [CorrectionChoice]
    /// S-SCO-02 How scores work.
    public var explainer: HowScoresWork?
    /// Trip edit and delete are possible (DRIVE-131).
    public var canEdit: Bool
    /// S-TRP-02 Edit trip, while it is open.
    public var edit: TripEditForm?
    /// S-TRP-03 Delete trip, while it is open.
    public var deleting: DeleteTripConfirmation?
    /// The toast after S-TRP-02 saved, with Undo.
    public var editMessage: TripEditToast?
    /// S-HIS-01's filter chips and S-HIS-02 (DRIVE-126 AC3); `sections` already holds only the matching drives.
    public var history: HistoryFilters

    public init(
        hasLoaded: Bool = false, trips: [TripRow] = [], latest: [TripRow]? = nil, notDrives: [TripRow] = [], sections: [TripSection] = [],
        canCorrect: Bool = false, detail: TripDetail? = nil, quality: DataQualityDetail? = nil, countText: String = "0 drives",
        sinceText: String? = nil, corrections: [CorrectionChoice] = [], explainer: HowScoresWork? = nil,
        canEdit: Bool = false, edit: TripEditForm? = nil, deleting: DeleteTripConfirmation? = nil,
        editMessage: TripEditToast? = nil, history: HistoryFilters = HistoryFilters()
    ) {
        self.hasLoaded = hasLoaded
        self.trips = trips
        self.latest = latest ?? trips
        self.notDrives = notDrives
        self.sections = sections
        self.canCorrect = canCorrect
        self.detail = detail
        self.quality = quality
        self.countText = countText
        self.sinceText = sinceText
        self.corrections = corrections
        self.explainer = explainer
        self.canEdit = canEdit
        self.edit = edit
        self.deleting = deleting
        self.editMessage = editMessage
        self.history = history
    }
}

/// Kotlin `DrivesIntent`.
public enum DrivesAction: Equatable, Sendable {
    case open(tripId: String)
    case closeDetail
    case openQuality(tripId: String)
    case closeQuality
    case openReplay(tripId: String)
    /// Analysis on trip detail: the presenter answers with `DrivesNavigation.openAnalysis`.
    case openAnalysis(tripId: String)
    /// S-SCO-01 is showing [tripId]: records `score_viewed {surface: breakdown}`.
    case showBreakdown(tripId: String)
    /// S-SCO-02 opened: records `scores_explainer_viewed {source}`. iOS pushes it with a navigation link.
    case openHowScoresWork(source: ExplainerSource)
    /// A contributor row was opened on S-SCO-01: records `contributor_tapped`.
    case tapContributor(ScoreContributorID)
    case answer(tripId: String, yes: Bool)
    case correct(tripId: String, to: TripCorrection)
    /// S-TRP-02 Edit trip and S-TRP-03 Delete trip (DRIVE-131).
    case edit(TripEditCommand)
    /// S-HIS-01's filter chips and S-HIS-02 (DRIVE-126 AC3).
    case history(HistoryFilterCommand)
}

/// Kotlin `DrivesEffect`.
public enum DrivesNavigation: Equatable, Sendable {
    /// Open S-RPL-01: `title` is shown under "Replay", `mapSummary` is the map's accessibility summary.
    case openReplay(tripId: String, title: String, mapSummary: String)
    /// Open S-ANL-01: `title` is shown under "Analysis".
    case openAnalysis(tripId: String, title: String)
    /// S-TRP-03 deleted the trip: leave its detail and show `message` ("Drive deleted").
    case tripDeleted(tripId: String, message: String)
}

/// The shared Drives presenter as features see it (DRIVE-217).
@MainActor
public protocol DrivesPresenting: AnyObject {
    var state: DrivesScreenState { get }
    /// The current state first, then every change.
    func states() -> AsyncStream<DrivesScreenState>
    /// One-shot navigation.
    func navigation() -> AsyncStream<DrivesNavigation>
    func dispatch(_ action: DrivesAction)
}
