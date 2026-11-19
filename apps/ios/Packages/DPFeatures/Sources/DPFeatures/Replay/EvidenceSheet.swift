import Charts
import DPDesignSystem
import DPSharedBridge
import SwiftUI

extension ReplayProvenance {
    var glyph: DPProvenanceGlyph.Kind {
        switch self {
        case .observed: .observed
        case .derived: .derived
        case .estimated: .estimated
        }
    }
}

/// The provenance glyph and word (F-DQ-05, components.md §10.2).
struct ReplayProvenanceLabel: View {
    let provenance: ReplayProvenance

    var body: some View {
        HStack(spacing: DPSpace.s1) {
            DPProvenanceGlyph(provenance.glyph)
            Text(provenance.label).font(DPFont.caption).foregroundStyle(DPColor.text2)
        }
    }
}

/// S-RPL-03, the stop or max-speed popover (F-RPL-02 AC2–3). The value is plain text in `text`, never accent or
/// coloured, with no animation, glow or haptic (speed-neutral rule).
struct ReplayPinSheetView: View {
    let sheet: ReplayPinSheetInfo
    let model: ReplayViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s3) {
            Text(sheet.title).font(DPFont.sheetTitle).accessibilityAddTraits(.isHeader)
            if !sheet.subtitle.isEmpty {
                Text(sheet.subtitle).font(DPFont.meta).foregroundStyle(DPColor.text2)
            }
            HStack(alignment: .firstTextBaseline, spacing: DPSpace.s1_5) {
                Text(sheet.value).dpNumeral(.xl).foregroundStyle(DPColor.text)
                Text(sheet.unit).font(DPFont.cardTitle).foregroundStyle(DPColor.text2)
                ReplayProvenanceLabel(provenance: sheet.provenance)
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(sheet.accessibilityLabel)
            if !sheet.rows.isEmpty {
                VStack(spacing: 0) {
                    ForEach(sheet.rows) { row in
                        HStack {
                            Text(row.label).font(DPFont.body)
                            Spacer()
                            Text(row.value).font(DPFont.body.monospacedDigit()).foregroundStyle(DPColor.text2)
                        }
                        .frame(minHeight: DPLayout.listRowMin)
                        if row.id != sheet.rows.last?.id { Divider() }
                    }
                }
                .padding(.horizontal, DPSpace.s4)
                .background(DPColor.surface2, in: RoundedRectangle(cornerRadius: DPRadius.md))
            }
            Text(sheet.note).font(DPFont.bodySecondary).foregroundStyle(DPColor.text2)
                .fixedSize(horizontal: false, vertical: true)
            Button {
                model.jumpToPin()
            } label: {
                Label("Jump to this moment", image: DPIcon.replay.rawValue).frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(.glass)
            .accessibilityIdentifier("replay.pinSheet.jump")
        }
        .padding(DPSpace.s5)
        .presentationDetents([.fraction(0.42), .medium])
        .presentationCompactAdaptation(.sheet)
        .accessibilityIdentifier("replay.pinSheet")
    }
}

