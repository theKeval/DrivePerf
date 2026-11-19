import Foundation

// Swift mirrors of `com.driveperf.presentation.analysis` (S-ANL-01/02/03, DRIVE-45 and DRIVE-50). The app target maps
// the Kotlin presenter (`CoreAnalysis.swift`); features never see Kotlin. Every x is a share of the visible timeline
// window, 0…1 left to right, and every lane y a share of the lane height, 0…1 bottom to top.

/// Kotlin `AnalysisChannel`.
public enum AnalysisChannelKind: String, Equatable, Sendable, CaseIterable {
    case speed, lonG, latG, elevation, jerk, quality
}

/// Kotlin `AnalysisAxis`.
public enum AnalysisAxisKind: String, Equatable, Sendable, CaseIterable {
    case time, distance
}

/// Kotlin `LegendKey`.
public enum AnalysisLegendKind: String, Equatable, Sendable {
    case speed, lonG, latG, elevation, jerk, estimated, noGps, notScored
}

/// Kotlin `TimelineLane`. `xs`/`ys` are the lane's polyline (a `nan` y breaks it); empty for sample quality.
public struct AnalysisLane: Equatable, Sendable, Identifiable {
    public var channel: AnalysisChannelKind
    public var title: String
    public var rangeLabel: String
    public var xs: [Double]
    public var ys: [Double]
    public var hasData: Bool
    public var dashedUntilX: Double
    public var zeroY: Double?

    public var id: AnalysisChannelKind { channel }

    public init(
        channel: AnalysisChannelKind, title: String, rangeLabel: String, xs: [Double] = [], ys: [Double] = [],
        hasData: Bool = true, dashedUntilX: Double = 0, zeroY: Double? = nil
    ) {
        self.channel = channel
        self.title = title
        self.rangeLabel = rangeLabel
        self.xs = xs
        self.ys = ys
        self.hasData = hasData
        self.dashedUntilX = dashedUntilX
        self.zeroY = zeroY
    }
}

/// Kotlin `TimelineBand`: estimated start, no GPS, excluded.
public struct AnalysisBand: Equatable, Sendable {
    public var style: ReplaySegmentStyle
    public var x0: Double
    public var x1: Double
    public var label: String?

    public init(style: ReplaySegmentStyle, x0: Double, x1: Double, label: String? = nil) {
        self.style = style
        self.x0 = x0
        self.x1 = x1
        self.label = label
    }
}

/// Kotlin `EventTick`.
public struct AnalysisTick: Equatable, Sendable, Identifiable {
    public var eventId: String
    public var type: DrivingEventType
    public var needed: Bool
    public var x: Double
    public var tPeakS: Double
    public var accessibilityLabel: String

    public var id: String { eventId }

    public init(eventId: String, type: DrivingEventType, needed: Bool, x: Double, tPeakS: Double, accessibilityLabel: String) {
        self.eventId = eventId
        self.type = type
        self.needed = needed
        self.x = x
        self.tPeakS = tPeakS
        self.accessibilityLabel = accessibilityLabel
    }
}

/// Kotlin `AxisTick` and `LegendItem`.
public struct AnalysisAxisTick: Equatable, Sendable {
    public var x: Double
    public var label: String

    public init(x: Double, label: String) {
        self.x = x
        self.label = label
    }
}

public struct AnalysisLegendItem: Equatable, Sendable, Identifiable {
    public var kind: AnalysisLegendKind
    public var label: String

    public var id: AnalysisLegendKind { kind }

    public init(kind: AnalysisLegendKind, label: String) {
        self.kind = kind
        self.label = label
    }
}

/// A point of the friction circle: lateral G across, longitudinal G up.
public struct AnalysisGPoint: Equatable, Sendable {
    public var latG: Double
    public var lonG: Double

    public init(latG: Double, lonG: Double) {
        self.latG = latG
        self.lonG = lonG
    }
}

/// A point on the map card, in metres east and north of its south-west corner.
public struct AnalysisMapPoint: Equatable, Sendable {
    public var x: Double
    public var y: Double

