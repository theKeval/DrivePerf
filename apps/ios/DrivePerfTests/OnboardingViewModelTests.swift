import DPSharedBridge
import XCTest
@testable import DPFeatures

@MainActor
final class OnboardingViewModelTests: XCTestCase {
    private var permissions: FakePermissions!
    private var preferences: MemoryPreferences!
    private var crash: FakeCrashReporter!
    private var analytics: FakeUsageAnalytics!
    private var refreshes = 0
    private var finished = false

    override func setUp() async throws {
        permissions = FakePermissions()
        preferences = MemoryPreferences()
        crash = FakeCrashReporter()
        analytics = FakeUsageAnalytics()
        refreshes = 0
        finished = false
    }

    private func makeModel() -> OnboardingViewModel {
        OnboardingViewModel(
            permissions: permissions, preferences: preferences, crashReporter: crash, analytics: analytics,
            refreshCapture: { [weak self] in self?.refreshes += 1 },
            onFinished: { [weak self] in self?.finished = true })
    }

    private func advance(_ model: OnboardingViewModel, to step: OnboardingStep) {
        while model.step.rawValue < step.rawValue, model.step != .ageTerms { model.next() }
        if step.rawValue > OnboardingStep.ageTerms.rawValue {
            model.ageConfirmed = true
            model.confirmAge()
        }
        while model.step.rawValue < step.rawValue { model.next() }
    }

    func testIntroPagesNeverAskForAPermission() {
        let model = makeModel()
        model.next()
        model.next()
        XCTAssertEqual(model.step, .privacy)
        XCTAssertTrue(permissions.requests.isEmpty)
    }

    func testSkipGoesToTheAgeStepWhichCannotBeSkipped() {
        let model = makeModel()
        model.skipIntro()
        XCTAssertEqual(model.step, .ageTerms)
        model.confirmAge()
        XCTAssertEqual(model.step, .ageTerms, "Continue is disabled until the box is ticked")
        XCTAssertFalse(model.ageConfirmed, "Never pre-ticked")
    }

    func testConfirmingAgeStoresTheTermsVersion() {
        let model = makeModel()
        model.skipIntro()
        model.ageConfirmed = true
        model.confirmAge()
        XCTAssertEqual(model.step, .capturePromise)
        XCTAssertEqual(preferences.preferences.termsAcceptedVersion, OnboardingViewModel.termsVersion)
        XCTAssertNotNil(preferences.preferences.termsAcceptedAt)
    }

    func testUnder18BlocksAndPersistsOnlyALocalFlag() {
        let model = makeModel()
        model.skipIntro()
        model.declareUnder18()
        XCTAssertTrue(model.under18Blocked)
        XCTAssertTrue(preferences.preferences.under18Blocked)
        XCTAssertFalse(model.canGoBack)
        let relaunched = makeModel()
        XCTAssertTrue(relaunched.under18Blocked)
        XCTAssertEqual(relaunched.step, .ageTerms)
        relaunched.undoUnder18()
        XCTAssertFalse(preferences.preferences.under18Blocked)
    }

    func testLocationGrantedWithPreciseGoesToMotion() async {
        let model = makeModel()
        advance(model, to: .location)
        await model.continueLocation()
        XCTAssertEqual(model.step, .motion)
        XCTAssertNil(model.sheet)
    }

    func testApproximateLocationShowsThePreciseSheetThenContinues() async {
        permissions.preciseResult = false
        let model = makeModel()
        advance(model, to: .location)
        await model.continueLocation()
        XCTAssertEqual(model.sheet, .precise)
        model.keepApproximate()
        XCTAssertTrue(model.preciseRefused)
        model.finishPreciseSheet()
        XCTAssertEqual(model.step, .motion)
    }

    func testDeniedLocationShowsTheManualSheetAndSkipsToNotifications() async {
        permissions.whenInUseResult = .denied
        let model = makeModel()
        advance(model, to: .location)
        await model.continueLocation()
        XCTAssertTrue(model.locationDenied)
        XCTAssertEqual(model.sheet, .locationDenied)
        model.continueWithoutLocation()
        XCTAssertEqual(model.step, .notifications)
        XCTAssertFalse(preferences.preferences.autoTripsEnabled)
    }

