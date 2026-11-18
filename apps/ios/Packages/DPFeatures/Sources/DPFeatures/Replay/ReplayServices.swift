import DPSharedBridge
import Foundation
import SwiftUI

/// What S-RPL-01 needs, built once by `AppModel` and handed to the Drives screens through the environment.
@MainActor
public final class ReplayServices {
    let loader: any ReplayLoading
    let analytics: (any ReplayAnalytics)?
    let connectivity: (any ConnectivityMonitoring)?
    let lock: any ReplayProLock
    let display: DisplaySettingsModel

    public init(
        loader: any ReplayLoading, analytics: (any ReplayAnalytics)? = nil,
        connectivity: (any ConnectivityMonitoring)? = nil, lock: any ReplayProLock = ReplayProLockNotBuilt(),
        display: DisplaySettingsModel
    ) {
        self.loader = loader
        self.analytics = analytics
        self.connectivity = connectivity
        self.lock = lock
        self.display = display
    }

    /// A fresh Replay for [trip], opened from [source] (`replay_opened {source}`).
    func model(for trip: TripSummary, source: String) -> ReplayViewModel {
        ReplayViewModel(
            tripId: trip.id, loader: loader, analytics: analytics, connectivity: connectivity, display: display,
            lock: lock, tripStartedAt: trip.startedAt, source: source)
    }
}

private struct ReplayServicesKey: EnvironmentKey {
    static let defaultValue: ReplayServices? = nil
}

extension EnvironmentValues {
    /// Nil where Replay is not available (previews, tests): the entry is hidden.
    var replayServices: ReplayServices? {
        get { self[ReplayServicesKey.self] }
        set { self[ReplayServicesKey.self] = newValue }
    }
}
