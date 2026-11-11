import CoreGraphics

/// Spacing scale (`space` in tokens.json). 1 pt = 1 px in the prototypes.
public enum DPSpace {
    public static let s0_5: CGFloat = 2
    public static let s1: CGFloat = 4
    public static let s1_5: CGFloat = 6
    public static let s2: CGFloat = 8
    public static let s3: CGFloat = 12
    public static let s4: CGFloat = 16
    public static let s5: CGFloat = 20
    public static let s6: CGFloat = 24
    public static let s7: CGFloat = 28
    public static let s8: CGFloat = 32
    public static let s10: CGFloat = 40
    public static let s12: CGFloat = 48
    public static let s14: CGFloat = 56
    public static let s16: CGFloat = 64
}

/// iOS layout constants (`layout.ios`).
public enum DPLayout {
    public static let screenMargin: CGFloat = 16
    public static let screenMarginWide: CGFloat = 20
    public static let cardGap: CGFloat = 12
    public static let sectionGap: CGFloat = 28
    public static let listRowMin: CGFloat = 52
    public static let touchMin: CGFloat = 44
    /// Minimum target while a driver trip is active (accessibility.md §7).
    public static let touchDrive: CGFloat = 64
    /// The in-drive Stop and passenger buttons (S-DRV-01).
    public static let driveButton: CGFloat = 96
    public static let toolbarButton: CGFloat = 44
    public static let avatar: CGFloat = 38
    public static let primaryButtonHeight: CGFloat = 50
}

/// iOS corner radii (`radius.ios`). Floating controls are capsules.
public enum DPRadius {
    public static let xs: CGFloat = 6
    public static let sm: CGFloat = 10
    public static let md: CGFloat = 14
    public static let lg: CGFloat = 20
    public static let card: CGFloat = 24
    public static let cardHero: CGFloat = 32
    public static let sheet: CGFloat = 38
    public static let field: CGFloat = 14
    public static let icon: CGFloat = 13
    /// S-DRV-01 Stop / I'm a passenger.
    public static let driveButton: CGFloat = 28
}
