import DPDesignSystem
import SwiftUI

/// Header of the auto-height permission sheets: glyph disc 36 + sheet title.
private struct SheetHeader: View {
    let icon: DPIcon
    let tone: GlyphDisc.Tone
    let title: String

    var body: some View {
        HStack(spacing: DPSpace.s3) {
            GlyphDisc(icon, tone: tone, size: 36)
            Text(title).font(DPFont.sheetTitle).foregroundStyle(DPColor.text).accessibilityAddTraits(.isHeader)
            Spacer()
        }
    }
}

/// S-ONB-11 Precise location sheet.
struct PreciseLocationSheet: View {
    let model: OnboardingViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s4) {
            if model.preciseRefused {
                SheetHeader(icon: .gps, tone: .accent, title: "Route only, for now")
                Text(
                    "Drives record as “route only, no event placement”. Braking, acceleration and cornering are "
                        + "still counted, but not placed on the road."
                )
                .font(DPFont.body).foregroundStyle(DPColor.text)
                .padding(DPSpace.s4)
                .dpCard(radius: DPRadius.lg)
                Text("You can turn on Precise Location in Settings › Privacy › Location Services › DrivePerf.")
                    .font(DPFont.meta).foregroundStyle(DPColor.text3)
                DPPrimaryButton("Continue") { model.finishPreciseSheet() }
            } else {
                SheetHeader(icon: .gps, tone: .accent, title: "Turn on precise location?")
                Text(
                    "Precise location places braking and cornering on the road. Approximate location only knows the "
                        + "area you’re in."
                )
                .font(DPFont.body).foregroundStyle(DPColor.text2)
                Text("Used during drives only, and processed on your phone.")
                    .font(DPFont.meta).foregroundStyle(DPColor.text3)
                DPPrimaryButton("Turn on Precise Location") { Task { await model.requestPrecise() } }
                    .disabled(model.isRequesting)
                DPSecondaryButton("Keep approximate") { model.keepApproximate() }
            }
        }
        .padding(DPSpace.s6)
        .presentationDetents([.medium])
        .presentationBackground(.regularMaterial)
    }
}

/// S-ONB-16 default: location denied.
struct LocationDeniedSheet: View {
    let model: OnboardingViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s4) {
            SheetHeader(icon: .gps, tone: .warning, title: "DrivePerf needs location to record")
            Text(
                "Without it, DrivePerf can’t draw a route or measure a drive, so nothing records, not even manual "
                    + "trips."
            )
            .font(DPFont.body).foregroundStyle(DPColor.text2)
            DPCardStack {
                ChecklistRow(.check, title: "While Using is enough for manual trips")
                ChecklistRow(.check, title: "Used only during drives")
                ChecklistRow(.close, tone: .neutral, title: "Never sold")
            }
            DPPrimaryButton("Open Settings", icon: .gear) { model.openSettings() }
            DPSecondaryButton("Not now") { model.continueWithoutLocation() }
            Text("You can look around and set things up. Recording starts once location is on.")
                .font(DPFont.meta).foregroundStyle(DPColor.text3)
        }
        .padding(DPSpace.s6)
        .presentationDetents([.large])
        .interactiveDismissDisabled()
    }
}

/// S-ONB-16 alwaysDeclined: manual trips it is. The Always prompt is never shown again in-app (F-ONB-04.3).
struct AlwaysDeclinedSheet: View {
    let model: OnboardingViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s4) {
            SheetHeader(icon: .record, tone: .accent, title: "Manual trips it is")
            Text(
                "Location is set to “While Using”, so DrivePerf won’t start drives on its own. Everything else works."
            )
            .font(DPFont.body).foregroundStyle(DPColor.text2)
            DPCardStack {
                ChecklistRow(.check, title: "Start drives yourself", detail: "From the app or Control Center.")
                ChecklistRow(.check, title: "Stories, Replay and scores")
                ChecklistRow(.close, tone: .neutral, title: "No automatic trips", detail: "We won’t ask again.")
            }
            DPPrimaryButton("Continue in manual mode") { model.continueInManualMode() }
            DPSecondaryButton("Open Settings") { model.openSettings() }
        }
        .padding(DPSpace.s6)
        .presentationDetents([.large])
        .interactiveDismissDisabled()
    }
}
