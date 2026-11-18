import DPDesignSystem
import DPSharedBridge
import SwiftUI

/// S-HOME-03 Pause auto-detect (F-CAP-15): a glass sheet that fits its content. The choices (inset list, checkmark-style
/// radios), then the confirmation with Done and Undo; opened while paused, Resume auto-detect and Keep paused. The
/// presenting view calls `model.prepare()` before it shows the sheet.
struct PauseAutoDetectSheet: View {
    let model: PauseAutoDetectViewModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var confirmHaptic = 0

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s4) {
            switch model.step {
            case .pick: picker
            case .confirmed(let until): confirmation(until)
            case .resume: resume
            }
        }
        .padding(.horizontal, DPSpace.s6)
        .padding(.top, DPSpace.s6)
        .padding(.bottom, DPSpace.s4)
        .animation(reduceMotion ? nil : .spring(duration: 0.35), value: model.step)
        .sensoryFeedback(.success, trigger: confirmHaptic)
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    private func header(_ title: String, _ subtitle: String?) -> some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: DPSpace.s1) {
                Text(title).font(DPFont.sheetTitle).foregroundStyle(DPColor.text).accessibilityAddTraits(.isHeader)
                if let subtitle { Text(subtitle).font(DPFont.bodySecondary).foregroundStyle(DPColor.text2) }
            }
            Spacer(minLength: DPSpace.s2)
            Button(role: .close) { dismiss() }
        }
    }

    @ViewBuilder private var picker: some View {
        header("Pause auto-detect", "No drives start on their own while paused")
        VStack(spacing: 0) {
            ForEach(Array(AutoDetectPauseLength.allCases.enumerated()), id: \.element) { index, length in
                if index > 0 { Divider().padding(.leading, 52) }
                OptionRow(
                    title: PauseRules.title(length), detail: model.detail(length), selected: model.choice == length,
                    position: index + 1, count: AutoDetectPauseLength.allCases.count
                ) { model.choice = length }
            }
        }
        .dpCard()
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Pause auto-detect")
        .sensoryFeedback(.selection, trigger: model.choice)
        Text("You can still record a drive with Start. Pausing doesn’t delete anything.")
            .font(DPFont.meta).foregroundStyle(DPColor.text2)
        DPPrimaryButton(PauseRules.button(model.choice), icon: .pause) {
            model.pause()
            confirmHaptic += 1
        }
    }

    @ViewBuilder private func confirmation(_ until: Date?) -> some View {
        header("Auto-detect paused", nil)
        HStack(alignment: .top, spacing: DPSpace.s4) {
            GlyphDisc(.pause, tone: .accent, size: 56)
                .transition(reduceMotion ? .opacity : .scale(scale: 0.86).combined(with: .opacity))
            VStack(alignment: .leading, spacing: DPSpace.s1) {
                Text(model.untilLine(until)).font(DPFont.cardTitle).foregroundStyle(DPColor.text)
                Text("No drives will start on their own. You can still tap Start, and we’ll let you know when it’s back on.")
                    .font(DPFont.bodySecondary).foregroundStyle(DPColor.text2)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.updatesFrequently)
        DPPrimaryButton("Done") { dismiss() }
        DPSecondaryButton("Undo") { model.undo() }
    }

    @ViewBuilder private var resume: some View {
        header("Auto-detect is paused", model.pausedUntilLine)
        Text("While it’s paused, drives only record when you tap Start.")
            .font(DPFont.body).foregroundStyle(DPColor.text2)
        DPPrimaryButton("Resume auto-detect", icon: .play) {
            model.resume()
            confirmHaptic += 1
            AccessibilityNotification.Announcement("Auto-detect resumed").post()
            dismiss()
        }
        Button { dismiss() } label: {
            Text("Keep paused").font(DPFont.button).foregroundStyle(DPColor.text)
                .frame(maxWidth: .infinity, minHeight: DPLayout.touchMin)
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.capsule)
    }
}

/// One S-HOME-03 choice: a checkmark-style radio, the title and when it ends. Reads "For 1 hour, Until 8:30 PM, 1 of 3".
private struct OptionRow: View {
    let title: String
    let detail: String
    let selected: Bool
    let position: Int
    let count: Int
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: DPSpace.s3) {
                ZStack {
                    Circle().strokeBorder(selected ? DPColor.accent : DPColor.text3, lineWidth: 2)
                    if selected { Circle().fill(DPColor.accent).padding(5) }
                }
                .frame(width: 24, height: 24)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(DPFont.body).foregroundStyle(DPColor.text)
                    Text(detail).font(DPFont.meta).foregroundStyle(DPColor.text2)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, DPSpace.s4)
            .frame(minHeight: DPLayout.listRowMin)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(title), \(detail)")
        .accessibilityValue("\(position) of \(count)")
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}

/// S-HOME-01 autoPaused: "Auto-detect paused until 6:00 AM tomorrow" with Resume (F-CAP-15 AC1). A pause glyph and the
/// words carry the state, never the colour alone.
struct AutoDetectPausedBanner: View {
    let untilPhrase: String
    let onResume: () -> Void

    var body: some View {
        HStack(spacing: DPSpace.s3) {
            DPIcon.pause.image.resizable().frame(width: 20, height: 20).foregroundStyle(DPColor.text2)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text("Auto-detect paused \(untilPhrase)").font(DPFont.body.weight(.semibold))
                    .foregroundStyle(DPColor.text)
                Text("No drives will start on their own. You can still tap Start.")
                    .font(DPFont.bodySecondary).foregroundStyle(DPColor.text2)
            }
            Spacer(minLength: DPSpace.s2)
            Button("Resume", action: onResume)
                .font(DPFont.body.weight(.semibold))
                .foregroundStyle(DPColor.accentText)
                .frame(minHeight: DPLayout.touchMin)
        }
        .padding(DPSpace.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .dpCard(radius: DPRadius.md)
        .accessibilityElement(children: .contain)
    }
}

/// The Pause (or, while paused, Resume) auto-detect row of the capture status sheet and Recording settings: opens
/// S-HOME-03.
struct PauseAutoDetectRow: View {
    let isPaused: Bool

    var body: some View {
        HStack(spacing: DPSpace.s3) {
            (isPaused ? DPIcon.play : DPIcon.pause).image.resizable().frame(width: 18, height: 18)
                .foregroundStyle(DPColor.text2)
            VStack(alignment: .leading, spacing: 0) {
                Text(isPaused ? "Resume auto-detect" : "Pause auto-detect").font(DPFont.body).foregroundStyle(DPColor.text)
                Text(isPaused ? "Turn automatic trips back on now" : "Stop automatic trips for a while")
                    .font(DPFont.meta).foregroundStyle(DPColor.text2)
            }
            Spacer()
            DPIcon.chevronRight.image.resizable().scaledToFit().frame(width: 12, height: 12)
                .foregroundStyle(DPColor.text3)
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }
}
