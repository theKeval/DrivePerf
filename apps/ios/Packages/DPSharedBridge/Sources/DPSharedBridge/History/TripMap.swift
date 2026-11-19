import Foundation

/// Kotlin `TripMapUi`: the S-TRP-01 map hero (DRIVE-55), the route drawn flat from the preview track as the S-ANL-01
/// map card is, with no tiles and no street names. Points are metres east and north of the route's south-west corner.
public struct TripMap: Equatable, Sendable {
    public var widthM: Double
    public var heightM: Double
    /// The route in its quality styles: estimated start dashed, GPS gaps dotted, excluded dimmed.
    public var segments: [AnalysisMapSegment]
    /// Pinned events only; none while the trip's scores are hidden.
    public var pins: [AnalysisMapPin]
    public var stops: [AnalysisMapPoint]
    /// The neutral max-speed triangle.
    public var maxSpeed: AnalysisMapPoint?
    /// Shows the "Estimated start" chip.
    public var estimatedStart: Bool
    /// "Map of your drive, 39.0 km. 2 events, 2 stops."
    public var accessibilityLabel: String

    public init(
        widthM: Double, heightM: Double, segments: [AnalysisMapSegment] = [], pins: [AnalysisMapPin] = [],
        stops: [AnalysisMapPoint] = [], maxSpeed: AnalysisMapPoint? = nil, estimatedStart: Bool = false,
        accessibilityLabel: String = ""
    ) {
        self.widthM = widthM
        self.heightM = heightM
        self.segments = segments
        self.pins = pins
        self.stops = stops
        self.maxSpeed = maxSpeed
        self.estimatedStart = estimatedStart
        self.accessibilityLabel = accessibilityLabel
    }
}

/// Kotlin `TripLinkCopy`: the words of the map hero and of the links to the evidence and the tip (decision 3). The
/// defaults match the shared copy for previews and tests; the core bridge fills them from Kotlin.
public struct TripLinkWords: Equatable, Sendable {
    public var estimatedStart: String
    public var seeTheMoment: String
    public var openEvidence: String
    public var openTip: String
    public var showEvents: String
    public var hideEvents: String

    public init(
        estimatedStart: String = "Estimated start", seeTheMoment: String = "See the moment",
        openEvidence: String = "Opens the evidence", openTip: String = "Opens the tip", showEvents: String = "Show events",
        hideEvents: String = "Hide events"
    ) {
        self.estimatedStart = estimatedStart
        self.seeTheMoment = seeTheMoment
        self.openEvidence = openEvidence
        self.openTip = openTip
        self.showEvents = showEvents
        self.hideEvents = hideEvents
    }
}
