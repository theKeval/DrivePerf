import Foundation

// Swift mirrors of the Replay pins (`com.driveperf.presentation.replay.ReplayOverlay`, DRIVE-34, DRIVE-203) and the
// evidence and tip presenters (`com.driveperf.presentation.evidence`, DRIVE-39, DRIVE-97, DRIVE-102). Every word comes
// from the shared presenters; the app target maps the Kotlin values into these.

// MARK: - Pins

/// Kotlin `ReplayEventPin`: one event, as a map pin and a scrubber tick at the same time (F-RPL-03 AC3).
public struct ReplayEventPinInfo: Equatable, Sendable, Identifiable {
    public var id: String
    public var kind: DrivingEventType
    public var tPeakS: Double
    public var lat: Double?
    public var lng: Double?
    /// Marked "Needed" (F-SCO-05): drawn hollow and not counted (AC4).
    public var needed: Bool
    /// Positioned and in measured time (AC5, EC-GPS); unpinned events only tick the timeline.
    public var pinned: Bool
    /// "Hard braking, 5:56 pm. Peak 0.40 g."
    public var accessibilityLabel: String

    public init(
        id: String, kind: DrivingEventType, tPeakS: Double, lat: Double?, lng: Double?, needed: Bool, pinned: Bool,
        accessibilityLabel: String
    ) {
        self.id = id
        self.kind = kind
        self.tPeakS = tPeakS
        self.lat = lat
        self.lng = lng
        self.needed = needed
        self.pinned = pinned
        self.accessibilityLabel = accessibilityLabel
    }
}

/// Kotlin `StopPin`: a stop of at least 10 s, numbered from 1 (F-RPL-02 AC3).
public struct ReplayStopPinInfo: Equatable, Sendable, Identifiable {
    public var number: Int
    public var atS: Double
    public var durationS: Double
    public var lat: Double?
    public var lng: Double?
    public var id: Int { number }

    public init(number: Int, atS: Double, durationS: Double, lat: Double?, lng: Double?) {
        self.number = number
        self.atS = atS
        self.durationS = durationS
        self.lat = lat
        self.lng = lng
    }
}

/// Kotlin `MaxSpeedPin`: neutral, never animated (D5/D6).
public struct ReplayMaxSpeedPinInfo: Equatable, Sendable {
    public var tS: Double
    public var lat: Double
    public var lng: Double

    public init(tS: Double, lat: Double, lng: Double) {
        self.tS = tS
        self.lat = lat
        self.lng = lng
    }
}

/// One elevation sample of the mini profile (F-RPL-02 AC4).
public struct ReplayElevationSample: Equatable, Sendable {
    public var tS: Double
    /// 0 at the drive's lowest point, 1 at its highest.
    public var fraction: Double

    public init(tS: Double, fraction: Double) {
        self.tS = tS
        self.fraction = fraction
    }
}

/// Kotlin `ReplayOverlay`: what S-RPL-01 draws over the route besides the marker, with its words.
public protocol ReplayOverlayReading: AnyObject, Sendable {
    /// Pinned events in time order.
    var eventPins: [ReplayEventPinInfo] { get }
    /// Every event, as scrubber ticks.
    var ticks: [ReplayEventPinInfo] { get }
    var stopPins: [ReplayStopPinInfo] { get }
    var stopTicks: [ReplayStopPinInfo] { get }
    var stopCount: Int { get }
    var maxSpeed: ReplayMaxSpeedPinInfo? { get }
    /// "103 km/h max · 2 stops · 2 events".
    var summary: String { get }
    /// "No abrupt inputs on this drive." with no events at all.
    var noEventsText: String? { get }
    /// "↗ 118 m ↘ 103 m", or nil without elevation data.
    var elevationText: String? { get }
    var elevation: [ReplayElevationSample] { get }
    /// "Map of your drive, 39.0 km. 2 events, 2 stops."
    var mapSummary: String { get }
    var speedUnit: String { get }
    var distanceUnit: String { get }
    /// The distance readout at `tS`, scaled to the trip's own distance: "12.3".
    func distanceText(at tS: Double) -> String
    /// The speed readout: "92", or "—" with no measured speed.
    func speedText(_ speedMps: Double?) -> String
    /// The elevation cursor's height at `tS`, 0–1.
    func elevationFraction(at tS: Double) -> Double
    /// Events whose peak the playhead passed going from `fromS` to `toS`: each pin pulses once (F-RPL-03 AC2).
    func crossed(from fromS: Double, to toS: Double) -> [String]
}

