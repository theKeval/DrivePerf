import DPSharedBridge
import Foundation

/// DRIVE-222: whether the app has been in the foreground since the phone last started.
///
/// iOS lets an app start background location only from a `CLServiceSession` / `CLBackgroundActivitySession` that was
/// started while the app was in use; a background relaunch can continue such a session but never start one (Apple
/// Developer Forums 767460, 767865). A restart ends the session, so until the user opens the app once, a wake can
/// still start a candidate but iOS suspends the app ~10 s later and no fix arrives. This records the boot during
/// which the app was last in the foreground, so a wake can tell that case apart and ask the user to open the app.
public struct OpenedSinceBoot {
    public enum State: String, Sendable, Equatable {
        /// Opened in the foreground since the phone started: background location can start.
        case opened
        /// Not opened since the phone started: auto-detect cannot record until it is.
        case notOpened
        /// No foreground was ever recorded (the first run of a build that predates DRIVE-222).
        case unknown
    }

    static let foregroundBootKey = "driveperf.lastForegroundBootTime"
    static let noticeBootKey = "driveperf.rearmNoticeBootTime"

    private let defaults: UserDefaults

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    /// The app is in the foreground during the boot that started at `bootTime`.
    public func recordForeground(bootTime: Date? = BootDetector.currentBootTime()) {
        guard let bootTime else { return }
        defaults.set(bootTime, forKey: Self.foregroundBootKey)
    }

    public func state(bootTime: Date? = BootDetector.currentBootTime()) -> State {
        guard let bootTime, let foreground = defaults.object(forKey: Self.foregroundBootKey) as? Date else {
            return .unknown
        }
        return BootDetector.isNewBoot(previous: foreground, current: bootTime) ? .notOpened : .opened
    }

    /// Asks only for an automatic candidate the core started (so the wake passed its guards: Automatic trips on, no
    /// pause, both restored from storage) while the app is in the background; in the foreground the app is in use.
    /// The status before the core starts shows `.on` by default, so health alone would not do (Codex P2 on #96).
    public static func shouldAsk(runState: AppRunState, status: RecorderStatus) -> Bool {
        !runState.isInUse && status.state == .candidate && !status.isManual
            && (status.autoDetectHealth == .on || status.autoDetectHealth == .needsAttention)
    }

    /// True once per boot, on the first call while the app has not been opened since the phone started. An unknown
    /// state never asks: it is the first launch of this build, not evidence of a restart.
    public func claimNotice(bootTime: Date? = BootDetector.currentBootTime()) -> Bool {
        guard let bootTime, state(bootTime: bootTime) == .notOpened else { return false }
        if let posted = defaults.object(forKey: Self.noticeBootKey) as? Date,
            !BootDetector.isNewBoot(previous: posted, current: bootTime)
        {
            return false
        }
        defaults.set(bootTime, forKey: Self.noticeBootKey)
        return true
    }
}
