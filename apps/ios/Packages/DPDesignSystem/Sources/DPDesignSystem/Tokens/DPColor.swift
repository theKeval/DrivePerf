import SwiftUI
import UIKit

/// Semantic iOS colours from `docs/design/system/tokens.json` (`color.semantic.ios`), "Night Drive" dark and "Day Drive"
/// light. Values are copied verbatim (RGBA hex); regenerate this file from the tokens rather than editing a value.
public enum DPColor {
    /// `bg`: dark #000000 · light #FFFFFF
    public static let bg = Color(dark: 0x0000_00FF, light: 0xFFFF_FFFF)
    /// `bgGrouped`: dark #080B10 · light #F2F3F6
    public static let bgGrouped = Color(dark: 0x080B_10FF, light: 0xF2F3_F6FF)
    /// `bgElevated`: dark #10151C · light #FFFFFF
    public static let bgElevated = Color(dark: 0x1015_1CFF, light: 0xFFFF_FFFF)
    /// `surface`: dark #131822 · light #FFFFFF
    public static let surface = Color(dark: 0x1318_22FF, light: 0xFFFF_FFFF)
    /// `surface2`: dark #1B222D · light #F2F3F6
    public static let surface2 = Color(dark: 0x1B22_2DFF, light: 0xF2F3_F6FF)
    /// `surface3`: dark #262E3B · light #E4E7EC
    public static let surface3 = Color(dark: 0x262E_3BFF, light: 0xE4E7_ECFF)
    /// `fill`: dark #7880961F · light #7880961F
    public static let fill = Color(dark: 0x7880_961F, light: 0x7880_961F)
    /// `fillStrong`: dark #7880963D · light #78809633
    public static let fillStrong = Color(dark: 0x7880_963D, light: 0x7880_9633)
    /// `text`: dark #F2F4F7 · light #0B0E13
    public static let text = Color(dark: 0xF2F4_F7FF, light: 0x0B0E_13FF)
    /// `text2`: dark #A3AAB6 · light #5A6271
    public static let text2 = Color(dark: 0xA3AA_B6FF, light: 0x5A62_71FF)
    /// `text3`: dark #6E7787 · light #8A919D
    public static let text3 = Color(dark: 0x6E77_87FF, light: 0x8A91_9DFF)
    /// `text4`: dark #3A4351 · light #C9CED6
    public static let text4 = Color(dark: 0x3A43_51FF, light: 0xC9CE_D6FF)
    /// `separator`: dark #96A0B429 · light #3C435024
    public static let separator = Color(dark: 0x96A0_B429, light: 0x3C43_5024)
    /// `outline`: dark #96A0B43D · light #3C435033
    public static let outline = Color(dark: 0x96A0_B43D, light: 0x3C43_5033)
    /// `accent`: dark #5BE7C4 · light #079777
    public static let accent = Color(dark: 0x5BE7_C4FF, light: 0x0797_77FF)
    /// `accentText`: dark #5BE7C4 · light #06775E
    public static let accentText = Color(dark: 0x5BE7_C4FF, light: 0x0677_5EFF)
    /// `onAccent`: dark #04241D · light #FFFFFF
    public static let onAccent = Color(dark: 0x0424_1DFF, light: 0xFFFF_FFFF)
    /// `accentContainer`: dark #0A4439 · light #C6F8EA
    public static let accentContainer = Color(dark: 0x0A44_39FF, light: 0xC6F8_EAFF)
    /// `onAccentContainer`: dark #91F1D8 · light #085C4A
    public static let onAccentContainer = Color(dark: 0x91F1_D8FF, light: 0x085C_4AFF)
    /// `record`: dark #FFC24B · light #9A6300
    public static let record = Color(dark: 0xFFC2_4BFF, light: 0x9A63_00FF)
    /// `onRecord`: dark #3D2700 · light #FFFFFF
    public static let onRecord = Color(dark: 0x3D27_00FF, light: 0xFFFF_FFFF)
    /// `recordContainer`: dark #3D2700 · light #FFF0CC
    public static let recordContainer = Color(dark: 0x3D27_00FF, light: 0xFFF0_CCFF)
    /// `positive`: dark #5BE7C4 · light #06775E
    public static let positive = Color(dark: 0x5BE7_C4FF, light: 0x0677_5EFF)
    /// `changeDown`: dark #A3AAB6 · light #5A6271
    public static let changeDown = Color(dark: 0xA3AA_B6FF, light: 0x5A62_71FF)
    /// `warning`: dark #F5B648 · light #9A5F00
    public static let warning = Color(dark: 0xF5B6_48FF, light: 0x9A5F_00FF)
    /// `warningContainer`: dark #3A2600 · light #FFF0CC
    public static let warningContainer = Color(dark: 0x3A26_00FF, light: 0xFFF0_CCFF)
    /// `critical`: dark #FF6B6B · light #C62828
    public static let critical = Color(dark: 0xFF6B_6BFF, light: 0xC628_28FF)
    /// `criticalContainer`: dark #3B0D0D · light #FDE7E7
    public static let criticalContainer = Color(dark: 0x3B0D_0DFF, light: 0xFDE7_E7FF)
    /// `live`: dark #5BE7C4 · light #079777
    public static let live = Color(dark: 0x5BE7_C4FF, light: 0x0797_77FF)
    /// `liveGlow`: dark #5BE7C459 · light #07977740
    public static let liveGlow = Color(dark: 0x5BE7_C459, light: 0x0797_7740)
    /// `scrim`: dark #000000A3 · light #0B0E1366
    public static let scrim = Color(dark: 0x0000_00A3, light: 0x0B0E_1366)
    /// `focus`: dark #5BE7C4 · light #079777
    public static let focus = Color(dark: 0x5BE7_C4FF, light: 0x0797_77FF)
    /// `glassStroke`: dark #FFFFFF24 · light #FFFFFF99
    public static let glassStroke = Color(dark: 0xFFFF_FF24, light: 0xFFFF_FF99)
    /// `driveBg`: dark #000000 · light #000000
    public static let driveBg = Color(dark: 0x0000_00FF, light: 0x0000_00FF)

