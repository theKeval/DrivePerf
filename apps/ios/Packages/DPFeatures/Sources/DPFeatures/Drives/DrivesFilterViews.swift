import DPDesignSystem
import DPSharedBridge
import SwiftUI

// S-HIS-01's filter chips and S-HIS-02 Filter sheet (DRIVE-126 AC3), drawn from the shared presenter's state through
// `TripListViewModel`. The list is filtered in the presenter, not per row here, so 1,000 drives stay cheap (AC2).

/// "Filters" then the applied filters (tap removes one), "Clear" and the quick filters, with "8 drives · filtered".
struct DrivesFilterBar: View {
    let trips: TripListViewModel

    var body: some View {
        let filters = trips.historyFilters
        VStack(alignment: .leading, spacing: DPSpace.s1) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: DPSpace.s2) {
                    Button { trips.filter(.openSheet) } label: {
                        ChipLabel(
                            text: filters.isActive ? "\(filters.buttonLabel) · \(filters.activeCount)" : filters.buttonLabel,
                            systemImage: "line.3.horizontal.decrease", isSelected: filters.isActive)
                    }
                    .accessibilityLabel(filters.buttonAccessibilityLabel)
                    .accessibilityIdentifier("drives.filters")
                    ForEach(filters.applied) { chip in
                        Button { trips.filter(.toggle(chip.key)) } label: {
                            ChipLabel(text: chip.label, trailingSystemImage: "xmark", isSelected: true)
                        }
                        .accessibilityLabel(chip.accessibilityLabel)
                    }
                    if filters.isActive {
                        Button(filters.clearLabel) { trips.filter(.clear) }.font(DPFont.caption.weight(.semibold))
                    }
                    ForEach(filters.suggestions) { chip in
                        Button { trips.filter(.toggle(chip.key)) } label: { ChipLabel(text: chip.label, isSelected: false) }
                            .accessibilityLabel(chip.accessibilityLabel)
                    }
                }
                .buttonStyle(.plain)
            }
            .accessibilityElement(children: .contain)
            .accessibilityAddTraits(.isHeader)
            if let subtitle = filters.subtitle {
                Text(subtitle).font(DPFont.meta).foregroundStyle(DPColor.text2)
                    .accessibilityAddTraits(.updatesFrequently)
            }
        }
    }
}

/// "No drives match." with "Clear filters".
struct DrivesFilterEmpty: View {
    let trips: TripListViewModel

    var body: some View {
        let filters = trips.historyFilters
        if let title = filters.emptyTitle {
            VStack(spacing: DPSpace.s2) {
                Text(title).font(DPFont.cardTitle)
                if let body = filters.emptyBody { Text(body).font(DPFont.meta).foregroundStyle(DPColor.text2) }
                Button(filters.clearLabel) { trips.filter(.clear) }.buttonStyle(.glass)
            }
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(.vertical, DPSpace.s6)
        }
    }
}

extension View {
    /// S-HIS-02 over All drives, while the presenter opens it.
    func drivesFilterSheet(trips: TripListViewModel) -> some View {
        sheet(isPresented: Binding(
            get: { trips.historyFilters.sheet != nil }, set: { if !$0 { trips.filter(.closeSheet) } })
        ) {
            DrivesFilterSheet(trips: trips)
        }
    }
}

