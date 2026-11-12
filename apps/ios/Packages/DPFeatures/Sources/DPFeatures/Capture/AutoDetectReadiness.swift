import DPSharedBridge
import Foundation
import Observation

/// What automatic trips need that the user can change in Settings, in the order the status UI shows them.
public enum AttentionReason: String, Equatable, Sendable, CaseIterable {
    case locationWhileUsing, locationDenied
    /// Motion & Fitness is off: the wake and the drive check cannot tell driving from walking.
    case motionOff
    /// Approximate location only: the trip start and route would be too coarse.
    case preciseLocationOff
    /// Background App Refresh is off or restricted: iOS may not relaunch the app after a force-quit or reboot.
    case backgroundRefreshOff

    /// The accessory's second line.
    public var shortText: String {
        switch self {
        case .locationWhileUsing: "Location is set to While Using"
        case .locationDenied: "Location is off"
        case .motionOff: "Motion & Fitness is off"
        case .preciseLocationOff: "Precise Location is off"
        case .backgroundRefreshOff: "Background App Refresh is off"
        }
    }

    /// The sheet's explanation, one line per reason.
    public var explanation: String {
        switch self {
        case .locationWhileUsing:
            "Location access changed to \"While Using\". Automatic trips are paused. Two taps to fix it."
        case .locationDenied:
            "Location is off, so automatic trips can’t start. Turn it on and choose Always."
        case .motionOff:
            "Motion & Fitness is off. DrivePerf uses it to tell driving from walking, so automatic trips are paused."
        case .preciseLocationOff:
            "Precise Location is off. Without it a drive’s start and route are too rough to measure."
        case .backgroundRefreshOff:
            "Background App Refresh is off, so iOS may not wake DrivePerf after a restart. Turn it on to keep "
                + "auto-detect running."
        }
    }
}

/// Pure rules for "Auto-detect on / off / needs attention" (F-CAP-02, S-HOME-01, S-HOME-02).
public enum AutoDetectReadiness {
    /// Everything that needs fixing, most important first. Empty: automatic trips can run.
    public static func reasons(for permissions: PermissionSnapshot) -> [AttentionReason] {
        var out: [AttentionReason] = []
        switch permissions.location {
        case .always: break
        case .whenInUse: out.append(.locationWhileUsing)
        case .notDetermined, .denied, .restricted: out.append(.locationDenied)
        }
        if permissions.motion == .denied || permissions.motion == .restricted { out.append(.motionOff) }
        // Without location there is nothing to be precise about; the first reason already covers it.
        if permissions.location.canRecord, !permissions.preciseLocation { out.append(.preciseLocationOff) }
        if permissions.backgroundRefresh != .available { out.append(.backgroundRefreshOff) }
        return out
    }
}

/// A request to open the capture status sheet from outside the UI, for example the watchdog notification's tap. It
/// stays pending until the shell is on screen and consumes it, so a tap that launches the app is not lost.
@MainActor
@Observable
public final class CaptureStatusRequests {
    public private(set) var isPending = false

    public init() {}

    public func request() { isPending = true }

    func consume() { isPending = false }
}
