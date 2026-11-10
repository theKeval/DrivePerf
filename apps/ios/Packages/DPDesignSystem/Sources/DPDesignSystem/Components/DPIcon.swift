import SwiftUI

/// UI glyphs copied from `docs/design/DRIVE-*/ios/assets/icon-*.svg` (template images, so they take the
/// foreground style). Never redraw these; add the SVG to `Resources/Icons.xcassets` instead.
public enum DPIcon: String, CaseIterable, Sendable {
    case drives, routes, insights, garage, profile
    case record, stop, passenger, gps, check, close, bolt, lock, shield, story, pause
    case chevronRight, chevronLeft, sparkle, gear, battery, minus, info, car

    public var image: Image {
        Image("dp.\(rawValue)", bundle: .module)
    }
}

extension Image {
    public init(dp icon: DPIcon) {
        self = icon.image
    }
}
