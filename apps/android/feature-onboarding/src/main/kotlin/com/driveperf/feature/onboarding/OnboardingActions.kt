package com.driveperf.feature.onboarding

/** What a page does when its buttons are tapped. Permission requests are launched by the route, not the page. */
data class OnboardingActions(
    val onContinue: () -> Unit,
    val onBack: () -> Unit,
    val onRequestLocation: () -> Unit,
    val onRequestBackground: () -> Unit,
    val onRequestActivity: () -> Unit,
    val onRequestNotifications: () -> Unit,
    val onOpenAppSettings: () -> Unit,
    val onOpenBatterySettings: () -> Unit,
    val onCrashReportsChanged: (Boolean) -> Unit,
    val onUsageStatisticsChanged: (Boolean) -> Unit,
)
