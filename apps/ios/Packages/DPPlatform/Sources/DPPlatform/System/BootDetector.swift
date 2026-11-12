import Foundation

/// Tells the first launch after the phone restarted from any other launch, for the core's `rearmed {reason: boot}`
/// (F-CAP-14.3). iOS has no boot broadcast: the boot time is read from the kernel and compared with the one seen at
/// the previous launch.
public struct BootDetector: Sendable {
    private static let key = "driveperf.lastSeenBootTime"
    /// The kernel's boot time moves by a few milliseconds between reads (it is derived from wall clock and uptime);
    /// a restart moves it by far more.
    static let tolerance: TimeInterval = 60

    public init() {}

    /// True once per restart: the first call after the device booted. Records the current boot for the next call.
    public func isFirstLaunchSinceBoot(defaults: UserDefaults = .standard, bootTime: Date? = Self.currentBootTime()) -> Bool {
        guard let bootTime else { return false }
        let previous = defaults.object(forKey: Self.key) as? Date
        defaults.set(bootTime, forKey: Self.key)
        return Self.isNewBoot(previous: previous, current: bootTime)
    }

    /// A first-ever launch is not a boot (there was nothing to re-arm after).
    static func isNewBoot(previous: Date?, current: Date) -> Bool {
        guard let previous else { return false }
        return abs(current.timeIntervalSince(previous)) > tolerance
    }

    /// `kern.boottime`, or nil if the kernel does not say.
    public static func currentBootTime() -> Date? {
        var time = timeval()
        var size = MemoryLayout<timeval>.stride
        guard sysctlbyname("kern.boottime", &time, &size, nil, 0) == 0 else { return nil }
        return Date(timeIntervalSince1970: TimeInterval(time.tv_sec) + TimeInterval(time.tv_usec) / 1_000_000)
    }
}
