package com.driveperf.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.feature.onboarding.AllTheTimeResult
import com.driveperf.feature.onboarding.OemGuides
import com.driveperf.feature.onboarding.OnboardingActions
import com.driveperf.feature.onboarding.OnboardingScreen
import com.driveperf.feature.onboarding.OnboardingSheet
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
// A tall screen so every page fits without scrolling. Not DrivePerfApplication: no Koin or services in UI tests.
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h2600dp-xxhdpi")
class OnboardingUiTest {
    @get:Rule val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = OnboardingActions(
        onContinue = { calls += "continue" },
        onBack = { calls += "back" },
        onSkipIntro = { calls += "skip" },
        onAgeConfirmedChanged = { calls += "age=$it" },
        onAgeContinue = { calls += "ageContinue" },
        onUnder18 = { calls += "under18" },
        onUnder18Undone = { calls += "under18Undone" },
        onOpenTerms = { calls += "terms" },
        onOpenPrivacy = { calls += "privacy" },
        onShowHowItWorks = { calls += "howItWorks" },
        onOpenHowItRecords = { calls += "howItRecords" },
        onDismissSheet = { calls += "dismissSheet" },
        onRequestLocation = { calls += "location" },
        onLocationNotNow = { calls += "locationNotNow" },
        onRequestPrecise = { calls += "precise" },
        onKeepApproximate = { calls += "keepApproximate" },
        onPreciseContinue = { calls += "preciseContinue" },
        onContinueWithoutLocation = { calls += "withoutLocation" },
        onOpenAppSettings = { calls += "appSettings" },
        onRequestActivity = { calls += "activity" },
        onAutoTripsToggled = { calls += "auto=$it" },
        onAutoTripsContinue = { calls += "autoContinue" },
        onOpenLocationSettings = { calls += "locationSettings" },
        onContinueInManualMode = { calls += "manual" },
        onRequestNotifications = { calls += "notifications" },
        onOpenGuideStep = { calls += "guide" },
        onBatteryDone = { calls += "batteryDone" },
        onBatteryRemindLater = { calls += "batteryLater" },
        onBatteryShowSteps = { calls += "batterySteps" },
        onCrashReportsChanged = { calls += "crash=$it" },
        onUsageStatisticsChanged = { calls += "stats=$it" },
        onHelpContinue = { calls += "helpContinue" },
    )

    private fun show(state: OnboardingUiState) {
        compose.setContent { DrivePerfTheme(darkTheme = true) { OnboardingScreen(state, actions) } }
    }

    private fun click(text: String) = compose.onNodeWithText(text).performClick()

    private val fine = PermissionState(fineLocation = true)

    // region intro

    @Test
    fun `welcome leads with the brand line and Skip goes to the age step`() {
        show(OnboardingUiState(step = OnboardingStep.Welcome))
        compose.onNodeWithText("Smooth is a skill.").assertIsDisplayed()
        compose.onNodeWithText("Every drive, decoded.").assertIsDisplayed()
        click("Skip")
        click("Get started")
        assertEquals(listOf("skip", "continue"), calls)
    }

    @Test
    fun `the last intro page states the privacy line and the Pro Preview and has no Skip`() {
        show(OnboardingUiState(step = OnboardingStep.Privacy))
        compose.onNodeWithText("No account needed. Your trips stay on your phone unless you turn on backup.").assertIsDisplayed()
        compose.onNodeWithText("Pro Preview included for 14 days, no card").assertIsDisplayed()
        compose.onAllNodes(hasText("Skip")).assertCountEquals(0)
    }

    // endregion

    // region age and terms

    @Test
    fun `Continue is disabled until the 18 plus box is ticked, which is never pre-ticked`() {
        show(OnboardingUiState(step = OnboardingStep.AgeTerms))
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        click("I’m 18 or older")
        assertEquals(listOf("age=true"), calls)
    }

    @Test
    fun `ticked, Continue is enabled and under 18 is offered`() {
        show(OnboardingUiState(step = OnboardingStep.AgeTerms, ageConfirmed = true))
        compose.onNodeWithText("Continue").assertIsEnabled().performClick()
        click("I’m under 18")
        assertEquals(listOf("ageContinue", "under18"), calls)
    }

