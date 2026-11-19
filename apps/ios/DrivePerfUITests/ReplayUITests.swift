import XCTest

/// DRIVE-200 and DRIVE-61: opens S-RPL-01 from the trip detail with the synthetic Replay fixture (no real data,
/// no Core Location), plays it, opens the legend, and checks the offline canvas.
final class ReplayUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    @MainActor
    private func launch(offline: Bool = false) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-replayFixture"] + (offline ? ["-replayOffline"] : [])
        app.launch()
        return app
    }

    /// Waits for `element`; on failure prints the screen's tree (CI is the only place these run) and stops the test.
    @MainActor
    private func require(_ element: XCUIElement, _ app: XCUIApplication, timeout: TimeInterval, _ message: String) {
        guard element.waitForExistence(timeout: timeout) else {
            print(app.debugDescription)
            XCTFail(message)
            return
        }
    }

    /// Drives tab → the fixture trip → Replay.
    @MainActor
    private func openReplay(_ app: XCUIApplication) {
        let trip = app.buttons["tripRow.synthetic-replay"]
        require(trip, app, timeout: 30, "the fixture trip is listed")
        trip.tap()
        let replay = app.buttons["tripDetail.replay"]
        require(replay, app, timeout: 10, "trip detail offers Replay")
        replay.tap()
    }

    @MainActor
    func testReplayOpensFromTripDetailPausedAndPlays() {
        let app = launch()
        openReplay(app)
        let play = app.buttons["replay.playPause"]
        require(play, app, timeout: 15, "Replay shows its playback bar")
        XCTAssertEqual(play.label, "Play replay", "Replay opens paused")
        XCTAssertTrue(app.descendants(matching: .any)["replay.scrubber"].exists)
        play.tap()
        XCTAssertEqual(app.buttons["replay.playPause"].label, "Pause replay")
        app.buttons["replay.playPause"].tap()
        XCTAssertEqual(app.buttons["replay.playPause"].label, "Play replay")

        let legend = app.buttons["replay.legend"]
        require(legend, app, timeout: 5, "the legend button is in the toolbar")
        legend.tap()
        require(app.staticTexts["On the map"], app, timeout: 5, "the overlay legend opens")
        // Each legend row is one combined element (swatch + word).
        for word in ["Estimated", "No GPS", "Not scored"] {
            XCTAssertTrue(app.descendants(matching: .any)[word].exists, "the legend names \(word)")
        }
    }

    @MainActor
    func testWithNoNetworkReplayDrawsThePlainCanvas() {
        let app = launch(offline: true)
        openReplay(app)
        let note = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "Map unavailable offline")).firstMatch
        require(note, app, timeout: 15, "the offline note shows over the canvas")
        require(app.buttons["replay.playPause"], app, timeout: 5, "the playback bar shows offline too")
    }
}
