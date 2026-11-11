import Foundation

// Swift mirrors of the trip-quality, correction and provenance types in `com.driveperf.domain.trip` (DRIVE-100,
// DRIVE-167, DRIVE-170, DRIVE-172). The app target maps the Kotlin values into these (`KotlinMapping`); features
// never see Kotlin.

// MARK: - What a trip was (F-CAP-04)

/// Kotlin `TripKind`.
public enum TripKind: String, Equatable, Sendable, Codable {
    case driver, passenger, transit, taxi, notDriving
}

/// Kotlin `TripCorrection`: the user's answer to "what was this?". `driver` undoes a correction.
public enum TripCorrection: String, Equatable, Sendable, Codable, CaseIterable {
    case driver, passenger, transit, bike, walk, other

    /// Out of scores and history, into the "Not drives" filter for 30 days.
    public var isNotADrive: Bool {
        switch self {
        case .transit, .bike, .walk, .other: true
        case .driver, .passenger: false
        }
    }
}

/// The question a trip still asks (Kotlin `Trip.openQuestion`).
public enum TripQuestion: String, Equatable, Sendable, Codable {
    /// "Was this a bus or train?"
    case transit
    /// "Was this a bike ride?"
    case bike
}

/// Kotlin `NotDrives`.
public enum NotDrives {
    public static let retentionDays = 30
}

// MARK: - Data quality (F-DQ-04)

/// Kotlin `QualityBadge`.
public enum QualityBadge: String, Equatable, Sendable, Codable, CaseIterable {
    case good, usable, limited, unscorable
}

/// Kotlin `PhoneState`.
public enum PhoneState: String, Equatable, Sendable, Codable {
    case mounted, loose, moved, unknown
}

/// Kotlin `SegmentReason`: why a stretch was left out or drawn differently.
public enum SpanReason: String, Equatable, Sendable, Codable {
    case estimatedStart, noGnss, tunnel, phoneMoved, appKilled, lowBattery, thermal, userPause, notAligned, lowImuRate
    case imuGap
}

/// Kotlin `OverlayStyle`: how a stretch is drawn (F-DQ-03, F-CAP-05, F-CAP-06).
public enum OverlayStyle: String, Equatable, Sendable, Codable {
    /// Dashed and labelled "Estimated"; never scored.
    case estimatedStart
    /// A GNSS gap under 3 min, bridged with a dashed estimate.
    case shortGap
    /// A GNSS gap of 3 min or more: a labelled break.
    case longGap
    /// Measured but not scored: hatched.
    case excluded
    /// Nothing recorded (the app was stopped): a gap, never scored.
    case interrupted
    case paused
}

/// Kotlin `TrackOverlay`, in seconds from the trip start.
public struct TrackOverlay: Equatable, Sendable, Codable {
    public var style: OverlayStyle
    public var t0S: Double
    public var t1S: Double
    public var reason: SpanReason?

    public init(style: OverlayStyle, t0S: Double, t1S: Double, reason: SpanReason? = nil) {
        self.style = style
        self.t0S = t0S
        self.t1S = t1S
        self.reason = reason
    }
}

/// Kotlin `LeftOut`: time left out of the score for one reason.
public struct LeftOut: Equatable, Sendable, Codable {
    public var reason: SpanReason
    public var seconds: Double

    public init(reason: SpanReason, seconds: Double) {
        self.reason = reason
        self.seconds = seconds
    }
}

/// Kotlin `TripQuality`, with what S-DQ-01 shows already derived (F-DQ-04 AC2).
public struct TripQualitySummary: Equatable, Sendable, Codable {
    public var badge: QualityBadge
    /// The Kotlin `QualityReason` name that set the badge, lower camel case ("lowGnss"); nil for Good.
    public var reason: String?
    public var phone: PhoneState
    /// Delivered motion-sample rate.
    public var imuHz: Double
    public var gnssCoveragePct: Double
    public var alignmentSettledS: Double?
    public var estimatedStartM: Double?
    public var scoredS: Double
    public var excludedS: Double
    public var totalS: Double
    public var leftOut: [LeftOut]
    public var overlays: [TrackOverlay]

    public init(
        badge: QualityBadge, reason: String? = nil, phone: PhoneState = .unknown, imuHz: Double = 50,
        gnssCoveragePct: Double = 100, alignmentSettledS: Double? = nil, estimatedStartM: Double? = nil,
        scoredS: Double, excludedS: Double = 0, totalS: Double, leftOut: [LeftOut] = [], overlays: [TrackOverlay] = []
    ) {
        self.badge = badge
        self.reason = reason
        self.phone = phone
        self.imuHz = imuHz
        self.gnssCoveragePct = gnssCoveragePct
        self.alignmentSettledS = alignmentSettledS
        self.estimatedStartM = estimatedStartM
        self.scoredS = scoredS
        self.excludedS = excludedS
        self.totalS = totalS
        self.leftOut = leftOut
        self.overlays = overlays
    }
}

