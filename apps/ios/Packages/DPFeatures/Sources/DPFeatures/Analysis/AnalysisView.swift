import DPDesignSystem
import DPSharedBridge
import SwiftUI

/// S-ANL-01 Analysis for one trip (F-RPL-05, F-RPL-06): the map card, Time/Distance and zoom, the synced timeline,
/// the legend, the G-G card and the stat tiles; S-ANL-03 is the Channels menu, S-ANL-02 a full-screen cover. `title`
/// is the trip's title, shown under "Analysis". `onReplay` opens S-RPL-01 for the trip; without it there is no Replay
/// button. Ticks and G-G peaks open S-RPL-02 over the screen when Replay's services are in the environment (DRIVE-39
/// AC1). The view model is made once, when the screen first appears, and closes its presenter when it goes away.
public struct AnalysisView: View {
    @State private var model: AnalysisViewModel?
    private let make: @MainActor () -> any AnalysisPresenting
    private let title: String
    private let onReplay: ((String) -> Void)?

    public init(presenter: any AnalysisPresenting, title: String) {
        self.init(presenter: presenter, title: title, onReplay: nil)
    }

    public init(presenter: any AnalysisPresenting, title: String, onReplay: ((String) -> Void)?) {
        make = { presenter }
        self.title = title
        self.onReplay = onReplay
    }

    /// Asks `presenters` for the trip's presenter only once, however often SwiftUI rebuilds this view.
    public init(presenters: any AnalysisPresenterMaking, tripId: String, title: String, onReplay: ((String) -> Void)? = nil) {
        make = { presenters.presenter(tripId: tripId) }
        self.title = title
        self.onReplay = onReplay
    }

    public var body: some View {
        if let model {
            AnalysisWorkspace(model: model, title: title, onReplay: onReplay)
        } else {
            DPColor.bgGrouped.onAppear { model = AnalysisViewModel(presenter: make()) }
        }
    }
}

/// The screen once its view model exists.
private struct AnalysisWorkspace: View {
    let model: AnalysisViewModel
    let title: String
    let onReplay: ((String) -> Void)?
    @Environment(\.dismiss) private var dismiss
    @Environment(\.verticalSizeClass) private var verticalSize
    @Environment(\.replayServices) private var replayServices
    /// S-RPL-02 for a tick or a peak (DRIVE-39 AC1); nil without Replay (previews, tests).
    @State private var evidence: TripEvidenceSheets?
    /// S-ANL-02 is on screen: the evidence waits for it to go, as a sheet cannot rise over a closing cover.
    @State private var ggOnScreen = false
    @State private var pendingEvidence: String?

    private var state: AnalysisScreenState { model.state }

    var body: some View {
        content
            .background(DPColor.bgGrouped)
            .navigationTitle(state.copy.title)
            .navigationSubtitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { toolbar }
            .overlay(alignment: .bottom) { toast }
            .fullScreenCover(isPresented: ggBinding, onDismiss: ggDismissed) {
                if let gg = state.gg {
                    AnalysisGgScreen(model: model, gg: gg, subtitle: title).onAppear { ggOnScreen = true }
                }
            }
            .tripEvidenceSheets(evidence)
            .onAppear {
                guard evidence == nil, let replayServices else { return }
                let sheets = TripEvidenceSheets(presenters: replayServices.presenters, tripId: state.tripId)
                // "Not accurate?" in the sheet: the event leaves the ticks, the map and the G-G.
                sheets.onFeedbackChanged = { [model] in model.send(.reload) }
                evidence = sheets
            }
            .task { await model.observe() }
            .task {
                for await navigation in model.navigation() {
                    switch navigation {
                    case .close: dismiss()
                    case .message(let text): model.message = text
                    case .openReplay(let tripId): onReplay?(tripId)
                    case .openEvidence(let eventId): openEvidence(eventId)
                    }
                }
            }
    }

    @ViewBuilder private var content: some View {
        if state.missing {
            ContentUnavailableView(state.copy.title, systemImage: "waveform.path.ecg", description: Text(state.copy.notFound))
        } else if let timeline = state.timeline {
            if verticalSize == .compact {
                landscape(timeline)
            } else {
                portrait(timeline)
            }
        } else {
            AnalysisLoading(text: state.loadingText)
        }
    }

