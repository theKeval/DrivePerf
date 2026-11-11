import DPSharedBridge
import Foundation

// Interim trip rules, ported from docs/architecture/pipeline-and-scoring.md §1.4–§1.6 so the shell can record real
// trips before the shared core exists. DRIVE-62 implements the real reducer in `:shared:pipeline`; this folder is
// deleted when `TripRecorder` from the shared core replaces `InterimTripRecorder` (DRIVE-62).

/// Values from §1.5, §1.6 and §9 (P-05, P-06, P-08, P-09).
struct TripRulesConfig: Sendable {
    var goodFixAccuracyM = 50.0
    var sustainedSpeedMps = 6.94
    var sustainedDurationS = 20.0
    var automotiveSpeedMps = 2.78
    var speedFreshnessS = 5.0
    var rejectAfterS = 90.0
    var rejectMaxSpeedMps = 4.17
    var candidateTimeoutS = 180.0
    var movingSpeedMps = 2.0
    var autoStopS = 240.0
    var manualStopS = 900.0
    var shortTripM = 500.0
    var shortTripS = 120.0
    var resumeWindowS = 600.0
    var parkedRadiusM = 120.0
    var activityHistoryWindowS = 900.0
    var usableFixAccuracyM = 20.0
    var minimumStepM = 5.0
    var imuHz = 50
    var checkpointS = 5.0
    /// Older wakes are stale (spike change 8: region events are delivered late on a relaunch).
    var maxWakeAgeS = 900.0
    /// A fix may be timestamped slightly before the wake that it caused (spike: up to 0.25 s).
    var wakeFixToleranceS = 5.0
}

enum CandidateDecision: Equatable {
    case pending, confirmed, rejected, timedOut
}

/// Confirms or rejects a woken candidate (§1.5). Motion alone never confirms a trip.
struct CandidateDecider {
    private let config: TripRulesConfig
    private let wokeAt: Date
    private var goodFixSeen = false
    private var fastSince: Date?
    private var lastSpeed: Double?
    private var lastSpeedAt: Date?
    private var maxSpeed = 0.0
    private var latestActivity: ActivitySample?

    init(wokeAt: Date, config: TripRulesConfig) {
        self.wokeAt = wokeAt
        self.config = config
    }

    mutating func observe(_ activity: ActivitySample) {
        if let latestActivity, activity.wallMs < latestActivity.wallMs { return }
        latestActivity = activity
    }

    mutating func observe(speedMps: Double?, accuracyM: Double?, at time: Date) {
        if let accuracyM, accuracyM <= config.goodFixAccuracyM { goodFixSeen = true }
        guard let speed = speedMps, speed >= 0 else { return }
        lastSpeed = speed
        lastSpeedAt = time
        maxSpeed = max(maxSpeed, speed)
        if speed >= config.sustainedSpeedMps {
            if fastSince == nil { fastSince = time }
        } else {
            fastSince = nil
        }
    }

    func decide(now: Date) -> CandidateDecision {
        let humanPowered = latestActivity?.isHumanPowered ?? false
        let cycling = latestActivity.map { $0.type == .cycling && $0.confidence >= .medium } ?? false
        if goodFixSeen {
            if !cycling, let fastSince, let lastSpeedAt,
                lastSpeedAt.timeIntervalSince(fastSince) >= config.sustainedDurationS
            {
                return .confirmed
            }
            if let activity = latestActivity, activity.type == .automotive, activity.confidence >= .medium,
                !humanPowered, let lastSpeed, let lastSpeedAt, lastSpeed >= config.automotiveSpeedMps,
                now.timeIntervalSince(lastSpeedAt) <= config.speedFreshnessS
            {
                return .confirmed
            }
        }
        if humanPowered, now.timeIntervalSince(wokeAt) >= config.rejectAfterS, maxSpeed < config.rejectMaxSpeedMps {
            return .rejected
        }
        if now.timeIntervalSince(wokeAt) >= config.candidateTimeoutS { return .timedOut }
        return .pending
    }
}

/// Ends a trip after no vehicle movement for the stop window (§1.6). Walking away from the car does not count as
/// movement.
struct StopDetector {
    let stopWindowS: TimeInterval
    private let movingSpeedMps: Double
    private(set) var lastMovingAt: Date
    private var latestActivity: ActivitySample?

    init(startedAt: Date, stopWindowS: TimeInterval, movingSpeedMps: Double) {
        lastMovingAt = startedAt
        self.stopWindowS = stopWindowS
        self.movingSpeedMps = movingSpeedMps
    }

    mutating func observe(_ activity: ActivitySample) {
        latestActivity = activity
    }

    mutating func observe(speedMps: Double?, at time: Date) {
        guard let speed = speedMps, speed >= movingSpeedMps else { return }
        if latestActivity?.isHumanPowered == true { return }
        lastMovingAt = max(lastMovingAt, time)
    }

    func hasEnded(now: Date) -> Bool {
        now.timeIntervalSince(lastMovingAt) >= stopWindowS
    }
}

/// Distance from usable fixes (h. accuracy ≤ 20 m), ignoring steps under 5 m (GPS jitter while stopped).
struct DistanceAccumulator {
    private let config: TripRulesConfig
    private(set) var distanceM = 0.0
    private(set) var lastUsable: (lat: Double, lng: Double)?
    private(set) var fixCount = 0

    init(config: TripRulesConfig, distanceM: Double = 0) {
        self.config = config
        self.distanceM = distanceM
    }

    mutating func ingest(_ sample: LocationSample) {
        fixCount += 1
        guard let accuracy = sample.hAccM, accuracy <= config.usableFixAccuracyM else { return }
        guard let last = lastUsable else {
            lastUsable = (sample.lat, sample.lng)
            return
        }
        let step = Self.haversineM(last.lat, last.lng, sample.lat, sample.lng)
        guard step >= config.minimumStepM else { return }
        distanceM += step
        lastUsable = (sample.lat, sample.lng)
    }

    static func haversineM(_ lat1: Double, _ lng1: Double, _ lat2: Double, _ lng2: Double) -> Double {
        let earthRadiusM = 6_371_008.8
        let dLat = (lat2 - lat1) * .pi / 180
        let dLng = (lng2 - lng1) * .pi / 180
        let a = sin(dLat / 2) * sin(dLat / 2)
            + cos(lat1 * .pi / 180) * cos(lat2 * .pi / 180) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * earthRadiusM * atan2(a.squareRoot(), (1 - a).squareRoot())
    }
}

enum TripOutcome: Equatable {
    case keep(TripStatus)
    case discard
}

/// §1.6 / F-CAP-17: short automatic trips are discarded, short manual trips are kept as "Short trip".
func classifyTrip(source: TripSource, distanceM: Double, durationS: TimeInterval, config: TripRulesConfig)
    -> TripOutcome
{
    let short = distanceM < config.shortTripM || durationS < config.shortTripS
    guard short else { return .keep(.finalised) }
    return source == .manual ? .keep(.short) : .discard
}
