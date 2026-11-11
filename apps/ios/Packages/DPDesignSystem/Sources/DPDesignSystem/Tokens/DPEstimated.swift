import SwiftUI

/// The "estimated" token (A6, F-DQ-05 AC2): estimated values are visibly different everywhere. Inline numbers get a
/// "≈" prefix at 60 % opacity (`.dp-est`); lines and strips are dashed 4/4 at 60 % (`--dp-est-dash`,
/// `--dp-data-estimated-opacity`).
public enum DPEstimated {
    /// `--dp-est-opacity` / `--dp-data-estimated-opacity`.
    public static let opacity: Double = 0.6
    /// `--dp-est-dash`: 4 on, 4 off.
    public static let dash: [CGFloat] = [4, 4]
    /// "≈" and a thin space, before an estimated number.
    public static let prefix = "\u{2248}\u{2009}"

    /// [text] marked as estimated: "≈ 0.4".
    public static func text(_ text: String) -> String { prefix + text }

    /// The stroke for estimated lines.
    public static func stroke(lineWidth: CGFloat) -> StrokeStyle {
        StrokeStyle(lineWidth: lineWidth, lineCap: .butt, dash: dash)
    }
}

extension View {
    /// Styles an estimated value (60 % opacity). Pair it with `DPEstimated.text` for the "≈" prefix.
    public func dpEstimated(_ isEstimated: Bool = true) -> some View {
        opacity(isEstimated ? DPEstimated.opacity : 1)
    }
}
