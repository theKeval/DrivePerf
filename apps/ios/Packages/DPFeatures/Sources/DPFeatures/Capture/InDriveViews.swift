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
        .sheet(isPresented: $capture.isEndSheetPresented) {
            EndTripSheet(capture: capture)
        }
    }

    private func content(now: Date) -> some View {
        let live = capture.snapshot.live
        let startedAt = live?.startedAt ?? now
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: DPSpace.s3) {
                Circle().fill(DPColor.live).frame(width: 12, height: 12)
                Text("Recording").font(.system(size: 24, weight: .semibold)).foregroundStyle(.white)
                Spacer()
            }
            .padding(.top, 18)
            .accessibilityElement(children: .combine)
            Text(sourceLine(live: live, startedAt: startedAt))
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
                Metric(label: "DISTANCE") {
                    if live?.hasFix == true {
                        HStack(alignment: .firstTextBaseline, spacing: DPSpace.s1) {
                            Text(DriveFormat.kilometres(live?.distanceM ?? 0)).dpNumeral(.xl)
                            Text("km").dpNumeral(.m).foregroundStyle(Color(rgba: 0xA3AA_B6FF))
                        }
                    } else {
                        Text("—").dpNumeral(.xl)
                    }
                }
            }
            .foregroundStyle(.white)
            if live?.hasFix != true {
                StatusCard(
                    icon: .gps, title: "Finding GPS…",
                    detail: "Motion is already recording. Distance starts with the first fix.")
                    .padding(.top, DPSpace.s6)
            }
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
            .padding(.bottom, DPSpace.s4)
        }
        .padding(.horizontal, DPSpace.s6)
    }

    private func sourceLine(live: LiveTrip?, startedAt: Date) -> String {
        let time = DriveFormat.clock(startedAt)
        return live?.source == .automatic ? "Started automatically · \(time)" : "Started by you · \(time)"
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

private struct StatusCard: View {
    let icon: DPIcon
    let title: String
    let detail: String

    var body: some View {
        HStack(alignment: .top, spacing: DPSpace.s3) {
            icon.image.resizable().frame(width: 22, height: 22).foregroundStyle(DPColor.accent)
            VStack(alignment: .leading, spacing: DPSpace.s0_5) {
                Text(title).font(DPFont.body.weight(.semibold)).foregroundStyle(.white)
                Text(detail).font(DPFont.bodySecondary).foregroundStyle(Color(rgba: 0xA3AA_B6FF))
            }
        }
        .padding(DPSpace.s4)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white.opacity(0.06), in: RoundedRectangle(cornerRadius: DPRadius.lg, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

/// S-DRV-02 End trip sheet: a deliberate second step so a mis-tap never ends a drive.
struct EndTripSheet: View {
    let capture: CaptureViewModel

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let live = capture.snapshot.live
            let short = capture.isShortTrip(now: context.date)
            VStack(alignment: .leading, spacing: DPSpace.s4) {
                Text("End this drive?").font(DPFont.sheetTitle).foregroundStyle(DPColor.text)
                if let live {
                    Text(
                        "\(DriveFormat.elapsed(context.date.timeIntervalSince(live.startedAt))) · "
                            + "\(DriveFormat.kilometres(live.distanceM)) km"
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

/// S-DRV-04 done state, reduced to the shell: the trip is saved and listed. The Story (DRIVE-182) and the pipeline
/// (DRIVE-62) arrive later, so there is no score and no "Watch your Story" yet.
struct DriveFinishedView: View {
    let summary: TripSummary?
    let onDone: () -> Void

    var body: some View {
        VStack(spacing: DPSpace.s5) {
            Spacer()
            LoopMark(size: 132)
            if let summary {
                VStack(spacing: DPSpace.s2) {
                    Text(summary.status == .short ? "Short trip saved" : "Drive saved")
                        .font(DPFont.storyHeadline).foregroundStyle(.white)
                    Text(
                        "\(DriveFormat.kilometres(summary.distanceM)) km · "
                            + "\(max(Int((summary.duration / 60).rounded()), 1)) min"
                    )
                    .font(DPFont.body).foregroundStyle(Color(rgba: 0xA3AA_B6FF))
                    Text("Its Story arrives with the scoring update. The recording is safe on this phone.")
                        .font(DPFont.meta).foregroundStyle(Color(rgba: 0xA3AA_B6FF))
                        .multilineTextAlignment(.center)
                }
            } else {
                VStack(spacing: DPSpace.s2) {
                    Text("Not saved").font(DPFont.storyHeadline).foregroundStyle(.white)
                    Text("It was too short to count as a drive.")
                        .font(DPFont.body).foregroundStyle(Color(rgba: 0xA3AA_B6FF))
                }
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
}