/// The summary statistics S-TRP-01 shows as tiles, in SI units. Nil when the core did not compute one.
public struct TripStats: Equatable, Sendable, Codable {
    public var movingS: Double?
    public var stoppedS: Double?
    public var stopCount: Int?
    public var avgMovingSpeedMps: Double?
    public var maxSpeedMps: Double?
    public var elevGainM: Double?
    public var elevLossM: Double?
    /// Part of `distanceM` that is estimated (the start and gap fills).
    public var estimatedDistanceM: Double
    /// The start time was filled in from motion history (F-CAP-05).
    public var startEstimated: Bool

    public init(
        movingS: Double? = nil, stoppedS: Double? = nil, stopCount: Int? = nil, avgMovingSpeedMps: Double? = nil,
        maxSpeedMps: Double? = nil, elevGainM: Double? = nil, elevLossM: Double? = nil, estimatedDistanceM: Double = 0,
        startEstimated: Bool = false
    ) {
        self.movingS = movingS
        self.stoppedS = stoppedS
        self.stopCount = stopCount
        self.avgMovingSpeedMps = avgMovingSpeedMps
        self.maxSpeedMps = maxSpeedMps
        self.elevGainM = elevGainM
        self.elevLossM = elevLossM
        self.estimatedDistanceM = estimatedDistanceM
        self.startEstimated = startEstimated
    }
}

// MARK: - Provenance (F-DQ-05)

/// Kotlin `Provenance`.
public enum Provenance: String, Equatable, Sendable, Codable, CaseIterable {
    case observed, derived, estimated

    public var label: String {
        switch self {
        case .observed: "Observed"
        case .derived: "Derived"
        case .estimated: "Estimated"
        }
    }
}

/// Kotlin `Metric` with its `DisplayUnit`: provenance, a one-line meaning and the display precision.
public struct MetricInfo: Equatable, Sendable {
    public var key: String
    public var provenance: Provenance
    public var meaning: String
    /// Unit symbol ("km"); empty for counts and scores, nil when the metric is not a single number.
    public var unit: String?
    /// The most decimals the value may show (F-DQ-05 AC3).
    public var decimals: Int
    /// Multiply the stored SI value by this to get the unit.
    public var perSi: Double
    /// Becomes Estimated when the trip's value includes estimated data.
    public var partlyEstimated: Bool

    public init(
        key: String, provenance: Provenance, meaning: String, unit: String?, decimals: Int, perSi: Double,
        partlyEstimated: Bool = false
    ) {
        self.key = key
        self.provenance = provenance
        self.meaning = meaning
        self.unit = unit
        self.decimals = decimals
        self.perSi = perSi
        self.partlyEstimated = partlyEstimated
    }

    public func provenance(includesEstimate: Bool) -> Provenance {
        includesEstimate && partlyEstimated ? .estimated : provenance
    }

    /// The value at this metric's precision, rounded half away from zero, with a real minus sign: "39.0", "0.42".
    public func format(_ si: Double) -> String {
        let scale = pow(10, Double(decimals))
        let rounded = (si * perSi * scale).rounded(.toNearestOrAwayFromZero) / scale
        let text = String(format: "%.\(decimals)f", abs(rounded))
        return rounded < 0 ? "\u{2212}\(text)" : text
    }
}

/// The provenance catalogue (Kotlin `Metric.entries` and the `Provenance` legend), built once by the app.
public struct ProvenanceCatalog: Equatable, Sendable {
    public var metrics: [String: MetricInfo]
    /// One line per provenance, for "What the labels mean".
    public var legend: [Provenance: String]

    public init(metrics: [MetricInfo] = [], legend: [Provenance: String] = [:]) {
        self.metrics = Dictionary(metrics.map { ($0.key, $0) }, uniquingKeysWith: { first, _ in first })
        self.legend = legend
    }

    public subscript(key: String) -> MetricInfo? { metrics[key] }
}

// MARK: - Ports

/// Kotlin `TripCorrections`: answers and corrections about what a trip was (F-CAP-04).
@MainActor
public protocol TripCorrecting: AnyObject {
    /// Trips corrected to not-a-drive and not yet purged, newest first, now and after every change.
    func observeNotDrives() -> AsyncStream<[TripSummary]>
    /// Answers the trip's open question. Yes moves it to "Not drives"; No keeps it a drive.
    func answer(tripId: String, yes: Bool) async
    /// Corrects what the trip was; `.driver` restores it as a drive.
    func correct(tripId: String, to correction: TripCorrection) async
}
