import SwiftUI

/// Onboarding motion rules (F-ONB-01.4, DRIVE-54): with Reduce Motion on, motion is replaced by a 220 ms cross-fade
/// and toggles snap. Pure, so the rule is unit-tested.
enum OnboardingMotion {
    /// The cross-fade every onboarding page uses under Reduce Motion (design specs: "RM: 220 ms cross-fade").
    static let crossFadeDuration = 0.22

    /// How the page changes when `step` changes.
    static func pageAnimation(reduceMotion: Bool) -> Animation {
        reduceMotion ? .easeInOut(duration: crossFadeDuration) : .smooth(duration: crossFadeDuration)
    }

    /// Pages cross-fade (opacity only) in both modes; no page travels, so there is nothing to remove for Reduce Motion
    /// except the easing above.
    static var pageTransition: AnyTransition { .opacity }

    /// Toggles use `spring.fast`; under Reduce Motion they snap (no animation at all).
    static func toggleAnimation(reduceMotion: Bool) -> Animation? {
        reduceMotion ? nil : .snappy(duration: 0.18)
    }
}

extension View {
    /// Applies `OnboardingMotion.toggleAnimation` to a toggle's state change.
    func onboardingToggleMotion(_ value: some Equatable, reduceMotion: Bool) -> some View {
        animation(OnboardingMotion.toggleAnimation(reduceMotion: reduceMotion), value: value)
    }
}