    private func portrait(_ timeline: any AnalysisTimelineView) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: DPLayout.cardGap) {
                if state.summaryOnly { Text(state.copy.summaryOnly).font(DPFont.meta).foregroundStyle(DPColor.text2) }
                mapCard.frame(height: 176)
                AnalysisToolbar(model: model)
                AnalysisReadoutRow(model: model)
                timelineCard(timeline)
                AnalysisLegend(items: timeline.legend)
                bottomRow
                Text(state.copy.footnote).font(DPFont.caption).foregroundStyle(DPColor.text3)
            }
            .padding(.horizontal, DPLayout.screenMargin)
            .padding(.bottom, DPSpace.s6)
        }
    }

    /// F-RPL-05 AC5: in landscape the timeline takes the full width; the map shrinks to an inset with the G-G under it.
    private func landscape(_ timeline: any AnalysisTimelineView) -> some View {
        HStack(alignment: .top, spacing: DPLayout.cardGap) {
            ScrollView {
                VStack(alignment: .leading, spacing: DPSpace.s3) {
                    AnalysisToolbar(model: model)
                    AnalysisReadoutRow(model: model)
                    timelineCard(timeline)
                    AnalysisLegend(items: timeline.legend)
                }
            }
            .containerRelativeFrame(.horizontal) { w, _ in w * 0.68 }
            VStack(spacing: DPSpace.s3) {
                mapCard.aspectRatio(1.2, contentMode: .fit)
                ggCard.aspectRatio(1, contentMode: .fit)
            }
        }
        .padding(.horizontal, DPLayout.screenMargin)
    }

    @ViewBuilder private var mapCard: some View {
        Group {
            if let map = state.map {
                AnalysisMapCanvas(model: model, map: map)
            } else {
                Text(state.copy.noMap).font(DPFont.meta).foregroundStyle(DPColor.text2).frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: DPRadius.card))
    }

    private func timelineCard(_ timeline: any AnalysisTimelineView) -> some View {
        AnalysisTimelineCanvas(model: model, timeline: timeline)
            .padding(DPSpace.s3)
            .background(DPColor.surface, in: RoundedRectangle(cornerRadius: DPRadius.card))
    }

    @ViewBuilder private var bottomRow: some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .top, spacing: DPSpace.s3) {
                ggCard.frame(width: 158, height: 158)
                VStack(spacing: DPSpace.s2) { stats }
            }
            VStack(spacing: DPSpace.s3) {
                ggCard.aspectRatio(1, contentMode: .fit)
                stats
            }
        }
    }

    @ViewBuilder private var ggCard: some View {
        if let gg = state.gg {
            Button { model.send(.expandGg) } label: {
                Group {
                    if gg.empty {
                        Text(state.copy.ggEmpty).font(DPFont.caption).foregroundStyle(DPColor.text2).padding(DPSpace.s3)
                    } else {
                        AnalysisGgCanvas(model: model, gg: gg).padding(DPSpace.s1_5)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(DPColor.surface, in: RoundedRectangle(cornerRadius: DPRadius.card))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(gg.accessibilityLabel)
            .accessibilityHint("Opens the friction circle.")
        }
    }

    private var stats: some View {
        ForEach(state.stats) { tile in
            VStack(alignment: .leading, spacing: 2) {
                Text(tile.title).font(DPFont.caption).foregroundStyle(DPColor.text2)
                Text(tile.value).font(.title3.weight(.semibold).monospacedDigit()).foregroundStyle(DPColor.text)
                if let detail = tile.detail { Text(detail).font(DPFont.caption).foregroundStyle(DPColor.text3) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, DPSpace.s3)
            .padding(.vertical, DPSpace.s2)
            .background(DPColor.surface2, in: RoundedRectangle(cornerRadius: DPRadius.md))
            .accessibilityElement(children: .combine)
        }
    }

    @ToolbarContentBuilder private var toolbar: some ToolbarContent {
        if state.timeline != nil {
            ToolbarItem(placement: .topBarTrailing) { AnalysisChannelsMenu(model: model) }
            if onReplay != nil {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { model.send(.openReplay) } label: { Image(systemName: "play.circle") }
                        .accessibilityLabel("Replay")
                }
            }
        }
    }

    @ViewBuilder private var toast: some View {
        if let message = model.message {
            Text(message)
                .font(DPFont.label)
                .padding(.horizontal, DPSpace.s4)
                .padding(.vertical, DPSpace.s2)
                .glassEffect(.regular, in: Capsule())
                .padding(.bottom, DPSpace.s6)
                .task(id: message) {
                    try? await Task.sleep(for: .seconds(2))
                    model.message = nil
                }
                .accessibilityAddTraits(.isStaticText)
        }
    }

    private var ggBinding: Binding<Bool> {
        Binding(get: { model.state.ggExpanded }, set: { if !$0 { model.send(.collapseGg) } })
    }

    /// A tick, or a peak row on S-ANL-02 (which the presenter closes first): S-RPL-02 rises once nothing covers S-ANL-01.
    private func openEvidence(_ eventId: String) {
        if ggOnScreen {
            pendingEvidence = eventId
        } else {
            evidence?.openEvidence(eventId)
        }
    }

    private func ggDismissed() {
        ggOnScreen = false
        if let eventId = pendingEvidence {
            pendingEvidence = nil
            evidence?.openEvidence(eventId)
        }
    }
}

/// S-ANL-01 Load: skeletons that mirror the lanes, and the loader line.
private struct AnalysisLoading: View {
    let text: String

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s3) {
            ProgressView().progressViewStyle(.linear)
            Text(text).font(DPFont.meta).foregroundStyle(DPColor.text2)
            ForEach([176, 44, 64, 64], id: \.self) { h in
                RoundedRectangle(cornerRadius: DPRadius.md).fill(DPColor.fillStrong).frame(height: CGFloat(h))
            }
            Spacer()
        }
        .padding(DPLayout.screenMargin)
    }
}

