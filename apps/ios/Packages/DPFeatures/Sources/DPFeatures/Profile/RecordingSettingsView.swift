import DPDesignSystem
import SwiftUI

/// S-SET-03 Recording settings: the "Automatic trips" switch (DRIVE-153). Reached from Profile and from the capture
/// status sheet (S-HOME-02).
struct RecordingSettingsView: View {
    @Bindable var model: RecordingSettingsViewModel
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        List {
            if !model.autoTripsOn {
                Section {
                    Text("Manual mode. Tap Start in the app, Control Center or the widget when you set off.")
                        .font(DPFont.bodySecondary)
                        .listRowBackground(DPColor.fill)
                }
            }
            Section {
                Toggle(
                    isOn: Binding(
                        get: { model.autoTripsOn }, set: { on in Task { await model.setAutoTrips(on) } })
                ) {
                    VStack(alignment: .leading, spacing: DPSpace.s0_5) {
                        Text("Automatic trips").font(DPFont.body)
                        Text(
                            model.autoTripsOn
                                ? "DrivePerf notices when you set off and stops when you park"
                                : "Off. Drives record only when you tap Start"
                        )
                        .font(DPFont.meta).foregroundStyle(DPColor.text2)
                    }
                }
                .disabled(model.isRequesting)
                .sensoryFeedback(.selection, trigger: model.autoTripsOn)
                if model.autoTripsOn {
                    HStack(spacing: DPSpace.s3) {
                        Circle().fill(model.attentionReasons.isEmpty ? DPColor.live : DPColor.warning)
                            .frame(width: 10, height: 10)
                        VStack(alignment: .leading, spacing: 0) {
                            Text(model.statusText).font(DPFont.body)
                            if let detail = model.statusDetail {
                                Text(detail).font(DPFont.meta).foregroundStyle(DPColor.text2)
                            }
                        }
                    }
                    .accessibilityElement(children: .combine)
                    if !model.attentionReasons.isEmpty {
                        Button("Open Settings") { model.openSettings() }
                    }
                }
            } footer: {
                Text(
                    "Auto-detect catches most drives. The first few hundred metres may be estimated, and "
                        + "force-quitting the app pauses auto-detect. Checks run on this phone.")
            }
        }
        .tint(DPColor.accent)
        .navigationTitle("Recording")
        .navigationBarTitleDisplayMode(.inline)
        .task { await model.refresh() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { Task { await model.refresh() } }
        }
        .sheet(item: $model.explainer) { explainer in
            LocationAccessExplainerSheet(model: model, step: explainer)
        }
    }
}

/// Shown when automatic trips are switched on while location is not "Always". It follows onboarding's wording
/// (S-ONB-06, S-ONB-08, S-ONB-16 alwaysDeclined): the While Using prompt if location was never asked, then the one
/// iOS Always prompt when it can still appear, otherwise Settings.
private struct LocationAccessExplainerSheet: View {
    let model: RecordingSettingsViewModel
    let step: RecordingSettingsViewModel.AlwaysExplainer

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s4) {
            HStack(spacing: DPSpace.s3) {
                GlyphDisc(.gps, tone: .accent, size: 36)
                Text(title).font(DPFont.sheetTitle).foregroundStyle(DPColor.text)
                    .accessibilityAddTraits(.isHeader)
                Spacer()
            }
            Text(message).font(DPFont.body).foregroundStyle(DPColor.text2)
            Text("Automatic trips stay on, and the status shows “needs attention” until it is granted.")
                .font(DPFont.meta).foregroundStyle(DPColor.text3)
            DPPrimaryButton(step == .settings ? "Open Settings" : "Continue", icon: step == .settings ? .gear : nil) {
                Task { await model.confirmAlways() }
            }
            .disabled(model.isRequesting)
            DPSecondaryButton("Not now") { model.dismissExplainer() }
        }
        .padding(DPSpace.s6)
        .presentationDetents([.medium])
        .presentationBackground(.regularMaterial)
    }

    private var title: String {
        step == .initialAccess ? "Allow location" : "Allow location “Always”"
    }

    private var message: String {
        switch step {
        case .initialAccess:
            "DrivePerf needs your location to record drives. iOS will ask first, then once more to allow “Always” so "
                + "drives can start on their own."
        case .alwaysUpgrade:
            "To start drives on its own, DrivePerf needs location set to “Always”. iOS will ask once."
        case .settings:
            "Location is not set to “Always”, so DrivePerf can’t start drives on its own. Open Settings, choose "
                + "Location, then Always."
        }
    }
}
