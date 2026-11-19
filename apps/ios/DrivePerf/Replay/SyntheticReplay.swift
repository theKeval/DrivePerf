import DPSharedBridge
import DrivePerfShared
import Foundation

/// A synthetic Kotlin `Replay` with no real locations: an invented S-shaped route of about 20 km, a 40 s estimated
/// start, a 41 s GPS gap and a three-minute "phone moved" span. The `-replayFixture` UI tests and `-replayFixtureAt`
/// screenshots run it through the real shared `ReplayPresenter` (`ReplayPresenter.onMain`).
enum SyntheticReplay {
    static func make(tripId: String, durationS: Double = 1_800, estimatedS: Double = 40) -> DrivePerfShared.Replay {
        let gapStart = (durationS * 0.35).rounded()
        let gapEnd = gapStart + 41
        let excludedStart = (durationS * 0.62).rounded()
        let origin = (lat: 43.6400, lng: -79.4600)
        var track: [DrivePerfShared.ReplayPoint] = []
        for second in 0...Int(durationS) {
            let t = Double(second)
            if t > gapStart, t < gapEnd { continue }
            let f = t / durationS
            let speed = max(8 + 20 * sin(f * .pi) + 4 * sin(f * 40), 0)
            track.append(
                DrivePerfShared.ReplayPoint(
                    tS: t, lat: origin.lat + 0.045 * f + 0.012 * sin(f * .pi * 3), lng: origin.lng + 0.20 * f,
                    speedMps: t < estimatedS ? nil : KotlinDouble(value: speed)))
        }
        var spans: [DrivePerfShared.ReplaySpan] = []
        if estimatedS > 0 { spans.append(DrivePerfShared.ReplaySpan(style: .estimated, t0S: 0, t1S: estimatedS)) }
        spans.append(DrivePerfShared.ReplaySpan(style: .noGps, t0S: gapStart, t1S: gapEnd))
        spans.append(DrivePerfShared.ReplaySpan(style: .excluded, t0S: excludedStart, t1S: excludedStart + 180))
        let lats = track.map(\.lat)
        let lngs = track.map(\.lng)
        return DrivePerfShared.Replay(
            tripId: tripId,
            bbox: BoundingBox(
                minLat: lats.min() ?? origin.lat, minLng: lngs.min() ?? origin.lng, maxLat: lats.max() ?? origin.lat,
                maxLng: lngs.max() ?? origin.lng),
            durationS: durationS, spans: spans, summaryOnly: false, track: track)
    }
}

/// Presenters for the fixture trip: the real shared state machine over the synthetic replay, with no analytics.
@MainActor
final class SyntheticReplayPresenters: ReplayPresenterMaking {
    func presenter(tripId: String, source: ReplayOpenSource) -> any ReplayPresenting {
        let presenter = DrivePerfShared.ReplayPresenter.companion.onMain(
            tripId: tripId, source: CoreReplayPresenter.kotlin(source),
            replays: StaticReplayRepository(replay: SyntheticReplay.make(tripId: tripId)),
            isRecording: { KotlinBoolean(value: false) }, analytics: SilentAnalytics(),
            monoNowNs: { KotlinLong(value: Int64(clamping: DispatchTime.now().uptimeNanoseconds)) })
        return CoreReplayPresenter(presenter)
    }
}

/// The fixture sends nothing anywhere.
private final class SilentAnalytics: NSObject, DrivePerfShared.Analytics, @unchecked Sendable {
    func track(event: any AnalyticsEvent) {}
    func setUsageConsent(allowed: Bool) {}
    func resetId() {}
}
