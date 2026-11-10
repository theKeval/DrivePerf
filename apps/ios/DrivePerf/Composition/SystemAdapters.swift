import DPPlatform
import DPSharedBridge
import Foundation
import UIKit

/// `PreferencesStoring` in `UserDefaults`. Choices only, no account or location data.
@MainActor
final class UserDefaultsPreferences: PreferencesStoring {
    private static let key = "dp.preferences.v1"
    private let defaults: UserDefaults
    private(set) var preferences: AppPreferences

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let data = defaults.data(forKey: Self.key),
            let stored = try? JSONDecoder().decode(AppPreferences.self, from: data)
        {
            preferences = stored
        } else {
            preferences = AppPreferences()
        }
        if preferences.installedAt == nil { update { $0.installedAt = Date() } }
    }

    func update(_ change: (inout AppPreferences) -> Void) {
        change(&preferences)
        if let data = try? JSONEncoder().encode(preferences) { defaults.set(data, forKey: Self.key) }
    }
}

@MainActor
final class NotificationAdapter: TripNotifying {
    private let notifier = LocalNotifier()

    func postAutoTripRecording(tripId: String) { notifier.postAutoTripRecording(tripId: tripId) }
    func removeTripNotifications(tripId: String) { notifier.removeTripNotifications(tripId: tripId) }
}

/// Keeps a background wake alive until the first fix (Spike 1: GPS and IMU start within 0.25 s of the wake).
@MainActor
final class UIKitBackgroundAssertions: BackgroundAssertions {
    private var identifier: UIBackgroundTaskIdentifier = .invalid

    func begin() {
        guard identifier == .invalid else { return }
        identifier = UIApplication.shared.beginBackgroundTask(withName: "capture-wake") { [weak self] in
            MainActor.assumeIsolated { self?.end() }
        }
    }

    func end() {
        guard identifier != .invalid else { return }
        UIApplication.shared.endBackgroundTask(identifier)
        identifier = .invalid
    }
}

extension BuildFlags {
    /// Read from Info.plist keys set by build settings (`DP_FEATURE_*` in Config/DrivePerf.xcconfig). All off
    /// until the Apple Developer account exists (DRIVE-42).
    static func fromBundle(_ bundle: Bundle = .main) -> BuildFlags {
        func flag(_ key: String) -> Bool { (bundle.object(forInfoDictionaryKey: key) as? String) == "YES" }
        return BuildFlags(
            signInWithApple: flag("DPFeatureSignInWithApple"),
            pushNotifications: flag("DPFeaturePush"),
            testFlight: flag("DPFeatureTestFlight"))
    }
}
