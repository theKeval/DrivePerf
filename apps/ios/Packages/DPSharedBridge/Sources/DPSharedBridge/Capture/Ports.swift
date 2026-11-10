/// Device-facing ports the platform implements (ADR-002 §3; pipeline-and-scoring.md §0.1, §2.1). Names follow the
/// Kotlin ports DRIVE-62 adds to `:shared:domain` (`LocationSource`, `MotionSource`, `ActivitySource`, `Clock`,
/// `TripFileStore`) with the `Bridge` suffix ADR-002 uses for Swift-implemented, callback-style protocols: no
/// `async` and no streams in the signatures, so a Kotlin adapter can wrap them with `suspendCancellableCoroutine`.
///
/// Platform code delivers samples and nothing else: it never decides whether a trip exists, how a sample is
/// filtered or what a score is (§0.1).

/// Monotonic and wall clocks (`Clock`).
public protocol ClockBridge: Sendable {
    func nowMonoNs() -> Int64
    func nowWallMs() -> Int64
}

/// Why location stopped or degraded, reported next to the samples.
public enum LocationIssue: String, Equatable, Sendable {
    case authorizationDenied, insufficientlyInUse, serviceSessionRequired, locationUnavailable
}

/// GNSS at ~1 Hz, best for navigation (`LocationSource`).
@MainActor
public protocol LocationSourceBridge: AnyObject {
    /// Starts fixes. Fixes timestamped before `notBeforeWallMs` are dropped: a stream's first fix can be cached
    /// from before the wake (spike change 8).
    func start(
        notBeforeWallMs: Int64,
        onSample: @escaping @MainActor (LocationSample) -> Void,
        onStationary: @escaping @MainActor (Bool) -> Void,
        onIssue: @escaping @MainActor (LocationIssue) -> Void)
    func stop()
}

/// Device motion at 50 Hz nominal (`MotionSource`). `onSample` runs on the source's own queue, never the main
/// actor, so it must be `@Sendable` (spike change 6).
public protocol MotionSourceBridge: AnyObject, Sendable {
    var isAvailable: Bool { get }
    func start(rateHz: Int, onSample: @escaping @Sendable (MotionSample) -> Void)
    func setRate(hz: Int)
    func stop()
}

/// Motion activity (`ActivitySource`). History queries are bounded by the caller (spike change 3).
@MainActor
public protocol ActivitySourceBridge: AnyObject {
    var isAvailable: Bool { get }
    func start(onActivity: @escaping @MainActor (ActivitySample) -> Void)
    func stop()
    func queryHistory(
        fromWallMs: Int64, toWallMs: Int64, completion: @escaping @MainActor ([ActivitySample]) -> Void)
}

/// Platform wake triggers (§1.3): the parked-location region first, significant change and Visits as fallbacks.
@MainActor
public protocol WakeTriggerBridge: AnyObject {
    /// Starts the triggers and delivers fresh wakes. Stale events (cached fixes after a relaunch, old region
    /// events) are dropped before they reach `onWake` (spike change 8).
    func arm(onWake: @escaping @MainActor (WakeEvent) -> Void)
    func disarm()
    /// Arms the parked region at the end of a trip (`ArmParkedRegion`, 120 m, P-05). Called at every trip end and
    /// re-armed on every launch (spike change 1).
    func armParkedRegion(lat: Double, lng: Double, radiusM: Double)
    var isParkedRegionArmed: Bool { get }
}

/// Appends a trip's raw samples (the R0 capture log). Thread-safe: motion arrives off the main actor.
public protocol TripFileWriter: AnyObject, Sendable {
    func append(_ sample: LocationSample)
    func append(_ sample: MotionSample)
    func append(_ sample: ActivitySample)
    /// Writes buffered samples to disk (≤ 5 s, §0.2).
    func flush()
    func close()
    var counts: SampleCounts { get }
}

public struct SampleCounts: Equatable, Sendable, Codable {
    public var location: Int
    public var motion: Int
    public var activity: Int

    public init(location: Int = 0, motion: Int = 0, activity: Int = 0) {
        self.location = location
        self.motion = motion
        self.activity = activity
    }
}

public enum TripFileError: Error, Equatable, Sendable {
    /// Before the first unlock after a reboot (EC-RBT): no trip may start.
    case storageLocked
    case writeFailed
}

/// Trip files on disk (`TripFileStore`).
public protocol TripFileStoreBridge: AnyObject, Sendable {
    /// True once the phone has been unlocked since boot. Decided by reading a protected sentinel file, not by
    /// `isProtectedDataAvailable`, which is false on every lock (spike change 5).
    func isWritable() -> Bool
    func open(tripId: String) throws(TripFileError) -> any TripFileWriter
    func delete(tripId: String)
    func sizeBytes(tripId: String) -> Int64
}
