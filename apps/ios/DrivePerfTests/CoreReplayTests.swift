import DPSharedBridge
import DrivePerfShared
import XCTest

@testable import DPFeatures
@testable import DrivePerf

/// DRIVE-200: `CoreReplayTrack` maps the shared Kotlin `Replay` into the Swift mirror S-RPL-01 reads.
final class CoreReplayTests: XCTestCase {
    /// 0–10 s estimated start, 10–40 s observed, a gap 40–60 s, 60–100 s observed with 80–100 s excluded.
    private func kotlinReplay() -> DrivePerfShared.Replay {
        let track = stride(from: 0.0, through: 100, by: 1).compactMap { t -> DrivePerfShared.ReplayPoint? in
            if t > 40, t < 60 { return nil }
            return DrivePerfShared.ReplayPoint(
                tS: t, lat: 43.6 + t * 1e-4, lng: -79.4, speedMps: t < 10 ? nil : KotlinDouble(value: 12))
        }
        let spans = [
            DrivePerfShared.ReplaySpan(style: .estimated, t0S: 0, t1S: 10),
            DrivePerfShared.ReplaySpan(style: .noGps, t0S: 40, t1S: 60),
            DrivePerfShared.ReplaySpan(style: .excluded, t0S: 80, t1S: 100),
        ]
        return DrivePerfShared.Replay(
            tripId: "k1", bbox: BoundingBox(minLat: 43.6, minLng: -79.4, maxLat: 43.61, maxLng: -79.4),
            durationS: 100, spans: spans, summaryOnly: false, track: track)
    }

    func testTheKotlinReplayMapsIntoTheMirror() {
        let track = CoreReplayTrack(kotlinReplay())
        XCTAssertEqual(track.tripId, "k1")
        XCTAssertEqual(track.durationS, 100)
        XCTAssertEqual(track.bbox, ReplayBounds(minLat: 43.6, minLng: -79.4, maxLat: 43.61, maxLng: -79.4))
        XCTAssertEqual(track.spans.map(\.style), [.estimated, .noGps, .excluded])
        XCTAssertEqual(track.spans.map(\.label), ["Estimated start", "No GPS", "Phone moved \u{2014} not scored"])
        XCTAssertFalse(track.summaryOnly)
    }

    func testSegmentsKeepTheOverlayStyles() {
        let styles = CoreReplayTrack(kotlinReplay()).segments(zoomBucket: 22).map(\.style)
        XCTAssertEqual(styles.first, .estimated)
        XCTAssertTrue(styles.contains(.noGps))
        XCTAssertEqual(styles.last, .excluded)
    }

    func testFramesJumpTheGapAndPlaybackRatesComeFromTheCore() {
        let track = CoreReplayTrack(kotlinReplay())
        let inGap = track.frame(at: 50)
        XCTAssertEqual(inGap.style, .noGps)
        XCTAssertNil(inGap.speedMps)
        XCTAssertEqual(inGap.lat, 43.6 + 40 * 1e-4, accuracy: 1e-9, "held at the last fix before the gap")
        XCTAssertEqual(track.frame(at: 20).style, .observed)
        XCTAssertNil(track.frame(at: 5).speedMps, "no speed on the estimated start")
        XCTAssertEqual(track.span(at: 5)?.label, "Estimated start")
        XCTAssertNil(track.span(at: 20))
        XCTAssertEqual(track.span(at: 100)?.style, .excluded, "a span that runs to the end includes the end")
        XCTAssertEqual(track.playbackRate(.auto), 100.0 / 60, accuracy: 1e-9)
        XCTAssertEqual(track.playbackRate(.x30), 30)
    }

    func testZoomBucketsMatchTheSwiftFormulaUsedByTheFakes() {
        let track = CoreReplayTrack(kotlinReplay())
        for (delta, width) in [(360.0, 256.0), (0.35, 402.0), (0.01, 402.0), (0.0, 402.0)] {
            XCTAssertEqual(
                track.zoomBucket(longitudeDelta: delta, viewWidth: width),
                ReplayZoomMath.bucket(longitudeDelta: delta, viewWidth: width), "delta \(delta)")
        }
    }
}
