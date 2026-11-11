import Foundation

/// Usage statistics behind the user's choice (F-ONB-08, ADR-007). The shared core holds the consent gate: events wait in
/// memory until the user answers S-ONB-13, are sent if they allowed usage statistics and dropped if they did not. The
/// app target backs this with the Kotlin `Analytics` port; features only report the choice.
@MainActor
public protocol UsageAnalytics: AnyObject {
    /// The answer to "Share usage statistics" (S-ONB-13, S-PRV-07). On sends what was held; off drops it and stops
    /// collection at once, so a later change takes effect within the same session (F-ONB-08.3).
    func setUsageConsent(_ allowed: Bool)
    /// S-PRV-07 "Reset analytics ID": the vendor deletes the pseudonymous ID.
    func resetID()
}

/// For builds and tests with no analytics: records nothing, sends nothing.
@MainActor
public final class NoUsageAnalytics: UsageAnalytics {
    public init() {}
    public func setUsageConsent(_ allowed: Bool) {}
    public func resetID() {}
}