/// Time / Distance (glass segmented) and the zoom stepper.
private struct AnalysisToolbar: View {
    let model: AnalysisViewModel

    var body: some View {
        let state = model.state
        HStack(spacing: DPSpace.s3) {
            Picker("Axis", selection: Binding(get: { state.axis }, set: { model.send(.setAxis($0)) })) {
                Text(state.copy.axisTime).tag(AnalysisAxisKind.time)
                Text(state.copy.axisDistance).tag(AnalysisAxisKind.distance)
            }
            .pickerStyle(.segmented)
            HStack(spacing: DPSpace.s1) {
                Button { model.send(.zoomOut) } label: { Image(systemName: "minus").frame(width: 36, height: 36) }
                    .disabled(!state.canZoomOut)
                    .accessibilityLabel("Zoom out")
                Text(state.zoomLabel).font(.subheadline.monospacedDigit()).frame(minWidth: 40)
                Button { model.send(.zoomIn) } label: { Image(systemName: "plus").frame(width: 36, height: 36) }
                    .disabled(!state.canZoomIn)
                    .accessibilityLabel("Zoom in")
            }
            .buttonStyle(.bordered)
            .buttonBorderShape(.circle)
        }
    }
}

/// "12:04 elapsed · 9.8 km" and the span chip. It reads the cursor, so it redraws on a drag.
private struct AnalysisReadoutRow: View {
    let model: AnalysisViewModel

    var body: some View {
        HStack(spacing: DPSpace.s2) {
            Text(model.readout?.headline ?? "").font(.subheadline.weight(.semibold).monospacedDigit())
            if let chip = model.readout?.chip {
                Text(chip)
                    .font(DPFont.caption)
                    .padding(.horizontal, DPSpace.s2)
                    .padding(.vertical, 2)
                    .background(DPColor.fill, in: Capsule())
            }
        }
        .accessibilityElement(children: .combine)
    }
}

/// The legend: swatch and pattern, never colour alone.
private struct AnalysisLegend: View {
    let items: [AnalysisLegendItem]

    var body: some View {
        FlowLayoutRow(items: items)
    }

    private struct FlowLayoutRow: View {
        let items: [AnalysisLegendItem]

        var body: some View {
            ViewThatFits(in: .horizontal) {
                HStack(spacing: DPSpace.s3) { ForEach(items) { AnalysisLegendChip(item: $0) } }
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 110), alignment: .leading)], alignment: .leading, spacing: DPSpace.s1) {
                    ForEach(items) { AnalysisLegendChip(item: $0) }
                }
            }
        }
    }
}

private struct AnalysisLegendChip: View {
    let item: AnalysisLegendItem

    var body: some View {
        HStack(spacing: DPSpace.s1) {
            Canvas { ctx, size in
                let color = AnalysisPalette.legend(item.kind)
                let y = size.height / 2
                var line = Path()
                line.move(to: CGPoint(x: 0, y: y))
                line.addLine(to: CGPoint(x: size.width, y: y))
                switch item.kind {
                case .latG: ctx.stroke(line, with: .color(color), style: StrokeStyle(lineWidth: 2, dash: [6, 3]))
                case .estimated: ctx.stroke(line, with: .color(color), style: StrokeStyle(lineWidth: 2, dash: [3, 3]))
                case .noGps: ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(color.opacity(0.4)))
                case .notScored:
                    var h = Path()
                    for x in stride(from: -size.height, to: size.width, by: 3) {
                        h.move(to: CGPoint(x: x, y: size.height))
                        h.addLine(to: CGPoint(x: x + size.height, y: 0))
                    }
                    ctx.stroke(h, with: .color(DPColor.text3), lineWidth: 1)
                case .elevation: ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(AnalysisPalette.elevationFill))
                default: ctx.stroke(line, with: .color(color), lineWidth: 2)
                }
            }
            .frame(width: 18, height: 10)
            Text(item.label).font(DPFont.caption).foregroundStyle(DPColor.text2)
        }
        .accessibilityElement(children: .combine)
    }
}

/// S-ANL-03 as the native glass menu anchored to the Channels button; the choice persists per user (AC4).
private struct AnalysisChannelsMenu: View {
    let model: AnalysisViewModel

    var body: some View {
        let state = model.state
        Menu {
            Section(state.copy.channelsSub) {
                ForEach(state.channelOptions.filter { !$0.pro }) { toggle($0) }
            }
            Section(state.copy.channelsPro) {
                ForEach(state.channelOptions.filter(\.pro)) { toggle($0) }
            }
        } label: {
            Image(systemName: "line.3.horizontal.decrease")
        }
        .menuActionDismissBehavior(.disabled)
        .accessibilityLabel(state.copy.channelsTitle)
    }

    private func toggle(_ option: AnalysisChannelOption) -> some View {
        Toggle(isOn: Binding(get: { option.on }, set: { _ in model.send(.toggle(option.channel)) })) {
            Text(option.pro ? "\(option.title) · PRO" : option.title)
            Text(option.description)
        }
        .disabled(!option.enabled)
        .accessibilityLabel(option.accessibilityLabel)
    }
}
