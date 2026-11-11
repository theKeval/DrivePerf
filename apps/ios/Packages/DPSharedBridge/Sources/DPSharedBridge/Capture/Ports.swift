import Foundation

/// Swift mirrors of the device-facing ports in `:shared:domain` and `:shared:foundation` (ADR-002 §3):
/// `LocationSource`, `MotionSource`, `ActivitySource`, `PowerState`, `ParkedRegionMonitor`, `TripFileStore` and
/// `Clock`. Same methods, callback style with batched sinks, no `async` and no streams. `DPPlatform` implements them
/// on Apple frameworks; the app target wraps each one in the Kotlin protocol (`DrivePerf/CoreBridge/`), which only
/// converts types.
///
/// The Kotlin contract applies: the core calls these from one confined thread (not the main thread), and sinks may
/// be called on any thread. So the protocols are `Sendable` and not main-actor isolated; adapters that need the main
/// thread (Core Location) hop to it themselves. Platform code delivers samples and nothing else: it never decides
/// whether a trip exists, how a sample is filtered or what a score is (§0.1).

/// Kotlin `Clock`. The monotonic reading is the clock every sample's `tMonoNs` uses.
public protocol ClockBridge: Sendable {
    /// Never goes backwards and keeps counting while the phone sleeps.
    func monotonicNs() -> Int64
    /// UTC epoch milliseconds, display only.
    func wallMs() -> Int64
}

/// Kotlin `LocationSource`: 1 Hz GNSS for automotive use. Running it keeps a backgrounded app alive.
public protocol LocationSourceBridge: AnyObject, Sendable {
    /// Starts delivering fixes to `sink`. Calling it again replaces the sink.
    func start(sink: @escaping @Sendable ([LocationSample]) -> Void)
    /// Stops fixes. Safe to call when stopped.
    func stop()
}

/// Kotlin `MotionSource`: device motion, 50 Hz nominal.
public protocol MotionSourceBridge: AnyObject, Sendable {
    func capability() -> MotionCapability
    /// Starts delivering samples, in time order within a batch. The sink runs on the source's own queue, never the
    /// main actor (spike change 6).
    func start(rateHz: Int, sink: @escaping @Sendable ([MotionSample]) -> Void)
    /// Changes the rate while running (50, 25 or 10 Hz).
    func setRate(rateHz: Int)
    func stop()
    /// Raw accelerometer history between two UTC times (`CMSensorRecorder`, §2.8). `callback` runs exactly once.
    func queryRecordedAccelerometer(
        fromWallMs: Int64, toWallMs: Int64, callback: @escaping @Sendable ([RecordedAccelSample]) -> Void)
}

/// Kotlin `ActivitySource`: the OS activity classifier.
public protocol ActivitySourceBridge: AnyObject, Sendable {
    func start(sink: @escaping @Sendable ([ActivitySample]) -> Void)
    func stop()
    /// History between two UTC times, oldest first, exactly the window asked (never padded; the core bounds it,
    /// spike change 3). `callback` runs exactly once.
    func queryHistory(fromWallMs: Int64, toWallMs: Int64, callback: @escaping @Sendable ([ActivitySample]) -> Void)
}

/// Kotlin `PowerState`.
public protocol PowerStateBridge: AnyObject, Sendable {
    /// The current status, read synchronously from any thread.
    func current() -> PowerStatus
    func start(sink: @escaping @Sendable (PowerStatus) -> Void)
    func stop()
}

/// Kotlin `ParkedRegionMonitor`: the region around the last parking spot whose exit wakes the app
/// (`PARKED_EXIT`). The core re-arms it at every trip end and on every launch.
public protocol ParkedRegionMonitorBridge: AnyObject, Sendable {
    /// Arms (or moves) the single parked region.
    func arm(center: GeoPoint, radiusM: Double)
    func disarm()
}

// MARK: - Trip files

/// Kotlin `TripFileKind` (data-model.md §2.1).
public enum TripFileKind: String, Equatable, Sendable, CaseIterable {
    /// Capture log, appended while recording, never uploaded or backed up.
    case r0
    /// Analysis file, written once at finalisation.
    case r1
    /// Preview track.
    case preview
}

/// Kotlin `FileResult`.
public enum FileResult: String, Equatable, Sendable {
    case ok
    case diskFull
    /// The file is protected and the phone has not been unlocked since boot.
    case unavailable
    case ioError
}

/// Kotlin `StoredFile`. `sha256` is lower-case hex, or nil when it was not requested.
public struct StoredFile: Equatable, Sendable {
    public var sizeBytes: Int64
    public var sha256: String?
    public var compressed: Bool

    public init(sizeBytes: Int64, sha256: String?, compressed: Bool) {
        self.sizeBytes = sizeBytes
        self.sha256 = sha256
        self.compressed = compressed
    }
}

/// Kotlin `TripFileStore`: byte storage under `trips/<tripId>/`. The file format is the core's job; this port stores
/// bytes with the right protection. Calls are synchronous and never on the main thread in production; results are
/// codes, never thrown errors.
public protocol TripFileStoreBridge: AnyObject, Sendable {
    /// Appends to the end of the file, creating it if needed. Buffered: call `sync` to make it durable.
    func append(tripId: String, kind: TripFileKind, bytes: Data) -> FileResult
    /// `fsync`.
    func sync(tripId: String, kind: TripFileKind) -> FileResult
    /// Replaces the whole file atomically.
    func write(tripId: String, kind: TripFileKind, bytes: Data) -> FileResult
    /// Gzip in place (RFC 1952); `read` keeps returning the uncompressed bytes.
    func compress(tripId: String, kind: TripFileKind) -> FileResult
    func read(tripId: String, kind: TripFileKind) -> Data?
    func describe(tripId: String, kind: TripFileKind, withSha256: Bool) -> StoredFile?
    func delete(tripId: String, kind: TripFileKind) -> FileResult
    func deleteTrip(tripId: String) -> FileResult
    func tripIds() -> [String]
    func freeSpaceBytes() -> Int64
}
