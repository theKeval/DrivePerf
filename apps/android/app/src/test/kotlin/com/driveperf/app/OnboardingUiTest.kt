package com.driveperf.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.feature.onboarding.OnboardingActions
import com.driveperf.feature.onboarding.OnboardingScreen
import com.driveperf.feature.onboarding.OnboardingStep
import com.driveperf.feature.onboarding.OnboardingUiState
import com.driveperf.feature.onboarding.PermissionState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class) // not DrivePerfApplication: no Koin or services in UI tests
class OnboardingUiTest {
    @get:Rule val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = OnboardingActions(
        onContinue = { calls += "continue" },
        onBack = { calls += "back" },
        onRequestLocation = { calls += "location" },
        onRequestBackground = { calls += "background" },
        onRequestActivity = { calls += "activity" },
        onRequestNotifications = { calls += "notifications" },
        onOpenAppSettings = { calls += "appSettings" },
        onOpenBatterySettings = { calls += "battery" },
        onCrashReportsChanged = { calls += "crash=$it" },
        onUsageStatisticsChanged = { calls += "stats=$it" },
    )

    private fun show(state: OnboardingUiState) {
        compose.setContent { DrivePerfTheme(darkTheme = true) { OnboardingScreen(state, actions) } }
    }

    @Test
    fun `welcome leads with the brand line`() {
        show(OnboardingUiState(step = OnboardingStep.Welcome))
        compose.onNodeWithText("Smooth is a skill.").assertIsDisplayed()
        compose.onNodeWithText("Get started").performClick()
        assertEquals(listOf("continue"), calls)
    }

    @Test
    fun `fine location page asks for location and can be skipped`() {
        show(OnboardingUiState(step = OnboardingStep.Location))
        compose.onNodeWithText("Allow location while you use the app").assertIsDisplayed()
        compose.onNodeWithText("Allow").performClick()
        compose.onNodeWithText("Not now").performClick()
        assertEquals(listOf("location", "continue"), calls)
    }

    @Test
    fun `background location is its own page with the all-the-time instruction`() {
        show(OnboardingUiState(step = OnboardingStep.BackgroundLocation, permissions = PermissionState(fineLocation = true)))
        compose.onNodeWithText("Record automatically").assertIsDisplayed()
        compose.onNodeWithText("Allow", substring = false).performClick()
        assertEquals(listOf("background"), calls)
    }

    @Test
    fun `the activity recognition and notification pages request their own permission`() {
        show(OnboardingUiState(step = OnboardingStep.ActivityRecognition, permissions = PermissionState(fineLocation = true)))
        compose.onNodeWithText("Allow").performClick()
        assertEquals(listOf("activity"), calls)
    }

    @Test
    fun `a refused permission explains the consequence and offers Settings`() {
        show(OnboardingUiState(step = OnboardingStep.Location, deniedStep = OnboardingStep.Location))
        compose.onNodeWithText("Without location DrivePerf cannot record a drive.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Open settings").performClick()
        assertEquals(listOf("appSettings"), calls)
    }

    @Test
    fun `approximate location is called out`() {
        show(
            OnboardingUiState(
                step = OnboardingStep.Location,
                deniedStep = OnboardingStep.Location,
                permissions = PermissionState(approximateOnly = true),
            ),
        )
        compose.onNodeWithText("You chose approximate location.", substring = true).assertIsDisplayed()
    }

    @Test
    fun `consent switches start off and report each change`() {
        show(OnboardingUiState(step = OnboardingStep.HelpImprove))
        compose.onNodeWithText("Share usage statistics").assertIsDisplayed()
        compose.onNodeWithText("Send crash reports").assertIsDisplayed()
        compose.onAllNodesWithText("Share usage statistics")[0].assertIsOff()
        compose.onNodeWithText("Send crash reports").performClick()
        assertEquals(listOf("crash=true"), calls)
    }

    @Test
    fun `the ready page tells the truth about what is on`() {
        show(OnboardingUiState(step = OnboardingStep.Ready, permissions = PermissionState(fineLocation = true)))
        compose.onNodeWithText("Location: on").assertIsDisplayed()
        compose.onNodeWithText("Automatic recording: off", substring = true).assertIsDisplayed()
    }
}
