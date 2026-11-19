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

    /// Invented pins for the synthetic route (DRIVE-34, DRIVE-203): a braking event, a cornering event marked
    /// "Needed", an acceleration in the GPS gap (ticked, not pinned), one stop, the max speed and a gentle elevation
    /// profile. No real places.
    static func details(for replay: DrivePerfShared.Replay) -> DrivePerfShared.ReplayDetails {
        let d = replay.durationS
        func at(_ t: Double) -> (lat: Double, lng: Double) {
            let f = replay.frameAt(tS: t)
            return (f.lat, f.lng)
        }
        func event(_ id: String, _ type: DrivePerfShared.EventType, _ t: Double, pinned: Bool) -> DrivePerfShared.DrivingEvent {
            let p = at(t)
            return DrivePerfShared.DrivingEvent(
                id: id, tripId: replay.tripId, type: type, tPeakS: t, tStartS: t - 0.8, tEndS: t + 0.6, durationS: 1.4,
                peakG: 0.42, direction: nil, lat: pinned ? KotlinDouble(value: p.lat) : nil,
                lng: pinned ? KotlinDouble(value: p.lng) : nil, speedBeforeMps: KotlinDouble(value: 13.3),
                speedAfterMps: KotlinDouble(value: 2), confidence: .high, algorithmVersion: "synthetic")
        }
        let events = [
            event("syn-brake", .brake, (d * 0.2).rounded(), pinned: true),
            event("syn-accel", .accel, (d * 0.35).rounded() + 20, pinned: false),
            event("syn-corner", .corner, (d * 0.8).rounded(), pinned: true),
        ]
        let markers = events.map { e in
            DrivePerfShared.EventMarker(
                eventId: e.id, type: e.type, tStartS: e.tStartS, tPeakS: e.tPeakS, tEndS: e.tEndS, lat: e.lat, lng: e.lng,
                needed: e.type == .corner, pinned: e.lat != nil)
        }
        let stopAt = (d * 0.5).rounded()
        let stop = at(stopAt)
        // Far enough from the stop that the two 44 pt pins never overlap on a phone-width canvas.
        let maxAt = (d * 0.65).rounded()
        let max = at(maxAt)
        let elevation = stride(from: 0.0, through: d, by: d / 40).map {
            DrivePerfShared.ElevationPoint(tS: $0, elevM: 80 + 30 * sin($0 / d * .pi * 2))
        }
        return DrivePerfShared.ReplayDetails(
            markers: markers, events: Dictionary(uniqueKeysWithValues: events.map { ($0.id, $0) }),
            maxSpeed: DrivePerfShared.MaxSpeedPin(tS: maxAt, speedMps: 28.6, lat: max.lat, lng: max.lng),
            stops: [
                DrivePerfShared.StopPin(
                    number: 1, atS: stopAt, durationS: 34, lat: KotlinDouble(value: stop.lat),
                    lng: KotlinDouble(value: stop.lng)),
            ],
            elevation: DrivePerfShared.ElevationProfile(points: elevation, gainM: 64, lossM: 58),
            distance: DrivePerfShared.ReplayDistance(track: replay.track, tripDistanceM: KotlinDouble(value: 19_900)),
            startedAtMs: Int64(ReplayFixture.trip.startedAt.timeIntervalSince1970 * 1000))
    }
}

/// Presenters for the fixture trip: the real shared state machine over the synthetic replay, with no analytics.
@MainActor
final class SyntheticReplayPresenters: ReplayPresenterMaking {
    func presenter(tripId: String, source: ReplayOpenSource) -> any ReplayPresenting {
        let replay = SyntheticReplay.make(tripId: tripId)
        let presenter = DrivePerfShared.ReplayPresenter.companion.onMain(
            tripId: tripId, source: CoreReplayPresenter.kotlin(source),
            replays: StaticReplayRepository(replay: replay),
            isRecording: { KotlinBoolean(value: false) }, analytics: SilentAnalytics(),
            monoNowNs: { KotlinLong(value: Int64(clamping: DispatchTime.now().uptimeNanoseconds)) },
            details: StaticReplayDetails(details: SyntheticReplay.details(for: replay)),
            units: ReplayOverlayKt.fixedUnits(units: .metric), format: CoreReplayTimeFormat())
        return CoreReplayPresenter(presenter)
    }

    /// The fixture has no stored events, so there is no evidence to show.
    func evidencePresenter(tripId: String, eventId: String) -> any EvidencePresenting {
        UnavailableEvidencePresenter(eventId: eventId)
    }

    func tipPresenter(tripId: String, tipId: String?, eventId: String?) -> any TipDetailPresenting {
        UnavailableTipPresenter()
    }
}

/// The fixture sends nothing anywhere.
private final class SilentAnalytics: NSObject, DrivePerfShared.Analytics, @unchecked Sendable {
    func track(event: any AnalyticsEvent) {}
    func setUsageConsent(allowed: Bool) {}
    func resetId() {}
}
