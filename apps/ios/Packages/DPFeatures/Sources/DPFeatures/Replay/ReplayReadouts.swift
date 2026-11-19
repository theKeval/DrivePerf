import DPDesignSystem
import DPSharedBridge
import SwiftUI

/// Speed · Elapsed · Distance (S-RPL-01, F-RPL-02 AC1): DP Numerals 24 with units at half weight. Speed is neutral
/// text, never coloured; in a GPS gap it reads "—" (EC-GPS). Not a live region: VoiceOver reads it on focus.
struct ReplayReadouts: View {
    let model: ReplayViewModel

    var body: some View {
        HStack(spacing: 0) {
            readout(model.readoutSpeed, unit: model.overlay?.speedUnit ?? "", label: "Speed")
            Divider().frame(height: 32)
            readout(ReplayViewModel.clock(model.tS), unit: "", label: "Elapsed")
            if let distance = model.readoutDistance {
                Divider().frame(height: 32)
                readout(distance, unit: model.overlay?.distanceUnit ?? "", label: "Distance")
            }
        }
        .frame(minHeight: 56)
        .glassEffect(.regular, in: RoundedRectangle(cornerRadius: DPRadius.lg, style: .continuous))
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("replay.readouts")
    }

    private func readout(_ value: String, unit: String, label: String) -> some View {
        VStack(spacing: 0) {
            HStack(alignment: .firstTextBaseline, spacing: 2) {
                Text(value).dpNumeral(.m).foregroundStyle(DPColor.text)
                if !unit.isEmpty { Text(unit).font(DPFont.caption).foregroundStyle(DPColor.text2) }
            }
            Text(label).font(DPFont.caption).foregroundStyle(DPColor.text2)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, DPSpace.s1_5)
    }
}

/// A short confirmation from the sheets, for 3 s.
struct ReplayToast: View {
    let model: ReplayViewModel

    var body: some View {
        if let text = model.toast {
            Text(text).font(DPFont.bodySecondary).foregroundStyle(DPColor.text)
                .padding(.horizontal, DPSpace.s4).padding(.vertical, DPSpace.s3)
                .glassEffect(.regular, in: Capsule())
                .padding(.bottom, 200)
                .transition(.opacity)
                .accessibilityAddTraits(.isStaticText)
                .task(id: text) {
                    AccessibilityNotification.Announcement(text).post()
                    try? await Task.sleep(for: .seconds(3))
                    model.toastShown()
                }
        }
    }
}
