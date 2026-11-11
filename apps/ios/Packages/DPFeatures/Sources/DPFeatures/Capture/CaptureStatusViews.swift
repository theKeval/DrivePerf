import DPDesignSystem
import DPSharedBridge
import SwiftUI

/// The `tabViewBottomAccessory` capsule (S-HOME-01, S-NAV-01): capture status and Start on every tab. Folds inline
/// next to the minimised tab bar on scroll.
struct CaptureStatusAccessory: View {
    let capture: CaptureViewModel
    let onOpenStatus: () -> Void
    @Environment(\.tabViewBottomAccessoryPlacement) private var placement

    var body: some View {
        HStack(spacing: DPSpace.s3) {
            Button(action: onOpenStatus) {
                HStack(spacing: DPSpace.s2) {
                    Circle().fill(dotColor).frame(width: 10, height: 10)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(title).font(DPFont.bodySecondary.weight(.semibold)).foregroundStyle(DPColor.text)
                        if placement != .inline, let subtitle {
                            Text(subtitle).font(DPFont.caption).foregroundStyle(DPColor.text2)
                        }
                    }
                    .lineLimit(1)
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityHint("Shows capture status")
            if case .recording = capture.accessory {
                EmptyView()
            } else {
                Button {
                    Task { await capture.start() }
                } label: {
                    Label { Text("Start") } icon: { DPIcon.record.image.resizable().frame(width: 16, height: 16) }
                        .font(DPFont.bodySecondary.weight(.semibold))
                        .foregroundStyle(DPColor.onAccent)
                        .padding(.horizontal, DPSpace.s2)
                }
                .buttonStyle(.glassProminent)
                .tint(DPColor.accent)
                .accessibilityLabel("Start a drive")
            }
        }
        .padding(.leading, DPSpace.s4)
        .padding(.trailing, DPSpace.s1)
    }

    private var title: String {
        switch capture.accessory {
        case .ready: "Auto-detect on · ready"
        case .manual: "Manual mode"
        case .attention: "Auto-detect needs attention"
        case .recording(let startedAt, let distanceM):
            "Recording · \(Int(Date().timeIntervalSince(startedAt) / 60)) min · \(DriveFormat.kilometres(distanceM)) km"
        }
    }

    private var subtitle: String? {
        switch capture.accessory {
        case .ready: "Starts when you drive off"
        case .manual: "Auto-detect is off"
        case .attention(let reason):
            switch reason {
            case .locationWhileUsing: "Location is set to While Using"
            case .locationDenied: "Location is off"
            }
        case .recording: nil
        }
    }

    private var dotColor: Color {
        switch capture.accessory {
        case .ready, .recording: DPColor.live
        case .manual: DPColor.text3
        case .attention: DPColor.warning
        }
    }
}

/// S-HOME-02 Capture status sheet, reduced to the shell: the summary, the promise and the core checks.
struct CaptureStatusSheet: View {
    let capture: CaptureViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: DPSpace.s4) {
                    HStack(alignment: .top, spacing: DPSpace.s3) {
                        GlyphDisc(isAttention ? .info : .check, tone: isAttention ? .warning : .positive, size: 36)
                        VStack(alignment: .leading, spacing: DPSpace.s1) {
                            Text(summary).font(DPFont.cardTitle).foregroundStyle(DPColor.text)
                            Text(promise).font(DPFont.bodySecondary).foregroundStyle(DPColor.text2)
                        }
                    }
                    if isAttention {
                        DPPrimaryButton("Open Settings", icon: .gear) { capture.openSettings() }
                    }
                    Text("Checks run on this phone. Nothing is sent anywhere.")
                        .font(DPFont.meta).foregroundStyle(DPColor.text3)
                }
                .padding(DPLayout.screenMargin)
            }
            .navigationTitle("Capture status")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) { Button(role: .close) { dismiss() } }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private var isAttention: Bool {
        if case .attention = capture.accessory { return true }
        return false
    }

    private var summary: String {
        switch capture.accessory {
        case .ready, .recording: "Automatic trips are ready."
        case .manual: "Manual trips."
        case .attention: "Auto-detect needs attention."
        }
    }

    private var promise: String {
        switch capture.accessory {
        case .ready, .recording:
            "Auto-detects most drives. The first few hundred metres may be estimated. Force-quitting pauses "
                + "auto-detect."
        case .manual: "Tap Start when you set off and Stop when you park."
        case .attention:
            "Location access changed to \"While Using\". Automatic trips are paused. Two taps to fix it."
        }
    }
}

/// Start without location (S-ONB-16 from Home) or before the first unlock.
struct StartProblemSheet: View {
    let problem: CaptureViewModel.StartProblem
    let capture: CaptureViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s4) {
            HStack(spacing: DPSpace.s3) {
                GlyphDisc(.gps, tone: .warning, size: 36)
                Text(problem == .locationDenied ? "DrivePerf needs location to record" : "Unlock your phone first")
                    .font(DPFont.sheetTitle).foregroundStyle(DPColor.text)
            }
            Text(
                problem == .locationDenied
                    ? "Without it, DrivePerf can’t draw a route or measure a drive, so nothing records, not even "
                        + "manual trips."
                    : "Your phone restarted and hasn’t been unlocked yet, so DrivePerf can’t save a drive."
            )
            .font(DPFont.body).foregroundStyle(DPColor.text2)
            if problem == .locationDenied {
                DPPrimaryButton("Open Settings", icon: .gear) { capture.openSettings() }
            }
            DPSecondaryButton("Not now") { dismiss() }
        }
        .padding(DPSpace.s6)
        .presentationDetents([.medium])
    }
}
