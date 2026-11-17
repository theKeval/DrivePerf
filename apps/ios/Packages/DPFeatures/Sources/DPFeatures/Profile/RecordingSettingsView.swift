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
            AlwaysLocationExplainerSheet(model: model, canAsk: explainer.canAsk)
        }
    }
}

/// Shown when automatic trips are switched on while location is not "Always". It follows onboarding's wording
/// (S-ONB-08, S-ONB-16 alwaysDeclined): one iOS prompt when it can still appear, Settings otherwise.
private struct AlwaysLocationExplainerSheet: View {
    let model: RecordingSettingsViewModel
    let canAsk: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s4) {
            HStack(spacing: DPSpace.s3) {
                GlyphDisc(.gps, tone: .accent, size: 36)
                Text("Allow location “Always”").font(DPFont.sheetTitle).foregroundStyle(DPColor.text)
                    .accessibilityAddTraits(.isHeader)
                Spacer()
            }
            Text(
                canAsk
                    ? "To start drives on its own, DrivePerf needs location set to “Always”. iOS will ask once."
                    : "Location is not set to “Always”, so DrivePerf can’t start drives on its own. Open Settings, "
                        + "choose Location, then Always."
            )
            .font(DPFont.body).foregroundStyle(DPColor.text2)
            Text("Automatic trips stay on, and the status shows “needs attention” until it is granted.")
                .font(DPFont.meta).foregroundStyle(DPColor.text3)
            DPPrimaryButton(canAsk ? "Continue" : "Open Settings", icon: canAsk ? nil : .gear) {
                Task { await model.confirmAlways() }
            }
            .disabled(model.isRequesting)
            DPSecondaryButton("Not now") { model.dismissExplainer() }
        }
        .padding(DPSpace.s6)
        .presentationDetents([.medium])
        .presentationBackground(.regularMaterial)
    }
}
