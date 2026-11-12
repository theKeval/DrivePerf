import DPPlatform
import DPSharedBridge
import DrivePerfShared
import Foundation

/// `SharedCore` over the started Kotlin `CoreServices`: SKIE turns the recorder's `StateFlow` and the repository's
/// `Flow` into async sequences, mapped here to the Swift mirrors.
@MainActor
final class KotlinCore: SharedCore {
    private let services: CoreServices

    init(_ services: CoreServices) {
        self.services = services
    }

    func statusUpdates() -> AsyncStream<DPSharedBridge.RecorderStatus> {
        let flow = UncheckedSendable(services.recorder.status)
        return AsyncStream { continuation in
            let task = Task {
                for await next in flow.value { continuation.yield(KotlinMapping.swift(next)) }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func tripUpdates() -> AsyncStream<[TripSummary]> {
        let flow = UncheckedSendable(services.trips.observeTrips())
        return AsyncStream { continuation in
            let task = Task {
                for await trips in flow.value { continuation.yield(trips.compactMap(KotlinMapping.summary)) }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func onLaunch() { services.recorder.onLaunch() }
    func onWake(_ event: DPSharedBridge.WakeEvent) { services.recorder.onWake(event: KotlinMapping.kotlin(event)) }
    func stop() { services.recorder.stop() }
    func markPassenger(keep: Bool) { services.recorder.markPassenger(keep: keep) }
    func setAutoDetect(enabled: Bool) { services.recorder.setAutoDetect(enabled: enabled) }
    func onLaunch(reason: DPSharedBridge.RearmReason) { services.recorder.onLaunch(reason: KotlinMapping.kotlin(reason)) }
    func onForeground() { services.recorder.onForeground() }
    func onWatchdogOpened() { services.recorder.onWatchdogOpened() }
    func setTriggersAvailable(_ available: Bool) { services.recorder.setTriggersAvailable(available: available) }
    func setInDriveScreenVisible(_ visible: Bool) { services.recorder.setInDriveScreenVisible(visible: visible) }
}

/// The DPPlatform adapters as `PlatformBindings` for `DrivePerfCore.start(platform:)` (ADR-002 §5). Each function is
/// called once, at start; the port objects are thread-safe wrappers.
final class CorePlatformBindings: NSObject, DrivePerfShared.PlatformBindings, @unchecked Sendable {
    private let ports: (
        clock: KotlinClockPort, location: KotlinLocationPort, motion: KotlinMotionPort, activity: KotlinActivityPort,
        power: KotlinPowerPort, parkedRegion: KotlinParkedRegionPort, tripFiles: KotlinTripFilePort
    )
    private let device: String
    private let analytics: any DrivePerfShared.AnalyticsSink = StubAnalyticsSink()
    private let watchdogPort: any DrivePerfShared.AutoDetectWatchdog

    /// `watchdog` is the F-CAP-14 notification adapter; without one the core schedules nothing.
    @MainActor
    init(ports: CorePorts, deviceId: String, watchdog: (any AutoDetectWatchdogBridge)? = nil) {
        self.ports = (
            ports.clock, ports.location, ports.motion, ports.activity, ports.power, ports.parkedRegion, ports.tripFiles
        )
        device = deviceId
        watchdogPort = watchdog.map { KotlinWatchdogPort($0) } ?? NoOpAutoDetectWatchdog.shared
    }

    func clock() -> any DrivePerfShared.Clock { ports.clock }
    func location() -> any DrivePerfShared.LocationSource { ports.location }
    func motion() -> any DrivePerfShared.MotionSource { ports.motion }
    func activity() -> any DrivePerfShared.ActivitySource { ports.activity }
    func power() -> any DrivePerfShared.PowerState { ports.power }
    func parkedRegion() -> any DrivePerfShared.ParkedRegionMonitor { ports.parkedRegion }
    func tripFiles() -> any DrivePerfShared.TripFileStore { ports.tripFiles }
    func deviceId() -> String { device }
    func analyticsSink() -> any DrivePerfShared.AnalyticsSink { analytics }
    func watchdog() -> any DrivePerfShared.AutoDetectWatchdog { watchdogPort }
}

/// `AutoDetectWatchdog`.
final class KotlinWatchdogPort: NSObject, DrivePerfShared.AutoDetectWatchdog, Sendable {
    private let watchdog: any AutoDetectWatchdogBridge

    init(_ watchdog: any AutoDetectWatchdogBridge) {
        self.watchdog = watchdog
    }

    func schedule(fireAtWallMs: Int64) { watchdog.schedule(fireAtWallMs: fireAtWallMs) }
    func cancel() { watchdog.cancel() }
}
