import DPSharedBridge
import DrivePerfShared
import Foundation

/// `ReplayLoading` over the shared core's `ReplayRepository` (`CoreServices.replay`, DRIVE-200). Like the other core
/// bridges it exists before the core starts; until `attach` it finds nothing.
@MainActor
final class CoreReplayLoader: ReplayLoading {
    private var repository: (any DrivePerfShared.ReplayRepository)?

    func attach(_ repository: any DrivePerfShared.ReplayRepository) {
        self.repository = repository
    }

    func replay(tripId: String) async -> (any ReplayTrack)? {
        guard let repository else { return nil }
        return await Self.load(UncheckedSendable(repository), tripId: tripId)
    }

    // The repository reads the database and trip files on the core's IO context; this hops off the main actor.
    private nonisolated static func load(
        _ repository: UncheckedSendable<any DrivePerfShared.ReplayRepository>, tripId: String
    ) async -> (any ReplayTrack)? {
        guard let replay = try? await repository.value.replay(tripId: tripId) else { return nil }
        return CoreReplayTrack(replay)
    }
}

/// One Kotlin `Replay` as a `ReplayTrack`. The Kotlin object is immutable, so it is safe to read from any thread.
final class CoreReplayTrack: ReplayTrack, @unchecked Sendable {
    private let replay: DrivePerfShared.Replay
    let tripId: String
    let bbox: DPSharedBridge.ReplayBounds
    let durationS: Double
    let spans: [DPSharedBridge.ReplaySpan]
    let summaryOnly: Bool

    init(_ replay: DrivePerfShared.Replay) {
        self.replay = replay
        tripId = replay.tripId
        bbox = DPSharedBridge.ReplayBounds(
            minLat: replay.bbox.minLat, minLng: replay.bbox.minLng, maxLat: replay.bbox.maxLat,
            maxLng: replay.bbox.maxLng)
        durationS = replay.durationS
        spans = replay.spans.map {
            DPSharedBridge.ReplaySpan(style: Self.style($0.style), t0S: $0.t0S, t1S: $0.t1S, label: $0.label)
        }
        summaryOnly = replay.summaryOnly
    }

    func segments(zoomBucket: Int) -> [DPSharedBridge.ReplaySegment] {
        replay.segments(zoomBucket: Int32(zoomBucket)).map { segment in
            DPSharedBridge.ReplaySegment(
                style: Self.style(segment.style),
                points: segment.points.map {
                    DPSharedBridge.ReplayPoint(tS: $0.tS, lat: $0.lat, lng: $0.lng, speedMps: $0.speedMps?.doubleValue)
                })
        }
    }

    func frame(at tS: Double) -> DPSharedBridge.ReplayFrame {
        let f = replay.frameAt(tS: tS)
        return DPSharedBridge.ReplayFrame(
            tS: f.tS, lat: f.lat, lng: f.lng, speedMps: f.speedMps?.doubleValue, bearingDeg: f.bearingDeg?.doubleValue,
            style: Self.style(f.style))
    }

    func playbackRate(_ speed: DPSharedBridge.PlaybackSpeed) -> Double {
        replay.playbackRate(speed: Self.kotlin(speed))
    }

    func zoomBucket(longitudeDelta: Double, viewWidth: Double) -> Int {
        Int(DrivePerfShared.Replay.companion.zoomBucketFor(longitudeDeltaDeg: longitudeDelta, viewWidthPoints: viewWidth))
    }

    static func style(_ style: DrivePerfShared.SegmentStyle) -> ReplaySegmentStyle {
        switch style {
        case .observed: .observed
        case .estimated: .estimated
        case .noGps: .noGps
        case .excluded: .excluded
        }
    }

    static func kotlin(_ speed: DPSharedBridge.PlaybackSpeed) -> DrivePerfShared.PlaybackSpeed {
        switch speed {
        case .auto: .auto
        case .x1: .x1
        case .x10: .x10
        case .x30: .x30
        }
    }
}
