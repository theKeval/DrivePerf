import Foundation

// Swift mirrors of the shared score surfaces (`com.driveperf.presentation.score`, DRIVE-77, DRIVE-82, DRIVE-87,
// DRIVE-113, DRIVE-55). Everything is already worded by the core; `CoreDrivesPresenter` maps the Kotlin values.

/// Kotlin `ComparisonTone`: the comparison chip's look. Lower is neutral grey, never red.
public enum ScoreComparisonTone: Equatable, Sendable {
    case better, lower, same, calibrating
}

/// Kotlin `ComparisonUi`: "▲ +2 vs your usual on The Commute", "Calibrating your baseline · 2 of 3".
public struct ScoreComparison: Equatable, Sendable {
    public var tone: ScoreComparisonTone
    /// "▲", "▼" or "●"; empty while calibrating.
    public var glyph: String
    public var text: String
    /// The "k of 3" dots while calibrating; 0 otherwise.
    public var calibratingK: Int
    public var calibratingOf: Int

    public init(tone: ScoreComparisonTone, glyph: String, text: String, calibratingK: Int = 0, calibratingOf: Int = 0) {
        self.tone = tone
        self.glyph = glyph
        self.text = text
        self.calibratingK = calibratingK
        self.calibratingOf = calibratingOf
    }
}

/// Kotlin `EventRowUi`: one detected event (components.md §9.2).
public struct ScoreEventRow: Identifiable, Equatable, Sendable {
    public var id: String
    public var type: DrivingEventType
    /// "Hard braking"
    public var label: String
    /// "8:14 am"
    public var time: String
    /// "0.42 g · 1.4 s"
    public var meta: String
    public var isNeeded: Bool
    public var isExcluded: Bool
    /// "Needed", "Not counted" or "Low confidence".
    public var tag: String?
    public var accessibilityLabel: String

    public init(
        id: String, type: DrivingEventType, label: String, time: String, meta: String, isNeeded: Bool = false,
        isExcluded: Bool = false, tag: String? = nil, accessibilityLabel: String = ""
    ) {
        self.id = id
        self.type = type
        self.label = label
        self.time = time
        self.meta = meta
        self.isNeeded = isNeeded
        self.isExcluded = isExcluded
        self.tag = tag
        self.accessibilityLabel = accessibilityLabel
    }
}

/// Kotlin `EventCountUi`.
public struct ScoreEventCount: Equatable, Sendable {
    public var type: DrivingEventType
    /// "Braking"
    public var label: String
    public var count: Int

    public init(type: DrivingEventType, label: String, count: Int) {
        self.type = type
        self.label = label
        self.count = count
    }
}

/// Kotlin `ContributorUi`: one of the four contributors (S-SCO-01 "What moved it").
public struct ScoreContributor: Identifiable, Equatable, Sendable {
    public var id: ScoreContributorID
    /// "Braking"
    public var label: String
    /// Nil while calibrating.
    public var band: ScoreBand?
    /// "▲", "●" or "▼"
    public var bandGlyph: String?
    /// "Smoother than usual"
    public var bandText: String?
    /// "2 abrupt stops"
    public var reason: String
    public var events: [ScoreEventRow]
    public var accessibilityLabel: String

    public init(
        id: ScoreContributorID, label: String, band: ScoreBand? = nil, bandGlyph: String? = nil, bandText: String? = nil,
        reason: String, events: [ScoreEventRow] = [], accessibilityLabel: String = ""
    ) {
        self.id = id
        self.label = label
        self.band = band
        self.bandGlyph = bandGlyph
        self.bandText = bandText
        self.reason = reason
        self.events = events
        self.accessibilityLabel = accessibilityLabel
    }
}

