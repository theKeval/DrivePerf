import SwiftUI

/// Type roles (`type.roles`, iOS column) mapped to HIG text styles, so Dynamic Type scales them.
public enum DPFont {
    public static let screenTitle = Font.largeTitle.bold()
    public static let sheetTitle = Font.title2.bold()
    public static let sectionTitle = Font.title3.weight(.semibold)
    public static let cardTitle = Font.headline
    public static let body = Font.body
    public static let bodySecondary = Font.subheadline
    public static let meta = Font.footnote
    public static let caption = Font.caption
    public static let label = Font.subheadline
    public static let button = Font.headline
    public static let storyHeadline = Font.title.bold()
    /// S-ONB-01 lead: 40/44 bold display.
    public static let welcomeLead = Font.system(size: 40, weight: .bold, design: .default)
    /// S-ONB-02..15 page titles: 30/36 bold.
    public static let onboardingTitle = Font.system(size: 30, weight: .bold)
}

/// "DP Numerals" (`type.numeric`): SF Pro Expanded Semibold with tabular, lining figures.
public enum DPNumeralSize: Sendable {
    case drive, hero, xl, l, m, s, xs

    /// Point size at the default (Large) content size.
    public var points: CGFloat {
        switch self {
        case .drive: 96
        case .hero: 72
        case .xl: 48
        case .l: 34
        case .m: 24
        case .s: 17
        case .xs: 13
        }
    }

    var textStyle: Font.TextStyle {
        switch self {
        case .drive, .hero, .xl, .l: .largeTitle
        case .m: .title2
        case .s: .headline
        case .xs: .footnote
        }
    }
}

private struct DPNumeralModifier: ViewModifier {
    @ScaledMetric private var size: CGFloat

    init(_ numeral: DPNumeralSize) {
        _size = ScaledMetric(wrappedValue: numeral.points, relativeTo: numeral.textStyle)
    }

    func body(content: Content) -> some View {
        content
            .font(.system(size: size, weight: .semibold).width(.expanded))
            .monospacedDigit()
    }
}

extension View {
    /// Sets DP Numerals at the given size; it scales with Dynamic Type.
    public func dpNumeral(_ size: DPNumeralSize) -> some View {
        modifier(DPNumeralModifier(size))
    }
}
