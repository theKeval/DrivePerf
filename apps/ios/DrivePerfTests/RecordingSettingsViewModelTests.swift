import DPSharedBridge
import XCTest
@testable import DPFeatures

/// DRIVE-153: the S-SET-03 "Automatic trips" switch.
@MainActor
final class RecordingSettingsViewModelTests: XCTestCase {
    private var permissions: FakePermissions!
    private var preferences: MemoryPreferences!
    private var refreshes = 0

    override func setUp() async throws {
        permissions = FakePermissions()
        permissions.state = PermissionSnapshot(
            location: .always, preciseLocation: true, motion: .authorized, notifications: .authorized)
        preferences = MemoryPreferences()
        refreshes = 0
    }

    private func makeModel() -> RecordingSettingsViewModel {
        RecordingSettingsViewModel(
            preferences: preferences, permissions: permissions, refreshCapture: { [weak self] in self?.refreshes += 1 })
    }

    func testStartsFromTheSavedChoice() {
        preferences.update { $0.autoTripsEnabled = true }
        XCTAssertTrue(makeModel().autoTripsOn)
        preferences.update { $0.autoTripsEnabled = false }
        XCTAssertFalse(makeModel().autoTripsOn)
    }

    func testTurningOnWithAlwaysSavesAndRefreshesWithoutAsking() async {
        let model = makeModel()
        await model.setAutoTrips(true)
        XCTAssertTrue(preferences.preferences.autoTripsEnabled)
        XCTAssertEqual(refreshes, 1)
        XCTAssertNil(model.explainer)
        XCTAssertTrue(permissions.requests.isEmpty)
        XCTAssertEqual(model.statusText, "Auto-detect on · ready")
    }

    func testTurningOffSavesRefreshesAndShowsManual() async {
        preferences.update { $0.autoTripsEnabled = true }
        let model = makeModel()
        await model.setAutoTrips(false)
        XCTAssertFalse(preferences.preferences.autoTripsEnabled)
        XCTAssertFalse(model.autoTripsOn)
        XCTAssertEqual(refreshes, 1)
        XCTAssertEqual(model.statusText, "Manual mode")
        XCTAssertNil(model.statusDetail)
    }

    func testTurningOffClosesTheExplainer() async {
        permissions.state.location = .whenInUse
        let model = makeModel()
        await model.setAutoTrips(true)
        XCTAssertNotNil(model.explainer)
        await model.setAutoTrips(false)
        XCTAssertNil(model.explainer)
    }

    func testWhileUsingExplainsThenAsksOnceAndStaysOn() async {
        permissions.state.location = .whenInUse
        permissions.alwaysResult = .always
        let model = makeModel()
        await model.setAutoTrips(true)
        XCTAssertEqual(model.explainer, .init(canAsk: true))
        XCTAssertTrue(preferences.preferences.autoTripsEnabled, "The choice is kept while Always is missing")
        XCTAssertEqual(model.statusText, "Auto-detect needs attention")
        XCTAssertEqual(model.statusDetail, AttentionReason.locationWhileUsing.shortText)
        XCTAssertTrue(permissions.requests.isEmpty, "Nothing is asked before the explanation")

        await model.confirmAlways()
        XCTAssertEqual(permissions.requests, ["always"])
        XCTAssertTrue(preferences.preferences.alwaysPromptShown)
        XCTAssertNil(model.explainer)
        XCTAssertEqual(refreshes, 2, "Capture is refreshed again once the prompt is answered")
        XCTAssertEqual(model.statusText, "Auto-detect on · ready")
    }

    func testDecliningTheAlwaysPromptKeepsTheChoiceAndNeedsAttention() async {
        permissions.state.location = .whenInUse
        permissions.alwaysResult = .whenInUse
        let model = makeModel()
        await model.setAutoTrips(true)
        await model.confirmAlways()
        XCTAssertTrue(preferences.preferences.autoTripsEnabled)
        XCTAssertEqual(model.statusText, "Auto-detect needs attention")
    }

    func testAfterThePromptWasUsedTheExplainerSendsYouToSettings() async {
        permissions.state.location = .whenInUse
        preferences.update { $0.alwaysPromptShown = true }
        let model = makeModel()
        await model.setAutoTrips(true)
        XCTAssertEqual(model.explainer, .init(canAsk: false))
        await model.confirmAlways()
        XCTAssertTrue(permissions.requests.isEmpty, "The Always prompt is never shown again in-app")
        XCTAssertEqual(permissions.openedSettings, 1)
        XCTAssertNil(model.explainer)
    }

    func testDeniedLocationGoesToSettings() async {
        permissions.state.location = .denied
        let model = makeModel()
        await model.setAutoTrips(true)
        XCTAssertEqual(model.explainer, .init(canAsk: false))
        XCTAssertEqual(model.attentionReasons.first, .locationDenied)
    }

    func testNotNowKeepsTheChoice() async {
        permissions.state.location = .whenInUse
        let model = makeModel()
        await model.setAutoTrips(true)
        model.dismissExplainer()
        XCTAssertNil(model.explainer)
        XCTAssertTrue(preferences.preferences.autoTripsEnabled)
        XCTAssertTrue(permissions.requests.isEmpty)
    }

    func testRefreshPicksUpAlwaysGrantedInSettings() async {
        permissions.state.location = .whenInUse
        let model = makeModel()
        await model.setAutoTrips(true)
        permissions.state.location = .always
        await model.refresh()
        XCTAssertEqual(model.statusText, "Auto-detect on · ready")
    }
}