    @Test
    fun `the terms and privacy links are on the page`() {
        show(OnboardingUiState(step = OnboardingStep.AgeTerms))
        compose.onNodeWithText("Terms of Use", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Privacy Policy", substring = true).assertIsDisplayed()
    }

    @Test
    fun `under 18 blocks with the honest line and nothing to continue to`() {
        show(OnboardingUiState(step = OnboardingStep.AgeTerms, under18Blocked = true))
        compose.onNodeWithText("DrivePerf is for drivers 18 and over for now").assertIsDisplayed()
        click("I tapped this by mistake")
        assertEquals(listOf("under18Undone"), calls)
    }

    // endregion

    // region capture promise

    @Test
    fun `the capture promise carries the binding copy with the Android force-stop and phone-brand lines`() {
        show(OnboardingUiState(step = OnboardingStep.CapturePromise))
        compose.onNodeWithText("Auto-detects most drives.").assertIsDisplayed()
        compose.onNodeWithText("The first few hundred metres may be estimated.").assertIsDisplayed()
        compose.onNodeWithText("Force-stopping the app pauses auto-detect.").assertIsDisplayed()
        compose.onNodeWithText("Some phone brands put apps to sleep.").assertIsDisplayed()
        compose.onNodeWithText("We’ll help you check.").assertIsDisplayed()
        compose.onNodeWithText("About 2–4% battery per hour of driving with the screen off.").assertIsDisplayed()
    }

    @Test
    fun `How it works opens the sheet`() {
        show(OnboardingUiState(step = OnboardingStep.CapturePromise))
        click("How it works")
        assertEquals(listOf("howItWorks"), calls)
    }

    @Test
    fun `the How it works sheet explains both modes and links to the website`() {
        show(OnboardingUiState(step = OnboardingStep.Welcome, sheet = OnboardingSheet.HowItWorks))
        compose.onNodeWithText("Two ways to record. You can switch between them anytime in Settings.").assertIsDisplayed()
        compose.onNodeWithText("Automatic trips").assertIsDisplayed()
        compose.onNodeWithText("Manual trips").assertIsDisplayed()
        click("More on driveperf.com ›")
        assertEquals(listOf("howItRecords"), calls)
    }

    // endregion

    // region location, activity, notifications

    @Test
    fun `the location primer is shown before the dialog and Not now never shows it`() {
        show(OnboardingUiState(step = OnboardingStep.Location))
        compose.onNodeWithText("Draw the route of every drive").assertIsDisplayed()
        click("Continue")
        click("Not now")
        assertEquals(listOf("location", "locationNotNow"), calls)
    }

    @Test
    fun `refused location says so and offers settings or continuing without it`() {
        show(OnboardingUiState(step = OnboardingStep.Location, deniedStep = OnboardingStep.Location))
        compose.onNodeWithText("Location is off").assertIsDisplayed()
        compose.onNodeWithText("Location: Don’t allow").assertIsDisplayed()
        click("Open settings")
        click("Continue without location")
        assertEquals(listOf("appSettings", "withoutLocation"), calls)
    }

    @Test
    fun `physical activity names the permission and explains the denied consequence`() {
        show(OnboardingUiState(step = OnboardingStep.Motion, permissions = fine))
        compose.onNodeWithText("Physical activity lets DrivePerf tell driving from walking", substring = true).assertIsDisplayed()
        click("Continue")
        assertEquals(listOf("activity"), calls)
    }

    @Test
    fun `refused physical activity keeps capture on location alone`() {
        show(OnboardingUiState(step = OnboardingStep.Motion, deniedStep = OnboardingStep.Motion, permissions = fine))
        compose.onNodeWithText("Automatic trips can still run on location alone", substring = true).assertIsDisplayed()
    }

    @Test
    fun `notifications lists exactly what is sent and never while you drive`() {
        show(OnboardingUiState(step = OnboardingStep.Notifications, permissions = fine))
        compose.onNodeWithText("Three kinds of notification, and nothing else.").assertIsDisplayed()
        compose.onNodeWithText("Weekly recap").assertIsDisplayed()
        compose.onNodeWithText("Never while you drive").assertIsDisplayed()
        click("Not now")
        assertEquals(listOf("continue"), calls)
    }

    @Test
    fun `refused notifications mention the required recording notification`() {
        show(OnboardingUiState(step = OnboardingStep.Notifications, deniedStep = OnboardingStep.Notifications, permissions = fine))
        compose.onNodeWithText("Android still shows the recording notification", substring = true).assertIsDisplayed()
    }

    // endregion

    // region precise and denied sheets

    @Test
    fun `the precise sheet explains and offers the request or approximate`() {
        show(OnboardingUiState(step = OnboardingStep.Welcome, sheet = OnboardingSheet.PreciseLocation))
        compose.onNodeWithText("Precise location places braking and cornering on the road.", substring = true).assertIsDisplayed()
        click("Use precise location")
        click("Keep approximate")
        assertEquals(listOf("precise", "keepApproximate"), calls)
    }

    @Test
    fun `refusing precise shows route only`() {
        show(OnboardingUiState(step = OnboardingStep.Welcome, sheet = OnboardingSheet.PreciseLocation, preciseRefused = true))
        compose.onNodeWithText("Route only, for now").assertIsDisplayed()
        compose.onNodeWithText("route only, no event placement", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the denied sheet and the manual-mode sheet`() {
        show(OnboardingUiState(step = OnboardingStep.Welcome, sheet = OnboardingSheet.LocationDenied))
        compose.onNodeWithText("DrivePerf needs location to record").assertIsDisplayed()
        click("Not now")
        assertEquals(listOf("withoutLocation"), calls)
    }

    @Test
    fun `manual mode sheet continues in manual mode`() {
        show(OnboardingUiState(step = OnboardingStep.Welcome, permissions = fine, sheet = OnboardingSheet.ManualMode))
        compose.onNodeWithText("Manual trips it is").assertIsDisplayed()
        click("Continue in manual mode")
        assertEquals(listOf("manual"), calls)
    }

    // endregion

    // region automatic trips

    @Test
    fun `automatic trips is the prominent disclosure with the toggle off by default`() {
        show(OnboardingUiState(step = OnboardingStep.AutoTrips, permissions = fine))
        compose.onNodeWithText(
            "DrivePerf collects location data to enable automatic trip recording, even when the app is closed or not in use.",
        ).assertIsDisplayed()
        compose.onNodeWithText("Off until you turn it on").assertIsDisplayed()
        compose.onNodeWithText("Continue with manual trips").assertIsDisplayed()
    }

    @Test
    fun `turning it on changes the primary action to settings`() {
        show(OnboardingUiState(step = OnboardingStep.AutoTrips, permissions = fine, autoTripsOn = true))
        compose.onNodeWithText("Continue to settings").assertIsDisplayed().performClick()
        assertEquals(listOf("autoContinue"), calls)
    }

    @Test
    fun `the toggle reports the change`() {
        show(OnboardingUiState(step = OnboardingStep.AutoTrips, permissions = fine))
        click("Record drives automatically")
        assertEquals(listOf("auto=true"), calls)
    }

    @Test
    fun `the all-the-time guide opens settings or continues in manual mode`() {
        show(OnboardingUiState(step = OnboardingStep.AllTheTime, permissions = fine, autoTripsOn = true))
        compose.onNodeWithText("One more step in settings").assertIsDisplayed()
        click("Open settings")
        click("Continue with manual trips")
        assertEquals(listOf("locationSettings", "manual"), calls)
    }

    @Test
    fun `returned granted confirms automatic trips are on`() {
        show(OnboardingUiState(step = OnboardingStep.AllTheTime, allTheTime = AllTheTimeResult.Granted, autoTripsOn = true))
        compose.onNodeWithText("Automatic trips are on").assertIsDisplayed()
    }

    @Test
    fun `returned not granted says it is still while using the app and offers another try`() {
        show(OnboardingUiState(step = OnboardingStep.AllTheTime, allTheTime = AllTheTimeResult.NotGranted, autoTripsOn = true))
        compose.onNodeWithText("Location is still “While using the app”").assertIsDisplayed()
        click("Try again")
        assertEquals(listOf("locationSettings"), calls)
    }

    // endregion

    // region battery

    @Test
    fun `Samsung gets its steps with a deep link, Done and Remind me later`() {
        show(OnboardingUiState(step = OnboardingStep.Battery, oemGuide = OemGuides.forDevice("samsung"), autoTripsOn = true))
        compose.onNodeWithText("Keep DrivePerf awake on your Samsung").assertIsDisplayed()
        compose.onNodeWithText("Detected: Samsung").assertIsDisplayed()
        click("Open sleeping apps ›")
        click("Done")
        click("Remind me later")
        assertEquals(listOf("guide", "batteryDone", "batteryLater"), calls)
    }

    @Test
    fun `Xiaomi has three steps, the last one without a button`() {
        show(OnboardingUiState(step = OnboardingStep.Battery, oemGuide = OemGuides.forDevice("Xiaomi", "Redmi"), autoTripsOn = true))
        compose.onNodeWithText("Keep DrivePerf awake on your Xiaomi").assertIsDisplayed()
        compose.onNodeWithText("Lock DrivePerf in Recents").assertIsDisplayed()
    }

    @Test
    fun `remind later names the health check item and can show the steps again`() {
        show(OnboardingUiState(step = OnboardingStep.Battery, oemGuide = OemGuides.forDevice("samsung"), batteryReminded = true))
        compose.onNodeWithText("We’ll remind you").assertIsDisplayed()
        compose.onNodeWithText("Health Check: Keep DrivePerf awake").assertIsDisplayed()
        click("Show me the steps now")
        assertEquals(listOf("batterySteps"), calls)
    }

    // endregion

    // region consent and ready

    @Test
    fun `consent switches start off, are separate and report each change`() {
        show(OnboardingUiState(step = OnboardingStep.HelpImprove))
        compose.onNodeWithText("Share usage statistics").assertIsDisplayed()
        compose.onNodeWithText("Send crash reports").assertIsDisplayed()
        compose.onNodeWithText("Nothing is sent before you continue.", substring = true).assertIsDisplayed()
        click("Send crash reports")
        click("Share usage statistics")
        assertEquals(listOf("crash=true", "stats=true"), calls)
    }

    @Test
    fun `usage statistics are shown off, never pre-selected`() {
        show(OnboardingUiState(step = OnboardingStep.HelpImprove))
        compose.onAllNodes(isToggleable()).assertCountEquals(2)
        compose.onAllNodes(isToggleable())[0].assertIsOff()
        compose.onAllNodes(isToggleable())[1].assertIsOff()
    }

    @Test
    fun `Continue commits the choices`() {
        show(OnboardingUiState(step = OnboardingStep.HelpImprove))
        click("Continue")
        assertEquals(listOf("helpContinue"), calls)
    }

    @Test
    fun `the ready page tells the truth about what is on`() {
        show(OnboardingUiState(step = OnboardingStep.Ready, permissions = fine))
        compose.onNodeWithText("You’re set for manual trips.").assertIsDisplayed()
        compose.onNodeWithText("Precise").assertIsDisplayed()
        click("Go to Drives")
        assertEquals(listOf("continue"), calls)
    }

    @Test
    fun `the ready page with automatic trips on and every permission granted`() {
        val all = fine.copy(backgroundLocation = true, activityRecognition = true)
        show(OnboardingUiState(step = OnboardingStep.Ready, permissions = all, autoTripsOn = true))
        compose.onNodeWithText("You’re set.").assertIsDisplayed()
    }

    @Test
    fun `the ready page does not promise automatic trips when physical activity is missing`() {
        val noActivity = fine.copy(backgroundLocation = true)
        show(OnboardingUiState(step = OnboardingStep.Ready, permissions = noActivity, autoTripsOn = true))
        compose.onNodeWithText("You’re set for manual trips.").assertIsDisplayed()
        compose.onNodeWithText("You turned automatic trips on, but they need", substring = true).assertIsDisplayed()
    }

    // endregion
}
