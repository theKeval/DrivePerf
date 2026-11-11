import DPDesignSystem
import DPSharedBridge
import SwiftUI

/// S-DQ-01 Data-quality detail sheet (F-DQ-04 AC2): how much to trust a drive's numbers. Header, one honest
/// sentence, the estimated-start note, the coverage strip, the details with provenance, and the provenance legend.
struct DataQualitySheet: View {
    let detail: DataQualityDetail
    @Environment(\.dismiss) private var dismiss
    @State private var shownProvenance: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: DPLayout.sectionGap) {
                    hero
                    if let note = detail.estimatedStart {
                        Text(note).font(DPFont.bodySecondary).foregroundStyle(DPColor.text)
                            .padding(DPSpace.s3)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(DPColor.fill, in: RoundedRectangle(cornerRadius: DPRadius.md))
                    }
                    section("This drive") {
                        VStack(alignment: .leading, spacing: DPSpace.s2) {
                            CoverageStrip(spans: detail.coverage)
                                .accessibilityElement()
                                .accessibilityLabel(detail.coverageAccessibility)
                            CoverageLegend()
                            Text(detail.scoredLine).font(DPFont.meta).foregroundStyle(DPColor.text2)
                        }
                        .padding(DPSpace.s4)
                        .dpCard()
                    }
                    section("Details") {
                        VStack(spacing: 0) {
                            ForEach(Array(detail.rows.enumerated()), id: \.element.id) { index, row in
                                if index > 0 {
                                    Rectangle().fill(DPColor.separator).frame(height: 0.5).padding(.leading, DPSpace.s4)
                                }
                                detailRow(row)
                            }
                        }
                        .dpCard()
                    }
                    section("What the labels mean") {
                        VStack(alignment: .leading, spacing: DPSpace.s3) {
                            ForEach(detail.legend, id: \.0) { provenance, line in
                                HStack(alignment: .firstTextBaseline, spacing: DPSpace.s2) {
                                    DPProvenanceGlyph(provenance.glyph).foregroundStyle(DPColor.text2)
                                    Text("**\(provenance.label)** · \(line)").font(DPFont.bodySecondary)
                                        .foregroundStyle(DPColor.text)
                                }
                                .accessibilityElement(children: .combine)
                            }
                        }
                        .padding(DPSpace.s4)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .dpCard()
                    }
                }
                .padding(.horizontal, DPLayout.screenMargin)
                .padding(.vertical, DPSpace.s2)
            }
            .background(DPColor.bgElevated)
            .navigationTitle(detail.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    DPQualityGlyph(detail.badge.glyph, size: 24).accessibilityHidden(true)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { dismiss() } label: { DPIcon.close.image }.accessibilityLabel("Close")
                }
            }
        }
        .presentationDetents([.large])
    }

    private var hero: some View {
        HStack(alignment: .top, spacing: DPSpace.s3) {
            DPQualityGlyph(detail.badge.glyph, size: 34)
                .frame(width: 56, height: 56)
                .background(DPQualityGlyph.color(detail.badge.glyph).opacity(0.14), in: Circle())
            Text(detail.hero).font(DPFont.body).foregroundStyle(DPColor.text)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(DPSpace.s4)
        .frame(maxWidth: .infinity, alignment: .leading)
        .dpCard()
        .accessibilityElement(children: .combine)
    }

    private func section(_ title: String, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: DPSpace.s2) {
            Text(title).font(DPFont.sectionTitle).foregroundStyle(DPColor.text).accessibilityAddTraits(.isHeader)
            content()
        }
    }

    /// Title, then value with its provenance glyph and word; warn tone when it lowered the badge. Long-press shows
    /// the meaning (F-DQ-05).
    private func detailRow(_ row: ProvenancedValue) -> some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .firstTextBaseline) {
                Text(row.label).font(DPFont.body).foregroundStyle(DPColor.text)
                Spacer(minLength: DPSpace.s3)
                rowValue(row, alignment: .trailing)
            }
            VStack(alignment: .leading, spacing: DPSpace.s1) {
                Text(row.label).font(DPFont.body).foregroundStyle(DPColor.text)
                rowValue(row, alignment: .leading)
            }
        }
        .padding(.horizontal, DPSpace.s4)
        .padding(.vertical, DPSpace.s3)
        .frame(minHeight: DPLayout.listRowMin)
        .contentShape(Rectangle())
        .onLongPressGesture { shownProvenance = row.id }
        .popover(isPresented: Binding(get: { shownProvenance == row.id }, set: { shownProvenance = $0 ? row.id : nil })) {
            ProvenancePopover(value: row).presentationCompactAdaptation(.popover)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(row.accessibilityLabel + (row.note.map { " \($0)." } ?? ""))
        .accessibilityAction(named: "Show how this was measured") { shownProvenance = row.id }
    }

    private func rowValue(_ row: ProvenancedValue, alignment: HorizontalAlignment) -> some View {
        VStack(alignment: alignment, spacing: 2) {
            HStack(spacing: DPSpace.s1) {
                Text(([row.isEstimated ? DPEstimated.text(row.value) : row.value, row.unit].filter { !$0.isEmpty })
                    .joined(separator: " "))
                    .font(DPFont.body.weight(.semibold))
                    .foregroundStyle(row.lowersBadge ? DPColor.warning : DPColor.text)
                    .dpEstimated(row.isEstimated)
                DPProvenanceGlyph(row.provenance.glyph).foregroundStyle(DPColor.text3)
                Text(row.provenance.label).font(DPFont.caption).foregroundStyle(DPColor.text3)
            }
            if let note = row.note {
                Text(note).font(DPFont.caption).foregroundStyle(DPColor.text2)
            }
        }
    }
}

