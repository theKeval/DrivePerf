import SwiftUI

/// The Loop (brand.md §3.1), built from the Icon Composer layers `icon-ios-layer-1-trail.svg` and
/// `icon-ios-layer-2-dot.svg`. As a loader (S-DRV-04) it turns once every 1.6 s; with Reduce Motion it stays still.
public struct LoopMark: View {
    private let size: CGFloat
    private let spinning: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    public init(size: CGFloat, spinning: Bool = false) {
        self.size = size
        self.spinning = spinning
    }

    public var body: some View {
        TimelineView(.animation(paused: !spinning || reduceMotion)) { context in
            let turns = context.date.timeIntervalSinceReferenceDate / 1.6
            ZStack {
                Image("dp.loop.trail", bundle: .module).resizable()
                Image("dp.loop.dot", bundle: .module).resizable()
            }
            .rotationEffect(.degrees(spinning && !reduceMotion ? turns.truncatingRemainder(dividingBy: 1) * 360 : 0))
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

/// Single-colour Loop for small glyphs (Live Activity leading glyph, avatars, watermarks).
public struct LoopGlyph: View {
    private let size: CGFloat

    public init(size: CGFloat) {
        self.size = size
    }

    public var body: some View {
        Image("dp.loop.mono", bundle: .module)
            .resizable()
            .renderingMode(.template)
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}
