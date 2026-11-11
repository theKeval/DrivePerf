import DPSharedBridge
import Foundation

/// `MovementMonitoring` on the OS activity classifier: moving while it says automotive at medium confidence or better
/// (F-CAP-07.4). Give it its own `ActivitySourceBridge` instance: a source keeps one sink, and the shared core stops
/// its own at the end of every trip. The speed half of the rule needs GNSS, which is off after a trip; this reads the
/// activity only (the hold is at least a minute either way).
@MainActor
public final class ActivityMovementMonitor: MovementMonitoring {
    private let source: any ActivitySourceBridge

    public init(source: any ActivitySourceBridge) {
        self.source = source
    }

    public func movements() -> AsyncStream<Bool> {
        let source = source
        return AsyncStream { continuation in
            source.start { samples in
                if let moving = Self.isMoving(samples) { continuation.yield(moving) }
            }
            continuation.onTermination = { _ in source.stop() }
        }
    }

    /// The reading the newest sample gives, or nil for an empty batch.
    nonisolated static func isMoving(_ samples: [ActivitySample]) -> Bool? {
        guard let latest = samples.max(by: { $0.wallMs < $1.wallMs }) else { return nil }
        return latest.type == .automotive && latest.confidence >= .medium
    }
}
