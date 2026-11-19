import DPDesignSystem
import DPSharedBridge
import SwiftUI

/// The S-RPL-01 playback bar: play/pause 48, the speed selector (Auto · 1× · 10× · 30×) and the scrubber with the
/// overlay spans marked (F-RPL-08), on glass radius 30.
struct ReplayPlaybackBar: View {
    let model: ReplayViewModel
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        GlassEffectContainer {
            VStack(spacing: DPSpace.s3) {
                HStack(spacing: DPSpace.s3) {
                    playButton
                    if typeSize.isAccessibilitySize {
                        speedCycleButton
                    } else {
                        speedPicker
                    }
                }
                ReplayScrubber(model: model)
                ReplayTimeLabels(model: model)
            }
            .padding(DPSpace.s4)
            .glassEffect(.regular, in: RoundedRectangle(cornerRadius: 30, style: .continuous))
        }
    }

    private var playButton: some View {
        Button {
            model.togglePlay()
        } label: {
            Image(dp: model.isPlaying ? .pause : .play).resizable().scaledToFit().frame(width: 20, height: 20)
                .frame(width: 48, height: 48)
        }
        .buttonStyle(.glassProminent)
        .buttonBorderShape(.circle)
        .tint(DPColor.accent)
        .foregroundStyle(DPColor.onAccent)
        .accessibilityLabel(model.isPlaying ? "Pause replay" : "Play replay")
        .accessibilityIdentifier("replay.playPause")
        .sensoryFeedback(.selection, trigger: model.isPlaying)
    }

    private var speedPicker: some View {
        Picker("Speed", selection: Binding(get: { model.speed }, set: { model.select($0) })) {
            ForEach(PlaybackSpeed.allCases) { speed in
                Text(speed.label).tag(speed)
            }
        }
        .pickerStyle(.segmented)
        .accessibilityIdentifier("replay.speed")
        .sensoryFeedback(.selection, trigger: model.speed)
    }

    /// XL: one button that cycles the speeds.
    private var speedCycleButton: some View {
        Button("Speed: \(model.speed.label)") {
            let all = PlaybackSpeed.allCases
            let next = all[((all.firstIndex(of: model.speed) ?? 0) + 1) % all.count]
            model.select(next)
        }
        .buttonStyle(.glass)
        .accessibilityIdentifier("replay.speed")
    }
}

/// 0:00 · current (in a GPS gap the speed shows "—") · end.
private struct ReplayTimeLabels: View {
    let model: ReplayViewModel

    var body: some View {
        HStack {
            Text(ReplayViewModel.clock(model.tS))
            Spacer()
            Text(ReplayViewModel.clock(model.durationS))
        }
        .font(DPFont.caption.monospacedDigit())
        .foregroundStyle(DPColor.text2)
        .accessibilityHidden(true)
    }
}

/// 4 pt track, played part in the route colour, span marks under it (estimated dashed, no GPS as a gap, excluded
/// hatched), a 16 pt thumb that grows to 22 while dragging with the time above it. Adjustable ±30 s.
struct ReplayScrubber: View {
    let model: ReplayViewModel

    var body: some View {
        GeometryReader { geometry in
            let width = max(geometry.size.width, 1)
            let x = width * model.progress
            ZStack(alignment: .leading) {
                ReplayScrubberTrack(spans: model.spans, durationS: model.durationS)
                    .frame(height: 12)
                Capsule().fill(DPColor.Data.route).frame(width: max(x, 0), height: 4)
                Circle()
                    .fill(DPColor.Data.marker)
                    .overlay(Circle().strokeBorder(DPColor.Data.route, lineWidth: 3))
                    .frame(width: model.isScrubbing ? 22 : 16, height: model.isScrubbing ? 22 : 16)
                    .position(x: min(max(x, 8), width - 8), y: geometry.size.height / 2)
                    .animation(.spring(duration: 0.2), value: model.isScrubbing)
                if model.isScrubbing {
                    Text(ReplayViewModel.clock(model.tS))
                        .font(DPFont.caption.monospacedDigit().weight(.semibold))
                        .padding(.horizontal, DPSpace.s2).padding(.vertical, DPSpace.s0_5)
                        .glassEffect(.regular, in: Capsule())
                        .fixedSize()
                        .position(x: min(max(x, 28), width - 28), y: -14)
                }
            }
            .frame(height: geometry.size.height)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in model.scrub(to: Double(value.location.x / width) * model.durationS) }
                    .onEnded { _ in model.endScrub() })
        }
        .frame(height: 28)
        .accessibilityElement()
        .accessibilityLabel("Replay position")
        .accessibilityValue(model.scrubberAccessibilityValue)
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: model.step(by: 30)
            case .decrement: model.step(by: -30)
            @unknown default: break
            }
        }
        .accessibilityIdentifier("replay.scrubber")
    }
}

/// The scrubber's track with the overlay spans drawn on it.
struct ReplayScrubberTrack: View {
    let spans: [ReplaySpan]
    let durationS: Double

    var body: some View {
        Canvas { context, size in
            let mid = size.height / 2
            let track = Path(roundedRect: CGRect(x: 0, y: mid - 2, width: size.width, height: 4), cornerRadius: 2)
            context.fill(track, with: .color(DPColor.fillStrong))
            guard durationS > 0 else { return }
            for span in spans {
                let x0 = size.width * span.t0S / durationS
                let x1 = max(size.width * span.t1S / durationS, x0 + 2)
                let rect = CGRect(x: x0, y: 0, width: x1 - x0, height: size.height)
                var line = Path()
                line.move(to: CGPoint(x: x0, y: mid))
                line.addLine(to: CGPoint(x: x1, y: mid))
                switch span.style {
                case .estimated:
                    context.stroke(line, with: .color(DPColor.Data.route.opacity(DPEstimated.opacity)),
                                   style: StrokeStyle(lineWidth: 4, dash: DPEstimated.dash))
                case .noGps:
                    context.fill(Path(rect.insetBy(dx: 0, dy: 3)), with: .color(DPColor.Data.mapLand))
                    context.stroke(Path(rect.insetBy(dx: 0.5, dy: 3.5)), with: .color(DPColor.Data.gapStroke),
                                   style: StrokeStyle(lineWidth: 1, dash: [1, 2]))
                case .excluded:
                    ReplayHatch.fill(rect.insetBy(dx: 0, dy: 1), in: &context)
                case .observed:
                    break
                }
            }
        }
        .accessibilityHidden(true)
    }
}
