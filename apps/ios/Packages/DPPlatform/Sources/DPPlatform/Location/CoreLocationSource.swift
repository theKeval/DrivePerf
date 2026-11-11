import CoreLocation
import DPSharedBridge
import Foundation

/// `LocationSource` on Core Location. Two streams run together, as in Spike 1: `CLLocationUpdate.liveUpdates`
/// (automotive navigation) and the legacy `startUpdatingLocation` keep-alive, because a `CLBackgroundActivitySession`
/// created during a background wake can report `insufficientlyInUse`, and whether `liveUpdates` alone keeps a
/// background-woken app running is still untested (spike results, A19). One fix per ~second is kept.
///
/// Callable from any thread (the Kotlin core calls ports from its own); Core Location runs on the main thread.
/// Fixes are delivered one per batch at ~1 Hz. Dropping fixes cached from before a wake is the core's job.
public final class CoreLocationSource: LocationSourceBridge, @unchecked Sendable {
    private let engine: Engine

    @MainActor
    public init(clock: SystemClock = SystemClock()) {
        engine = Engine(clock: clock)
    }

    /// Turn off only for an A19 experiment on `liveUpdates` alone.
    @MainActor
    public var legacyKeepAlive: Bool {
        get { engine.legacyKeepAlive }
        set { engine.legacyKeepAlive = newValue }
    }

    public func start(sink: @escaping @Sendable ([LocationSample]) -> Void) {
        let engine = engine
        onMain { engine.start(sink: sink) }
    }

    public func stop() {
        let engine = engine
        onMain { engine.stop() }
    }

    @MainActor
    private final class Engine: NSObject, CLLocationManagerDelegate {
        var legacyKeepAlive = true
        private let clock: SystemClock
        private let manager = CLLocationManager()
        private var backgroundSession: CLBackgroundActivitySession?
        private var liveTask: Task<Void, Never>?
        private var lastAccepted: Date?
        private var sink: (@Sendable ([LocationSample]) -> Void)?

        init(clock: SystemClock) {
            self.clock = clock
            super.init()
            manager.delegate = self
            manager.activityType = .automotiveNavigation
            manager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
            manager.distanceFilter = kCLDistanceFilterNone
            manager.pausesLocationUpdatesAutomatically = false
            manager.showsBackgroundLocationIndicator = true
        }

        func start(sink: @escaping @Sendable ([LocationSample]) -> Void) {
            self.sink = sink
            guard liveTask == nil else { return }
            backgroundSession = CLBackgroundActivitySession()
            liveTask = Task { [weak self] in
                do {
                    for try await update in CLLocationUpdate.liveUpdates(.automotiveNavigation) {
                        guard let self, !Task.isCancelled else { return }
                        if let location = update.location { self.ingest(location) }
                    }
                } catch {
                    // The stream ended (authorization lost); the app reports permissions separately.
                }
            }
            if legacyKeepAlive {
                manager.allowsBackgroundLocationUpdates = true
                manager.startUpdatingLocation()
            }
        }

        func stop() {
            liveTask?.cancel()
            liveTask = nil
            manager.stopUpdatingLocation()
            manager.allowsBackgroundLocationUpdates = false
            backgroundSession?.invalidate()
            backgroundSession = nil
            lastAccepted = nil
            sink = nil
        }

        func ingest(_ location: CLLocation) {
            guard let sink,
                let sample = LocationMapping.sample(from: location, lastAccepted: lastAccepted, clock: clock)
            else { return }
            lastAccepted = location.timestamp
            sink([sample])
        }

        nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
            MainActor.assumeIsolated { locations.forEach(ingest) }
        }
    }
}

/// CLLocation → `LocationSample` (§2.1, §2.2). Pure, so it is unit-tested.
public enum LocationMapping {
    /// Two streams deliver the same fix; keep one per this interval.
    public static let minimumInterval: TimeInterval = 0.5
    public static func sample(
        from location: CLLocation, lastAccepted: Date?, clock: SystemClock
    ) -> LocationSample? {
        if let lastAccepted, location.timestamp.timeIntervalSince(lastAccepted) < minimumInterval { return nil }
        return LocationSample(
            tMonoNs: clock.monotonicNs(atWall: location.timestamp),
            wallMs: location.timestamp.wallMs,
            lat: location.coordinate.latitude,
            lng: location.coordinate.longitude,
            altM: location.verticalAccuracy >= 0 ? location.altitude : nil,
            hAccM: location.horizontalAccuracy >= 0 ? location.horizontalAccuracy : nil,
            vAccM: location.verticalAccuracy >= 0 ? location.verticalAccuracy : nil,
            speedMps: location.speed >= 0 ? location.speed : nil,
            speedAccMps: location.speedAccuracy >= 0 ? location.speedAccuracy : nil,
            courseDeg: location.course >= 0 ? location.course : nil,
            courseAccDeg: location.courseAccuracy >= 0 ? location.courseAccuracy : nil,
            provider: provider(of: location),
            ageMs: ageMs(of: location, now: clock.wallMs()))
    }

    /// F-DQ-03: Core Location tells only accessory and simulated fixes apart; the rest are its fused position.
    public static func provider(of location: CLLocation) -> FixProvider {
        guard let info = location.sourceInformation else { return .fused }
        return provider(simulated: info.isSimulatedBySoftware, accessory: info.isProducedByAccessory)
    }

    /// A simulated fix wins over the accessory flag; anything else is Core Location's fused position.
    public static func provider(simulated: Bool, accessory: Bool) -> FixProvider {
        if simulated { return .simulated }
        if accessory { return .accessory }
        return .fused
    }

    /// How old the fix was at delivery, in ms; nil for a fix stamped in the future (a clock change).
    public static func ageMs(of location: CLLocation, now wallMs: Int64) -> Int64? {
        let age = wallMs - location.timestamp.wallMs
        return age >= 0 ? age : nil
    }
}
