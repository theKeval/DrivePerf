import DPPlatform
import DPSharedBridge
import DrivePerfShared
import Foundation

/// The DPPlatform adapters as the Kotlin ports of `:shared:domain` and `:shared:foundation` (ADR-002 §3): one
/// object per port, conversion only. These are what `PlatformBindings` hands to `DrivePerfCore.start` once the shared
/// recorder lands (DRIVE-62 slice 3); until then the on-device self-test drives them through the Kotlin types.
@MainActor
final class CorePorts {
    let clock: KotlinClockPort
    let location: KotlinLocationPort
    let motion: KotlinMotionPort
    let activity: KotlinActivityPort
    let power: KotlinPowerPort
    let parkedRegion: KotlinParkedRegionPort
    let tripFiles: KotlinTripFilePort

    init(
        clock: SystemClock, location: any LocationSourceBridge, motion: any MotionSourceBridge,
        activity: any ActivitySourceBridge, power: any PowerStateBridge, parkedRegion: any ParkedRegionMonitorBridge,
        tripFiles: any TripFileStoreBridge
    ) {
        self.clock = KotlinClockPort(clock)
        self.location = KotlinLocationPort(location)
        self.motion = KotlinMotionPort(motion)
        self.activity = KotlinActivityPort(activity)
        self.power = KotlinPowerPort(power)
        self.parkedRegion = KotlinParkedRegionPort(parkedRegion)
        self.tripFiles = KotlinTripFilePort(tripFiles)
    }
}

/// `com.driveperf.foundation.Clock`.
final class KotlinClockPort: NSObject, DrivePerfShared.Clock, Sendable {
    private let clock: any ClockBridge

    init(_ clock: any ClockBridge) {
        self.clock = clock
    }

    func monotonicNs() -> Int64 { clock.monotonicNs() }
    func wallMs() -> Int64 { clock.wallMs() }
}

/// `LocationSource`. Fixes reach the Kotlin sink as they arrive (one per batch at ~1 Hz).
final class KotlinLocationPort: NSObject, DrivePerfShared.LocationSource, Sendable {
    private let source: any LocationSourceBridge

    init(_ source: any LocationSourceBridge) {
        self.source = source
    }

    func start(sink: any LocationSink) {
        let sink = UncheckedSendable(sink)
        source.start { samples in sink.value.onLocations(samples: samples.map(KotlinMapping.kotlin)) }
    }

    func stop() { source.stop() }
}

/// `MotionSource`.
final class KotlinMotionPort: NSObject, DrivePerfShared.MotionSource, Sendable {
    private let source: any MotionSourceBridge

    init(_ source: any MotionSourceBridge) {
        self.source = source
    }

    func capability() -> DrivePerfShared.MotionCapability { KotlinMapping.kotlin(source.capability()) }

    func start(rateHz: Int32, sink: any MotionSink) {
        let sink = UncheckedSendable(sink)
        source.start(rateHz: Int(rateHz)) { samples in sink.value.onMotion(samples: samples.map(KotlinMapping.kotlin)) }
    }

    func setRate(rateHz: Int32) { source.setRate(rateHz: Int(rateHz)) }

    func stop() { source.stop() }

    func queryRecordedAccelerometer(fromWallMs: Int64, toWallMs: Int64, callback: any RecordedAccelCallback) {
        let callback = UncheckedSendable(callback)
        source.queryRecordedAccelerometer(fromWallMs: fromWallMs, toWallMs: toWallMs) { samples in
            callback.value.onResult(samples: samples.map(KotlinMapping.kotlin))
        }
    }
}

/// `ActivitySource`.
final class KotlinActivityPort: NSObject, DrivePerfShared.ActivitySource, Sendable {
    private let source: any ActivitySourceBridge

    init(_ source: any ActivitySourceBridge) {
        self.source = source
    }

    func start(sink: any ActivitySink) {
        let sink = UncheckedSendable(sink)
        source.start { samples in sink.value.onActivity(samples: samples.map(KotlinMapping.kotlin)) }
    }

    func stop() { source.stop() }

