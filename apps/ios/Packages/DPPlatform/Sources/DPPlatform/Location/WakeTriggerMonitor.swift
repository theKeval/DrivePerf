import CoreLocation
import DPSharedBridge
import Foundation
import UIKit

/// Platform wake triggers (pipeline-and-scoring.md §1.3), with the Spike 1 changes:
/// 1. the parked-location `CLMonitor` region is the primary trigger, re-armed at every trip end and every launch;
///    significant change stays as a fallback, Visits only place the parking spot when there is no trip yet;
/// 7. the monitor name is alphanumeric (`parked-spot` crashed `CLMonitor.init`);
/// 8. stale events and cached fixes are dropped by `WakeFilter`.
///
/// `arm` must run early in `didFinishLaunching`: Core Location only holds events for a relaunched app for a few
/// seconds (ADR-002 §5: sessions are recreated in Swift before the Kotlin core starts).
@MainActor
public final class WakeTriggerMonitor: NSObject, WakeTriggerBridge {
    /// Fixed and alphanumeric; `MonitorNaming.isValid` is unit-tested against it.
    nonisolated public static let monitorName = "parkedSpot"
    nonisolated public static let parkedIdentifier = "parked"

    public private(set) var isParkedRegionArmed = false

    private let clock: any ClockBridge
    private let spotStore: ParkedSpotStore
    private let filter = WakeFilter()
    private let slcManager = CLLocationManager()
    private let visitManager = CLLocationManager()
    private let processStartWallMs: Int64
    private var monitor: CLMonitor?
    private var monitorTask: Task<Void, Never>?
    private var onWake: (@MainActor (WakeEvent) -> Void)?

    public init(clock: any ClockBridge = SystemClock(), spotStore: ParkedSpotStore = ParkedSpotStore()) {
        self.clock = clock
        self.spotStore = spotStore
        processStartWallMs = clock.nowWallMs()
        super.init()
        slcManager.delegate = self
        visitManager.delegate = self
    }

    public func arm(onWake: @escaping @MainActor (WakeEvent) -> Void) {
        self.onWake = onWake
        slcManager.startMonitoringSignificantLocationChanges()
        visitManager.startMonitoringVisits()
        restoreMonitor()
    }

    public func disarm() {
        onWake = nil
        slcManager.stopMonitoringSignificantLocationChanges()
        visitManager.stopMonitoringVisits()
        monitorTask?.cancel()
        monitorTask = nil
        if let monitor {
            Task { await monitor.remove(Self.parkedIdentifier) }
        }
        isParkedRegionArmed = false
    }

    public func armParkedRegion(lat: Double, lng: Double, radiusM: Double) {
        let spot = ParkedSpot(lat: lat, lng: lng, radiusM: radiusM)
        spotStore.save(spot)
        guard let monitor else { return }
        add(spot, to: monitor)
    }

    private func add(_ spot: ParkedSpot, to monitor: CLMonitor) {
        let condition = CLMonitor.CircularGeographicCondition(
            center: CLLocationCoordinate2D(latitude: spot.lat, longitude: spot.lng), radius: spot.radiusM)
        Task { [weak self] in
            await monitor.remove(Self.parkedIdentifier)
            await monitor.add(condition, identifier: Self.parkedIdentifier, assuming: .satisfied)
            self?.isParkedRegionArmed = true
        }
    }

    private func restoreMonitor() {
        guard monitorTask == nil else { return }
        monitorTask = Task { [weak self] in
            let monitor = await CLMonitor(Self.monitorName)
            guard let self else { return }
            self.monitor = monitor
            let armed = await monitor.identifiers.contains(Self.parkedIdentifier)
            self.isParkedRegionArmed = armed
            // A Visit or a trip end can save the spot before this monitor exists, so arm it here too.
            if !armed, let spot = self.spotStore.load() { self.add(spot, to: monitor) }
            do {
                for try await event in await monitor.events {
                    self.handle(event)
                }
            } catch {
                self.isParkedRegionArmed = false
            }
        }
    }

