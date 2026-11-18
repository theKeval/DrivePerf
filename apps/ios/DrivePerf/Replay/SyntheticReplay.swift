import DPSharedBridge
import Foundation

/// A synthetic replay that follows the shared Replay contract (DRIVE-200), with no real locations: an invented route
/// with an estimated start, a 41 s GPS gap and a "phone moved" span. It backs the UI tests and simulator screenshots
/// (`-replayFixture`) and stands in for `CoreServices.replay` until the shared model lands (see `ReplaySourceSeam`).
final class SyntheticReplay: ReplayTrack, @unchecked Sendable {
    let tripId: String
    let durationS: Double
    let summaryOnly: Bool
    let spans: [ReplaySpan]
    let bbox: ReplayBounds
    /// 1 Hz fixes; nil inside the GPS gap.
    private let fixes: [ReplayPoint?]
    /// The last fix before the gap and the first after it.
    private let gap: ClosedRange<Double>

    /// - Parameters:
    ///   - durationS: trip length in seconds.
    ///   - estimatedS: length of the estimated start (0 for a manual trip).
    init(tripId: String, durationS: Double = 1_800, estimatedS: Double = 40, summaryOnly: Bool = false) {
        self.tripId = tripId
        self.durationS = durationS
        self.summaryOnly = summaryOnly
        let gap = (durationS * 0.35).rounded()...(durationS * 0.35).rounded() + 41
        let excluded = (durationS * 0.62).rounded()...(durationS * 0.62).rounded() + 180
        self.gap = gap
        var spans: [ReplaySpan] = []
        if estimatedS > 0 { spans.append(ReplaySpan(style: .estimated, t0S: 0, t1S: estimatedS)) }
        spans.append(ReplaySpan(style: .noGps, t0S: gap.lowerBound, t1S: gap.upperBound))
        spans.append(ReplaySpan(style: .excluded, t0S: excluded.lowerBound, t1S: excluded.upperBound))
        self.spans = spans
        let count = Int(durationS) + 1
        // An invented S-shaped drive of about 20 km, east-north-east.
        let origin = (lat: 43.6400, lng: -79.4600)
        var fixes: [ReplayPoint?] = []
        fixes.reserveCapacity(count)
        for second in 0..<count {
            let t = Double(second)
            if t > gap.lowerBound, t < gap.upperBound {
                fixes.append(nil)
                continue
            }
            let f = t / durationS
            let lat = origin.lat + 0.045 * f + 0.012 * sin(f * .pi * 3)
            let lng = origin.lng + 0.20 * f
            let speed = 8 + 20 * sin(f * .pi) + 4 * sin(f * 40)
            fixes.append(ReplayPoint(tS: t, lat: lat, lng: lng, speedMps: max(speed, 0)))
        }
        self.fixes = fixes
        let points = fixes.compactMap { $0 }
        bbox = ReplayBounds(
            minLat: points.map(\.lat).min() ?? origin.lat, minLng: points.map(\.lng).min() ?? origin.lng,
            maxLat: points.map(\.lat).max() ?? origin.lat, maxLng: points.map(\.lng).max() ?? origin.lng)
    }

    /// How a fix is drawn: estimated or excluded by span, otherwise observed (the gap has no fixes).
    private func drawStyle(at tS: Double) -> ReplaySegmentStyle {
        spans.first { $0.style != .noGps && $0.contains(tS) }?.style ?? .observed
    }

    private func inGap(_ tS: Double) -> Bool { tS > gap.lowerBound && tS < gap.upperBound }

    /// Runs of one style that meet at their boundary fix; the gap is a two-point `noGps` segment across it. Coarser
    /// buckets keep every n-th fix but always each run's first and last.
    func segments(zoomBucket: Int) -> [ReplaySegment] {
        let stride = max(1, 1 << max(0, 14 - zoomBucket))
        var out: [ReplaySegment] = []
        var run: [ReplayPoint] = []
        var runStyle: ReplaySegmentStyle?
        var lastFix: ReplayPoint?
        func close(at point: ReplayPoint?) {
            if let point, run.last != point { run.append(point) }
            if let runStyle, run.count > 1 { out.append(ReplaySegment(style: runStyle, points: run)) }
            run = []
        }
        for (index, entry) in fixes.enumerated() {
            guard let fix = entry else { continue }
            if let previous = lastFix, fix.tS - previous.tS > 1 {
                close(at: previous)
                out.append(ReplaySegment(style: .noGps, points: [previous, fix]))
                runStyle = nil
            }
            let style = drawStyle(at: fix.tS)
            if style != runStyle {
                if runStyle != nil, let previous = lastFix {
                    close(at: previous)
                    run = [previous]
                }
                runStyle = style
            }
            if run.isEmpty || index % stride == 0 { run.append(fix) }
            lastFix = fix
        }
        close(at: lastFix)
        return out
    }

    func frame(at tS: Double) -> ReplayFrame {
        let t = min(max(tS, 0), durationS)
        if inGap(t), let held = fixes[Int(gap.lowerBound)] {
            return ReplayFrame(tS: t, lat: held.lat, lng: held.lng, style: .noGps)
        }
        let i = min(Int(t), fixes.count - 1)
        guard let a = fixes[i] else { return ReplayFrame(tS: t, lat: 0, lng: 0, style: .noGps) }
        guard i + 1 < fixes.count, let b = fixes[i + 1] else {
            return ReplayFrame(tS: t, lat: a.lat, lng: a.lng, speedMps: a.speedMps, style: drawStyle(at: t))
        }
        let f = t - a.tS
        let bearing = atan2((b.lng - a.lng) * cos(a.lat * .pi / 180), b.lat - a.lat) * 180 / .pi
        return ReplayFrame(
            tS: t, lat: a.lat + (b.lat - a.lat) * f, lng: a.lng + (b.lng - a.lng) * f,
            speedMps: a.speedMps.map { $0 + ((b.speedMps ?? $0) - $0) * f },
            bearingDeg: (bearing + 360).truncatingRemainder(dividingBy: 360), style: drawStyle(at: t))
    }

    func playbackRate(_ speed: PlaybackSpeed) -> Double {
        switch speed {
        case .auto: durationS / 60
        case .x1: 1
        case .x10: 10
        case .x30: 30
        }
    }
}

/// Serves `SyntheticReplay` for any trip.
@MainActor
final class SyntheticReplayLoader: ReplayLoading {
    func replay(tripId: String) async -> (any ReplayTrack)? {
        SyntheticReplay(tripId: tripId)
    }
}

/// DRIVE-200 seam: where Replay reads trips from. The shared model (`CoreServices.replay`, built in parallel on
/// `DRIVE-200-replay-model`) has not merged yet, so every trip shows the synthetic replay. When it merges this
/// returns the Kotlin-backed loader instead and the fake stays for `-replayFixture` only.
enum ReplaySourceSeam {
    @MainActor
    static func make() -> any ReplayLoading { SyntheticReplayLoader() }
}
