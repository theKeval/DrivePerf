import DPDesignSystem
import DPSharedBridge
import SwiftUI

/// The single scaffold screen (DRIVE-59). The text comes from shared code via `DrivePerfCore.hello()`.
public struct HelloView: View {
    @State private var viewModel: HelloViewModel

    public init(provider: any HelloProviding) {
        _viewModel = State(initialValue: HelloViewModel(provider: provider))
    }

    public var body: some View {
        VStack(spacing: 8) {
            Text(viewModel.content.title)
                .font(.largeTitle.bold())
                .foregroundStyle(DrivePerfColors.glide)
            Text(viewModel.content.subtitle)
                .font(.body)
                .foregroundStyle(DrivePerfColors.onNight)
                .multilineTextAlignment(.center)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(DrivePerfColors.night)
        .preferredColorScheme(.dark)
    }
}

private struct PreviewProvider: HelloProviding {
    func hello() -> HelloContent {
        HelloContent(title: "Hello DrivePerf", subtitle: "Shared core running on iOS 26.0")
    }
}

#Preview {
    HelloView(provider: PreviewProvider())
}
