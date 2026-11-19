import Charts
import DPDesignSystem
import DPSharedBridge
import SwiftUI

/// S-SCO-04 "One thing for next time" (F-SCO-06), pushed from the evidence sheet: the positive card first, the tip
/// (once the tip library has its words), why this one with the longitudinal-G sparkline, and three steps. "See the
/// moment" and Back return to the evidence.
struct TipDetailView: View {
    let model: TipDetailViewModel
    let onDone: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: DPSpace.s4) {
                if model.state.loading {
                    ProgressView().frame(maxWidth: .infinity)
                } else if let noTip = model.state.noTip {
                    card {
                        Text(model.state.words.noTipTitle).font(DPFont.sectionTitle)
                        Text(noTip).font(DPFont.body)
                    }
                } else {
                    content
                }
            }
            .padding(DPLayout.screenMargin)
        }
        .navigationTitle(model.state.title)
        .navigationBarTitleDisplayMode(.inline)
        .task { await model.observe() }
        .task { await model.observeNavigation() }
        .onChange(of: model.finished) { _, finished in if finished { onDone() } }
        .accessibilityIdentifier("tip.detail")
    }

    @ViewBuilder private var content: some View {
        let s = model.state
        if let cleanest = s.cleanest {
            card {
                HStack(alignment: .top, spacing: DPSpace.s3) {
                    Image(dp: .sparkle).foregroundStyle(DPColor.accent)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(s.words.cleanest).font(DPFont.caption).foregroundStyle(DPColor.text2)
                        Text(cleanest).font(DPFont.body)
                    }
                }
            }
        }
        if let text = s.tipText {
            VStack(alignment: .leading, spacing: DPSpace.s2) {
                Text(s.kicker).font(DPFont.caption.weight(.semibold)).foregroundStyle(DPColor.accentText)
                Text(text).font(DPFont.sectionTitle)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(DPSpace.s5)
            .background(DPColor.accent.opacity(0.09), in: RoundedRectangle(cornerRadius: DPRadius.cardHero))
            .accessibilityElement(children: .combine)
        }
        section(s.words.whyThisOne) {
            Text(s.why).font(DPFont.body).fixedSize(horizontal: false, vertical: true)
            if !s.sparkline.isEmpty {
                Chart(Array(s.sparkline.enumerated()), id: \.offset) { item in
                    LineMark(x: .value("Sample", item.offset), y: .value("Long G", item.element))
                        .foregroundStyle(ReplayDataColor.longG)
                    if item.offset == s.sparklinePeakIndex {
                        PointMark(x: .value("Sample", item.offset), y: .value("Long G", item.element))
                            .foregroundStyle(ReplayDataColor.longG)
                    }
                }
                .chartXAxis(.hidden)
                .chartYAxis(.hidden)
                .frame(height: 48)
                .accessibilityLabel("Braking and acceleration around the moment")
            }
            if s.eventId != nil {
                Button {
                    model.send(.seeTheMoment)
                } label: {
                    Label(s.words.seeTheMoment, image: DPIcon.replay.rawValue).frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.glass)
            }
        }
        section(s.words.howToTry) {
            ForEach(Array(s.steps.enumerated()), id: \.offset) { index, step in
                HStack(alignment: .top, spacing: DPSpace.s3) {
                    Text("\(index + 1).").font(DPFont.body.monospacedDigit()).foregroundStyle(DPColor.accentText)
                    Text(step).font(DPFont.body).fixedSize(horizontal: false, vertical: true)
                }
                .accessibilityElement(children: .combine)
            }
        }
        Text(s.footer).font(DPFont.meta).foregroundStyle(DPColor.text2)
    }

    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: DPSpace.s2) {
            Text(title).font(DPFont.caption).foregroundStyle(DPColor.text2).accessibilityAddTraits(.isHeader)
            card(content: content)
        }
    }

    private func card<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: DPSpace.s2, content: content)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(DPSpace.s4)
            .background(DPColor.surface, in: RoundedRectangle(cornerRadius: DPRadius.card))
    }
}
