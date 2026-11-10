import CoreMotion
import DPSharedBridge
import Foundation

/// `ActivitySource` on `CMMotionActivityManager`. Not a wake trigger: history is queried on a wake (§1.3).
@MainActor
public final class CoreMotionActivitySource: ActivitySourceBridge {
    private let manager = CMMotionActivityManager()

    public init() {}

    public var isAvailable: Bool { CMMotionActivityManager.isActivityAvailable() }

    public func start(onActivity: @escaping @MainActor (ActivitySample) -> Void) {
        guard isAvailable else { return }
        manager.startActivityUpdates(to: .main) { activity in
            guard let activity else { return }
            let sample = ActivityMapping.sample(from: activity)
            MainActor.assumeIsolated { onActivity(sample) }
        }
    }

    public func stop() {
        manager.stopActivityUpdates()
    }

    public func queryHistory(
        fromWallMs: Int64, toWallMs: Int64, completion: @escaping @MainActor ([ActivitySample]) -> Void
    ) {
        guard isAvailable, fromWallMs < toWallMs else {
            completion([])
            return
        }
        manager.queryActivityStarting(from: Date(wallMs: fromWallMs), to: Date(wallMs: toWallMs), to: .main) {
            activities, _ in
            let samples = (activities ?? []).map(ActivityMapping.sample(from:))
            MainActor.assumeIsolated { completion(samples) }
        }
    }
}

/// `CMMotionActivity` → `ActivitySample`. Automotive wins when automotive and stationary are both set at a red
/// light (A3 §1.1).
public enum ActivityMapping {
    public static func sample(from activity: CMMotionActivity) -> ActivitySample {
        ActivitySample(
            startWallMs: activity.startDate.wallMs,
            kind: kind(
                automotive: activity.automotive, cycling: activity.cycling, running: activity.running,
                walking: activity.walking, stationary: activity.stationary),
            confidence: confidence(activity.confidence))
    }

    public static func kind(automotive: Bool, cycling: Bool, running: Bool, walking: Bool, stationary: Bool)
        -> ActivityKind
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
