import DPDesignSystem
import DPSharedBridge
import SwiftUI

/// The S-ANL-01 data colours that `DPColor.Data` does not have yet, from `prototypes/shared/tokens.css`
/// (`--dp-data-long-g`, `-lat-g`, `-elevation`, `-jerk`, `-grid`, `-axis`, `-gg-*`). Kept in the feature so the frozen
/// token files stay untouched in Sprint 4 wave 1.
enum AnalysisPalette {
    static let longG = Color(dark: 0x5AB8_F5FF, light: 0x0A6A_B8FF)
    static let latG = Color(dark: 0xF5A1_42FF, light: 0xB25A_00FF)
    static let elevation = Color(dark: 0xB8A4_8AFF, light: 0x8A76_60FF)
    static let elevationFill = Color(dark: 0xB8A4_8A33, light: 0x8A76_6033)
    static let jerk = Color(dark: 0xC9CE_D6FF, light: 0x3A43_51FF)
    static let grid = Color(dark: 0xFFFF_FF14, light: 0x0B0E_1312)
    static let axis = Color(dark: 0xFFFF_FF33, light: 0x0B0E_1333)
    static let ggCloud = Color(dark: 0x5BE7_C4FF, light: 0x0797_77FF)
    static let ggEnvelope = Color(dark: 0x5BE7_C4B3, light: 0x0677_5EB3)
    static let ggDot = Color(dark: 0xFFFF_FFFF, light: 0x0B0E_13FF)
    static let ggRing = Color(dark: 0xFFFF_FF1F, light: 0x0B0E_131A)

    static func line(_ channel: AnalysisChannelKind) -> Color {
        switch channel {
        case .speed: DPColor.Data.speed
        case .lonG: longG
        case .latG: latG
        case .elevation: elevation
        case .jerk: jerk
        case .quality: DPColor.Data.qualityGood
        }
    }

    static func legend(_ kind: AnalysisLegendKind) -> Color {
        switch kind {
        case .speed: DPColor.Data.speed
        case .lonG: longG
        case .latG: latG
        case .elevation: elevation
        case .jerk: jerk
        case .estimated: DPColor.Data.speed.opacity(0.6)
        case .noGps: DPColor.Data.gapStroke
        case .notScored: DPColor.Data.excludedHatch
        }
    }

    static func glyph(_ type: DrivingEventType) -> DPEventGlyph.Kind {
        switch type {
        case .brake: .brake
        case .accel: .accel
        case .corner: .corner
        }
    }

    static func event(_ type: DrivingEventType) -> Color { DPEventGlyph.color(glyph(type)) }
}

/// The event mark drawn inside a `Canvas`: square braking, circle acceleration, diamond cornering; hollow when needed.
extension GraphicsContext {
    func eventMark(_ type: DrivingEventType, needed: Bool, at c: CGPoint, size: CGFloat, surface: Color) {
        let r = size / 2
        let path: Path
        switch type {
        case .brake:
            path = Path(roundedRect: CGRect(x: c.x - r * 0.82, y: c.y - r * 0.82, width: r * 1.64, height: r * 1.64), cornerRadius: r * 0.3)
        case .accel:
            path = Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: size, height: size))
        case .corner:
            path = Path { p in
                p.move(to: CGPoint(x: c.x, y: c.y - r))
                p.addLine(to: CGPoint(x: c.x + r, y: c.y))
                p.addLine(to: CGPoint(x: c.x, y: c.y + r))
                p.addLine(to: CGPoint(x: c.x - r, y: c.y))
                p.closeSubpath()
            }
        }
        let color = AnalysisPalette.event(type)
        fill(path, with: .color(needed ? surface : color))
        stroke(path, with: .color(color), lineWidth: max(1, size * 0.14))
    }
}