/// Kotlin `ReplaySelection`.
public enum ReplaySelectionInfo: Equatable, Sendable {
    case event(String)
    case stop(Int)
    case maxSpeed
}

/// Kotlin `Provenance` (F-DQ-05).
public enum ReplayProvenance: String, Equatable, Sendable {
    case observed, derived, estimated

    /// "Observed", "Derived", "Estimated".
    public var label: String { rawValue.prefix(1).uppercased() + rawValue.dropFirst() }
}

/// Kotlin `ReplayPinSheet`: S-RPL-03. The value is plain text, never accent or coloured.
public struct ReplayPinSheetInfo: Equatable, Sendable {
    public enum Kind: Equatable, Sendable { case stop, maxSpeed }

    public var kind: Kind
    public var title: String
    public var subtitle: String
    public var value: String
    public var unit: String
    public var provenance: ReplayProvenance
    public var rows: [Row]
    public var note: String
    public var accessibilityLabel: String

    public struct Row: Equatable, Sendable, Identifiable {
        public var label: String
        public var value: String
        public var id: String { label }

        public init(label: String, value: String) {
            self.label = label
            self.value = value
        }
    }

    public init(
        kind: Kind, title: String, subtitle: String, value: String, unit: String, provenance: ReplayProvenance,
        rows: [Row], note: String, accessibilityLabel: String
    ) {
        self.kind = kind
        self.title = title
        self.subtitle = subtitle
        self.value = value
        self.unit = unit
        self.provenance = provenance
        self.rows = rows
        self.note = note
        self.accessibilityLabel = accessibilityLabel
    }
}

// MARK: - S-RPL-02 evidence

/// Kotlin `EventEvidence` for the chart: times relative to nothing in particular (trip seconds), speed already in the
/// user's unit.
public struct EvidenceTraceInfo: Equatable, Sendable {
    public struct G: Equatable, Sendable {
        public var tS: Double
        public var lonG: Double
        public var latG: Double

        public init(tS: Double, lonG: Double, latG: Double) {
            self.tS = tS
            self.lonG = lonG
            self.latG = latG
        }
    }

    public struct Speed: Equatable, Sendable {
        public var tS: Double
        public var value: Double

        public init(tS: Double, value: Double) {
            self.tS = tS
            self.value = value
        }
    }

    public var t0S: Double
    public var t1S: Double
    public var tStartS: Double
    public var tPeakS: Double
    public var tEndS: Double
    public var g: [G]
    public var speed: [Speed]

    public init(
        t0S: Double, t1S: Double, tStartS: Double, tPeakS: Double, tEndS: Double, g: [G], speed: [Speed]
    ) {
        self.t0S = t0S
        self.t1S = t1S
        self.tStartS = tStartS
        self.tPeakS = tPeakS
        self.tEndS = tEndS
        self.g = g
        self.speed = speed
    }
}

/// Kotlin `EvidenceFact`.
public struct EvidenceFactInfo: Equatable, Sendable, Identifiable {
    public var label: String
    public var value: String
    public var unit: String
    public var provenance: ReplayProvenance
    public var struck: Bool
    public var spoken: String
    public var id: String { label }

    public init(label: String, value: String, unit: String, provenance: ReplayProvenance, struck: Bool, spoken: String) {
        self.label = label
        self.value = value
        self.unit = unit
        self.provenance = provenance
        self.struck = struck
        self.spoken = spoken
    }
}

/// Kotlin `EvidenceBanner`.
public struct EvidenceBannerInfo: Equatable, Sendable, Identifiable {
    public enum Kind: String, Equatable, Sendable { case lowConfidence, needed, traceRemoved }

    public var kind: Kind
    public var text: String
    /// "Count it again" on the needed banner.
    public var action: String?
    public var id: String { kind.rawValue }

    public init(kind: Kind, text: String, action: String?) {
        self.kind = kind
        self.text = text
        self.action = action
    }
}

/// Kotlin `EvidenceTip`: the related tip (F-RPL-04 AC3).
public struct EvidenceTipInfo: Equatable, Sendable {
    public var tipId: String
    public var kicker: String
    public var title: String
    public var text: String
    public var thisDrive: Bool

