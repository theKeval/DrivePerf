import CoreLocation
import DPSharedBridge
import Foundation

/// `LocationSource` on Core Location. Two streams run together, as in Spike 1: `CLLocationUpdate.liveUpdates`
/// (automotive navigation) and the legacy `startUpdatingLocation` keep-alive, because a `CLBackgroundActivitySession`
/// created during a background wake can report `insufficientlyInUse`, and whether `liveUpdates` alone keeps a
/// background-woken app running is still untested (spike results, A19). One fix per ~second is kept.
@MainActor
public final class CoreLocationSource: NSObject, LocationSourceBridge {
    /// Turn off only for an A19 experiment on `liveUpdates` alone.
    public var legacyKeepAlive = true

    private let clock: any ClockBridge
    private let manager = CLLocationManager()
    private var backgroundSession: CLBackgroundActivitySession?
    private var liveTask: Task<Void, Never>?
    private var notBefore = Date.distantPast
    private var lastAccepted: Date?
    private var onSample: (@MainActor (LocationSample) -> Void)?
    private var onStationary: (@MainActor (Bool) -> Void)?
    private var onIssue: (@MainActor (LocationIssue) -> Void)?

    public init(clock: any ClockBridge = SystemClock()) {
        self.clock = clock
        super.init()
        manager.delegate = self
        manager.activityType = .automotiveNavigation
        manager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        manager.distanceFilter = kCLDistanceFilterNone
        manager.pausesLocationUpdatesAutomatically = false
        manager.showsBackgroundLocationIndicator = true
    }

    public func start(
        notBeforeWallMs: Int64,
        onSample: @escaping @MainActor (LocationSample) -> Void,
        onStationary: @escaping @MainActor (Bool) -> Void,
        onIssue: @escaping @MainActor (LocationIssue) -> Void
    ) {
        stop()
        notBefore = Date(wallMs: notBeforeWallMs)
        self.onSample = onSample
        self.onStationary = onStationary
        self.onIssue = onIssue
        backgroundSession = CLBackgroundActivitySession()
        liveTask = Task { [weak self] in
            do {
                for try await update in CLLocationUpdate.liveUpdates(.automotiveNavigation) {
                    guard let self, !Task.isCancelled else { return }
                    self.handle(update)
                }
            } catch {
                self?.onIssue?(.locationUnavailable)
            }
        }
        if legacyKeepAlive {
            manager.allowsBackgroundLocationUpdates = true
            manager.startUpdatingLocation()
        }
    }

    public func stop() {
        liveTask?.cancel()
        liveTask = nil
        manager.stopUpdatingLocation()
        manager.allowsBackgroundLocationUpdates = false
        backgroundSession?.invalidate()
        backgroundSession = nil
        lastAccepted = nil
        onSample = nil
        onStationary = nil
        onIssue = nil
    }

    private func handle(_ update: CLLocationUpdate) {
        if update.authorizationDenied { onIssue?(.authorizationDenied) }
        if update.insufficientlyInUse { onIssue?(.insufficientlyInUse) }
        if update.serviceSessionRequired { onIssue?(.serviceSessionRequired) }
        if update.locationUnavailable { onIssue?(.locationUnavailable) }
        onStationary?(update.stationary)
        if let location = update.location { ingest(location) }
    }

    fileprivate func ingest(_ location: CLLocation) {
        guard
            let sample = LocationMapping.sample(
                from: location, notBefore: notBefore, lastAccepted: lastAccepted, clock: clock)
        else { return }
        lastAccepted = location.timestamp
        onSample?(sample)
    }
}

extension CoreLocationSource: @preconcurrency CLLocationManagerDelegate {
    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        locations.forEach(ingest)
    }
}

/// CLLocation → `LocationSample` (§2.1, §2.2). Pure, so it is unit-tested.
public enum LocationMapping {
    /// Two streams deliver the same fix; keep one per this interval.
    public static let minimumInterval: TimeInterval = 0.5
    /// A fix may be timestamped slightly before the wake that it caused (spike: up to 0.25 s).
    public static let wakeTolerance: TimeInterval = 5

    public static func sample(
        from location: CLLocation, notBefore: Date, lastAccepted: Date?, clock: any ClockBridge
    ) -> LocationSample? {
        if location.timestamp < notBefore.addingTimeInterval(-wakeTolerance) { return nil }
        if let lastAccepted, location.timestamp.timeIntervalSince(lastAccepted) < minimumInterval { return nil }
        let ageNs = Int64((Date().timeIntervalSince(location.timestamp) * 1_000_000_000).rounded())
        return LocationSample(
            tMonoNs: clock.nowMonoNs() - max(ageNs, 0),
            wallMs: location.timestamp.wallMs,
            lat: location.coordinate.latitude,
            lng: location.coordinate.longitude,
            altM: location.verticalAccuracy >= 0 ? location.altitude : nil,
            hAccM: location.horizontalAccuracy >= 0 ? location.horizontalAccuracy : nil,
            vAccM: location.verticalAccuracy >= 0 ? location.verticalAccuracy : nil,
            speedMps: location.speed >= 0 ? location.speed : nil,
            speedAccMps: location.speedAccuracy >= 0 ? location.speedAccuracy : nil,
            courseDeg: location.course >= 0 ? location.course : nil,
            courseAccDeg: location.courseAccuracy >= 0 ? location.courseAccuracy : nil)
    }
}
