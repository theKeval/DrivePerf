import DPDesignSystem
import DPSharedBridge
import SwiftUI

/// S-ANL-01 Analysis for one trip. A placeholder until DRIVE-45 adds the workspace; nothing opens it yet. `title` is the
/// trip detail's title, shown under "Analysis". It closes its presenter when it goes away.
public struct AnalysisView: View {
    private let presenter: any AnalysisPresenting
    private let title: String
    @Environment(\.dismiss) private var dismiss

    public init(presenter: any AnalysisPresenting, title: String) {
        self.presenter = presenter
        self.title = title
    }

    public var body: some View {
        Text("Analysis is on its way.")
            .font(DPFont.body)
            .foregroundStyle(DPColor.text2)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(DPColor.bgGrouped)
            .navigationTitle("Analysis")
            .navigationSubtitle(title)
            .task {
                for await navigation in presenter.navigation() where navigation == .close { dismiss() }
            }
            .onDisappear { presenter.close() }
    }
}
