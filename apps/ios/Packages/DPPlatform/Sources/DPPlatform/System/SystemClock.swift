import DPSharedBridge
import Foundation

/// `Clock` on iOS. The monotonic base is `ProcessInfo.systemUptime`, the same base as `CMDeviceMotion.timestamp`,
/// so motion and location share one clock per trip (pipeline-and-scoring.md §2.2).
public struct SystemClock: ClockBridge {
    public init() {}

    public func nowMonoNs() -> Int64 {
        Int64((ProcessInfo.processInfo.systemUptime * 1_000_000_000).rounded())
    }

    public func nowWallMs() -> Int64 {
        Int64((Date().timeIntervalSince1970 * 1000).rounded())
    }
}

extension Date {
    var wallMs: Int64 { Int64((timeIntervalSince1970 * 1000).rounded()) }

    init(wallMs: Int64) {
        self.init(timeIntervalSince1970: TimeInterval(wallMs) / 1000)
    }
}
