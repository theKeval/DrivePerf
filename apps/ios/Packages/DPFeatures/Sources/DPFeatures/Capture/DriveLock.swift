import DPSharedBridge
import Foundation

/// Content someone tried to open while the in-drive lock applies (F-CAP-07.2). S-DRV-05 names it, except paywalls and
/// share prompts, which are never named: they show the Story copy and are dropped, not reopened (D12).
public enum HeldContent: Equatable, Sendable {
    case story, replay, analysis, recap, wrapped, share, paywall

    /// "Your <noun> will be ready when you've parked."
    public var noun: String {
        switch self {
        case .story, .share, .paywall: "Story"
        case .replay: "Replay"
        case .analysis: "Analysis"
        case .recap: "Recap"
        case .wrapped: "Wrapped"
        }
    }

    /// Paywalls and share prompts held by the lock are dropped when it lifts.
    public var isReopened: Bool { self != .share && self != .paywall }
}

/// Whether the phone still looks like it is in a moving vehicle: the OS activity classifier says automotive, or it
/// moves faster than 10 km/h (PRD F-CAP-07.4, placeholders). Features code against this port; the app target adapts
/// the platform signal. Read after a trip ends, when the shared recorder has stopped its own sources.
@MainActor
public protocol MovementMonitoring: AnyObject {
    /// Emits the current reading first, then every change. `true` while moving.
    func movements() -> AsyncStream<Bool>
}

/// The timings of the in-drive lock (PRD F-CAP-07, F-CAP-01.4). All placeholders until Batch 2 field tests.
public enum DriveLock {
    /// After a trip ends, the lock holds until the phone has been stationary or not automotive for this long.
    public static let calmInterval: TimeInterval = 60
    /// The manual-trip stop timer (`CaptureConfig.manualStopS`, 15 min).
    public static let manualStopTimeout: TimeInterval = 900
    /// A manual trip whose end was recorded this long before it was finalised ended on the stop timer, not on Stop.
    /// A minute of slack for the timer's own scheduling.
    public static let autoEndGap: TimeInterval = manualStopTimeout - 60
    /// While driving, S-DRV-05 steps back to S-DRV-01 after this long so Stop is never out of reach.
    public static let heldReturnAfter: TimeInterval = 8

    /// True when the 15-minute stationary timeout ended this trip. A stop-timer trip is finalised with its end set
    /// to when stopping began, so it is dated about the timeout before the moment it was finalised; a trip the user
    /// stopped is dated at the moment of the tap. Read from the finalised trip, so the capture core stays untouched.
    public static func endedOnStopTimer(_ trip: TripSummary, finalisedAt: Date) -> Bool {
        trip.source == .manual && trip.status == .finalised
            && finalisedAt.timeIntervalSince(trip.endedAt) >= autoEndGap
    }
}