/// S-RPL-02, the event evidence sheet (F-RPL-04): medium detent, expandable to large. The tip pushes S-SCO-04 and "Was
/// it needed?" opens S-SCO-03 over it. Every word comes from the shared presenter.
struct EvidenceSheetView: View {
    @Bindable var model: EvidenceViewModel
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: DPSpace.s4) {
                    if model.state.loading {
                        ProgressView().frame(maxWidth: .infinity)
                    } else if model.state.missing {
                        Text("This event is no longer on this drive.").font(DPFont.body)
                    } else {
                        content
                    }
                }
                .padding(DPLayout.screenMargin)
            }
            .navigationDestination(isPresented: tipShown) {
                if let tip = model.tip { TipDetailView(model: tip, onDone: model.tipClosed) }
            }
        }
        .presentationDetents(typeSize.isAccessibilitySize ? [.large] : [.medium, .large])
        .presentationBackgroundInteraction(.enabled(upThrough: .medium))
        .sheet(isPresented: $model.isNeededSheetShown) {
            if let sheet = model.state.neededSheet { NeededSheetView(sheet: sheet, model: model) }
        }
        .task { await model.observe() }
        .accessibilityIdentifier("evidence.sheet")
    }

    private var tipShown: Binding<Bool> {
        Binding(get: { model.tip != nil }, set: { if !$0 { model.tipClosed() } })
    }

    @ViewBuilder private var content: some View {
        let s = model.state
        header(s)
        ForEach(s.banners) { banner in EvidenceBannerView(banner: banner) { model.send(.askCountAgain) } }
        if let trace = s.trace {
            EvidenceTraceChart(trace: trace, speedUnit: s.speedUnit)
                .padding(DPSpace.s3)
                .background(DPColor.surface2, in: RoundedRectangle(cornerRadius: 18))
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(s.traceSummary)
        }
        EvidenceFacts(facts: s.facts)
        if let tip = s.tip {
            EvidenceTipCard(tip: tip, link: s.words.whyThisTip) { model.send(.openTip) }
        }
        actions(s)
        if s.reasonsOpen { reasons(s) }
        stepper(s)
    }

    private func header(_ s: EvidenceScreenState) -> some View {
        HStack(spacing: DPSpace.s3) {
            if let kind = s.kind { DPEventGlyph(kind.glyph, needed: s.needed, size: 28) }
            VStack(alignment: .leading, spacing: 2) {
                Text(s.title).font(DPFont.sheetTitle)
                Text(s.subtitle).font(DPFont.meta).foregroundStyle(DPColor.text2)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(s.accessibilityLabel)
        .accessibilityAddTraits(.isHeader)
    }

    @ViewBuilder private func actions(_ s: EvidenceScreenState) -> some View {
        if !s.needed {
            Button {
                model.send(.askNeeded)
            } label: {
                Text(s.words.wasItNeeded).frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(.glass)
            .disabled(s.busy)
            .accessibilityIdentifier("evidence.wasItNeeded")
        }
        if !s.reasonsOpen {
            Button(s.words.notAccurate) { model.send(.openReasons) }
                .frame(maxWidth: .infinity, minHeight: 44)
                .disabled(s.busy)
                .accessibilityIdentifier("evidence.notAccurate")
        }
    }

    /// "Not accurate?": radios, then Send and Cancel (F-RPL-04 AC4).
    private func reasons(_ s: EvidenceScreenState) -> some View {
        VStack(alignment: .leading, spacing: DPSpace.s2) {
            Text(s.words.reasonsTitle).font(DPFont.cardTitle).accessibilityAddTraits(.isHeader)
            Text(s.words.reasonsBody).font(DPFont.bodySecondary).foregroundStyle(DPColor.text2)
            ForEach(s.reasons, id: \.reason) { option in
                Button {
                    model.send(.selectReason(option.reason))
                } label: {
                    HStack {
                        Image(systemName: option.reason == s.selectedReason ? "largecircle.fill.circle" : "circle")
                            .foregroundStyle(DPColor.accent)
                        Text(option.label).font(DPFont.body).foregroundStyle(DPColor.text)
                        Spacer()
                    }
                    .frame(minHeight: DPLayout.touchMin)
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(option.reason == s.selectedReason ? .isSelected : [])
            }
            HStack {
                Button(s.words.send) { model.send(.sendReason) }
                    .buttonStyle(.glassProminent)
                    .disabled(s.selectedReason == nil || s.busy)
                Button(s.words.cancel) { model.send(.cancelReasons) }
            }
        }
    }

    /// "‹ Event 1 of 2 ›" (accessibility.md §6).
    @ViewBuilder private func stepper(_ s: EvidenceScreenState) -> some View {
        if !s.position.isEmpty {
            HStack {
                Button { model.send(.previous) } label: { Image(dp: .chevronLeft).frame(width: 44, height: 44) }
                    .buttonStyle(.glass).disabled(!s.hasPrevious || s.busy).accessibilityLabel("Previous event")
                Spacer()
                Text(s.position).font(DPFont.label)
                Spacer()
                Button { model.send(.next) } label: { Image(dp: .chevronRight).frame(width: 44, height: 44) }
                    .buttonStyle(.glass).disabled(!s.hasNext || s.busy).accessibilityLabel("Next event")
            }
        }
    }
}

private struct EvidenceBannerView: View {
    let banner: EvidenceBannerInfo
    let onAction: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s1) {
            Text(banner.text).font(DPFont.bodySecondary).fixedSize(horizontal: false, vertical: true)
            if let action = banner.action {
                Button(action, action: onAction).font(DPFont.label.weight(.semibold))
                    .accessibilityIdentifier("evidence.countAgain")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(DPSpace.s3)
        .background(banner.kind == .needed ? DPColor.accentContainer : DPColor.fill, in: RoundedRectangle(cornerRadius: 12))
    }
}

/// Peak · Duration · Speed · Confidence, four columns (two at the largest sizes), each with its provenance.
private struct EvidenceFacts: View {
    let facts: [EvidenceFactInfo]
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let columns = Array(repeating: GridItem(.flexible(), alignment: .topLeading), count: typeSize.isAccessibilitySize ? 2 : 4)
        LazyVGrid(columns: columns, alignment: .leading, spacing: DPSpace.s3) {
            ForEach(facts) { fact in
                VStack(alignment: .leading, spacing: 2) {
                    ReplayProvenanceLabel(provenance: fact.provenance)
                    Text(fact.label).font(DPFont.caption).foregroundStyle(DPColor.text2)
                    HStack(alignment: .firstTextBaseline, spacing: 2) {
                        Text(fact.value).dpNumeral(.s).strikethrough(fact.struck)
                        if !fact.unit.isEmpty { Text(fact.unit).font(DPFont.caption).foregroundStyle(DPColor.text2) }
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(fact.spoken)
            }
        }
    }
}

struct EvidenceTipCard: View {
    let tip: EvidenceTipInfo
    let link: String
    let onOpen: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s1_5) {
            Text(tip.kicker).font(DPFont.caption).foregroundStyle(DPColor.text2)
            Text(tip.text).font(DPFont.body)
            Button(action: onOpen) {
                HStack(spacing: DPSpace.s1) {
                    Text(link)
                    Image(dp: .chevronRight).resizable().frame(width: 12, height: 12)
                }
            }
            .font(DPFont.label.weight(.semibold))
            .accessibilityIdentifier("evidence.tip")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(DPSpace.s4)
        .background(DPColor.accent.opacity(0.10), in: RoundedRectangle(cornerRadius: DPRadius.lg))
        .accessibilityElement(children: .combine)
    }
}

/// The ±10 s trace (F-RPL-04 AC1) in Swift Charts: speed (neutral) on top, longitudinal (solid) and lateral (dashed) G
/// below, start and end guides and the peak rule. Patterns, not colour, tell the lines apart.
struct EvidenceTraceChart: View {
    let trace: EvidenceTraceInfo
    let speedUnit: String

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s1_5) {
            Chart(trace.speed, id: \.tS) { s in
                LineMark(x: .value("Time", s.tS - trace.tPeakS), y: .value("Speed", s.value))
                    .foregroundStyle(DPColor.Data.speed)
                RuleMark(x: .value("Peak", 0)).foregroundStyle(DPColor.Data.cursor.opacity(0.6))
            }
            .chartXScale(domain: -10.0...10.0)
            .chartXAxis(.hidden)
            .chartYAxis { AxisMarks(position: .leading, values: .automatic(desiredCount: 2)) }
            .frame(height: 44)
            Chart {
                ForEach(trace.g, id: \.tS) { g in
                    LineMark(x: .value("Time", g.tS - trace.tPeakS), y: .value("G", g.lonG), series: .value("Axis", "Long"))
                        .foregroundStyle(ReplayDataColor.longG)
                        .lineStyle(StrokeStyle(lineWidth: 2))
                    LineMark(x: .value("Time", g.tS - trace.tPeakS), y: .value("G", g.latG), series: .value("Axis", "Lat"))
                        .foregroundStyle(ReplayDataColor.latG)
                        .lineStyle(StrokeStyle(lineWidth: 2, dash: [6, 3]))
                }
                RuleMark(y: .value("Zero", 0)).foregroundStyle(DPColor.separator)
                RuleMark(x: .value("Start", trace.tStartS - trace.tPeakS))
                    .foregroundStyle(DPColor.text3).lineStyle(StrokeStyle(lineWidth: 1, dash: [2, 3]))
                RuleMark(x: .value("End", trace.tEndS - trace.tPeakS))
                    .foregroundStyle(DPColor.text3).lineStyle(StrokeStyle(lineWidth: 1, dash: [2, 3]))
                RuleMark(x: .value("Peak", 0)).foregroundStyle(DPColor.Data.cursor.opacity(0.6))
            }
            .chartXScale(domain: -10.0...10.0)
            .chartXAxis {
                AxisMarks(values: [-10, 0, 10]) { value in
                    AxisValueLabel {
                        if let v = value.as(Double.self) { Text(v == 0 ? "Peak" : v < 0 ? "−10 s" : "+10 s") }
                    }
                }
            }
            .chartYAxis { AxisMarks(position: .leading, values: .automatic(desiredCount: 3)) }
            .frame(height: 74)
            HStack(spacing: DPSpace.s4) {
                legend("Speed (\(speedUnit))", color: DPColor.Data.speed, dashed: false)
                legend("Long G", color: ReplayDataColor.longG, dashed: false)
                legend("Lat G", color: ReplayDataColor.latG, dashed: true)
            }
        }
    }

    private func legend(_ text: String, color: Color, dashed: Bool) -> some View {
        HStack(spacing: DPSpace.s1) {
            Canvas { context, size in
                var line = Path()
                line.move(to: CGPoint(x: 0, y: size.height / 2))
                line.addLine(to: CGPoint(x: size.width, y: size.height / 2))
                context.stroke(line, with: .color(color), style: StrokeStyle(lineWidth: 2, dash: dashed ? [4, 2] : []))
            }
            .frame(width: 18, height: 6)
            Text(text).font(DPFont.caption).foregroundStyle(DPColor.text2)
        }
    }
}

/// S-SCO-03 "Was it needed?" (F-SCO-05), a small sheet over the evidence, with decision D4 in words.
struct NeededSheetView: View {
    let sheet: NeededSheetInfo
    let model: EvidenceViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s4) {
            Text(sheet.title).font(DPFont.sheetTitle).accessibilityAddTraits(.isHeader)
            if let kind = model.state.kind {
                HStack(spacing: DPSpace.s3) {
                    DPEventGlyph(kind.glyph, needed: sheet.step == .done || model.state.needed, size: 30)
                    VStack(alignment: .leading) {
                        Text(model.state.title).font(DPFont.cardTitle)
                        Text(model.state.subtitle).font(DPFont.meta).foregroundStyle(DPColor.text2)
                    }
                }
                .accessibilityElement(children: .combine)
            }
            if let score = sheet.score {
                Text("\(score)").dpNumeral(.xl).contentTransition(.numericText())
                    .accessibilityLabel("Smoothness \(score)")
            }
            Text(sheet.body).font(DPFont.body).fixedSize(horizontal: false, vertical: true)
            Text(sheet.capNote).font(DPFont.meta).foregroundStyle(DPColor.text2).fixedSize(horizontal: false, vertical: true)
            Button {
                switch sheet.step {
                case .ask: model.send(.confirmNeeded)
                case .done: model.send(.dismissNeeded)
                case .countAgain: model.send(.confirmCountAgain)
                }
            } label: {
                Text(sheet.primary).frame(maxWidth: .infinity, minHeight: 50)
            }
            .buttonStyle(.glassProminent)
            .disabled(model.state.busy)
            .sensoryFeedback(.impact, trigger: sheet.step == .done)
            .accessibilityIdentifier("needed.primary")
            if let secondary = sheet.secondary {
                Button(secondary) { model.send(.dismissNeeded) }.frame(maxWidth: .infinity, minHeight: 44)
            }
        }
        .padding(DPSpace.s5)
        .presentationDetents([.medium])
        .accessibilityIdentifier("needed.sheet")
    }
}
