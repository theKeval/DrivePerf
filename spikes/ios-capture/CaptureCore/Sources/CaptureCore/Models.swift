import Foundation

public struct GeoPoint: Codable, Equatable, Sendable {
    public var latitude: Double
    public var longitude: Double

    public init(latitude: Double, longitude: Double) {
        self.latitude = latitude
        self.longitude = longitude
    }

    /// Great-circle distance in metres (haversine).
    public func distance(to other: GeoPoint) -> Double {
        let earthRadius = 6_371_000.0
        let dLat = (other.latitude - latitude) * .pi / 180
        let dLon = (other.longitude - longitude) * .pi / 180
        let lat1 = latitude * .pi / 180
        let lat2 = other.latitude * .pi / 180
        let a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * earthRadius * atan2(sqrt(a), sqrt(1 - a))
    }
}

public enum FixSource: String, Codable, Sendable {
    case legacy
    case liveUpdates
}

public struct GPSFix: Codable, Equatable, Sendable {
    public var time: Date
    public var point: GeoPoint
    public var altitude: Double
    public var horizontalAccuracy: Double
    public var verticalAccuracy: Double
    /// Metres per second; negative means invalid (Core Location convention).
    public var speed: Double
    public var speedAccuracy: Double
    public var course: Double
    public var source: FixSource

    public init(
        time: Date, point: GeoPoint, altitude: Double, horizontalAccuracy: Double,
        verticalAccuracy: Double, speed: Double, speedAccuracy: Double, course: Double, source: FixSource
    ) {
        self.time = time
        self.point = point
        self.altitude = altitude
        self.horizontalAccuracy = horizontalAccuracy
        self.verticalAccuracy = verticalAccuracy
        self.speed = speed
        self.speedAccuracy = speedAccuracy
        self.course = course
        self.source = source
    }
}

public enum WakeTrigger: String, Codable, Sendable, CaseIterable {
    case significantChange
    case parkedRegionExit
    case visitDeparture
    case manual
    case relaunchRecovery
}

public enum LaunchKind: String, Codable, Sendable {
    case coldBackground
    case resumedBackground
    case foreground
}

public enum TripMode: String, Codable, Sendable {
    case automatic
    case manual
}

public enum TripStatus: String, Codable, Sendable {
    case candidate
    case recording
    case finished
    case rejected
    case interrupted
}

public enum ActivityKind: String, Codable, Sendable {
    case automotive
    case cycling
    case running
    case walking
    case stationary
    case unknown
}

public enum ActivityConfidence: Int, Codable, Comparable, Sendable {
    case low = 0
    case medium = 1
    case high = 2

    public static func < (lhs: ActivityConfidence, rhs: ActivityConfidence) -> Bool {
        lhs.rawValue < rhs.rawValue
    }
}

/// One Core Motion activity change event; it holds until the next event.
public struct ActivityObservation: Codable, Equatable, Sendable {
    public var start: Date
    public var kind: ActivityKind
    public var confidence: ActivityConfidence

    public init(start: Date, kind: ActivityKind, confidence: ActivityConfidence) {
        self.start = start
        self.kind = kind
        self.confidence = confidence
    }
}

public struct TriggerEvent: Codable, Equatable, Sendable {
    public var kind: WakeTrigger
    public var at: Date

    public init(kind: WakeTrigger, at: Date) {
        self.kind = kind
        self.at = at
    }
}

public struct TripRecord: Codable, Identifiable, Sendable {
    public var id: UUID
    public var mode: TripMode
    public var status: TripStatus
    public var wakeTrigger: WakeTrigger
    public var launchKind: LaunchKind
    public var wakeAt: Date
    public var triggers: [TriggerEvent] = []
    public var relaunchCount = 0
    public var legacyKeeperEnabled: Bool
    public var lowPowerModeAtStart: Bool
    public var appStateAtWake: String

    public var parkingSpot: GeoPoint?
    public var motionStartAt: Date?
    public var confirmedAt: Date?
    public var confirmation: String?
    public var rejection: String?
    public var lastMovingAt: Date?
    public var endedAt: Date?
    public var endReason: String?
    public var lastCheckpointAt: Date

    public var batteryStart: Double?
    public var batteryEnd: Double?
    public var accumulator = TripAccumulator()
    public var imuStats = StreamStats(expectedHz: 50, gapThreshold: 0.1)
    public var backfillSamples = 0
    public var backfillWindowSeconds: Double?
    public var fixesLegacy = 0
    public var fixesLiveUpdates = 0
    public var notes: [String] = []
    public var metrics: TripMetrics?

    public init(
        id: UUID = UUID(), mode: TripMode, wakeTrigger: WakeTrigger, launchKind: LaunchKind, wakeAt: Date,
        legacyKeeperEnabled: Bool, lowPowerModeAtStart: Bool, appStateAtWake: String, batteryStart: Double?
    ) {
        self.id = id
        self.mode = mode
        self.status = mode == .manual ? .recording : .candidate
        self.wakeTrigger = wakeTrigger
        self.launchKind = launchKind
        self.wakeAt = wakeAt
        self.legacyKeeperEnabled = legacyKeeperEnabled
        self.lowPowerModeAtStart = lowPowerModeAtStart
        self.appStateAtWake = appStateAtWake
        self.lastCheckpointAt = wakeAt
        self.batteryStart = batteryStart
    }

    public mutating func addNote(_ note: String) {
        if !notes.contains(note) { notes.append(note) }
    }
}
