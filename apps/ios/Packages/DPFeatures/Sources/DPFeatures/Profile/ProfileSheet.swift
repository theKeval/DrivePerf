import DPDesignSystem
import DPSharedBridge
import SwiftUI

/// Profile (S-ACC-01) in local mode, as a large sheet with its own navigation stack (S-NAV-01).
struct ProfileSheet: View {
    let model: ProfileViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Label { Text("Local mode · no account") } icon: { Image(dp: .shield) }
                    if model.flags.signInWithApple {
                        Text("Sign in with Apple")
                    }
                } footer: {
                    Text("Your trips stay on your phone unless you turn on backup.")
                }
                Section("Analytics and diagnostics") {
                    Toggle(
                        "Share usage statistics",
                        isOn: Binding(get: { model.shareUsageStatistics }, set: model.setShareUsageStatistics))
                    Toggle(
                        "Send crash reports",
                        isOn: Binding(get: { model.sendCrashReports }, set: model.setSendCrashReports))
                    LabeledContent("Crash reports", value: model.crashStatusText)
                }
                Section("Diagnostics") {
                    NavigationLink("Self-test") { SelfTestView(model: model) }
                }
                Section("About") {
                    LabeledContent("Version", value: Self.version)
                    LabeledContent("Shared core", value: model.coreInfo.subtitle)
                }
            }
            .tint(DPColor.accent)
            .navigationTitle("Profile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) { Button(role: .close) { dismiss() } }
            }
        }
        .presentationDetents([.large])
    }

    private static var version: String {
        let info = Bundle.main.infoDictionary
        let short = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return "\(short) (\(build))"
    }
}

/// On-device self-test (spike change 10). Results show counts and timings, never a location.
struct SelfTestView: View {
    let model: ProfileViewModel

    var body: some View {
        List {
            Section {
                Button(model.isSelfTestRunning ? "Running…" : "Run self-test") {
                    Task { await model.runSelfTest() }
                }
                .disabled(model.isSelfTestRunning)
            } footer: {
                Text(
                    "Checks storage, permissions, GPS, motion at 50 Hz, the wake path and a manual trip on this "
                        + "phone, in about a minute. Keep the phone still, face up, near a window."
                )
            }
            if let summary = model.selfTestSummary {
                Section { Text(summary).font(DPFont.cardTitle) }
            }
            Section {
                ForEach(model.selfTestSteps) { step in
                    SelfTestRow(step: step)
                }
            }
        }
        .navigationTitle("Self-test")
    }
}

private struct SelfTestRow: View {
    let step: SelfTestStep

    var body: some View {
        HStack(alignment: .top, spacing: DPSpace.s3) {
            symbol.frame(width: 22)
            VStack(alignment: .leading, spacing: DPSpace.s0_5) {
                Text(step.title).font(DPFont.body)
                if let detail { Text(detail).font(DPFont.meta).foregroundStyle(DPColor.text2) }
            }
        }
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder private var symbol: some View {
        switch step.state {
        case .pending: Image(systemName: "circle").foregroundStyle(DPColor.text3)
        case .running: ProgressView()
        case .passed: Image(systemName: "checkmark.circle.fill").foregroundStyle(DPColor.positive)
        case .failed: Image(systemName: "xmark.octagon.fill").foregroundStyle(DPColor.critical)
        case .skipped: Image(systemName: "minus.circle").foregroundStyle(DPColor.text3)
        }
    }

    private var detail: String? {
        switch step.state {
        case .passed(let text), .failed(let text), .skipped(let text): text
        case .pending, .running: nil
        }
    }
}
