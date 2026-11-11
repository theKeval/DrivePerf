import DPSharedBridge
import Foundation

/// `Clock` on iOS. The monotonic reading is `CLOCK_MONOTONIC_RAW` (`mach_continuous_time`), which keeps counting
/// while the phone sleeps, as the Kotlin `Clock` contract requires. Every adapter converts its timestamps to it:
/// Core Location from the fix's wall time (`nowMono − (nowWall − fix.timestamp)`, §2.2), Core Motion from its uptime
/// base (`ProcessInfo.systemUptime`, which stops during sleep) with the offset read at delivery.
public struct SystemClock: ClockBridge {
    public init() {}

    public func monotonicNs() -> Int64 {
        Int64(clamping: clock_gettime_nsec_np(CLOCK_MONOTONIC_RAW))
    }

    public func wallMs() -> Int64 {
        Date().wallMs
    }

    /// Converts a wall-clock time to the monotonic clock, for a timestamp that is in the past.
    public func monotonicNs(atWall date: Date) -> Int64 {
        let ageNs = Int64((Date().timeIntervalSince(date) * 1_000_000_000).rounded())
        return monotonicNs() - max(ageNs, 0)
    }

    /// Converts a Core Motion timestamp (seconds of uptime) to the monotonic clock.
    public func monotonicNs(atUptime uptime: TimeInterval) -> Int64 {
        let offsetNs = monotonicNs() - Int64((ProcessInfo.processInfo.systemUptime * 1_000_000_000).rounded())
        return Int64((uptime * 1_000_000_000).rounded()) + offsetNs
    }
}

/// Runs `work` on the main actor: now when already on the main thread, otherwise queued in call order. The Kotlin
/// core calls ports from its own thread; Core Location and UIKit need the main one.
func onMain(_ work: @escaping @MainActor @Sendable () -> Void) {
    if Thread.isMainThread {
        MainActor.assumeIsolated(work)
    } else {
        DispatchQueue.main.async { MainActor.assumeIsolated(work) }
    }
}
