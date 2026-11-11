import DPDesignSystem
import DPSharedBridge
import SwiftUI

/// S-TRP-01 Trip detail, with what the shell has today: state banners (question, passenger or not a drive,
/// interrupted, short, unscorable), the stat tiles with provenance on long-press (F-DQ-05), the data-quality row
/// (→ S-DQ-01) and "What was this?" (S-TRP-02 type picker). Map, score and events arrive with their own stories.
struct TripDetailView: View {
    let trips: TripListViewModel
    let tripId: String
    @State private var showsQuality = false
    @State private var shownProvenance: String?

    var body: some View {
        Group {
            if let detail = trips.detail(id: tripId) {
                content(detail)
            } else {
                Text("This drive was deleted.").font(DPFont.body).foregroundStyle(DPColor.text2)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .dpScreenBackground()
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showsQuality) {
            if let quality = trips.dataQuality(id: tripId) { DataQualitySheet(detail: quality) }
        }
    }

    private func content(_ detail: TripDetail) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: DPLayout.sectionGap) {
                if !detail.banners.isEmpty {
                    VStack(spacing: DPSpace.s2) {
                        ForEach(Array(detail.banners.enumerated()), id: \.offset) { _, banner in bannerView(banner) }
                    }
                }
                tiles(detail.tiles)
                Text("Touch and hold a number to see how it was measured.")
                    .font(DPFont.caption).foregroundStyle(DPColor.text3)
                VStack(spacing: 0) {
                    if let badge = detail.quality {
                        Button { showsQuality = true } label: {
                            HStack {
                                Text("Data quality").font(DPFont.body).foregroundStyle(DPColor.text)
                                Spacer()
                                QualityBadgeLabel(badge: badge)
                                DPIcon.chevronRight.image.resizable().scaledToFit().frame(width: 12, height: 12)
                                    .foregroundStyle(DPColor.text3)
                            }
                            .frame(minHeight: DPLayout.listRowMin)
                            .padding(.horizontal, DPSpace.s4)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Data quality, \(TripPresenter.badgeWord(badge))")
                        Rectangle().fill(DPColor.separator).frame(height: 0.5).padding(.leading, DPSpace.s4)
                    }
                    if trips.canCorrect {
                        CorrectionPicker(trips: trips, tripId: tripId, current: detail.correction)
                            .frame(minHeight: DPLayout.listRowMin)
                            .padding(.horizontal, DPSpace.s4)
                    }
                }
                .dpCard()
            }
            .padding(.horizontal, DPLayout.screenMargin)
            .padding(.vertical, DPSpace.s2)
        }
        .navigationTitle(detail.title)
        .navigationSubtitle(detail.subtitle)
    }

    @ViewBuilder
    private func bannerView(_ banner: TripBanner) -> some View {
        VStack(alignment: .leading, spacing: DPSpace.s2) {
            Text(banner.text).font(DPFont.bodySecondary).foregroundStyle(DPColor.text)
                .fixedSize(horizontal: false, vertical: true)
            switch banner.action {
            case .answer(let question):
                HStack(spacing: DPSpace.s3) {
                    Button("Yes") { Task { await trips.answer(tripId: tripId, yes: true) } }
                        .buttonStyle(.bordered)
                    Button("No") { Task { await trips.answer(tripId: tripId, yes: false) } }
                        .buttonStyle(.borderedProminent)
                }
                .accessibilityLabel(question == .transit ? "Was this a bus or train?" : "Was this a bike ride?")
            case .restore:
                Button("Restore as a drive") { Task { await trips.correct(tripId: tripId, to: .driver) } }
                    .font(DPFont.body.weight(.semibold)).foregroundStyle(DPColor.accentText)
                    .frame(minHeight: DPLayout.touchMin)
            case nil:
                EmptyView()
            }
        }
        .padding(DPSpace.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            banner.tone == .attention ? DPColor.warningContainer : DPColor.fill,
            in: RoundedRectangle(cornerRadius: DPRadius.md))
    }

    /// 3 columns, 2 at accessibility sizes (S-TRP-01 XL).
    private func tiles(_ tiles: [ProvenancedValue]) -> some View {
        ViewThatFits(in: .horizontal) {
            grid(tiles, columns: 3)
            grid(tiles, columns: 2)
        }
    }

    private func grid(_ tiles: [ProvenancedValue], columns: Int) -> some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: DPSpace.s2), count: columns), spacing: DPSpace.s2) {
            ForEach(tiles) { tile in
                StatTile(value: tile)
                    .popover(isPresented: Binding(
                        get: { shownProvenance == tile.id }, set: { shownProvenance = $0 ? tile.id : nil })
                    ) {
                        ProvenancePopover(value: tile).presentationCompactAdaptation(.popover)
                    }
                    .onLongPressGesture { shownProvenance = tile.id }
                    .accessibilityAction(named: "Show how this was measured") { shownProvenance = tile.id }
            }
        }
    }
}