    public init(x: Double, y: Double) {
        self.x = x
        self.y = y
    }
}

/// Kotlin `LaneValue`: the bubble text ("—" in a gap) and its height (`nan` when none).
public struct AnalysisLaneValue: Equatable, Sendable {
    public var channel: AnalysisChannelKind
    public var text: String
    public var y: Double

    public init(channel: AnalysisChannelKind, text: String, y: Double) {
        self.channel = channel
        self.text = text
        self.y = y
    }
}

/// Kotlin `CursorReadout`: everything under the cursor, built in the frame of the drag (F-RPL-05 AC2).
public struct AnalysisCursorReadout: Equatable, Sendable {
    public var tS: Double
    public var x: Double
    public var headline: String
    public var chip: String?
    public var values: [AnalysisLaneValue]
    public var marker: AnalysisMapPoint?
    public var gg: AnalysisGPoint?
    public var accessibilityValue: String

    public init(
        tS: Double = 0, x: Double = 0, headline: String = "", chip: String? = nil, values: [AnalysisLaneValue] = [],
        marker: AnalysisMapPoint? = nil, gg: AnalysisGPoint? = nil, accessibilityValue: String = ""
    ) {
        self.tS = tS
        self.x = x
        self.headline = headline
        self.chip = chip
        self.values = values
        self.marker = marker
        self.gg = gg
        self.accessibilityValue = accessibilityValue
    }
}

/// One "View as table" / Audio Graph row.
public struct AnalysisTableRow: Equatable, Sendable {
    public var time: String
    public var speed: String
    public var lonG: String
    public var latG: String
    public var note: String?

    public init(time: String, speed: String, lonG: String, latG: String, note: String? = nil) {
        self.time = time
        self.speed = speed
        self.lonG = lonG
        self.latG = latG
        self.note = note
    }
}

/// Kotlin `AnalysisTimeline`: the visible window, built on zoom, axis or channel changes; the lookups run per frame.
@MainActor
public protocol AnalysisTimelineView: AnyObject, Sendable {
    var axis: AnalysisAxisKind { get }
    var lanes: [AnalysisLane] { get }
    var bands: [AnalysisBand] { get }
    var ticks: [AnalysisTick] { get }
    var axisTicks: [AnalysisAxisTick] { get }
    var legend: [AnalysisLegendItem] { get }
    var summary: String { get }
    func timeAt(_ x: Double) -> Double
    func xOf(_ tS: Double) -> Double
    /// Snaps to a tick within `tolerance` (a share of the width).
    func snap(_ x: Double, tolerance: Double) -> (tS: Double, eventId: String?)
    func readout(_ tS: Double) -> AnalysisCursorReadout
    func tableRows() -> [AnalysisTableRow]
}

/// Kotlin `MapSegment` and `MapPin`.
public struct AnalysisMapSegment: Equatable, Sendable {
    public var style: ReplaySegmentStyle
    public var points: [AnalysisMapPoint]

    public init(style: ReplaySegmentStyle, points: [AnalysisMapPoint]) {
        self.style = style
        self.points = points
    }
}

public struct AnalysisMapPin: Equatable, Sendable, Identifiable {
    public var eventId: String
    public var type: DrivingEventType
    public var needed: Bool
    public var at: AnalysisMapPoint

    public var id: String { eventId }

    public init(eventId: String, type: DrivingEventType, needed: Bool, at: AnalysisMapPoint) {
        self.eventId = eventId
        self.type = type
        self.needed = needed
        self.at = at
    }
}

/// Kotlin `AnalysisMap`: the route drawn flat, without tiles.
@MainActor
public protocol AnalysisMapView: AnyObject, Sendable {
    var widthM: Double { get }
    var heightM: Double { get }
    var segments: [AnalysisMapSegment] { get }
    var pins: [AnalysisMapPin] { get }
    func nearestTime(x: Double, y: Double) -> Double
}

/// Kotlin `StatTile` and `ChannelOption`.
public struct AnalysisStatTile: Equatable, Sendable, Identifiable {
    public var title: String
    public var value: String
    public var detail: String?