    /// Garage colours (`color.car`). A car colour tints its Story and its chip; never used for data.
    public enum Car {
        public static let carmine = Color(rgba: 0xE548_4DFF)
        public static let ember = Color(rgba: 0xF270_3CFF)
        public static let sodium = Color(rgba: 0xF5B2_3DFF)
        public static let lime = Color(rgba: 0xA6D8_4AFF)
        public static let glide = Color(rgba: 0x5BE7_C4FF)
        public static let glacier = Color(rgba: 0x7FC8_E8FF)
        public static let lucid = Color(rgba: 0x7FA7_D9FF)
        public static let cobalt = Color(rgba: 0x4D6B_FEFF)
        public static let violet = Color(rgba: 0x9C7C_F2FF)
        public static let graphite = Color(rgba: 0x8C95_A3FF)
        public static let pearl = Color(rgba: 0xE8E6_E1FF)
        public static let onyx = Color(rgba: 0x2A2E_35FF)
    }

    /// Data colours (`color.data`): fixed per theme, never tinted.
    public enum Data {
        public static let qualityGood = Color(dark: 0x5FDB_9CFF, light: 0x1E9E_62FF)
        public static let qualityUsable = Color(dark: 0x8FB8_FFFF, light: 0x2F5D_B8FF)
        public static let qualityLimited = Color(dark: 0xF5B6_48FF, light: 0x9A5F_00FF)
        public static let qualityUnscorable = Color(dark: 0x8C95_A3FF, light: 0x7A83_92FF)
        /// `--dp-data-gap-stroke`: GPS gaps and interrupted stretches.
        public static let gapStroke = Color(dark: 0x7A83_92FF, light: 0x7A83_92FF)
        public static let speed = Color(dark: 0xD6DC_E4FF, light: 0x404A_58FF)
    }
}

extension Color {
    /// A colour that follows the trait collection's light/dark style.
    public init(dark: UInt32, light: UInt32) {
        self.init(uiColor: UIColor { traits in
            UIColor(rgba: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }

    public init(rgba: UInt32) {
        self.init(uiColor: UIColor(rgba: rgba))
    }
}

extension UIColor {
    convenience init(rgba: UInt32) {
        self.init(
            red: CGFloat((rgba >> 24) & 0xFF) / 255,
            green: CGFloat((rgba >> 16) & 0xFF) / 255,
            blue: CGFloat((rgba >> 8) & 0xFF) / 255,
            alpha: CGFloat(rgba & 0xFF) / 255)
    }
}
