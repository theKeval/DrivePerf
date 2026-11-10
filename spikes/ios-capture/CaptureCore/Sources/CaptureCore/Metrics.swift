import Foundation

/// Online delivery statistics for one sample stream: counts, gaps and rate.
public struct StreamStats: Codable, Equatable, Sendable {
    public let expectedHz: Double
    public let gapThreshold: TimeInterval
    public private(set) var count = 0
    public private(set) var firstAt: Date?
    public private(set) var lastAt: Date?
    public private(set) var gapCount = 0
    public private(set) var longestGap: TimeInterval = 0

    public init(expectedHz: Double, gapThreshold: TimeInterval) {
        self.expectedHz = expectedHz
        self.gapThreshold = gapThreshold
    }

    public mutating func record(at time: Date) {
        if let lastAt {
            let delta = time.timeIntervalSince(lastAt)
            if delta > gapThreshold {
                gapCount += 1
                longestGap = max(longestGap, delta)
            }
        } else {
            firstAt = time
        }
        lastAt = time
        count += 1
    }

    public var duration: TimeInterval {
        guard let firstAt, let lastAt else { return 0 }
        return lastAt.timeIntervalSince(firstAt)
    }

    public var samplesPerSecond: Double {
        duration > 0 ? Double(max(count - 1, 0)) / duration : 0
    }

    /// Share of the expected samples that arrived over the stream's span (PRD Q13).
    public var delivery: Double {
        let expected = duration * expectedHz
        return expected > 0 ? min(Double(max(count - 1, 0)) / expected, 1) : 0
    }
}

/// Running GNSS state for a trip, checkpointed to disk so a killed process loses nothing.
public struct TripAccumulator: Codable, Equatable, Sendable {
    public static let startAccuracyLimit = 20.0
    public static let distanceAccuracyLimit = 50.0

    public private(set) var distanceMeters = 0.0
    public private(set) var stats = StreamStats(expectedHz: 1, gapThreshold: 3)
    public private(set) var firstFix: GPSFix?
    public private(set) var firstGoodFix: GPSFix?
    public private(set) var lastUsableFix: GPSFix?
    private var distanceAnchor: GeoPoint?
    /// Ignores steps shorter than this, so GPS jitter while parked adds no distance.
    private static let minimumStep = 5.0

    public init() {}

    public mutating func ingest(_ fix: GPSFix) {
        stats.record(at: fix.time)
        if firstFix == nil { firstFix = fix }
        if firstGoodFix == nil, fix.horizontalAccuracy >= 0, fix.horizontalAccuracy <= Self.startAccuracyLimit {
            firstGoodFix = fix
        }
        guard fix.horizontalAccuracy >= 0, fix.horizontalAccuracy <= Self.distanceAccuracyLimit else { return }
        lastUsableFix = fix
        guard let anchor = distanceAnchor else {
            distanceAnchor = fix.point
            return
        }
        let step = anchor.distance(to: fix.point)
        if step >= Self.minimumStep {
            distanceMeters += step
            distanceAnchor = fix.point
        }
    }
}

public struct TripMetrics: Codable, Equatable, Sendable {
    public var distanceMeters: Double
    public var durationSeconds: Double
    /// PRD Q2: straight line from the parking spot to the first GNSS point with accuracy ≤ 20 m.
    public var startLatencyMeters: Double?
    /// PRD Q2: seconds from motion start (activity history) to that same point.
    public var startLatencySeconds: Double?
    /// Seconds from the trigger (wake or tap) to the first recorded point, any accuracy (PRD Q3).
    public var firstFixAfterTriggerSeconds: Double?
    public var gpsSamplesPerSecond: Double
    public var imuSamplesPerSecond: Double
    public var gpsDelivery: Double
    public var imuDelivery: Double
    public var gpsGaps: Int
    public var imuGaps: Int
    public var longestGpsGapSeconds: Double
    public var longestImuGapSeconds: Double
    public var batteryDeltaPercent: Double?
    public var batteryPercentPerHour: Double?

    public init(record: TripRecord, now: Date) {
        let gps = record.accumulator.stats
        let end = record.endedAt ?? now
        let duration = end.timeIntervalSince(record.wakeAt)
        distanceMeters = record.accumulator.distanceMeters
        durationSeconds = duration

        if let spot = record.parkingSpot, let first = record.accumulator.firstGoodFix {
            startLatencyMeters = spot.distance(to: first.point)
        }
        if let motionStart = record.motionStartAt, let first = record.accumulator.firstGoodFix {
            startLatencySeconds = first.time.timeIntervalSince(motionStart)
        }
        if let first = record.accumulator.firstFix {
            firstFixAfterTriggerSeconds = first.time.timeIntervalSince(record.wakeAt)
        }

        gpsSamplesPerSecond = gps.samplesPerSecond
        imuSamplesPerSecond = record.imuStats.samplesPerSecond
        gpsDelivery = gps.delivery
        imuDelivery = record.imuStats.delivery
        gpsGaps = gps.gapCount
        imuGaps = record.imuStats.gapCount
        longestGpsGapSeconds = gps.longestGap
        longestImuGapSeconds = record.imuStats.longestGap

        if let start = record.batteryStart, let stop = record.batteryEnd, start >= 0, stop >= 0 {
            let delta = (start - stop) * 100
            batteryDeltaPercent = delta
            if duration >= 60 { batteryPercentPerHour = delta / (duration / 3600) }
        }
    }
}
