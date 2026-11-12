import DPDesignSystem
import DPSharedBridge
import SwiftUI

/// S-DRV-01 In-drive locked screen (DRIVE-115): the only screen while a driver trip is active. Elapsed time,
/// distance and two huge exits; no score, speed, map or tab bar. Black on both themes.
struct InDriveView: View {
    @Bindable var capture: CaptureViewModel

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            content(now: context.date)
        }
        .background(DPColor.driveBg.ignoresSafeArea())
        .environment(\.colorScheme, .dark)
        .interactiveDismissDisabled()
        .onAppear { capture.setInDriveScreenVisible(true) }
        .onDisappear { capture.setInDriveScreenVisible(false) }
        .sheet(isPresented: $capture.isEndSheetPresented) {
            EndTripSheet(capture: capture)
        }
        .sheet(isPresented: $capture.isPassengerSheetPresented) {
            PassengerSheet(capture: capture)
        }
    }

    private func content(now: Date) -> some View {
        let status = capture.status
        let startedAt = capture.tripStartedAt ?? now
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: DPSpace.s3) {
                Circle().fill(DPColor.live).frame(width: 12, height: 12)
                Text("Recording").font(.system(size: 24, weight: .semibold)).foregroundStyle(.white)
                Spacer()
                // Garage (DRIVE-173) fills the car name; until then there is no chip.
                if let carName = capture.carName { CarChip(name: carName) }
            }
            .padding(.top, 18)
            .accessibilityElement(children: .combine)
            Text(sourceLine(isManual: status.isManual, startedAt: startedAt))
                .font(.subheadline)
                .foregroundStyle(Color(rgba: 0xA3AA_B6FF))
                .padding(.top, DPSpace.s2)
            Spacer()
            VStack(alignment: .leading, spacing: DPSpace.s5) {
                Metric(label: "ELAPSED") {
                    Text(DriveFormat.elapsed(now.timeIntervalSince(startedAt)))
                        .dpNumeral(.drive)
                        .minimumScaleFactor(0.5)
                        .lineLimit(1)
                }
                // The recorder status has no GNSS-fix flag, so the "Finding GPS…" state is not shown (reported to
                // DRIVE-62 as a possible additive field).
                Metric(label: "DISTANCE") {
                    HStack(alignment: .firstTextBaseline, spacing: DPSpace.s1) {
                        Text(DriveFormat.kilometres(status.distanceM)).dpNumeral(.xl)
                        Text("km").dpNumeral(.m).foregroundStyle(Color(rgba: 0xA3AA_B6FF))
                    }
                }
            }
            .foregroundStyle(.white)
            Spacer()
            Button {
                capture.requestStop()
            } label: {
                Label { Text("Stop") } icon: { DPIcon.stop.image.resizable().frame(width: 26, height: 26) }
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: DPLayout.driveButton)
                    .background(
                        Color(rgba: 0x262E_3BFF), in: RoundedRectangle(cornerRadius: DPRadius.driveButton, style: .continuous))
                    .contentShape(RoundedRectangle(cornerRadius: DPRadius.driveButton))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Stop the drive")
            PassengerButton { capture.requestPassenger() }
                .padding(.top, DPSpace.s3)
                .padding(.bottom, DPSpace.s4)
        }
        .padding(.horizontal, DPSpace.s6)
    }

    private func sourceLine(isManual: Bool, startedAt: Date) -> String {
        let time = DriveFormat.clock(startedAt)
        return isManual ? "Started by you · \(time)" : "Started automatically · \(time)"
    }
}

private struct Metric<Value: View>: View {
    let label: String
    @ViewBuilder let value: () -> Value

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s1) {
            Text(label).font(.footnote.weight(.semibold)).kerning(0.6).foregroundStyle(Color(rgba: 0xA3AA_B6FF))
            value()
        }
        .accessibilityElement(children: .combine)
    }
}