    public var id: String { title }

    public init(title: String, value: String, detail: String? = nil) {
        self.title = title
        self.value = value
        self.detail = detail
    }
}

public struct AnalysisChannelOption: Equatable, Sendable, Identifiable {
    public var channel: AnalysisChannelKind
    public var title: String
    public var description: String
    public var on: Bool
    public var pro: Bool
    public var enabled: Bool
    public var accessibilityLabel: String

    public var id: AnalysisChannelKind { channel }

    public init(
        channel: AnalysisChannelKind, title: String, description: String, on: Bool, pro: Bool = false,
        enabled: Bool = true, accessibilityLabel: String = ""
    ) {
        self.channel = channel
        self.title = title
        self.description = description
        self.on = on
        self.pro = pro
        self.enabled = enabled
        self.accessibilityLabel = accessibilityLabel
    }
}

/// Kotlin `GgView` and its parts (F-RPL-06).
public struct AnalysisGgDot: Equatable, Sendable {
    public var at: AnalysisGPoint
    public var weight: Double
    public var emphasised: Bool

    public init(at: AnalysisGPoint, weight: Double, emphasised: Bool = true) {
        self.at = at
        self.weight = weight
        self.emphasised = emphasised
    }
}

public struct AnalysisGgPin: Equatable, Sendable, Identifiable {
    public var eventId: String
    public var type: DrivingEventType
    public var needed: Bool
    public var at: AnalysisGPoint

    public var id: String { eventId }

    public init(eventId: String, type: DrivingEventType, needed: Bool, at: AnalysisGPoint) {
        self.eventId = eventId
        self.type = type
        self.needed = needed
        self.at = at
    }
}

public struct AnalysisGgChip: Equatable, Sendable, Identifiable {
    public var type: DrivingEventType?
    public var label: String
    public var selected: Bool

    public var id: String { type?.rawValue ?? "all" }

    public init(type: DrivingEventType?, label: String, selected: Bool) {
        self.type = type
        self.label = label
        self.selected = selected
    }
}

public struct AnalysisGgPeak: Equatable, Sendable, Identifiable {
    public var type: DrivingEventType
    public var title: String
    public var detail: String
    public var value: String
    public var eventId: String

    public var id: String { eventId }

    public init(type: DrivingEventType, title: String, detail: String, value: String, eventId: String) {
        self.type = type
        self.title = title
        self.detail = detail
        self.value = value
        self.eventId = eventId
    }
}

public struct AnalysisGgRing: Equatable, Sendable {
    public var g: Double
    public var label: String

    public init(g: Double, label: String) {
        self.g = g
        self.label = label
    }
}

public struct AnalysisGgView: Equatable, Sendable {
    public var dots: [AnalysisGgDot]
    public var envelope: [AnalysisGPoint]
    public var pins: [AnalysisGgPin]
    public var rings: [AnalysisGgRing]
    public var scaleG: Double
    public var caption: String?
    public var filterCaption: String?
    public var filters: [AnalysisGgChip]
    public var peaks: [AnalysisGgPeak]
    public var partBanner: String?
    public var accessibilityLabel: String

    public var empty: Bool { dots.isEmpty }

    public init(
        dots: [AnalysisGgDot] = [], envelope: [AnalysisGPoint] = [], pins: [AnalysisGgPin] = [],
        rings: [AnalysisGgRing] = [], scaleG: Double = 0.6, caption: String? = nil, filterCaption: String? = nil,
        filters: [AnalysisGgChip] = [], peaks: [AnalysisGgPeak] = [], partBanner: String? = nil,
        accessibilityLabel: String = ""
    ) {
        self.dots = dots
        self.envelope = envelope
        self.pins = pins
        self.rings = rings
        self.scaleG = scaleG
        self.caption = caption
        self.filterCaption = filterCaption
        self.filters = filters
        self.peaks = peaks
        self.partBanner = partBanner
        self.accessibilityLabel = accessibilityLabel
    }
}

