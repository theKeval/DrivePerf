import Foundation

// Swift mirrors of the scoring enums in `com.driveperf.domain.trip` and `com.driveperf.domain.score` (Sprint 4). The
// names are prefixed so a file that imports both `DPSharedBridge` and `DrivePerfShared` never sees two `EventType`s.
// The app target maps the Kotlin values (`KotlinEventMapping.swift`); features never see Kotlin.

/// Kotlin `EventType`: what kind of abrupt input was detected (pipeline-and-scoring.md §4).
public enum DrivingEventType: String, Equatable, Sendable, CaseIterable {
    case brake, accel, corner
}

/// Kotlin `Confidence`: how sure the detector is of one event (§4.4).
public enum DrivingEventConfidence: String, Equatable, Sendable, CaseIterable {
    case high, medium, low
}

/// Kotlin `ContributorId`: the four Smoothness contributors (§5.6).
public enum ScoreContributorID: String, Equatable, Sendable, CaseIterable {
    case braking, acceleration, cornering, consistency
}

/// Kotlin `Band`: one contributor against the driver's usual (§5.7).
public enum ScoreBand: String, Equatable, Sendable, CaseIterable {
    case smoother, usual, rougher
}

/// Kotlin `TipCategory`: coaching tip categories, in the §6.2 tie order.
public enum ScoreTipCategory: String, Equatable, Sendable, CaseIterable {
    case braking, cornering, acceleration, consistency, anticipation
}