/// S-DRV-02 End trip sheet: a deliberate second step so a mis-tap never ends a drive.
struct EndTripSheet: View {
    let capture: CaptureViewModel

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let startedAt = capture.tripStartedAt
            let short = capture.isShortTrip(now: context.date)
            VStack(alignment: .leading, spacing: DPSpace.s4) {
                Text("End this drive?").font(DPFont.sheetTitle).foregroundStyle(DPColor.text)
                if let startedAt {
                    Text(
                        "\(DriveFormat.elapsed(context.date.timeIntervalSince(startedAt))) · "
                            + "\(DriveFormat.kilometres(capture.status.distanceM)) km"
                    )
                    .font(.system(size: 22, weight: .semibold).width(.expanded))
                    .monospacedDigit()
                    .foregroundStyle(DPColor.text2)
                }
                if short {
                    VStack(alignment: .leading, spacing: DPSpace.s1) {
                        Text("This drive is very short").font(DPFont.body.weight(.semibold))
                        Text("Under 500 m or 2 minutes. It’s kept with a “Short trip” label, without a score.")
                            .font(DPFont.bodySecondary).foregroundStyle(DPColor.text2)
                    }
                    .padding(DPSpace.s4)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(DPColor.surface2, in: RoundedRectangle(cornerRadius: DPRadius.lg))
                } else {
                    Text("We’ll decode it now. Your Story will be ready when you’ve parked.")
                        .font(DPFont.body).foregroundStyle(DPColor.text2)
                }
                DPPrimaryButton(short ? "End anyway" : "End drive", icon: .stop, height: DPLayout.touchDrive) {
                    Task { await capture.endDrive() }
                }
                .sensoryFeedback(.impact(weight: .medium), trigger: capture.isStopping)
                Button {
                    capture.keepRecording()
                } label: {
                    Text("Keep recording").font(DPFont.button).foregroundStyle(DPColor.text)
                        .frame(maxWidth: .infinity, minHeight: DPLayout.touchDrive)
                }
                .buttonStyle(.glass)
                .buttonBorderShape(.capsule)
            }
            .padding(DPSpace.s6)
        }
        .presentationDetents([.height(380)])
    }
}

/// The small outlined "I'm a passenger" control under Stop (S-DRV-01, S-DRV-05): 96 tall, quieter than Stop (D14).
struct PassengerButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Label { Text("I’m a passenger") } icon: { DPIcon.passenger.image.resizable().frame(width: 26, height: 26) }
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity, minHeight: DPLayout.driveButton)
                .overlay(
                    RoundedRectangle(cornerRadius: DPRadius.driveButton, style: .continuous)
                        .stroke(Color(rgba: 0x3A43_51FF), lineWidth: 1.5)
                )
                .contentShape(RoundedRectangle(cornerRadius: DPRadius.driveButton))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("I’m a passenger")
    }
}

/// The car's chip: its name on a faint capsule. Shown only once Garage can name the car.
struct CarChip: View {
    let name: String

    var body: some View {
        Text(name)
            .font(.system(size: 17, weight: .semibold))
            .foregroundStyle(Color(rgba: 0xF2F4_F7FF))
            .padding(.horizontal, DPSpace.s3)
            .frame(minHeight: 40)
            .background(Color(rgba: 0xFFFF_FF14), in: Capsule())
    }
}

/// S-DRV-05 "Your Story will be ready when you've parked": what anyone sees when content is opened while a driver trip
/// is active, or until the phone has been calm for a minute after one (F-CAP-07.2, .4). Nothing here moves.
struct HeldContentView: View {
    @Bindable var capture: CaptureViewModel

    var body: some View {
        let noun = capture.heldContent?.noun ?? "Story"
        VStack(spacing: DPSpace.s5) {
            Spacer()
            LoopMark(size: 96)
            VStack(spacing: DPSpace.s3) {
                Text("Your \(noun) will be ready when you’ve parked.")
                    .font(DPFont.storyHeadline).foregroundStyle(Color(rgba: 0xF2F4_F7FF))
                    .multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader)
                Text("It opens by itself after a minute stopped. Nothing to tap until then.")
                    .font(DPFont.body).foregroundStyle(Color(rgba: 0xA3AA_B6FF))
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: 300)
            Spacer()
            // Only while someone is still at the wheel: after the trip there is nothing to mark.
            if capture.status.isDriving {
                PassengerButton { capture.requestPassenger() }
                    .padding(.bottom, DPSpace.s4)
            }
        }
        .padding(.horizontal, DPSpace.s6)
        .background(DPColor.driveBg.ignoresSafeArea())
        .environment(\.colorScheme, .dark)
        .interactiveDismissDisabled()
        .task {
            while !Task.isCancelled {
                capture.tick(now: Date())
                try? await Task.sleep(for: .seconds(1))
            }
        }
        .sheet(isPresented: $capture.isPassengerSheetPresented) {
            PassengerSheet(capture: capture)
        }
    }
}