    private func handle(_ event: CLMonitor.Event) {
        guard event.identifier == Self.parkedIdentifier, event.state == .unsatisfied else { return }
        deliver(.parkedExit, eventWallMs: event.date.wallMs, source: .region)
    }

    fileprivate func deliver(_ kind: WakeKind, eventWallMs: Int64, source: WakeFilter.Source) {
        let now = clock.nowWallMs()
        let foreground = UIApplication.shared.applicationState == .active
        guard
            filter.accepts(
                source: source, eventWallMs: eventWallMs, nowWallMs: now, processStartWallMs: processStartWallMs,
                appInForeground: foreground)
        else { return }
        onWake?(WakeEvent(kind: kind, eventWallMs: eventWallMs, receivedWallMs: now))
    }

    fileprivate func handleVisitArrival(lat: Double, lng: Double) {
        // With no previous trip, the first parking spot comes from a Visit (PRD F-CAP-02).
        guard spotStore.load() == nil else { return }
        armParkedRegion(lat: lat, lng: lng, radiusM: ParkedSpot.defaultRadiusM)
    }
}

extension WakeTriggerMonitor: @preconcurrency CLLocationManagerDelegate {
    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard manager === slcManager, let location = locations.last else { return }
        deliver(.moveSignal, eventWallMs: location.timestamp.wallMs, source: .significantChange)
    }

    public func locationManager(_ manager: CLLocationManager, didVisit visit: CLVisit) {
        guard visit.departureDate == .distantFuture else { return }
        handleVisitArrival(lat: visit.coordinate.latitude, lng: visit.coordinate.longitude)
    }
}

/// Drops wakes that would open a false trip candidate (spike change 8). Pure and unit-tested.
public struct WakeFilter: Sendable {
    public enum Source: Sendable { case region, significantChange }

    /// Every cold launch delivered a cached significant-change fix 504–52,106 s old in the spike.
    public var maxLocationAgeMs: Int64 = 300_000
    /// Region events are delivered late on a relaunch; older than this, the car has long gone.
    public var maxRegionAgeMs: Int64 = 900_000
    /// Starting significant change delivers one immediate fix; opening the app must not look like a drive.
    public var initialDeliveryWindowMs: Int64 = 10_000

    public init() {}

    public func accepts(
        source: Source, eventWallMs: Int64, nowWallMs: Int64, processStartWallMs: Int64, appInForeground: Bool
    ) -> Bool {
        let age = nowWallMs - eventWallMs
        switch source {
        case .region:
            return age <= maxRegionAgeMs
        case .significantChange:
            if appInForeground, nowWallMs - processStartWallMs < initialDeliveryWindowMs { return false }
            return age <= maxLocationAgeMs
        }
    }
}

/// `CLMonitor` names must be alphanumeric: anything else raises an assertion in `CLMonitor.init` (spike change 7).
public enum MonitorNaming {
    public static func isValid(_ name: String) -> Bool {
        !name.isEmpty && name.unicodeScalars.allSatisfy { CharacterSet.alphanumerics.contains($0) && $0.isASCII }
    }
}

/// The last parking spot. Stays on this phone, in a file readable once the phone has been unlocked since boot.
public struct ParkedSpot: Codable, Equatable, Sendable {
    public static let defaultRadiusM = 120.0

    public var lat: Double
    public var lng: Double
    public var radiusM: Double
}

public struct ParkedSpotStore: Sendable {
    private let url: URL

    public init(directory: URL = FileTripStore.defaultRoot) {
        url = directory.appendingPathComponent("parked-spot.json")
    }

    public func save(_ spot: ParkedSpot) {
        guard let data = try? JSONEncoder().encode(spot) else { return }
        try? FileManager.default.createDirectory(
            at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? data.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }

    public func load() -> ParkedSpot? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(ParkedSpot.self, from: data)
    }
}