    func queryHistory(fromWallMs: Int64, toWallMs: Int64, callback: any ActivityHistoryCallback) {
        let callback = UncheckedSendable(callback)
        source.queryHistory(fromWallMs: fromWallMs, toWallMs: toWallMs) { samples in
            callback.value.onResult(samples: samples.map(KotlinMapping.kotlin))
        }
    }
}

/// `PowerState`.
final class KotlinPowerPort: NSObject, DrivePerfShared.PowerState, Sendable {
    private let source: any PowerStateBridge

    init(_ source: any PowerStateBridge) {
        self.source = source
    }

    func current() -> DrivePerfShared.PowerStatus { KotlinMapping.kotlin(source.current()) }

    func start(sink: any PowerSink) {
        let sink = UncheckedSendable(sink)
        source.start { status in sink.value.onPower(status: KotlinMapping.kotlin(status)) }
    }

    func stop() { source.stop() }
}

/// `ParkedRegionMonitor`.
final class KotlinParkedRegionPort: NSObject, DrivePerfShared.ParkedRegionMonitor, Sendable {
    private let monitor: any ParkedRegionMonitorBridge

    init(_ monitor: any ParkedRegionMonitorBridge) {
        self.monitor = monitor
    }

    func arm(center: DrivePerfShared.GeoPoint, radiusM: Double) {
        monitor.arm(center: KotlinMapping.swift(center), radiusM: radiusM)
    }

    func disarm() { monitor.disarm() }
}

/// `com.driveperf.domain.tripfile.TripFileStore`.
final class KotlinTripFilePort: NSObject, DrivePerfShared.TripFileStore, Sendable {
    private let store: any TripFileStoreBridge

    init(_ store: any TripFileStoreBridge) {
        self.store = store
    }

    func append(tripId: String, kind: DrivePerfShared.TripFileKind, bytes: KotlinByteArray)
        -> DrivePerfShared.FileResult
    {
        KotlinMapping.kotlin(store.append(tripId: tripId, kind: KotlinMapping.swift(kind), bytes: KotlinMapping.swift(bytes)))
    }

    func sync(tripId: String, kind: DrivePerfShared.TripFileKind) -> DrivePerfShared.FileResult {
        KotlinMapping.kotlin(store.sync(tripId: tripId, kind: KotlinMapping.swift(kind)))
    }

    func write(tripId: String, kind: DrivePerfShared.TripFileKind, bytes: KotlinByteArray)
        -> DrivePerfShared.FileResult
    {
        KotlinMapping.kotlin(store.write(tripId: tripId, kind: KotlinMapping.swift(kind), bytes: KotlinMapping.swift(bytes)))
    }

    func compress(tripId: String, kind: DrivePerfShared.TripFileKind) -> DrivePerfShared.FileResult {
        KotlinMapping.kotlin(store.compress(tripId: tripId, kind: KotlinMapping.swift(kind)))
    }

    func read(tripId: String, kind: DrivePerfShared.TripFileKind) -> KotlinByteArray? {
        store.read(tripId: tripId, kind: KotlinMapping.swift(kind)).map(KotlinMapping.kotlin)
    }

    func describe(tripId: String, kind: DrivePerfShared.TripFileKind, withSha256: Bool) -> DrivePerfShared.StoredFile? {
        store.describe(tripId: tripId, kind: KotlinMapping.swift(kind), withSha256: withSha256).map(KotlinMapping.kotlin)
    }

    func delete(tripId: String, kind: DrivePerfShared.TripFileKind) -> DrivePerfShared.FileResult {
        KotlinMapping.kotlin(store.delete(tripId: tripId, kind: KotlinMapping.swift(kind)))
    }

    func deleteTrip(tripId: String) -> DrivePerfShared.FileResult {
        KotlinMapping.kotlin(store.deleteTrip(tripId: tripId))
    }

    func tripIds() -> [String] { store.tripIds() }

    func freeSpaceBytes() -> Int64 { store.freeSpaceBytes() }
}

/// Kotlin sinks and callbacks are thread-safe by contract ("callbacks on any thread"), but the Objective-C export
/// does not mark them `Sendable`.
struct UncheckedSendable<Value>: @unchecked Sendable {
    let value: Value

    init(_ value: Value) {
        self.value = value
    }
}