/// The shared copy the screen needs that is not part of a view (Kotlin `AnalysisCopy`).
public struct AnalysisScreenCopy: Equatable, Sendable {
    public var title = "Analysis"
    public var ggTitle = "Friction circle"
    public var channelsTitle = "Channels"
    public var channelsSub = "Choose what the timeline shows. Your choice is remembered."
    public var channelsPro = "Pro channels"
    public var channelsFooter = ""
    public var axisTime = "Time"
    public var axisDistance = "Distance"
    public var footnote = ""
    public var notFound = ""
    public var summaryOnly = ""
    public var noMap = ""
    public var ggEmpty = ""
    public var ggFooter = ""
    public var ggHow = [String]()
    public var ggHowTitle = "How to read it"
    public var ggPeaksTitle = "Peaks on this drive"
    public var ggAxes = ""

    public init() {}
}

/// Kotlin `AnalysisState`.
public struct AnalysisScreenState: Sendable {
    public var tripId: String
    public var loading: Bool
    public var loadingText: String
    public var missing: Bool
    public var summaryOnly: Bool
    public var axis: AnalysisAxisKind
    public var zoomLabel: String
    public var canZoomIn: Bool
    public var canZoomOut: Bool
    public var cursorS: Double
    public var channelOptions: [AnalysisChannelOption]
    public var timeline: (any AnalysisTimelineView)?
    public var map: (any AnalysisMapView)?
    public var stats: [AnalysisStatTile]
    public var gg: AnalysisGgView?
    public var ggExpanded: Bool
    public var ggFilter: DrivingEventType?
    public var copy: AnalysisScreenCopy

    public init(
        tripId: String = "", loading: Bool = true, loadingText: String = "Preparing channels…", missing: Bool = false,
        summaryOnly: Bool = false, axis: AnalysisAxisKind = .time, zoomLabel: String = "1×", canZoomIn: Bool = true,
        canZoomOut: Bool = false, cursorS: Double = 0, channelOptions: [AnalysisChannelOption] = [],
        timeline: (any AnalysisTimelineView)? = nil, map: (any AnalysisMapView)? = nil,
        stats: [AnalysisStatTile] = [], gg: AnalysisGgView? = nil, ggExpanded: Bool = false,
        ggFilter: DrivingEventType? = nil, copy: AnalysisScreenCopy = AnalysisScreenCopy()
    ) {
        self.tripId = tripId
        self.loading = loading
        self.loadingText = loadingText
        self.missing = missing
        self.summaryOnly = summaryOnly
        self.axis = axis
        self.zoomLabel = zoomLabel
        self.canZoomIn = canZoomIn
        self.canZoomOut = canZoomOut
        self.cursorS = cursorS
        self.channelOptions = channelOptions
        self.timeline = timeline
        self.map = map
        self.stats = stats
        self.gg = gg
        self.ggExpanded = ggExpanded
        self.ggFilter = ggFilter
        self.copy = copy
    }
}

/// Kotlin `AnalysisIntent`.
public enum AnalysisAction: Equatable, Sendable {
    case back
    case setAxis(AnalysisAxisKind)
    case zoomIn, zoomOut, resetZoom
    case pinch(Double)
    case moveCursor(Double)
    case toggle(AnalysisChannelKind)
    case expandGg, collapseGg
    case filterGg(DrivingEventType?)
    case showEvent(String)
    case openReplay
}

/// Kotlin `AnalysisEffect`.
public enum AnalysisNavigation: Equatable, Sendable {
    case close
    case message(String)
    case openReplay(tripId: String)
}

/// The shared Analysis presenter as features see it.
@MainActor
public protocol AnalysisPresenting: AnyObject {
    var state: AnalysisScreenState { get }
    /// The current state first, then every change.
    func states() -> AsyncStream<AnalysisScreenState>
    /// One-shot navigation.
    func navigation() -> AsyncStream<AnalysisNavigation>
    func dispatch(_ action: AnalysisAction)
    func close()
}

/// Makes one presenter per Analysis screen (`CoreServices.analysisPresenter`).
@MainActor
public protocol AnalysisPresenterMaking: AnyObject {
    func presenter(tripId: String) -> any AnalysisPresenting
}
