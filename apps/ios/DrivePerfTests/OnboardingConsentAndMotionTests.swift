import DPSharedBridge
import SwiftUI
import XCTest
@testable import DPFeatures

@MainActor
final class FakeUsageAnalytics: UsageAnalytics {
    private(set) var answers: [Bool] = []
    private(set) var resets = 0

    func setUsageConsent(_ allowed: Bool) { answers.append(allowed) }
    func resetID() { resets += 1 }
}

/// DRIVE-70 on iOS: the usage-statistics answer reaches the analytics gate exactly when the user continues from
/// S-ONB-13, and a later change in Profile (S-PRV-07) reaches it at once.
@MainActor
final class OnboardingConsentTests: XCTestCase {
    private func makeModel(_ analytics: FakeUsageAnalytics, preferences: MemoryPreferences = MemoryPreferences())
        -> OnboardingViewModel
    {
        OnboardingViewModel(
            permissions: FakePermissions(), preferences: preferences, crashReporter: FakeCrashReporter(),
            analytics: analytics, refreshCapture: {}, onFinished: {})
    }

    func testNothingIsSentToTheGateBeforeTheChoice() {
        let analytics = FakeUsageAnalytics()
        let model = makeModel(analytics)

        model.skipIntro()
        model.shareUsageStatistics = true // toggling is not a choice until Continue

        XCTAssertEqual(analytics.answers, [])
    }

    func testContinuingWithUsageOnReportsTheAnswer() async {
        let analytics = FakeUsageAnalytics()
        let model = makeModel(analytics)
        model.shareUsageStatistics = true
        model.sendCrashReports = false

        await model.continueHelpImprove()

        XCTAssertEqual(analytics.answers, [true])
        XCTAssertEqual(model.step, .ready)
    }

    func testContinuingWithNothingSelectedReportsADecline() async {
        let analytics = FakeUsageAnalytics()
        let model = makeModel(analytics)

        await model.continueHelpImprove()

        XCTAssertEqual(analytics.answers, [false], "Usage statistics are never pre-selected, so the answer is no")
    }

    func testProfileChangesReachTheGateAtOnce() {
        let analytics = FakeUsageAnalytics()
        let dependencies = AppDependencies(
            recorder: FakeRecorder(), history: FakeRecorder(), clock: FixedClock(), permissions: FakePermissions(),
            preferences: MemoryPreferences(), crashReporter: FakeCrashReporter(), analytics: analytics,
            selfTest: FakeSelfTest(), flags: BuildFlags(), coreInfo: FakeHello(), refreshCapture: {})
        let profile = ProfileViewModel(dependencies: dependencies)

        profile.setShareUsageStatistics(true)
        profile.setShareUsageStatistics(false)
        profile.resetAnalyticsID()

        XCTAssertEqual(analytics.answers, [true, false])
        XCTAssertEqual(analytics.resets, 1)
    }
}

/// F-ONB-01.4 / DRIVE-54: Reduce Motion swaps motion for a 220 ms cross-fade and makes toggles snap.
final class OnboardingMotionTests: XCTestCase {
    func testReduceMotionUsesTheTwoHundredTwentyMillisecondCrossFade() {
        XCTAssertEqual(OnboardingMotion.crossFadeDuration, 0.22)
        XCTAssertEqual(
            OnboardingMotion.pageAnimation(reduceMotion: true), .easeInOut(duration: OnboardingMotion.crossFadeDuration))
    }

    func testWithoutReduceMotionPagesKeepTheSmoothTiming() {
        XCTAssertEqual(
            OnboardingMotion.pageAnimation(reduceMotion: false), .smooth(duration: OnboardingMotion.crossFadeDuration))
    }

    func testTogglesSnapUnderReduceMotion() {
        XCTAssertNil(OnboardingMotion.toggleAnimation(reduceMotion: true))
        XCTAssertNotNil(OnboardingMotion.toggleAnimation(reduceMotion: false))
    }
}

/// F-ONB-02.3 / F-ONB-03.3: web links open in the in-app browser; anything else goes to the system.
final class InAppBrowserPolicyTests: XCTestCase {
    func testWebLinksOpenInTheApp() {
        XCTAssertTrue(InAppBrowserPolicy.opensInApp(URL(string: "https://driveperf.com/terms")!))
        XCTAssertTrue(InAppBrowserPolicy.opensInApp(URL(string: "HTTPS://driveperf.com/privacy")!))
        XCTAssertTrue(InAppBrowserPolicy.opensInApp(URL(string: "http://driveperf.com/how-it-records")!))
    }

    func testOtherSchemesGoToTheSystem() {
        XCTAssertFalse(InAppBrowserPolicy.opensInApp(URL(string: "mailto:hello@driveperf.com")!))
        XCTAssertFalse(InAppBrowserPolicy.opensInApp(URL(string: "app-settings:")!))
        XCTAssertFalse(InAppBrowserPolicy.opensInApp(URL(string: "tel:123")!))
    }
}
