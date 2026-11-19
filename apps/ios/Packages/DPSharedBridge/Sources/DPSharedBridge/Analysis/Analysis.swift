import Foundation

// Swift mirrors of `com.driveperf.presentation.analysis` (S-ANL-01, DRIVE-45). A placeholder until DRIVE-45 adds the
// channels, the cursor and the range statistics. The app target maps the Kotlin presenter (`CoreAnalysis.swift`).

/// Kotlin `AnalysisState`.
public struct AnalysisScreenState: Equatable, Sendable {
    public var tripId: String
    public var loading: Bool

    public init(tripId: String = "", loading: Bool = true) {
        self.tripId = tripId
        self.loading = loading
    }
}

/// Kotlin `AnalysisIntent`.
public enum AnalysisAction: Equatable, Sendable {
    case back
}

/// Kotlin `AnalysisEffect`.
public enum AnalysisNavigation: Equatable, Sendable {
    case close
}

/// The shared Analysis presenter as features see it.
@MainActor
public protocol AnalysisPresenting: AnyObject {
    var state: AnalysisScreenState { get }
    /// The current state first, then every change.
    func states() -> AsyncStream<AnalysisScreenState>
    /// One-shot navigation.
    func navigation() -> AsyncStream<AnalysisNavigation>
    func dispatch(_ action: AnalysisAction)
    func close()
}

/// Makes one presenter per Analysis screen (`CoreServices.analysisPresenter`).
@MainActor
public protocol AnalysisPresenterMaking: AnyObject {
    func presenter(tripId: String) -> any AnalysisPresenting
}
