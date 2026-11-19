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

    /// Drives tab → the fixture trip → Replay.
    @MainActor
    private func openReplay(_ app: XCUIApplication) {
        let trip = app.buttons["tripRow.synthetic-replay"]
        guard trip.waitForExistence(timeout: 30) else {
            // CI is the only place these run: leave the screen's tree in the log.
            print(app.debugDescription)
            XCTFail("the fixture trip is listed")
            return
        }
        trip.tap()
        let replay = app.buttons["tripDetail.replay"]
        guard replay.waitForExistence(timeout: 10) else {
            print(app.debugDescription)
            XCTFail("trip detail offers Replay")
            return
        }
        replay.tap()
    }

    @MainActor
    func testReplayOpensFromTripDetailPausedAndPlays() {
        let app = launch()
        openReplay(app)
        let play = app.buttons["replay.playPause"]
        XCTAssertTrue(play.waitForExistence(timeout: 15), "Replay shows its playback bar")
        XCTAssertEqual(play.label, "Play replay", "Replay opens paused")
        XCTAssertTrue(app.descendants(matching: .any)["replay.scrubber"].exists)
        play.tap()
        XCTAssertEqual(app.buttons["replay.playPause"].label, "Pause replay")
        app.buttons["replay.playPause"].tap()
        XCTAssertEqual(app.buttons["replay.playPause"].label, "Play replay")

        app.buttons["replay.legend"].tap()
        XCTAssertTrue(app.staticTexts["On the map"].waitForExistence(timeout: 5), "the overlay legend opens")
        XCTAssertTrue(app.staticTexts["Estimated"].exists)
        XCTAssertTrue(app.staticTexts["No GPS"].exists)
        XCTAssertTrue(app.staticTexts["Not scored"].exists)
    }

    @MainActor
    func testWithNoNetworkReplayDrawsThePlainCanvas() {
        let app = launch(offline: true)
        openReplay(app)
        let note = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "Map unavailable offline")).firstMatch
        XCTAssertTrue(note.waitForExistence(timeout: 15), "the offline note shows over the canvas")
        XCTAssertTrue(app.buttons["replay.playPause"].exists)
    }
}