/// Kotlin `ScoreUi`: the Smoothness ring and what sits around it (S-TRP-01 score card, S-SCO-01 hero).
public struct ScoreSummary: Equatable, Sendable {
    /// Nil: "Not scored".
    public var smoothness: Int?
    /// The baseline notch; nil while calibrating.
    public var baseline: Int?
    public var comparison: ScoreComparison?
    /// "New route record · beat 89 from 14 Oct"
    public var recordLine: String?
    /// "Score based on 31 of 34 min"
    public var basedOn: String?
    /// "Unusual pace — not counted for records"
    public var paceNote: String?
    public var notScoredReason: String?
    public var contributors: [ScoreContributor]
    public var accessibilityLabel: String

    public var isScored: Bool { smoothness != nil }

    public init(
        smoothness: Int?, baseline: Int? = nil, comparison: ScoreComparison? = nil, recordLine: String? = nil,
        basedOn: String? = nil, paceNote: String? = nil, notScoredReason: String? = nil, contributors: [ScoreContributor] = [],
        accessibilityLabel: String = ""
    ) {
        self.smoothness = smoothness
        self.baseline = baseline
        self.comparison = comparison
        self.recordLine = recordLine
        self.basedOn = basedOn
        self.paceNote = paceNote
        self.notScoredReason = notScoredReason
        self.contributors = contributors
        self.accessibilityLabel = accessibilityLabel
    }
}

/// Kotlin `BasisOptionUi`: one choice of S-SCO-01 "Compared with".
public struct ScoreBasisOption: Equatable, Sendable {
    public var title: String
    public var detail: String
    public var isSelected: Bool
    /// "usual 83"
    public var value: String?

    public init(title: String, detail: String, isSelected: Bool, value: String? = nil) {
        self.title = title
        self.detail = detail
        self.isSelected = isSelected
        self.value = value
    }
}

/// Kotlin `BreakdownUi`: S-SCO-01 beyond the hero.
public struct ScoreBreakdown: Equatable, Sendable {
    public var basisLine: String
    public var options: [ScoreBasisOption]
    public var optionsFooter: String
    /// "Your last 6 runs of The Commute"; nil with fewer than three.
    public var runsTitle: String?
    public var runs: [Int]
    public var runsSummary: String?
    public var contributorsFooter: String
    public var notScoredText: String?
    public var notScoredFooter: String?

    public init(
        basisLine: String, options: [ScoreBasisOption] = [], optionsFooter: String = "", runsTitle: String? = nil,
        runs: [Int] = [], runsSummary: String? = nil, contributorsFooter: String = "", notScoredText: String? = nil,
        notScoredFooter: String? = nil
    ) {
        self.basisLine = basisLine
        self.options = options
        self.optionsFooter = optionsFooter
        self.runsTitle = runsTitle
        self.runs = runs
        self.runsSummary = runsSummary
        self.contributorsFooter = contributorsFooter
        self.notScoredText = notScoredText
        self.notScoredFooter = notScoredFooter
    }
}

/// Kotlin `TipUi`: the drive's one coaching tip.
public struct ScoreTip: Equatable, Sendable {
    public var tipId: String
    public var category: ScoreTipCategory
    /// "One thing for next time"
    public var kicker: String
    public var title: String
    /// "Next time, …"
    public var text: String
    public var why: String
    /// The event "See the moment" opens (S-RPL-02); nil for a tip with no counted event.
    public var eventId: String?

    public init(
        tipId: String, category: ScoreTipCategory, kicker: String, title: String, text: String, why: String,
        eventId: String? = nil
    ) {
        self.eventId = eventId
        self.tipId = tipId
        self.category = category
        self.kicker = kicker
        self.title = title
        self.text = text
        self.why = why
    }
}

/// Kotlin `ScoreBadgeUi`: a trip card's score badge.
public struct ScoreBadge: Equatable, Sendable {
    /// "85"; nil for "Not scored".
    public var score: String?
    /// "+2"; nil while calibrating.
    public var delta: String?
    public var tone: ScoreComparisonTone
    public var isNewRecord: Bool
    public var notScoredText: String?

    public init(score: String?, delta: String? = nil, tone: ScoreComparisonTone = .same, isNewRecord: Bool = false, notScoredText: String? = nil) {
        self.score = score
        self.delta = delta
        self.tone = tone
        self.isNewRecord = isNewRecord
        self.notScoredText = notScoredText
    }
}

