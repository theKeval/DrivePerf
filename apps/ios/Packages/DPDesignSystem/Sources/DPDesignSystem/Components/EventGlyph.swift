import SwiftUI

// The event pin (components.md §9.1): its shape carries the type, so colour is never the only signal. Geometry is
// `DP_ICONS.pin` in `prototypes/shared/icons.js`: a 28 × 28 box in a 30 × 30 view box (-1 … 29), scaled to the frame.

/// Hard braking = rounded square · hard acceleration = circle · hard cornering = diamond. `needed` (F-SCO-05) draws it
/// hollow: no fill, an event-colour outline and glyph. 28 pt on the map, 16 pt in chips, 14 pt on the timeline. The
/// glyph is decorative: callers say the type (and "marked as needed") in words.
public struct DPEventGlyph: View {
    public enum Kind: Sendable, CaseIterable { case brake, accel, corner }

    private let kind: Kind
    private let needed: Bool
    private let size: CGFloat

    public init(_ kind: Kind, needed: Bool = false, size: CGFloat = 28) {
        self.kind = kind
        self.needed = needed
        self.size = size
    }

    public static func color(_ kind: Kind) -> Color {
        switch kind {
        case .brake: DPColor.Data.eventBrake
        case .accel: DPColor.Data.eventAccel
        case .corner: DPColor.Data.eventCorner
        }
    }

    public var body: some View {
        Canvas { context, canvas in
            let k = canvas.width / 30
            let c = Self.color(kind)
            func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: (x + 1) * k, y: (y + 1) * k) }
            let shape = Self.shape(kind, p: p, k: k)
            context.fill(shape, with: .color(needed ? DPColor.surface : c))
            context.stroke(
                shape, with: .color(needed ? c : DPColor.Data.pinRing),
                style: StrokeStyle(lineWidth: (needed ? 2.2 : 1.8) * k, lineJoin: .round))
            context.stroke(
                Self.mark(kind, p: p), with: .color(needed ? c : DPColor.Data.pinGlyph),
                style: StrokeStyle(lineWidth: 2.4 * k, lineCap: .round, lineJoin: .round))
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }

    private static func shape(_ kind: Kind, p: (CGFloat, CGFloat) -> CGPoint, k: CGFloat) -> Path {
        switch kind {
        case .brake:
            Path(roundedRect: CGRect(origin: p(3, 3), size: CGSize(width: 22 * k, height: 22 * k)), cornerRadius: 6.2 * k)
        case .accel:
            Path(ellipseIn: CGRect(origin: p(2.5, 2.5), size: CGSize(width: 23 * k, height: 23 * k)))
        case .corner:
            Path { path in
                path.move(to: p(14, 1.6))
                path.addLine(to: p(24.6, 12.2))
                path.addQuadCurve(to: p(24.6, 15.8), control: p(25.5, 14))
                path.addLine(to: p(14, 26.4))
                path.addLine(to: p(3.4, 15.8))
                path.addQuadCurve(to: p(3.4, 12.2), control: p(2.5, 14))
                path.closeSubpath()
            }
        }
    }

    private static func mark(_ kind: Kind, p: (CGFloat, CGFloat) -> CGPoint) -> Path {
        Path { path in
            switch kind {
            case .brake:
                path.move(to: p(9, 10.2))
                path.addLine(to: p(14, 14.8))
                path.addLine(to: p(19, 10.2))
                path.move(to: p(9, 18.6))
                path.addLine(to: p(19, 18.6))
            case .accel:
                path.move(to: p(9, 15.4))
                path.addLine(to: p(14, 10.8))
                path.addLine(to: p(19, 15.4))
                path.move(to: p(9, 9))
                path.addLine(to: p(19, 9))
            case .corner:
                path.move(to: p(9.5, 18.5))
                path.addCurve(to: p(17, 10), control1: p(9.5, 13.5), control2: p(12.1, 10))
                path.move(to: p(14.5, 7.4))
                path.addLine(to: p(17.4, 10))
                path.addLine(to: p(14.5, 12.6))
            }
        }
    }
}
