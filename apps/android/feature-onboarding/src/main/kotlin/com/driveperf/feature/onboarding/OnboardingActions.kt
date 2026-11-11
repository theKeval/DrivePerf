package com.driveperf.feature.onboarding

/**
 * What a page does when its buttons are tapped. Permission requests, Settings and the in-app browser are launched by
 * the route, not the page; the rest go to the view model.
 */
data class OnboardingActions(
    val onContinue: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onSkipIntro: () -> Unit = {},
    // S-ONB-04
    val onAgeConfirmedChanged: (Boolean) -> Unit = {},
    val onAgeContinue: () -> Unit = {},
    val onUnder18: () -> Unit = {},
    val onUnder18Undone: () -> Unit = {},
    val onOpenTerms: () -> Unit = {},
    val onOpenPrivacy: () -> Unit = {},
    // S-ONB-05
    val onShowHowItWorks: () -> Unit = {},
    val onOpenHowItRecords: () -> Unit = {},
    val onDismissSheet: () -> Unit = {},
    // S-ONB-06, S-ONB-11, S-ONB-16
    val onRequestLocation: () -> Unit = {},
    val onLocationNotNow: () -> Unit = {},
    val onRequestPrecise: () -> Unit = {},
    val onKeepApproximate: () -> Unit = {},
    val onPreciseContinue: () -> Unit = {},
    val onContinueWithoutLocation: () -> Unit = {},
    val onOpenAppSettings: () -> Unit = {},
    // S-ONB-07
    val onRequestActivity: () -> Unit = {},
    // S-ONB-08, S-ONB-09
    val onAutoTripsToggled: (Boolean) -> Unit = {},
    val onAutoTripsContinue: () -> Unit = {},
    val onOpenLocationSettings: () -> Unit = {},
    val onContinueInManualMode: () -> Unit = {},
    // S-ONB-10
    val onRequestNotifications: () -> Unit = {},
    // S-ONB-12
    val onOpenGuideStep: (GuideStep) -> Unit = {},
    val onBatteryDone: () -> Unit = {},
    val onBatteryRemindLater: () -> Unit = {},
    val onBatteryShowSteps: () -> Unit = {},
    // S-ONB-13
    val onCrashReportsChanged: (Boolean) -> Unit = {},
    val onUsageStatisticsChanged: (Boolean) -> Unit = {},
    val onHelpContinue: () -> Unit = {},
)