/// S-DRV-03 "I'm a passenger" sheet: mark the trip as not driven, or throw it away. Targets are at least 64 pt.
struct PassengerSheet: View {
    let capture: CaptureViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s4) {
            switch capture.passengerStep {
            case .choose: choose
            case .confirmDiscard: confirmDiscard
            }
        }
        .padding(DPSpace.s6)
        .presentationDetents([.height(capture.passengerStep == .choose ? 430 : 360)])
        .animation(.easeInOut(duration: 0.18), value: capture.passengerStep)
    }

    @ViewBuilder private var choose: some View {
        Text("Not driving?").font(DPFont.sheetTitle).foregroundStyle(DPColor.text)
            .accessibilityAddTraits(.isHeader)
        Text("Only if someone else is at the wheel.").font(DPFont.body).foregroundStyle(DPColor.text2)
        OptionRow(
            icon: DPIcon.passenger.image, title: "Keep the trip as a passenger trip",
            detail: "Saved without a score. DrivePerf unlocks."
        ) { Task { await capture.keepAsPassenger() } }
        .sensoryFeedback(.impact(weight: .medium), trigger: capture.status.tripId == nil)
        OptionRow(
            icon: Image(systemName: "trash"), title: "Don’t save this trip",
            detail: "Recording stops and the data is deleted.", tint: DPColor.critical
        ) { capture.askDiscard() }
        Button {
            capture.cancelPassenger()
        } label: {
            Text("Cancel").font(DPFont.button).foregroundStyle(DPColor.text)
                .frame(maxWidth: .infinity, minHeight: DPLayout.touchDrive)
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.capsule)
    }

    @ViewBuilder private var confirmDiscard: some View {
        Text("Delete this trip?").font(DPFont.sheetTitle).foregroundStyle(DPColor.text)
            .accessibilityAddTraits(.isHeader)
        Text("Recording stops and everything recorded so far is deleted within a minute. This can’t be undone.")
            .font(DPFont.body).foregroundStyle(DPColor.text2)
        Button(role: .destructive) {
            Task { await capture.discardTrip() }
        } label: {
            Label { Text("Delete this trip") } icon: { Image(systemName: "trash") }
                .font(DPFont.button)
                .frame(maxWidth: .infinity, minHeight: DPLayout.touchDrive)
        }
        .buttonStyle(.glassProminent)
        .buttonBorderShape(.capsule)
        .tint(DPColor.critical)
        // The delete button is not the first thing VoiceOver lands on.
        .accessibilitySortPriority(-1)
        Button {
            capture.cancelPassenger()
        } label: {
            Text("Keep recording").font(DPFont.button).foregroundStyle(DPColor.text)
                .frame(maxWidth: .infinity, minHeight: DPLayout.touchDrive)
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.capsule)
    }
}

/// A 76-tall option row of S-DRV-03: glyph in a disc, title, detail and a chevron.
private struct OptionRow: View {
    let icon: Image
    let title: String
    let detail: String
    var tint: Color = DPColor.accent
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: DPSpace.s3) {
                icon.resizable().scaledToFit().frame(width: 22, height: 22).foregroundStyle(tint)
                    .frame(width: 44, height: 44).background(tint.opacity(0.16), in: Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(DPFont.body.weight(.semibold)).foregroundStyle(DPColor.text)
                    Text(detail).font(DPFont.bodySecondary).foregroundStyle(DPColor.text2)
                }
                Spacer(minLength: 0)
                DPIcon.chevronRight.image.resizable().frame(width: 14, height: 14).foregroundStyle(DPColor.text3)
            }
            .multilineTextAlignment(.leading)
            .padding(.horizontal, DPSpace.s4)
            .frame(maxWidth: .infinity, minHeight: 76, alignment: .leading)
            .background(DPColor.surface2, in: RoundedRectangle(cornerRadius: DPRadius.lg, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: DPRadius.lg))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(title). \(detail)")
    }
}

