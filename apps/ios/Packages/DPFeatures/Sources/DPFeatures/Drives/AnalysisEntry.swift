import DPSharedBridge
import SwiftUI

private struct AnalysisPresentersKey: EnvironmentKey {
    // A nil constant: nothing is shared across isolation domains.
    nonisolated(unsafe) static let defaultValue: (any AnalysisPresenterMaking)? = nil
}

extension EnvironmentValues {
    /// S-ANL-01 presenters (`AppModel.analysis`, passed down by `AppRootView`). Nil where Analysis is not available
    /// (previews, tests): trip detail then hides the Analysis button.
    public var analysisPresenters: (any AnalysisPresenterMaking)? {
        get { self[AnalysisPresentersKey.self] }
        set { self[AnalysisPresentersKey.self] = newValue }
    }
}