/// S-HIS-02: four groups of chips with their drive counts, and Reset · "Show n drives".
struct DrivesFilterSheet: View {
    let trips: TripListViewModel
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        if let sheet = trips.historyFilters.sheet {
            NavigationStack {
                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        ForEach(sheet.sections) { section in
                            VStack(alignment: .leading, spacing: DPSpace.s2) {
                                Text(section.title).font(DPFont.caption.weight(.semibold)).foregroundStyle(DPColor.text2)
                                    .textCase(.uppercase)
                                    .accessibilityAddTraits(.isHeader)
                                FlowLayout(spacing: DPSpace.s2) {
                                    ForEach(section.chips) { chip in
                                        Button { trips.filter(.toggleDraft(chip.key)) } label: {
                                            ChipLabel(
                                                text: chip.label, count: chip.count, quality: chip.quality,
                                                systemImage: chip.isSelected ? "checkmark" : nil, isSelected: chip.isSelected)
                                        }
                                        .buttonStyle(.plain)
                                        .accessibilityLabel(chip.accessibilityLabel)
                                        .accessibilityAddTraits(chip.isSelected ? [.isButton, .isSelected] : .isButton)
                                    }
                                }
                            }
                        }
                    }
                    .padding(DPLayout.screenMargin)
                }
                .safeAreaInset(edge: .bottom) { footer(sheet) }
                .navigationTitle(sheet.title)
                .navigationSubtitle(sheet.subtitle)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Close", systemImage: "xmark") { trips.filter(.closeSheet) }
                    }
                }
            }
            .presentationDetents(typeSize.isAccessibilitySize ? [.large] : [.medium, .large])
        }
    }

    private func footer(_ sheet: HistoryFilterSheet) -> some View {
        HStack(spacing: DPSpace.s2) {
            Button(sheet.resetLabel) { trips.filter(.resetDraft) }
                .buttonStyle(.glass)
                .disabled(!sheet.canReset)
            Button { trips.filter(.apply) } label: {
                Text(sheet.showLabel).font(DPFont.button).frame(maxWidth: .infinity, minHeight: DPLayout.touchMin)
            }
            .buttonStyle(.glassProminent)
            .tint(DPColor.accent)
            .disabled(!sheet.canShow)
            .accessibilityIdentifier("drives.filters.show")
        }
        .padding(DPLayout.screenMargin)
    }
}

/// A capsule chip: selected chips use the accent container (components.md chip).
private struct ChipLabel: View {
    let text: String
    var count: Int?
    var quality: QualityBadge?
    var systemImage: String?
    var trailingSystemImage: String?
    let isSelected: Bool

    var body: some View {
        HStack(spacing: DPSpace.s1) {
            if let systemImage { Image(systemName: systemImage).font(DPFont.caption) }
            if let quality { DPQualityGlyph(quality.glyph, size: 12) }
            Text(text).font(DPFont.caption.weight(.semibold))
            if let count { Text("\(count)").font(DPFont.caption).foregroundStyle(DPColor.text3) }
            if let trailingSystemImage { Image(systemName: trailingSystemImage).font(DPFont.caption) }
        }
        .foregroundStyle(isSelected ? DPColor.accent : DPColor.text)
        .padding(.horizontal, DPSpace.s3)
        .frame(minHeight: DPLayout.touchMin)
        .background(isSelected ? DPColor.accentContainer : DPColor.fill, in: Capsule())
        .contentShape(Capsule())
    }
}

/// Chips that wrap onto as many rows as they need.
private struct FlowLayout: Layout {
    var spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = rows(for: subviews, width: proposal.width ?? .infinity)
        let height = rows.map(\.height).reduce(0, +) + spacing * CGFloat(max(rows.count - 1, 0))
        return CGSize(width: proposal.width ?? rows.map(\.width).max() ?? 0, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in rows(for: subviews, width: bounds.width) {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
            y += row.height + spacing
        }
    }

    private struct Row {
        var indices: [Int] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func rows(for subviews: Subviews, width: CGFloat) -> [Row] {
        var rows: [Row] = [Row()]
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            let needed = rows[rows.count - 1].indices.isEmpty ? size.width : rows[rows.count - 1].width + spacing + size.width
            if needed > width, !rows[rows.count - 1].indices.isEmpty {
                rows.append(Row())
            }
            var row = rows[rows.count - 1]
            row.width = row.indices.isEmpty ? size.width : row.width + spacing + size.width
            row.height = max(row.height, size.height)
            row.indices.append(index)
            rows[rows.count - 1] = row
        }
        return rows
    }
}
