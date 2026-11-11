package com.driveperf.feature.onboarding

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The onboarding pages (WP-A, S-ONB-01 to S-ONB-16, Android order). Each page is its own composable in a sibling file;
 * sheets (S-ONB-05 How it works, S-ONB-11, S-ONB-16) are drawn over the page by [OnboardingSheets].
 */
@Suppress("CyclomaticComplexMethod") // one branch per page: the whole order reads in one place
@Composable
fun OnboardingScreen(state: OnboardingUiState, actions: OnboardingActions, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when (state.step) {
            OnboardingStep.Welcome -> WelcomePage(state, actions)
            OnboardingStep.StoryPreview -> StoryPreviewPage(state, actions)
            OnboardingStep.Privacy -> PrivacyPage(state, actions)
            OnboardingStep.AgeTerms -> if (state.under18Blocked) Under18Page(state, actions) else AgeTermsPage(state, actions)
            OnboardingStep.CapturePromise -> CapturePromisePage(state, actions)
            OnboardingStep.Location -> LocationPage(state, actions)
            OnboardingStep.Motion -> MotionPage(state, actions)
            OnboardingStep.AutoTrips -> AutoTripsPage(state, actions)
            OnboardingStep.AllTheTime -> AllTheTimePage(state, actions)
            OnboardingStep.Notifications -> NotificationsPage(state, actions)
            OnboardingStep.Battery -> BatteryPage(state, actions)
            OnboardingStep.HelpImprove -> HelpImprovePage(state, actions)
            OnboardingStep.Ready -> ReadyPage(state, actions)
        }
        OnboardingSheets(state, actions)
    }
}