/// S-DRV-04 "Decoding your drive": between Stop and the saved trip. The decoding steps and the Story arrive with the
/// pipeline (DRIVE-62) and DRIVE-182; today the wait is the core finalising the trip, a moment.
struct DecodingDriveView: View {
    var carName: String?
    let onBack: () -> Void

    var body: some View {
        VStack(spacing: DPSpace.s5) {
            Spacer()
            LoopMark(size: 132)
                .accessibilityLabel("Decoding your drive")
            VStack(spacing: DPSpace.s2) {
                Text("Decoding your drive…").font(DPFont.storyHeadline).foregroundStyle(Color(rgba: 0xF2F4_F7FF))
                    .accessibilityAddTraits(.isHeader)
                if let carName { CarChip(name: carName) }
                Text("Usually under 10 seconds. You can leave; the drive is safe on this phone.")
                    .font(DPFont.meta).foregroundStyle(Color(rgba: 0xA3AA_B6FF)).multilineTextAlignment(.center)
            }
            Spacer()
            DPSecondaryButton("Back to Drives", action: onBack)
                .padding(.bottom, DPSpace.s4)
        }
        .padding(.horizontal, DPSpace.s8)
        .background(
            LinearGradient(colors: [.black, Color(rgba: 0x0A14_20FF)], startPoint: .center, endPoint: .bottom)
                .ignoresSafeArea())
        .environment(\.colorScheme, .dark)
        .interactiveDismissDisabled()
    }
}

/// S-DRV-04 done state, reduced to the shell: the trip is saved and listed. The Story (DRIVE-182) and the pipeline
/// (DRIVE-62) arrive later, so this is a "Drive saved" placeholder with no score and no "Watch your Story" yet. The
/// real "Decoding your drive" steps wait for the pipeline; the core finalises in the moment the Stop returns.
struct DriveFinishedView: View {
    let outcome: CaptureViewModel.TripOutcome
    var carName: String?
    let onDone: () -> Void

    var body: some View {
        VStack(spacing: DPSpace.s5) {
            Spacer()
            LoopMark(size: 132)
            VStack(spacing: DPSpace.s2) {
                Text(title).font(DPFont.storyHeadline).foregroundStyle(.white)
                if let facts { Text(facts).font(DPFont.body).foregroundStyle(Color(rgba: 0xA3AA_B6FF)) }
                if let carName { CarChip(name: carName) }
                Text(note).font(DPFont.meta).foregroundStyle(Color(rgba: 0xA3AA_B6FF)).multilineTextAlignment(.center)
            }
            Spacer()
            DPPrimaryButton("Back to Drives", height: 52, action: onDone)
                .padding(.bottom, DPSpace.s4)
        }
        .padding(.horizontal, DPSpace.s8)
        .background(
            LinearGradient(colors: [.black, Color(rgba: 0x0A14_20FF)], startPoint: .center, endPoint: .bottom)
                .ignoresSafeArea())
        .environment(\.colorScheme, .dark)
    }

    private var summary: TripSummary? {
        switch outcome {
        case .saved(let summary, _): summary
        case .passenger(let summary): summary
        case .discarded: nil
        }
    }

    private var title: String {
        switch outcome {
        case .saved(let summary, _): summary == nil ? "Not saved" : summary?.status == .short ? "Short trip saved" : "Drive saved"
        case .passenger(let summary): summary == nil ? "Passenger trip" : "Passenger trip saved"
        case .discarded: "Trip not saved"
        }
    }

    private var facts: String? {
        guard let summary else { return nil }
        return "\(DriveFormat.kilometres(summary.distanceM)) km · \(max(Int((summary.duration / 60).rounded()), 1)) min"
    }

    private var note: String {
        switch outcome {
        case .saved(nil, _): "It was too short to count as a drive."
        case .saved(_, let autoEnded):
            (autoEnded ? "Ended automatically after 15 minutes stationary. " : "")
                + "Its Story arrives with the scoring update. The recording is safe on this phone."
        case .passenger(nil):
            "Marked as a passenger trip, so it isn’t scored. A very short trip isn’t kept at all."
        case .passenger: "Saved without a score. It won’t count toward your baseline, records or recaps."
        case .discarded: "Nothing was kept."
        }
    }
}