/// The S-DQ-01 coverage strip (14 tall, radius 7): measured solid, estimated dashed, GPS gaps outlined, left out
/// hatched, interrupted as an empty gap. Patterns carry the meaning, not colour alone.
struct CoverageStrip: View {
    let spans: [CoverageSpan]

    var body: some View {
        Canvas { context, size in
            let h = size.height
            let track = Path(roundedRect: CGRect(origin: .zero, size: size), cornerRadius: h / 2)
            context.fill(track, with: .color(DPColor.accent))
            context.clip(to: track)
            for span in spans {
                let rect = CGRect(x: span.from * size.width, y: 0, width: (span.to - span.from) * size.width, height: h)
                switch span.style {
                case .estimatedStart, .shortGap:
                    context.fill(Path(rect), with: .color(DPColor.surface2))
                    var line = Path()
                    line.move(to: CGPoint(x: rect.minX, y: h / 2))
                    line.addLine(to: CGPoint(x: rect.maxX, y: h / 2))
                    context.stroke(
                        line, with: .color(DPColor.accent.opacity(DPEstimated.opacity)), style: DPEstimated.stroke(lineWidth: h * 0.5))
                case .longGap, .interrupted, .paused:
                    context.fill(Path(rect), with: .color(DPColor.surface2))
                    context.stroke(Path(rect.insetBy(dx: 0.75, dy: 0.75)), with: .color(DPColor.Data.gapStroke), lineWidth: 1.5)
                case .excluded:
                    context.fill(Path(rect), with: .color(DPColor.surface3))
                    var hatch = Path()
                    var x = rect.minX - h
                    while x < rect.maxX {
                        hatch.move(to: CGPoint(x: x, y: h))
                        hatch.addLine(to: CGPoint(x: x + h, y: 0))
                        x += 5
                    }
                    context.stroke(hatch, with: .color(DPColor.text3), lineWidth: 1)
                }
            }
        }
        .frame(height: 14)
    }
}

private struct CoverageLegend: View {
    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: DPSpace.s3) { items }
            VStack(alignment: .leading, spacing: DPSpace.s1) { items }
        }
        .font(DPFont.caption)
        .foregroundStyle(DPColor.text2)
        .accessibilityHidden(true)
    }

    @ViewBuilder private var items: some View {
        item(CoverageSpan(style: .shortGap, from: 0, to: 1), "Measured", solid: true)
        item(CoverageSpan(style: .estimatedStart, from: 0, to: 1), "Estimated")
        item(CoverageSpan(style: .longGap, from: 0, to: 1), "No GPS")
        item(CoverageSpan(style: .excluded, from: 0, to: 1), "Left out")
    }

    private func item(_ span: CoverageSpan, _ label: String, solid: Bool = false) -> some View {
        HStack(spacing: DPSpace.s1) {
            CoverageStrip(spans: solid ? [] : [span]).frame(width: 22)
            Text(label)
        }
    }
}
