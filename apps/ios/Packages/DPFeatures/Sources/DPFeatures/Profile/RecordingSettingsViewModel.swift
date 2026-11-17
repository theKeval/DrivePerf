import DPSharedBridge
import Foundation
import Observation

/// S-SET-03 Recording settings, reduced to the "Automatic trips" switch (F-SET-01.3, DRIVE-153). It writes the same
/// `autoTripsEnabled` choice onboarding sets (S-ONB-08) and asks the app to re-read it at once, so triggers, the core's
/// auto-detect flag, the watchdog and Home's status follow the switch. Default car, battery saver recording, Health
/// Check and Pause arrive with their own tickets.
@MainActor
@Observable
public final class RecordingSettingsViewModel {
    /// The sheet that explains why automatic trips need "Always" location while it is missing.
    public struct AlwaysExplainer: Equatable, Identifiable, Sendable {
        /// True while iOS can still show its one-time "Change to Always Allow" prompt: location is While Using and
        /// the prompt has not been shown before (F-ONB-04.3). Otherwise only Settings can grant it.
        public let canAsk: Bool
        public var id: String { canAsk ? "ask" : "settings" }
    }

    public private(set) var autoTripsOn: Bool
    public private(set) var permissions = PermissionSnapshot()
    public private(set) var isRequesting = false
    public var explainer: AlwaysExplainer?

    private let preferences: any PreferencesStoring
    private let permissionsPort: any PermissionRequesting
    private let refreshCapture: @MainActor () -> Void

    public init(
        preferences: any PreferencesStoring, permissions: any PermissionRequesting,
        refreshCapture: @escaping @MainActor () -> Void
    ) {
        self.preferences = preferences
        permissionsPort = permissions
        self.refreshCapture = refreshCapture
        autoTripsOn = preferences.preferences.autoTripsEnabled
    }

    /// What stops automatic trips right now; empty when the switch is on and nothing needs fixing.
    public var attentionReasons: [AttentionReason] { AutoDetectReadiness.reasons(for: permissions) }

    /// The status line under the switch, in S-HOME-01's words.
    public var statusText: String {
        guard autoTripsOn else { return "Manual mode" }
        return attentionReasons.isEmpty ? "Auto-detect on · ready" : "Auto-detect needs attention"
    }

    public var statusDetail: String? {
        guard autoTripsOn else { return nil }
        return attentionReasons.first?.shortText
    }

    /// Re-reads the saved choice and the permissions. Call when the screen opens and when the app returns from
    /// Settings, where the user may have granted "Always".
    public func refresh() async {
        reloadChoice()
        permissions = await permissionsPort.current()
    }

    /// Re-reads only the saved choice. The model outlives onboarding, which saves it too, so the app calls this when
    /// onboarding finishes and the status sheet calls it when it opens.
    public func reloadChoice() {
        autoTripsOn = preferences.preferences.autoTripsEnabled
    }

    /// The switch. Both directions save and refresh capture before anything else, so the app never lags behind it.
    public func setAutoTrips(_ on: Bool) async {
        autoTripsOn = on
        preferences.update { $0.autoTripsEnabled = on }
        refreshCapture()
        permissions = await permissionsPort.current()
        guard on else {
            explainer = nil
            return
        }
        // The choice stays on while "Always" is missing: the status says "needs attention" until it is granted.
        if permissions.location != .always {
            explainer = AlwaysExplainer(canAsk: canAskForAlways)
        }
    }

    /// The explainer's "Continue": the one-time iOS prompt, or Settings once it has been used (F-ONB-04.3).
    public func confirmAlways() async {
        guard let explainer else { return }
        if explainer.canAsk {
            isRequesting = true
            preferences.update { $0.alwaysPromptShown = true }
            permissions = await permissionsPort.requestLocationAlways()
            isRequesting = false
            self.explainer = nil
            refreshCapture()
        } else {
            self.explainer = nil
            permissionsPort.openSystemSettings()
        }
    }

    public func dismissExplainer() {
        explainer = nil
    }

    public func openSettings() {
        permissionsPort.openSystemSettings()
    }

    private var canAskForAlways: Bool {
        permissions.location == .whenInUse && !preferences.preferences.alwaysPromptShown
    }
}
