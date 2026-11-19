import DPSharedBridge
import Foundation
import SwiftUI

/// What S-RPL-01 needs, built once by `AppModel` and handed to the Drives screens through the environment.
@MainActor
public final class ReplayServices {
    let presenters: any ReplayPresenterMaking
    let connectivity: (any ConnectivityMonitoring)?
    let display: DisplaySettingsModel

    public init(
        presenters: any ReplayPresenterMaking, connectivity: (any ConnectivityMonitoring)? = nil,
        display: DisplaySettingsModel
    ) {
        self.presenters = presenters
        self.connectivity = connectivity
        self.display = display
    }

    /// A fresh Replay for [tripId], opened from [source] (the shared presenter sends `replay_opened {source}`).
    func model(tripId: String, source: ReplayOpenSource) -> ReplayViewModel {
        ReplayViewModel(
            presenter: presenters.presenter(tripId: tripId, source: source), connectivity: connectivity,
            display: display, sheets: ReplaySheetSource(presenters: presenters, tripId: tripId))
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
