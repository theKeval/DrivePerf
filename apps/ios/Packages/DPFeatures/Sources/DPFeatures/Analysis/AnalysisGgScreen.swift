import DPDesignSystem
import DPSharedBridge
import SwiftUI

/// S-ANL-02: the friction circle full screen (F-RPL-06 AC2): labelled rings, the event-type filter, the part banner,
/// the peaks and how to read it. Close or swipe down returns to S-ANL-01.
struct AnalysisGgScreen: View {
    let model: AnalysisViewModel
    let gg: AnalysisGgView
    let subtitle: String

    /// The presenter re-plots on a filter change; read the latest view rather than the one the cover opened with.
    private var current: AnalysisGgView { model.state.gg ?? gg }

    var body: some View {
        let copy = model.state.copy
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: DPLayout.cardGap) {
                    VStack(spacing: DPSpace.s2) {
                        AnalysisGgCanvas(model: model, gg: current, expanded: true)
                            .frame(maxWidth: 372)
                            .aspectRatio(1, contentMode: .fit)
                        Text(copy.ggAxes).font(DPFont.caption).foregroundStyle(DPColor.text3)
                        Text(current.caption ?? copy.ggEmpty).font(DPFont.body)
                        if let filter = current.filterCaption { Text(filter).font(DPFont.meta).foregroundStyle(DPColor.text2) }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(DPSpace.s4)
                    .background(DPColor.surface, in: RoundedRectangle(cornerRadius: DPRadius.card))
                    chips
                    if let banner = current.partBanner {
                        Text(banner)
                            .font(DPFont.meta)
                            .padding(DPSpace.s3)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(DPColor.fill, in: RoundedRectangle(cornerRadius: 12))
                    }
                    if !current.peaks.isEmpty { peaks(copy) }
                    Text(copy.ggHowTitle).font(DPFont.sectionTitle)
                    ForEach(copy.ggHow, id: \.self) { Text($0).font(DPFont.body).foregroundStyle(DPColor.text2) }
                }
                .padding(.horizontal, DPLayout.screenMargin)
                .padding(.bottom, DPSpace.s6)
            }
            .background(DPColor.bgGrouped)
            .navigationTitle(copy.ggTitle)
            .navigationSubtitle(subtitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { model.send(.collapseGg) } label: { Image(systemName: "xmark") }
                        .accessibilityLabel("Close")
                }
            }
        }
    }

    /// All · Braking · Accel · Cornering, a tab list with the pin glyph in each.
    private var chips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: DPSpace.s2) {
                ForEach(current.filters) { chip in
                    Button { model.send(.filterGg(chip.type)) } label: {
                        HStack(spacing: DPSpace.s1) {
                            if let type = chip.type { DPEventGlyph(AnalysisPalette.glyph(type), size: 14) }
                            Text(chip.label).font(DPFont.caption)
                        }
                        .padding(.horizontal, DPSpace.s3)
                        .frame(minHeight: 32)
                    }
                    .buttonStyle(.plain)
                    .glassEffect(chip.selected ? .regular.tint(DPColor.accent.opacity(0.35)) : .regular, in: Capsule())
                    .accessibilityAddTraits(chip.selected ? [.isSelected, .isButton] : .isButton)
                }
            }
        }
        .sensoryFeedback(.selection, trigger: model.state.ggFilter)
    }

    private func peaks(_ copy: AnalysisScreenCopy) -> some View {
        VStack(alignment: .leading, spacing: DPSpace.s2) {
            Text(copy.ggPeaksTitle).font(DPFont.sectionTitle)
            VStack(spacing: 0) {
                ForEach(current.peaks) { row in
                    Button { model.send(.showEvent(row.eventId)) } label: {
                        HStack(spacing: DPSpace.s3) {
                            DPEventGlyph(AnalysisPalette.glyph(row.type), size: 16)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(row.title).font(DPFont.body).foregroundStyle(DPColor.text)
                                Text(row.detail).font(DPFont.meta).foregroundStyle(DPColor.text2)
                            }
                            Spacer()
                            Text(row.value).font(.body.monospacedDigit()).foregroundStyle(DPColor.text)
                            Image(systemName: "chevron.right").font(.footnote).foregroundStyle(DPColor.text3)
                        }
                        .frame(minHeight: DPLayout.listRowMin)
                        .padding(.horizontal, DPSpace.s4)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .background(DPColor.surface, in: RoundedRectangle(cornerRadius: DPRadius.card))
            Text(copy.ggFooter).font(DPFont.caption).foregroundStyle(DPColor.text3)
        }
    }
}
