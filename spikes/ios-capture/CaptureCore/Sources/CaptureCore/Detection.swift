import Foundation

/// A3 §1.3 placeholder thresholds; A19 calibrates the real ones.
public struct ConfirmationConfig: Sendable {
    public var sustainedSpeed = 25.0 / 3.6
    public var sustainedDuration: TimeInterval = 20
    public var automotiveSpeed = 10.0 / 3.6
    public var speedFreshness: TimeInterval = 5
    public var timeout: TimeInterval = 180
    public var earlyRejectAfter: TimeInterval = 90
    public var earlyRejectMaxSpeed = 15.0 / 3.6

    public init() {}
}

public enum ConfirmationDecision: Equatable, Sendable {
    case pending
    case confirmed(String)
    case rejected(String)
}

/// Decides whether a woken candidate is really a drive (PRD Q5/Q6 rejection logic).
public struct DriveConfirmation: Sendable {
    private let config: ConfirmationConfig
    private let startedAt: Date
    private var fastSince: Date?
    private var lastSpeed: Double?
    private var lastSpeedAt: Date?
    private var maxSpeed = 0.0
    private var latest: ActivityObservation?

    public init(startedAt: Date, config: ConfirmationConfig = ConfirmationConfig()) {
        self.startedAt = startedAt
        self.config = config
    }

    public mutating func observe(activity: ActivityObservation) {
        if let latest, activity.start < latest.start { return }
        latest = activity
    }

    public mutating func observeSpeed(_ speed: Double, at time: Date) {
        guard speed >= 0 else { return }
        lastSpeed = speed
        lastSpeedAt = time
        maxSpeed = max(maxSpeed, speed)
        if speed >= config.sustainedSpeed {
            if fastSince == nil { fastSince = time }
        } else {
            fastSince = nil
        }
    }

    public func decide(now: Date) -> ConfirmationDecision {
        let humanPowered = humanPoweredKind
        // Walking and running cannot sustain 25 km/h, so only cycling blocks the speed rule.
        if humanPowered != .cycling, let fastSince, let lastSpeedAt,
            lastSpeedAt.timeIntervalSince(fastSince) >= config.sustainedDuration
        {
            return .confirmed("sustained speed")
        }
        if humanPowered == nil, let latest, latest.kind == .automotive, latest.confidence >= .medium,
            let lastSpeed, let lastSpeedAt,
            lastSpeed >= config.automotiveSpeed,
            now.timeIntervalSince(lastSpeedAt) <= config.speedFreshness
        {
            return .confirmed("automotive plus speed")
        }
        if let humanPowered, now.timeIntervalSince(startedAt) >= config.earlyRejectAfter,
            maxSpeed < config.earlyRejectMaxSpeed
        {
            return .rejected("activity: \(humanPowered.rawValue)")
        }
        if now.timeIntervalSince(startedAt) >= config.timeout {
            return .rejected(humanPowered.map { "timeout, activity: \($0.rawValue)" } ?? "timeout, no driving")
        }
        return .pending
    }

    private var humanPoweredKind: ActivityKind? {
        guard let latest, latest.confidence >= .medium else { return nil }
        switch latest.kind {
        case .walking, .running, .cycling: return latest.kind
        default: return nil
        }
    }
}

public enum MotionHistory {
    /// Finds when the current automotive spell began, walking back through activity change events.
    /// Short stationary or unknown stretches (red lights) stay inside the spell.
    public static func automotiveStart(
        in observations: [ActivityObservation], maxInterruption: TimeInterval = 120
    ) -> Date? {
        let sorted = observations.sorted { $0.start < $1.start }
        guard let lastAutomotive = sorted.lastIndex(where: { $0.kind == .automotive }) else { return nil }

        var start = sorted[lastAutomotive].start
        var index = lastAutomotive - 1
        while index >= 0 {
            let observation = sorted[index]
            switch observation.kind {
            case .automotive:
                start = observation.start
            case .walking, .running, .cycling:
                if observation.confidence >= .medium { return start }
            case .stationary, .unknown:
                let nextStart = sorted[index + 1].start
                if nextStart.timeIntervalSince(observation.start) > maxInterruption { return start }
            }
            index -= 1
        }
        return start
    }
}

/// Decides when a drive has ended: no vehicle movement for `stopWindow` (PRD Q10 is ≤ 5 min p90).
public struct TripEndDetector: Codable, Sendable {
    public static let automaticStopWindow: TimeInterval = 240
    public static let manualStopWindow: TimeInterval = 1800

    public let stopWindow: TimeInterval
    public let movingSpeed: Double
    public private(set) var lastMovingAt: Date

    public init(startedAt: Date, stopWindow: TimeInterval, movingSpeed: Double = 2.0) {
        self.lastMovingAt = startedAt
        self.stopWindow = stopWindow
        self.movingSpeed = movingSpeed
    }

    public mutating func observe(speed: Double, activity: ActivityObservation?, at time: Date) {
        guard speed >= movingSpeed else { return }
        if let activity, activity.confidence >= .medium {
            switch activity.kind {
            case .walking, .running, .cycling: return
            default: break
            }
        }
        lastMovingAt = max(lastMovingAt, time)
    }

    public func hasEnded(now: Date) -> Bool {
        now.timeIntervalSince(lastMovingAt) >= stopWindow
    }
}
