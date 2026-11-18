import DPSharedBridge
import Foundation
import Observation

/// S-SET-03 Recording settings, reduced to the "Automatic trips" switch (F-SET-01.3, DRIVE-153). It writes the same
/// `autoTripsEnabled` choice onboarding sets (S-ONB-08) and asks the app to re-read it at once, so triggers, the core's
/// auto-detect flag, the watchdog and Home's status follow the switch. It also offers Pause auto-detect (F-CAP-15).
/// Default car, battery saver recording and Health Check arrive with their own tickets.
@MainActor
@Observable
public final class RecordingSettingsViewModel {
    public let display: DisplaySettingsModel
    public let capture: CaptureViewModel?
    public var onGarage: () -> Void = {}

    /// The sheet that explains what automatic trips need from location while "Always" is missing. One sheet, whose
    /// content moves through the steps, so it never flickers between them.
    public enum AlwaysExplainer: Equatable, Identifiable, Sendable {
        /// Location was never asked (onboarding skipped it): iOS lists no location options in Settings until the app
        /// has asked once, so ask for While Using first (S-ONB-06).
        case initialAccess
        /// While Using is granted and iOS can still show its one-time "Change to Always Allow" prompt (F-ONB-04.3).
        case alwaysUpgrade
        /// Only Settings can grant it: location denied or restricted, or the one-time prompt was already used.
        case settings

        public var id: String { "location-access" }
    }

    public private(set) var autoTripsOn: Bool
    public private(set) var permissions = PermissionSnapshot()
    public private(set) var isRequesting = false
    public var explainer: AlwaysExplainer?

    /// S-HOME-03 Pause auto-detect (F-CAP-15); nil hides the row (tests that only cover the switch).
    public let pause: PauseAutoDetectViewModel?

    private let preferences: any PreferencesStoring
    private let permissionsPort: any PermissionRequesting
    private let refreshCapture: @MainActor () -> Void

    public init(
        preferences: any PreferencesStoring, permissions: any PermissionRequesting,
        refreshCapture: @escaping @MainActor () -> Void, pause: PauseAutoDetectViewModel? = nil, display: DisplaySettingsModel? = nil, capture: CaptureViewModel? = nil
    ) {
        self.display = display ?? DisplaySettingsModel()
        self.capture = capture
        self.preferences = preferences
        permissionsPort = permissions
        self.refreshCapture = refreshCapture
        self.pause = pause
        autoTripsOn = preferences.preferences.autoTripsEnabled
    }

    /// What stops automatic trips right now; empty when the switch is on and nothing needs fixing.
    public var attentionReasons: [AttentionReason] { AutoDetectReadiness.reasons(for: permissions) }

    /// The status line under the switch, in S-HOME-01's words.
    public var statusText: String {
        guard autoTripsOn else { return "Manual mode" }
        if pause?.isPaused == true { return "Auto-detect paused" }
        return attentionReasons.isEmpty ? "Auto-detect on · ready" : "Auto-detect needs attention"
    }

    public var statusDetail: String? {
        guard autoTripsOn else { return nil }
        if let pause, pause.isPaused { return pause.pausedUntilLine }
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
        if autoTripsOn != on { display.trackAutomaticTripsChange() }
        autoTripsOn = on
        preferences.update { $0.autoTripsEnabled = on }
        refreshCapture()
        permissions = await permissionsPort.current()
        guard on else {
            explainer = nil
            return
        }
        // The choice stays on while "Always" is missing: the status says "needs attention" until it is granted.
        explainer = nextExplainer()
    }

    /// The explainer's "Continue": ask for While Using, or for the one-time Always upgrade, or open Settings
    /// (F-ONB-04.3: the Always prompt is never shown again in-app).
    public func confirmAlways() async {
        guard let step = explainer else { return }
        switch step {
        case .initialAccess:
            await request { await self.permissionsPort.requestLocationWhenInUse() }
            refreshCapture()
            // On to the Always upgrade when While Using was granted; denied goes to Settings, where DrivePerf now
            // has location options.
            explainer = nextExplainer()
        case .alwaysUpgrade:
            preferences.update { $0.alwaysPromptShown = true }
            await request { await self.permissionsPort.requestLocationAlways() }
            explainer = nil
            refreshCapture()
        case .settings:
            explainer = nil
            permissionsPort.openSystemSettings()
        }
    }

    public func dismissExplainer() {
        explainer = nil
    }

    public func openSettings() {
        permissionsPort.openSystemSettings()
    }

    /// What is still missing before automatic trips can run, as the next explainer step; nil when "Always" is held.
    private func nextExplainer() -> AlwaysExplainer? {
        switch permissions.location {
        case .always: nil
        case .notDetermined: .initialAccess
        case .whenInUse: preferences.preferences.alwaysPromptShown ? .settings : .alwaysUpgrade
        case .denied, .restricted: .settings
        }
    }

    private func request(_ body: @MainActor () async -> PermissionSnapshot) async {
        isRequesting = true
        permissions = await body()
        isRequesting = false
    }
}