    public init(tipId: String, kicker: String, title: String, text: String, thisDrive: Bool) {
        self.tipId = tipId
        self.kicker = kicker
        self.title = title
        self.text = text
        self.thisDrive = thisDrive
    }
}

/// Kotlin `NeededSheet`: S-SCO-03.
public struct NeededSheetInfo: Equatable, Sendable {
    public enum Step: Equatable, Sendable { case ask, done, countAgain }

    public var step: Step
    public var title: String
    public var body: String
    public var primary: String
    public var secondary: String?
    public var score: Int?
    /// Decision D4 in words.
    public var capNote: String

    public init(
        step: Step, title: String, body: String, primary: String, secondary: String?, score: Int?, capNote: String
    ) {
        self.step = step
        self.title = title
        self.body = body
        self.primary = primary
        self.secondary = secondary
        self.score = score
        self.capNote = capNote
    }
}

/// Kotlin `InaccurateReason`.
public enum EvidenceInaccurateReason: String, Equatable, Sendable, CaseIterable, Identifiable {
    case phoneMoved, pothole, notMe, other
    public var id: String { rawValue }
}

/// Kotlin `EvidenceState`.
public struct EvidenceScreenState: Equatable, Sendable {
    public var eventId: String
    public var loading: Bool
    public var missing: Bool
    public var kind: DrivingEventType?
    public var needed: Bool
    public var title: String
    public var subtitle: String
    public var banners: [EvidenceBannerInfo]
    public var trace: EvidenceTraceInfo?
    public var traceSummary: String
    public var facts: [EvidenceFactInfo]
    public var tip: EvidenceTipInfo?
    public var position: String
    public var hasPrevious: Bool
    public var hasNext: Bool
    public var neededSheet: NeededSheetInfo?
    public var reasonsOpen: Bool
    public var reasons: [(reason: EvidenceInaccurateReason, label: String)]
    public var selectedReason: EvidenceInaccurateReason?
    public var busy: Bool
    public var accessibilityLabel: String
    public var speedUnit: String
    /// The section titles and buttons, from the shared copy.
    public var words: EvidenceWords

    public init(
        eventId: String, loading: Bool = true, missing: Bool = false, kind: DrivingEventType? = nil,
        needed: Bool = false, title: String = "", subtitle: String = "", banners: [EvidenceBannerInfo] = [],
        trace: EvidenceTraceInfo? = nil, traceSummary: String = "", facts: [EvidenceFactInfo] = [],
        tip: EvidenceTipInfo? = nil, position: String = "", hasPrevious: Bool = false, hasNext: Bool = false,
        neededSheet: NeededSheetInfo? = nil, reasonsOpen: Bool = false,
        reasons: [(reason: EvidenceInaccurateReason, label: String)] = [], selectedReason: EvidenceInaccurateReason? = nil,
        busy: Bool = false, accessibilityLabel: String = "", speedUnit: String = "km/h", words: EvidenceWords = EvidenceWords()
    ) {
        self.eventId = eventId
        self.loading = loading
        self.missing = missing
        self.kind = kind
        self.needed = needed
        self.title = title
        self.subtitle = subtitle
        self.banners = banners
        self.trace = trace
        self.traceSummary = traceSummary
        self.facts = facts
        self.tip = tip
        self.position = position
        self.hasPrevious = hasPrevious
        self.hasNext = hasNext
        self.neededSheet = neededSheet
        self.reasonsOpen = reasonsOpen
        self.reasons = reasons
        self.selectedReason = selectedReason
        self.busy = busy
        self.accessibilityLabel = accessibilityLabel
        self.speedUnit = speedUnit
        self.words = words
    }

    public static func == (a: EvidenceScreenState, b: EvidenceScreenState) -> Bool {
        a.eventId == b.eventId && a.loading == b.loading && a.missing == b.missing && a.needed == b.needed
            && a.facts == b.facts && a.banners == b.banners && a.neededSheet == b.neededSheet
            && a.reasonsOpen == b.reasonsOpen && a.selectedReason == b.selectedReason && a.busy == b.busy
            && a.trace == b.trace && a.tip == b.tip && a.position == b.position
    }
}

/// The shared copy S-RPL-02 needs for its fixed labels (Kotlin `EvidenceCopy`).
public struct EvidenceWords: Equatable, Sendable {
    public var wasItNeeded = "Was it needed?"
    public var notAccurate = "Not accurate?"
    public var reasonsTitle = "What went wrong?"
    public var reasonsBody = ""
    public var send = "Send"
    public var cancel = "Cancel"
    public var whyThisTip = "Why this tip"

