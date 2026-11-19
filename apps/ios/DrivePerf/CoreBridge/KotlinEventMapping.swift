import DPSharedBridge
import DrivePerfShared
import Foundation

// Kotlin → Swift for detected events and scores (Sprint 4): the enums mirrored in `DPSharedBridge/Score`.

extension KotlinMapping {
    static func swift(_ t: DrivePerfShared.EventType) -> DrivingEventType {
        switch t {
        case .brake: .brake
        case .accel: .accel
        case .corner: .corner
        }
    }

    static func kotlin(_ t: DrivingEventType) -> DrivePerfShared.EventType {
        switch t {
        case .brake: .brake
        case .accel: .accel
        case .corner: .corner
        }
    }

    static func swift(_ c: DrivePerfShared.Confidence) -> DrivingEventConfidence {
        switch c {
        case .high: .high
        case .medium: .medium
        case .low: .low
        }
    }

    static func swift(_ id: DrivePerfShared.ContributorId) -> ScoreContributorID {
        switch id {
        case .braking: .braking
        case .acceleration: .acceleration
        case .cornering: .cornering
        case .consistency: .consistency
        }
    }

    static func swift(_ b: DrivePerfShared.Band) -> ScoreBand {
        switch b {
        case .smoother: .smoother
        case .usual: .usual
        case .rougher: .rougher
        }
    }

    static func swift(_ c: DrivePerfShared.TipCategory) -> ScoreTipCategory {
        switch c {
        case .braking: .braking
        case .cornering: .cornering
        case .acceleration: .acceleration
        case .consistency: .consistency
        case .anticipation: .anticipation
        }
    }
}
