import CoreMotion
import DPSharedBridge
import Foundation

/// `ActivitySource` on `CMMotionActivityManager`. Not a wake trigger on iOS: the core queries history on a wake
/// (§1.3). Callbacks run on a private queue.
public final class CoreMotionActivitySource: ActivitySourceBridge, @unchecked Sendable {
    private let manager = CMMotionActivityManager()
    private let queue: OperationQueue
    private let clock: SystemClock

    public init(clock: SystemClock = SystemClock()) {
        self.clock = clock
        queue = OperationQueue()
        queue.name = "com.driveperf.activity"
        queue.maxConcurrentOperationCount = 1
    }

    public var isAvailable: Bool { CMMotionActivityManager.isActivityAvailable() }

    public func start(sink: @escaping @Sendable ([ActivitySample]) -> Void) {
        guard isAvailable else { return }
        let clock = clock
        manager.startActivityUpdates(to: queue) { activity in
            guard let activity else { return }
            sink([ActivityMapping.sample(from: activity, clock: clock)])
        }
    }

    public func stop() {
        manager.stopActivityUpdates()
    }

    public func queryHistory(
        fromWallMs: Int64, toWallMs: Int64, callback: @escaping @Sendable ([ActivitySample]) -> Void
    ) {
        guard isAvailable, fromWallMs < toWallMs else {
            callback([])
            return
        }
        let clock = clock
        manager.queryActivityStarting(from: Date(wallMs: fromWallMs), to: Date(wallMs: toWallMs), to: queue) {
            activities, _ in
            callback(
                (activities ?? []).map { ActivityMapping.sample(from: $0, clock: clock) }.sorted { $0.wallMs < $1.wallMs })
        }
    }
}

/// `CMMotionActivity` → `ActivitySample`. Automotive wins when automotive and stationary are both set at a red
/// light (A3 §1.1).
public enum ActivityMapping {
    public static func sample(from activity: CMMotionActivity, clock: SystemClock) -> ActivitySample {
        ActivitySample(
            tMonoNs: clock.monotonicNs(atWall: activity.startDate),
            wallMs: activity.startDate.wallMs,
            type: type(
                automotive: activity.automotive, cycling: activity.cycling, running: activity.running,
                walking: activity.walking, stationary: activity.stationary),
            confidence: confidence(activity.confidence))
    }

    public static func type(automotive: Bool, cycling: Bool, running: Bool, walking: Bool, stationary: Bool)
        -> ActivityType
    {
        if automotive { return .automotive }
        if cycling { return .cycling }
        if running { return .running }
        if walking { return .walking }
        if stationary { return .stationary }
        return .unknown
    }

    static func confidence(_ confidence: CMMotionActivityConfidence) -> ActivityConfidence {
        switch confidence {
        case .high: .high
        case .medium: .medium
        default: .low
        }
    }
}