/// Kotlin `TripEventsUi`: "Events · n".
public struct TripEvents: Equatable, Sendable {
    public var title: String
    public var counts: [ScoreEventCount]
    public var rows: [ScoreEventRow]
    public var emptyTitle: String
    public var emptyBody: String
    /// "Events aren’t detected when the phone moves around freely."
    public var note: String?

    public init(title: String, counts: [ScoreEventCount] = [], rows: [ScoreEventRow] = [], emptyTitle: String = "", emptyBody: String = "", note: String? = nil) {
        self.title = title
        self.counts = counts
        self.rows = rows
        self.emptyTitle = emptyTitle
        self.emptyBody = emptyBody
        self.note = note
    }
}

/// Kotlin `TripMetaUi`: car chip · how it started · start–end.
public struct TripMeta: Equatable, Sendable {
    public var car: String?
    public var isPassenger: Bool
    /// "Auto-detected"
    public var source: String
    /// "8:14 am–8:52 am"
    public var timeRange: String

    public init(car: String?, isPassenger: Bool, source: String, timeRange: String) {
        self.car = car
        self.isPassenger = isPassenger
        self.source = source
        self.timeRange = timeRange
    }
}

/// Kotlin `ExplainerSectionKind`.
public enum ExplainerSectionKind: Equatable, Sendable {
    case text, events, quality, provenance
}

/// Kotlin `ExplainerSectionUi`.
public struct ExplainerSection: Equatable, Sendable {
    public var kind: ExplainerSectionKind
    public var title: String
    public var paragraphs: [String]
    public var note: String?

    public init(kind: ExplainerSectionKind, title: String, paragraphs: [String] = [], note: String? = nil) {
        self.kind = kind
        self.title = title
        self.paragraphs = paragraphs
        self.note = note
    }
}

/// Kotlin `ThresholdUi`: "Hard braking · 0.30 g or more".
public struct ExplainerThreshold: Equatable, Sendable {
    public var type: DrivingEventType
    public var label: String
    public var text: String

    public init(type: DrivingEventType, label: String, text: String) {
        self.type = type
        self.label = label
        self.text = text
    }
}

/// Kotlin `ProvenanceLineUi`: one provenance with its meaning.
public struct ExplainerProvenance: Equatable, Sendable {
    public var provenance: Provenance
    public var label: String
    public var meaning: String

    public init(provenance: Provenance, label: String, meaning: String) {
        self.provenance = provenance
        self.label = label
        self.meaning = meaning
    }
}

/// Kotlin `HowScoresWorkUi`: S-SCO-02.
public struct HowScoresWork: Equatable, Sendable {
    public var title: String
    public var exampleScore: Int
    public var exampleBaseline: Int
    public var exampleAccessibility: String
    public var heroTitle: String
    public var heroBody: String
    public var notchKey: String
    public var sections: [ExplainerSection]
    public var thresholds: [ExplainerThreshold]
    public var provenance: [ExplainerProvenance]
    /// "Scoring version smooth-1.0.0 · updated 10 November 2026"
    public var versionLine: String

    public init(
        title: String, exampleScore: Int, exampleBaseline: Int, exampleAccessibility: String, heroTitle: String,
        heroBody: String, notchKey: String, sections: [ExplainerSection], thresholds: [ExplainerThreshold], provenance: [ExplainerProvenance] = [],
        versionLine: String
    ) {
        self.title = title
        self.exampleScore = exampleScore
        self.exampleBaseline = exampleBaseline
        self.exampleAccessibility = exampleAccessibility
        self.heroTitle = heroTitle
        self.heroBody = heroBody
        self.notchKey = notchKey
        self.sections = sections
        self.thresholds = thresholds
        self.provenance = provenance
        self.versionLine = versionLine
    }
}

/// Kotlin `ExplainerSource`: where S-SCO-02 was opened from.
public enum ExplainerSource: Equatable, Sendable {
    case tripDetail, breakdown
}