    func testMotionDeniedStaysThenContinuesWithoutReasking() async {
        permissions.motionResult = .denied
        let model = makeModel()
        advance(model, to: .location)
        await model.continueLocation()
        await model.continueMotion()
        XCTAssertTrue(model.motionDenied)
        XCTAssertEqual(model.step, .motion)
        await model.continueMotion()
        XCTAssertEqual(model.step, .autoTrips)
        XCTAssertEqual(permissions.requests.filter { $0 == "motion" }.count, 1)
    }

    func testAutomaticTripsAreOffUntilTurnedOn() async {
        let model = makeModel()
        XCTAssertFalse(model.autoTripsOn)
        advance(model, to: .location)
        await model.continueLocation()
        await model.continueMotion()
        await model.continueAutoTrips()
        XCTAssertEqual(model.step, .notifications)
        XCTAssertFalse(preferences.preferences.autoTripsEnabled)
        XCTAssertFalse(permissions.requests.contains("always"), "Manual trips never trigger the Always prompt")
    }

    func testAlwaysGrantedTurnsAutomaticTripsOn() async {
        let model = makeModel()
        advance(model, to: .location)
        await model.continueLocation()
        await model.continueMotion()
        model.autoTripsOn = true
        await model.continueAutoTrips()
        XCTAssertEqual(model.step, .notifications)
        XCTAssertTrue(preferences.preferences.autoTripsEnabled)
        XCTAssertTrue(preferences.preferences.alwaysPromptShown)
        XCTAssertGreaterThan(refreshes, 0)
    }

    func testAlwaysDeclinedFallsBackToManualAndIsNeverAskedAgain() async {
        permissions.alwaysResult = .whenInUse
        let model = makeModel()
        advance(model, to: .location)
        await model.continueLocation()
        await model.continueMotion()
        model.autoTripsOn = true
        await model.continueAutoTrips()
        XCTAssertEqual(model.sheet, .alwaysDeclined)
        XCTAssertTrue(model.alwaysDeclined)
        XCTAssertFalse(model.autoTripsOn)
        XCTAssertFalse(preferences.preferences.autoTripsEnabled)
        model.autoTripsOn = true
        await model.continueAutoTrips()
        XCTAssertEqual(permissions.requests.filter { $0 == "always" }.count, 1, "F-ONB-04.3: asked exactly once")
        model.continueInManualMode()
        XCTAssertEqual(model.step, .notifications)
    }

    func testNotificationsNotNowSkipsThePrompt() {
        let model = makeModel()
        advance(model, to: .notifications)
        model.skipNotifications()
        XCTAssertEqual(model.step, .helpImprove)
        XCTAssertFalse(permissions.requests.contains("notifications"))
    }

    func testConsentChoicesAreSavedExactlyAsSetAndNothingIsPreSelected() async {
        let model = makeModel()
        advance(model, to: .helpImprove)
        XCTAssertFalse(model.shareUsageStatistics)
        XCTAssertFalse(model.sendCrashReports)
        model.sendCrashReports = true
        await model.continueHelpImprove()
        XCTAssertEqual(model.step, .ready)
        XCTAssertFalse(preferences.preferences.shareUsageStatistics)
        XCTAssertTrue(preferences.preferences.sendCrashReports)
        XCTAssertEqual(crash.consents, [true])
    }

    func testFinishCompletesOnboarding() {
        let model = makeModel()
        model.finish()
        XCTAssertTrue(preferences.preferences.onboardingCompleted)
        XCTAssertTrue(finished)
    }

    func testSetupProgressNumbersTheSevenSetupSteps() {
        XCTAssertEqual(OnboardingStep.ageTerms.setupStep, 1)
        XCTAssertEqual(OnboardingStep.helpImprove.setupStep, OnboardingStep.setupTotal)
        XCTAssertNil(OnboardingStep.welcome.setupStep)
        XCTAssertNil(OnboardingStep.ready.setupStep)
    }
}
