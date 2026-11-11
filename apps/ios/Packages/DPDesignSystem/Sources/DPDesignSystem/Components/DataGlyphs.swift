import SwiftUI

// Meaning-carrying glyphs (components.md §10): their shapes are fixed across platforms and must never be swapped for
// SF Symbols. Geometry is `DP_ICONS.quality` (16 × 16) and `DP_ICONS.provenance` (12 × 12) in
// `prototypes/shared/icons.js`, scaled to the frame.

/// The data-quality badge glyph: Good = filled disc + check · Usable = half disc · Limited = triangle "!" ·
/// Unscorable = slashed ring.
public struct DPQualityGlyph: View {
    public enum Kind: Sendable, CaseIterable { case good, usable, limited, unscorable }

    private let kind: Kind
    private let size: CGFloat

    public init(_ kind: Kind, size: CGFloat = 16) {
        self.kind = kind
        self.size = size
    }

    public static func color(_ kind: Kind) -> Color {
        switch kind {
        case .good: DPColor.Data.qualityGood
        case .usable: DPColor.Data.qualityUsable
        case .limited: DPColor.Data.qualityLimited
        case .unscorable: DPColor.Data.qualityUnscorable
        }
    }

    public var body: some View {
        Canvas { context, canvas in
            let k = canvas.width / 16
            let c = Self.color(kind)
            let ink = DPColor.bgGrouped
            func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * k, y: y * k) }
            func circle(_ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: (8 - r) * k, y: (8 - r) * k, width: 2 * r * k, height: 2 * r * k)) }
            switch kind {
            case .good:
                context.fill(circle(7), with: .color(c))
                var check = Path()
                check.move(to: p(4.8, 8.2))
                check.addLine(to: p(6.9, 10.3))
                check.addLine(to: p(11.2, 5.9))
                context.stroke(check, with: .color(ink), style: StrokeStyle(lineWidth: 1.8 * k, lineCap: .round, lineJoin: .round))
            case .usable:
                context.stroke(circle(6.2), with: .color(c), lineWidth: 1.6 * k)
                var half = Path()
                half.addArc(center: p(8, 8), radius: 6.2 * k, startAngle: .degrees(-90), endAngle: .degrees(90), clockwise: false)
                half.closeSubpath()
                context.fill(half, with: .color(c))
            case .limited:
                var tri = Path()
                tri.move(to: p(8, 1.6))
                tri.addLine(to: p(14.8, 13.6))
                tri.addLine(to: p(1.2, 13.6))
                tri.closeSubpath()
                context.fill(tri, with: .color(c))
                context.stroke(tri, with: .color(c), style: StrokeStyle(lineWidth: 1.2 * k, lineJoin: .round))
                var bar = Path()
                bar.move(to: p(8, 6))
                bar.addLine(to: p(8, 9.6))
                context.stroke(bar, with: .color(ink), style: StrokeStyle(lineWidth: 1.7 * k, lineCap: .round))
                context.fill(Path(ellipseIn: CGRect(x: 7 * k, y: 10.6 * k, width: 2 * k, height: 2 * k)), with: .color(ink))
            case .unscorable:
                context.stroke(circle(6.2), with: .color(c), lineWidth: 1.6 * k)
                var slash = Path()
                slash.move(to: p(3.6, 12.4))
                slash.addLine(to: p(12.4, 3.6))
                context.stroke(slash, with: .color(c), style: StrokeStyle(lineWidth: 1.6 * k, lineCap: .round))
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

/// The provenance glyph: Observed = filled dot · Derived = ring with dot · Estimated = dashed ring. Takes the
/// foreground colour.
public struct DPProvenanceGlyph: View {
    public enum Kind: Sendable, CaseIterable { case observed, derived, estimated }

    private let kind: Kind
    private let size: CGFloat

    public init(_ kind: Kind, size: CGFloat = 12) {
        self.kind = kind
        self.size = size
    }

    public var body: some View {
        Canvas { context, canvas in
            let k = canvas.width / 12
            func circle(_ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: (6 - r) * k, y: (6 - r) * k, width: 2 * r * k, height: 2 * r * k)) }
            let ink = GraphicsContext.Shading.foreground
            switch kind {
            case .observed:
                context.fill(circle(4), with: ink)
            case .derived:
                context.stroke(circle(4.2), with: ink, lineWidth: 1.4 * k)
                context.fill(circle(1.6), with: ink)
            case .estimated:
                context.stroke(circle(4.2), with: ink, style: StrokeStyle(lineWidth: 1.4 * k, dash: [2 * k, 1.8 * k]))
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}
