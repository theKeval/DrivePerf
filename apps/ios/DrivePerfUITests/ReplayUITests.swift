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

    /// DRIVE-34 and DRIVE-203 on the offline canvas (fixed pin positions): event and stop pins are buttons, the stats
    /// and elevation show, and a stop pin opens S-RPL-03, whose "Jump to this moment" closes it.
    @MainActor
    func testPinsStatsAndTheStopSheet() {
        let app = launch(offline: true)
        openReplay(app)
        let event = app.buttons.matching(identifier: "replay.eventPin").firstMatch
        require(event, app, timeout: 15, "event pins are buttons")
        XCTAssertEqual(app.buttons.matching(identifier: "replay.eventPin").count, 2, "the event in the GPS gap is not pinned")
        XCTAssertTrue(app.descendants(matching: .any)["replay.readouts"].exists, "Speed · Elapsed · Distance")
        XCTAssertTrue(app.staticTexts["replay.stats"].exists, "the stats line")
        XCTAssertTrue(app.descendants(matching: .any)["replay.elevation"].exists, "the elevation profile")
        let stop = app.buttons.matching(identifier: "replay.stopPin").firstMatch
        require(stop, app, timeout: 5, "the stop pin")
        stop.tap()
        let jump = app.buttons["replay.pinSheet.jump"]
        require(jump, app, timeout: 5, "S-RPL-03 opens")
        jump.tap()
        XCTAssertTrue(jump.waitForNonExistence(timeout: 5), "Jump to this moment closes the sheet")
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
