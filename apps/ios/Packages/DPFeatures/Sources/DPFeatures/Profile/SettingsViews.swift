import DPDesignSystem
import DPSharedBridge
import SwiftUI

struct SettingsHubView: View {
    @State private var isHealthPresented = false
    let display: DisplaySettingsModel
    let recording: RecordingSettingsViewModel
    var body: some View {
        List {
            Group {
            Section {
                Button { isHealthPresented = true } label: {
                    VStack(alignment: .leading) {
                        Text("Health Check").foregroundStyle(DPColor.text)
                        Text(recording.statusText).font(DPFont.meta).foregroundStyle(DPColor.text2)
                    }
                }
            }
            Section("Recording") {
                NavigationLink("Recording") { RecordingSettingsView(model: recording) }
            }
            Section("Preferences") {
                NavigationLink { UnitsAndDisplayView(model: display) } label: {
                    VStack(alignment: .leading) {
                        Text("Units and display")
                        Text(display.summary).font(DPFont.meta).foregroundStyle(DPColor.text2)
                    }
                }
            }
            }
            .listRowBackground(DPColor.surface)
            .listRowSeparatorTint(DPColor.separator)
        }
        .scrollContentBackground(.hidden)
        .background(DPColor.bgGrouped)
        .environment(\.defaultMinListRowHeight, 52)
        .tint(DPColor.accent)
        .navigationTitle("Settings").navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $isHealthPresented) {
            if let capture = recording.capture { CaptureStatusSheet(capture: capture, recording: recording) }
        }
    }
}

struct UnitsAndDisplayView: View {
    @Bindable var model: DisplaySettingsModel
    var body: some View {
        List {
            Group {
            Section {
                Picker("Units", selection: Binding(get: { model.preferences.units }, set: { value in
                    Task { await model.change { $0.units = value } }
                })) {
                    Text("Metric").tag(DisplayUnits.metric)
                    Text("Imperial").tag(DisplayUnits.imperial)
                }.pickerStyle(.segmented).accessibilityIdentifier("settings.units")
                LabeledContent("Distance", value: model.preferences.units == .metric ? "Kilometres (km)" : "Miles (mi)")
                LabeledContent("Speed", value: model.preferences.units == .metric ? "Kilometres per hour (km/h)" : "Miles per hour (mph)")
                LabeledContent("Elevation", value: model.preferences.units == .metric ? "Metres (m)" : "Feet (ft)")
                LabeledContent("G-force", value: "g, always")
            } header: { Text("Units") } footer: {
                Text("Your regional default is \(model.regionalUnits.rawValue). Changes apply everywhere at once. Exports keep a units row.")
            }
            Section("Preview") {
                VStack(alignment: .leading, spacing: DPSpace.s3) {
                    Text("Home → Work").font(DPFont.cardTitle)
                    Text("\(model.distance(37_900)) · 30 min · ↗ \(model.metric("elevation").format(90)) \(model.metric("elevation").unit ?? "")")
                        .font(DPFont.sectionTitle).monospacedDigit()
                }.accessibilityElement(children: .combine)
            }
            Section {
                ForEach(AppAppearance.allCases, id: \.self) { appearance in
                    Button {
                        Task { await model.change { $0.appearance = appearance } }
                    } label: {
                        HStack {
                            VStack(alignment: .leading) {
                                Text(appearance.rawValue.capitalized).foregroundStyle(DPColor.text)
                                if appearance == .system { Text("Follows your phone’s setting").font(DPFont.meta).foregroundStyle(DPColor.text2) }
                                if appearance == .dark { Text("Night Drive. DrivePerf is designed dark-first").font(DPFont.meta).foregroundStyle(DPColor.text2) }
                            }
                            Spacer()
                            if model.preferences.appearance == appearance { Image(systemName: "checkmark") }
                        }.frame(minHeight: 44)
                    }
                    .accessibilityAddTraits(model.preferences.appearance == appearance ? .isSelected : [])
                    .accessibilityIdentifier("settings.appearance.\(appearance.rawValue)")
                }
            } header: { Text("Appearance") } footer: {
                Text("Share cards always use the night style, whatever you pick here.")
            }
            Section("Text size") { Text("Follows your system text size. Change it in Settings → Accessibility → Display & Text Size.") }
            }
            .listRowBackground(DPColor.surface)
            .listRowSeparatorTint(DPColor.separator)
        }
        .scrollContentBackground(.hidden)
        .background(DPColor.bgGrouped)
        .environment(\.defaultMinListRowHeight, 52)
        .disabled(model.isSaving)
        .tint(DPColor.accent)
        .navigationTitle("Units and display").navigationBarTitleDisplayMode(.inline)
        .sensoryFeedback(.selection, trigger: model.preferences)
        .alert("Could not save settings", isPresented: $model.saveFailed) { Button("OK", role: .cancel) {} }
    }
}
