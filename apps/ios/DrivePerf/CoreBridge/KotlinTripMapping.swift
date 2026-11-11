import DPSharedBridge
import DrivePerfShared
import Foundation

// Kotlin → Swift for what the Drives screens show about a trip: what it was (DRIVE-100), its data quality and how to
// draw its gaps (DRIVE-170, DRIVE-167) and the provenance catalogue (DRIVE-172).

extension KotlinMapping {
    static func swift(_ k: DrivePerfShared.TripKind) -> DPSharedBridge.TripKind {
        switch k {
        case .driver: .driver
        case .passenger: .passenger
        case .transit: .transit
        case .taxi: .taxi
        case .notDriving: .notDriving
        }
    }

    static func swift(_ c: DrivePerfShared.TripCorrection) -> DPSharedBridge.TripCorrection {
        switch c {
        case .driver: .driver
        case .passenger: .passenger
        case .transit: .transit
        case .bike: .bike
        case .walk: .walk
        case .other: .other
        }
    }

    static func kotlin(_ c: DPSharedBridge.TripCorrection) -> DrivePerfShared.TripCorrection {
        switch c {
        case .driver: .driver
        case .passenger: .passenger
        case .transit: .transit
        case .bike: .bike
        case .walk: .walk
        case .other: .other
        }
    }

    static func question(_ trip: DrivePerfShared.Trip) -> TripQuestion? {
        guard let suspect = trip.openQuestion else { return nil }
        switch suspect {
        case .transit: return .transit
        case .cycling: return .bike
        case DrivePerfShared.Suspect.none: return nil
        }
    }

    static func stats(_ trip: DrivePerfShared.Trip) -> TripStats {
        TripStats(
            movingS: trip.movingS?.doubleValue, stoppedS: trip.stoppedS?.doubleValue,
            stopCount: trip.stopCount.map { Int($0.int32Value) }, avgMovingSpeedMps: trip.avgMovingSpeedMps?.doubleValue,
            maxSpeedMps: trip.maxSpeedMps?.doubleValue, elevGainM: trip.elevGainM?.doubleValue,
            elevLossM: trip.elevLossM?.doubleValue, estimatedDistanceM: trip.estimatedDistanceM?.doubleValue ?? 0,
            startEstimated: trip.startEstimated)
    }

    static func swift(_ q: DrivePerfShared.TripQuality) -> TripQualitySummary {
        TripQualitySummary(
            badge: swift(q.badge), reason: q.reason.map(reasonKey), phone: swift(q.phoneState), imuHz: q.imuHz,
            gnssCoveragePct: q.gnssCoveragePct, alignmentSettledS: q.alignmentSettledS?.doubleValue,
            estimatedStartM: q.estimatedStartM?.doubleValue, scoredS: q.scoredS, excludedS: q.excludedS,
            totalS: q.totalS,
            leftOut: q.leftOut().map { LeftOut(reason: swift($0.reason), seconds: $0.seconds) },
            overlays: q.overlays().map {
                TrackOverlay(style: swift($0.style), t0S: $0.t0S, t1S: $0.t1S, reason: $0.reason.map(swift))
            })
    }

    static func swift(_ b: DrivePerfShared.QualityBadge) -> DPSharedBridge.QualityBadge {
        switch b {
        case .good: .good
        case .usable: .usable
        case .limited: .limited
        case .unscorable: .unscorable
        }
    }

    static func swift(_ p: DrivePerfShared.PhoneState) -> DPSharedBridge.PhoneState {
        switch p {
        case .mounted: .mounted
        case .loose: .loose
        case .moved: .moved
        case .unknown: .unknown
        }
    }

    static func reasonKey(_ r: DrivePerfShared.QualityReason) -> String {
        switch r {
        case .noImu: "noImu"
        case .noPreciseLocation: "noPreciseLocation"
        case .notAligned: "notAligned"
        case .lowScoredShare: "lowScoredShare"
        case .tooLittleScoredTime: "tooLittleScoredTime"
        case .lowGnss: "lowGnss"
        case .noGyro: "noGyro"
        case .lowImuRate: "lowImuRate"
        }
    }

    static func swift(_ r: DrivePerfShared.SegmentReason) -> SpanReason {
        switch r {
        case .estimatedStart: .estimatedStart
        case .noGnss: .noGnss
        case .tunnel: .tunnel
        case .phoneMoved: .phoneMoved
        case .appKilled: .appKilled
        case .lowBattery: .lowBattery
        case .thermal: .thermal
        case .userPause: .userPause
        case .notAligned: .notAligned
        case .lowImuRate: .lowImuRate
        case .imuGap: .imuGap
        }
    }

    static func swift(_ s: DrivePerfShared.OverlayStyle) -> DPSharedBridge.OverlayStyle {
        switch s {
        case .estimatedStart: .estimatedStart
        case .shortGap: .shortGap
        case .longGap: .longGap
        case .excluded: .excluded
        case .interrupted: .interrupted
        case .paused: .paused
        }
    }

    static func swift(_ p: DrivePerfShared.Provenance) -> DPSharedBridge.Provenance {
        switch p {
        case .observed: .observed
        case .derived: .derived
        case .estimated: .estimated
        }
    }

    /// The shared provenance catalogue (Kotlin `Metric`, `Provenance`), mapped once.
    static func provenanceCatalog() -> ProvenanceCatalog {
        let metrics = DrivePerfShared.Metric.allCases.map { m in
            MetricInfo(
                key: m.key, provenance: swift(m.provenance(includesEstimate: false)), meaning: m.meaning,
                unit: m.unit?.symbol, decimals: Int(m.unit?.decimals ?? 0), perSi: m.unit?.perSi ?? 1,
                partlyEstimated: m.partlyEstimated)
        }
        let legend = Dictionary(
            uniqueKeysWithValues: DrivePerfShared.Provenance.allCases.map { (swift($0), $0.meaning) })
        return ProvenanceCatalog(metrics: metrics, legend: legend)
    }
}
