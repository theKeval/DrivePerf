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
                if let overlay = model.overlay {
                    if !overlay.elevation.isEmpty { ReplayElevationRow(model: model, overlay: overlay) }
                    Text(overlay.noEventsText.map { "\(overlay.summary) · \($0)" } ?? overlay.summary)
                        .font(DPFont.caption).foregroundStyle(DPColor.text2)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .accessibilityIdentifier("replay.stats")
                }
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
    /// The event tick the drag last snapped to, for the selection haptic.
    @State private var snapped: String?

    var body: some View {
        GeometryReader { geometry in
            let width = max(geometry.size.width, 1)
            let x = width * model.progress
            ZStack(alignment: .leading) {
                ReplayScrubberTrack(spans: model.spans, durationS: model.durationS)
                    .frame(height: 12)
                if let overlay = model.overlay {
                    ReplayScrubberTicks(events: overlay.ticks, stops: overlay.stopTicks, durationS: model.durationS)
                }
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
                    .onChanged { value in model.scrub(to: snap(value.location.x, width: width)) }
                    .onEnded { _ in
                        snapped = nil
                        model.endScrub()
                    })
            .sensoryFeedback(.selection, trigger: snapped) { _, new in new != nil }
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
        .accessibilityActions {
            if let overlay = model.overlay, !overlay.ticks.isEmpty {
                Button("Next event") { seek(overlay.ticks.first { $0.tPeakS > model.tS + 0.5 }?.tPeakS) }
                Button("Previous event") { seek(overlay.ticks.last { $0.tPeakS < model.tS - 0.5 }?.tPeakS) }
            }
            if let overlay = model.overlay, !overlay.stopTicks.isEmpty {
                Button("Next stop") { seek(overlay.stopTicks.first { $0.atS > model.tS + 0.5 }?.atS) }
            }
        }
        .accessibilityIdentifier("replay.scrubber")
    }

    private func seek(_ tS: Double?) {
        guard let tS else { return }
        model.step(by: tS - model.tS)
    }

    /// The drag position in trip seconds, snapped to an event tick within 8 pt (S-RPL-01).
    private func snap(_ x: CGFloat, width: CGFloat) -> Double {
        let t = Double(min(max(x / width, 0), 1)) * model.durationS
        guard model.durationS > 0, let ticks = model.overlay?.ticks else { return t }
        let near = ticks.min { abs($0.tPeakS / model.durationS * width - x) < abs($1.tPeakS / model.durationS * width - x) }
        if let near, abs(near.tPeakS / model.durationS * width - x) <= 8 {
            if snapped != near.id { snapped = near.id }
            return near.tPeakS
        }
        snapped = nil
        return t
    }
}

/// Event ticks 12 pt above the track (by shape, hollow when needed) and stop ticks under it, at the same times as the
/// map pins (F-RPL-03 AC3).
struct ReplayScrubberTicks: View {
    let events: [ReplayEventPinInfo]
    let stops: [ReplayStopPinInfo]
    let durationS: Double

    var body: some View {
        Canvas { context, size in
            guard durationS > 0 else { return }
            let mid = size.height / 2
            for stop in stops {
                let x = size.width * stop.atS / durationS
                var line = Path()
                line.move(to: CGPoint(x: x, y: mid + 5))
                line.addLine(to: CGPoint(x: x, y: mid + 10))
                context.stroke(line, with: .color(DPColor.text2), style: StrokeStyle(lineWidth: 2, lineCap: .round))
            }
            for event in events {
                let c = CGPoint(x: size.width * event.tPeakS / durationS, y: mid - 12)
                let r: CGFloat = 3.5
                let shape: Path = switch event.kind {
                case .brake: Path(roundedRect: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r), cornerRadius: 1.5)
                case .accel: Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r))
                case .corner: Path { p in
                    p.move(to: CGPoint(x: c.x, y: c.y - r * 1.3))
                    p.addLine(to: CGPoint(x: c.x + r * 1.3, y: c.y))
                    p.addLine(to: CGPoint(x: c.x, y: c.y + r * 1.3))
                    p.addLine(to: CGPoint(x: c.x - r * 1.3, y: c.y))
                    p.closeSubpath()
                }
                }
                let color = DPEventGlyph.color(event.kind.glyph)
                if event.needed {
                    context.stroke(shape, with: .color(color), lineWidth: 1.5)
                } else {
                    context.fill(shape, with: .color(color))
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// The elevation mini-profile under the scrubber (F-RPL-02 AC4): 24 pt on the scrubber's time axis, a cursor synced to
/// the marker, then "↗ 118 m ↘ 103 m".
struct ReplayElevationRow: View {
    let model: ReplayViewModel
    let overlay: any ReplayOverlayReading

    var body: some View {
        HStack(spacing: DPSpace.s2) {
            Canvas { context, size in
                let duration = max(model.durationS, 1)
                func point(_ s: ReplayElevationSample) -> CGPoint {
                    CGPoint(x: size.width * s.tS / duration, y: size.height * (1 - s.fraction))
                }
                var line = Path()
                for (i, sample) in overlay.elevation.enumerated() {
                    i == 0 ? line.move(to: point(sample)) : line.addLine(to: point(sample))
                }
                var area = line
                if let last = overlay.elevation.last, let first = overlay.elevation.first {
                    area.addLine(to: CGPoint(x: point(last).x, y: size.height))
                    area.addLine(to: CGPoint(x: point(first).x, y: size.height))
                    area.closeSubpath()
                }
                context.fill(area, with: .color(DPColor.Data.speed.opacity(0.18)))
                context.stroke(line, with: .color(DPColor.Data.speed), lineWidth: 1.5)
                let x = size.width * model.tS / duration
                var cursor = Path()
                cursor.move(to: CGPoint(x: x, y: 0))
                cursor.addLine(to: CGPoint(x: x, y: size.height))
                context.stroke(cursor, with: .color(DPColor.Data.cursor), lineWidth: 1.5)
                let y = size.height * (1 - overlay.elevationFraction(at: model.tS))
                context.fill(Path(ellipseIn: CGRect(x: x - 3, y: y - 3, width: 6, height: 6)), with: .color(DPColor.Data.cursor))
            }
            .frame(height: 24)
            if let text = overlay.elevationText {
                Text(text).font(DPFont.caption.monospacedDigit()).foregroundStyle(DPColor.text2).fixedSize()
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(overlay.elevationText ?? "")
        .accessibilityIdentifier("replay.elevation")
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
