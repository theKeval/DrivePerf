import DPDesignSystem
import DPSharedBridge
import SwiftUI

/// S-SET-03 Recording settings: the "Automatic trips" switch (DRIVE-153) and Pause auto-detect (DRIVE-152). Reached
/// from Profile and from the capture status sheet (S-HOME-02).
struct RecordingSettingsView: View {
    @Bindable var model: RecordingSettingsViewModel
    @Environment(\.scenePhase) private var scenePhase
    @State private var isPausePresented = false

    var body: some View {
        List {
            Group {
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
                        Circle().fill(statusColor).frame(width: 10, height: 10)
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
                    if let pause = model.pause, pause.isAvailable {
                        Button {
                            pause.prepare()
                            isPausePresented = true
                        } label: {
                            PauseAutoDetectRow(isPaused: pause.isPaused)
                        }
                        .buttonStyle(.plain)
                    }
                }
            } footer: {
                Text(
                    "Auto-detect catches most drives. The first few hundred metres may be estimated, and "
                        + "force-quitting the app pauses auto-detect. Checks run on this phone.")
            }
            Section("Default car") {
                if model.display.cars.isEmpty {
                    Text("No cars yet").foregroundStyle(DPColor.text2)
                    Button("Add a car in Garage", action: model.onGarage)
                } else {
                    Picker("Default car", selection: Binding(get: { model.display.preferences.defaultVehicleId }, set: { value in
                        Task { await model.display.change { $0.defaultVehicleId = value } }
                    })) {
                        Text("No default car").tag(String?.none)
                        ForEach(model.display.cars) { car in Text(car.name).tag(Optional(car.id)) }
                    }
                    Button("Manage cars in Garage", action: model.onGarage)
                }
            }
            Section {
                Toggle("Battery saver recording", isOn: Binding(get: { model.display.preferences.batterySaverRecording }, set: { on in
                    Task { await model.display.change { $0.batterySaverRecording = on } }
                })).accessibilityIdentifier("settings.batterySaver")
                Text("Records motion at 25 Hz on every drive. Uses less battery; braking and cornering detail is a little coarser.")
                    .font(DPFont.meta).foregroundStyle(DPColor.text2)
                if model.display.preferences.batterySaverRecording {
                    Text("Drives may show Usable data").font(DPFont.meta).foregroundStyle(DPColor.text2)
                }
            } header: { Text("Battery") } footer: {
                Text("DrivePerf already drops to 25 Hz on its own when your phone is hot, low on battery or in Low Power Mode.")
            }
            Section { Button("Health Check") { model.openHealthCheck() } }
            }
            .listRowBackground(DPColor.surface)
            .listRowSeparatorTint(DPColor.separator)
        }
        .scrollContentBackground(.hidden)
        .background(DPColor.bgGrouped)
        .environment(\.defaultMinListRowHeight, 52)
        .tint(DPColor.accent)
        .navigationTitle("Recording")
        .navigationBarTitleDisplayMode(.inline)
        .task { await model.refresh(); await model.display.loadCars() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { Task { await model.refresh() } }
        }
        .sheet(isPresented: $model.isHealthPresented) {
            if let capture = model.capture { CaptureStatusSheet(capture: capture, recording: model) }
        }
        .alert("Could not save settings", isPresented: Binding(get: { model.display.saveFailed }, set: { model.display.saveFailed = $0 })) {
            Button("OK", role: .cancel) {}
        }
        .sheet(item: $model.explainer) { explainer in
            LocationAccessExplainerSheet(model: model, step: explainer)
        }
        .sheet(isPresented: $isPausePresented) {
            if let pause = model.pause { PauseAutoDetectSheet(model: pause) }
        }
    }

    private var statusColor: Color {
        if model.pause?.isPaused == true { return DPColor.text3 }
        return model.attentionReasons.isEmpty ? DPColor.live : DPColor.warning
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