    public init() {}

    public init(
        wasItNeeded: String, notAccurate: String, reasonsTitle: String, reasonsBody: String, send: String,
        cancel: String, whyThisTip: String
    ) {
        self.wasItNeeded = wasItNeeded
        self.notAccurate = notAccurate
        self.reasonsTitle = reasonsTitle
        self.reasonsBody = reasonsBody
        self.send = send
        self.cancel = cancel
        self.whyThisTip = whyThisTip
    }
}

/// Kotlin `EvidenceIntent`.
public enum EvidenceAction: Equatable, Sendable {
    case askNeeded, confirmNeeded, askCountAgain, confirmCountAgain, dismissNeeded
    case openReasons, selectReason(EvidenceInaccurateReason), sendReason, cancelReasons
    case previous, next, openTip, close
}

/// Kotlin `EvidenceEffect`.
public enum EvidenceNavigation: Equatable, Sendable {
    /// The sheet stepped to another event: move the replay underneath.
    case showEvent(String)
    /// Feedback changed: the replay reads its pins again.
    case feedbackChanged
    case openTip(tipId: String, eventId: String)
    case toast(String)
    case close
}

/// Kotlin `EvidencePresenter`.
@MainActor
public protocol EvidencePresenting: AnyObject {
    var state: EvidenceScreenState { get }
    func states() -> AsyncStream<EvidenceScreenState>
    func navigation() -> AsyncStream<EvidenceNavigation>
    func dispatch(_ action: EvidenceAction)
    func close()
}

// MARK: - S-SCO-04 tip detail

/// Kotlin `TipDetailState`.
public struct TipDetailScreenState: Equatable, Sendable {
    public var loading: Bool
    public var title: String
    public var tipId: String?
    public var cleanest: String?
    public var kicker: String
    public var tipText: String?
    public var why: String
    public var sparkline: [Double]
    public var sparklinePeakIndex: Int
    public var eventId: String?
    public var steps: [String]
    public var footer: String
    public var noTip: String?
    public var words: TipWords

    public init(
        loading: Bool = true, title: String = "", tipId: String? = nil, cleanest: String? = nil, kicker: String = "",
        tipText: String? = nil, why: String = "", sparkline: [Double] = [], sparklinePeakIndex: Int = -1,
        eventId: String? = nil, steps: [String] = [], footer: String = "", noTip: String? = nil,
        words: TipWords = TipWords()
    ) {
        self.loading = loading
        self.title = title
        self.tipId = tipId
        self.cleanest = cleanest
        self.kicker = kicker
        self.tipText = tipText
        self.why = why
        self.sparkline = sparkline
        self.sparklinePeakIndex = sparklinePeakIndex
        self.eventId = eventId
        self.steps = steps
        self.footer = footer
        self.noTip = noTip
        self.words = words
    }
}

/// The shared copy S-SCO-04 needs for its section titles (Kotlin `EvidenceCopy`).
public struct TipWords: Equatable, Sendable {
    public var cleanest = "Cleanest stretch"
    public var whyThisOne = "Why this one"
    public var howToTry = "How to try it"
    public var seeTheMoment = "See the moment"
    public var noTipTitle = "No tip this time."

    public init() {}

    public init(cleanest: String, whyThisOne: String, howToTry: String, seeTheMoment: String, noTipTitle: String) {
        self.cleanest = cleanest
        self.whyThisOne = whyThisOne
        self.howToTry = howToTry
        self.seeTheMoment = seeTheMoment
        self.noTipTitle = noTipTitle
    }
}

/// Kotlin `TipDetailIntent`.
public enum TipDetailAction: Equatable, Sendable {
    case seeTheMoment, back
}

/// Kotlin `TipDetailEffect`.
public enum TipDetailNavigation: Equatable, Sendable {
    case openEvidence(eventId: String)
    case close
}

/// Kotlin `TipDetailPresenter`.
@MainActor
public protocol TipDetailPresenting: AnyObject {
    var state: TipDetailScreenState { get }
    func states() -> AsyncStream<TipDetailScreenState>
    func navigation() -> AsyncStream<TipDetailNavigation>
    func dispatch(_ action: TipDetailAction)
    func close()
}
