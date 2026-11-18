import DPSharedBridge
import Foundation

/// The marker's frames sampled once from `ReplayTrack.frame(at:)` when Replay opens, so playback reads a contiguous
/// table and never calls into the core or allocates per display frame (F-RPL-01 AC4). Between two samples the
/// position, speed and bearing are interpolated for display only; across a `noGps` sample nothing is interpolated, so
/// the marker jumps the gap exactly as the core's `frameAt` does.
public struct ReplayFrameTable: Sendable {
    /// Trip seconds between samples. The core interpolates between 1 Hz fixes, so half a second keeps its shape.
    public static let step: Double = 0.5

    public let durationS: Double
    private let stepS: Double
    private let frames: ContiguousArray<ReplayFrame>

    /// Samples `frame` every `step` seconds from 0 to `durationS`, end included.
    public init(durationS: Double, step: Double = ReplayFrameTable.step, frame: (Double) -> ReplayFrame) {
        let duration = max(durationS, 0)
        self.durationS = duration
        let count = Int((duration / step).rounded(.up)) + 1
        var frames = ContiguousArray<ReplayFrame>()
        frames.reserveCapacity(count)
        for index in 0..<count { frames.append(frame(min(Double(index) * step, duration))) }
        self.frames = frames
        stepS = step
    }

    public var isEmpty: Bool { frames.isEmpty }

    /// The marker at `tS`, clamped to the trip.
    public func frame(at tS: Double) -> ReplayFrame {
        guard !frames.isEmpty else { return ReplayFrame(tS: 0, lat: 0, lng: 0, style: .noGps) }
        let t = min(max(tS, 0), durationS)
        let position = t / stepS
        let index = min(Int(position), frames.count - 1)
        guard index + 1 < frames.count else { return frames[frames.count - 1] }
        let a = frames[index]
        let b = frames[index + 1]
        let span = b.tS - a.tS
        guard span > 0, a.style != .noGps, b.style != .noGps else {
            var held = a
            held.tS = t
            return held
        }
        let f = (t - a.tS) / span
        return ReplayFrame(
            tS: t, lat: a.lat + (b.lat - a.lat) * f, lng: a.lng + (b.lng - a.lng) * f,
            speedMps: Self.lerp(a.speedMps, b.speedMps, f), bearingDeg: Self.bearing(a.bearingDeg, b.bearingDeg, f),
            style: f < 0.5 ? a.style : b.style)
    }

    private static func lerp(_ a: Double?, _ b: Double?, _ f: Double) -> Double? {
        guard let a, let b else { return a ?? b }
        return a + (b - a) * f
    }

    /// The shorter way round, in [0, 360).
    private static func bearing(_ a: Double?, _ b: Double?, _ f: Double) -> Double? {
        guard let a, let b else { return a ?? b }
        var delta = (b - a).truncatingRemainder(dividingBy: 360)
        if delta > 180 { delta -= 360 }
        if delta < -180 { delta += 360 }
        let value = (a + delta * f).truncatingRemainder(dividingBy: 360)
        return value < 0 ? value + 360 : value
    }
}