/// "What was this?" as a menu row (S-TRP-02 type picker).
private struct CorrectionPicker: View {
    let trips: TripListViewModel
    let tripId: String
    let current: TripCorrection

    var body: some View {
        Menu {
            ForEach(TripCorrection.allCases, id: \.self) { correction in
                Button {
                    Task { await trips.correct(tripId: tripId, to: correction) }
                } label: {
                    if correction == current {
                        Label(CorrectionMenu.title(correction), systemImage: "checkmark")
                    } else {
                        Text(CorrectionMenu.title(correction))
                    }
                }
            }
        } label: {
            HStack {
                Text("What was this?").font(DPFont.body).foregroundStyle(DPColor.text)
                Spacer()
                Text(CorrectionMenu.title(current)).font(DPFont.body).foregroundStyle(DPColor.text2)
            }
            .contentShape(Rectangle())
        }
    }
}

/// One number tile (components.md §5): label, value + unit, provenance glyph top-trailing; estimated values use the
/// estimated token.
struct StatTile: View {
    let value: ProvenancedValue

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s1) {
            HStack(alignment: .top) {
                Text(value.label).font(DPFont.caption).foregroundStyle(DPColor.text2)
                Spacer(minLength: DPSpace.s1)
                DPProvenanceGlyph(value.provenance.glyph).foregroundStyle(DPColor.text3)
            }
            HStack(alignment: .firstTextBaseline, spacing: 2) {
                Text(value.isEstimated ? DPEstimated.text(value.value) : value.value).dpNumeral(.m)
                if !value.unit.isEmpty {
                    Text(value.unit).font(DPFont.caption.weight(.medium)).foregroundStyle(DPColor.text2)
                }
            }
            .foregroundStyle(DPColor.text)
            .dpEstimated(value.isEstimated)
            if let note = value.note {
                Text(note).font(DPFont.caption).foregroundStyle(DPColor.text3)
            }
        }
        .padding(DPSpace.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DPColor.surface2, in: RoundedRectangle(cornerRadius: DPRadius.md))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(value.accessibilityLabel)
    }
}

/// The provenance popover (components.md §10.2): glyph + word + the legend line + the metric's own line.
struct ProvenancePopover: View {
    let value: ProvenancedValue

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s2) {
            HStack(spacing: DPSpace.s2) {
                DPProvenanceGlyph(value.provenance.glyph, size: 14)
                Text(value.provenance.label).font(DPFont.cardTitle)
            }
            Text(TripPresenter.fallbackLegend(value.provenance)).font(DPFont.bodySecondary).foregroundStyle(DPColor.text2)
            if !value.meaning.isEmpty {
                Text(value.meaning).font(DPFont.bodySecondary).foregroundStyle(DPColor.text)
            }
        }
        .fixedSize(horizontal: false, vertical: true)
        .frame(maxWidth: 280, alignment: .leading)
        .padding(DPSpace.s4)
    }
}
