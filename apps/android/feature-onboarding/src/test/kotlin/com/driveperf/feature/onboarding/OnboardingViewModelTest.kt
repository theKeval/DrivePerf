package com.driveperf.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingViewModelTest {
    private class FakeSettings : OnboardingSettings {
        override var under18Blocked = false
        override var automaticTripsEnabled = false
        var termsVersion: String? = null
        var guide: ReliabilityGuideState? = null

        override fun recordTermsAccepted(version: String) {
            termsVersion = version
        }

        override fun recordReliabilityGuide(state: ReliabilityGuideState) {
            guide = state
        }
    }

    private val settings = FakeSettings()
    private val commits = mutableListOf<ConsentChoices>()
    private val fine = PermissionState(fineLocation = true)
    private val samsung = OemGuides.forDevice("samsung")

    private fun vm(
        permissions: PermissionState = PermissionState(),
        sdk: Int = 34,
        oem: OemGuide? = null,
        crash: Boolean = false,
        usage: Boolean = false,
    ) = OnboardingViewModel(
        initialPermissions = permissions,
        settings = settings,
        oemGuide = oem,
        initialChoices = ConsentChoices(crash, usage),
        onConsentCommitted = { commits += it },
        sdkInt = sdk,
    )

    private fun OnboardingViewModel.goTo(step: OnboardingStep) {
        var guard = 0
        while (state.value.step != step && guard++ < 30) {
            when (state.value.step) {
                OnboardingStep.AgeTerms -> {
                    onAgeConfirmedChanged(true)
                    onAgeContinue()
                }
                else -> onContinue()
            }
        }
        assertEquals(step, state.value.step)
    }

    private val OnboardingViewModel.step get() = state.value.step

    // region intro and age

    @Test
    fun `consent starts off and is never pre-selected`() {
        val state = vm().state.value
        assertFalse(state.crashReports)
        assertFalse(state.usageStatistics)
        assertFalse(state.autoTripsOn)
        assertFalse(state.ageConfirmed)
    }

    @Test
    fun `skip on an intro page goes to the age step and the age step cannot be skipped`() {
        val vm = vm()
        vm.onSkipIntro()
        assertEquals(OnboardingStep.AgeTerms, vm.step)
        assertFalse("Skip does not exist on the age page", vm.step.isIntro)
        vm.onAgeContinue()
        assertEquals("Continue does nothing until the box is ticked", OnboardingStep.AgeTerms, vm.step)
    }

    @Test
    fun `ticking the box and continuing saves the accepted version and date on the phone`() {
        val vm = vm()
        vm.onSkipIntro()
        vm.onAgeConfirmedChanged(true)
        vm.onAgeContinue()
        assertEquals(OnboardingStep.CapturePromise, vm.step)
        assertEquals(TERMS_VERSION, settings.termsVersion)
    }

    @Test
    fun `under 18 stops onboarding and stores only a local flag`() {
        val vm = vm()
        vm.onSkipIntro()
        vm.onAgeConfirmedChanged(true)
        vm.onUnder18()
        assertTrue(vm.state.value.under18Blocked)
        assertTrue(settings.under18Blocked)
        assertNull("no terms were saved", settings.termsVersion)
        assertFalse(vm.state.value.ageConfirmed)
        vm.onBack()
        assertEquals("Back does nothing while blocked", OnboardingStep.AgeTerms, vm.step)
        assertFalse(vm.state.value.canGoBack)
    }

    @Test
    fun `a phone already blocked reopens on the blocked page`() {
        settings.under18Blocked = true
        val vm = vm()
        assertEquals(OnboardingStep.AgeTerms, vm.step)
        assertTrue(vm.state.value.under18Blocked)
    }

    @Test
    fun `I tapped this by mistake clears the block`() {
        val vm = vm()
        vm.onSkipIntro()
        vm.onUnder18()
        vm.onUnder18Undone()
        assertFalse(vm.state.value.under18Blocked)
        assertFalse(settings.under18Blocked)
    }

    @Test
    fun `back returns to the page the user came from`() {
        val vm = vm()
        vm.onContinue()
        vm.onContinue()
        vm.onBack()
        assertEquals(OnboardingStep.StoryPreview, vm.step)
        vm.onBack()
        assertEquals(OnboardingStep.Welcome, vm.step)
        assertFalse(vm.state.value.canGoBack)
    }

    @Test
    fun `how it works is a sheet over the capture promise`() {
        val vm = vm()
        vm.goTo(OnboardingStep.CapturePromise)
        vm.onShowHowItWorks()
        assertEquals(OnboardingSheet.HowItWorks, vm.state.value.sheet)
        vm.onDismissSheet()
        assertNull(vm.state.value.sheet)
        assertEquals(OnboardingStep.CapturePromise, vm.step)
    }

    // endregion

    // region location

    @Test
    fun `precise location moves on to physical activity`() {
        val vm = vm()
        vm.goTo(OnboardingStep.Location)
        vm.onLocationResult(fine)
        assertEquals(OnboardingStep.Motion, vm.step)
    }

    @Test
    fun `refusing location shows the S-ONB-16 sheet and Not now goes on to notifications with automatic trips off`() {
        val vm = vm()
        vm.goTo(OnboardingStep.Location)
        vm.onLocationResult(PermissionState())
        assertEquals(OnboardingSheet.LocationDenied, vm.state.value.sheet)
        assertEquals(OnboardingStep.Location, vm.step)
        vm.onContinueWithoutLocation()
        assertEquals(OnboardingStep.Notifications, vm.step)
        assertFalse(settings.automaticTripsEnabled)
        assertNull(vm.state.value.sheet)
    }

    @Test
    fun `Not now on the primer never shows the system dialog and opens the same sheet`() {
        val vm = vm()
        vm.goTo(OnboardingStep.Location)
        vm.onLocationNotNow()
        assertEquals(OnboardingSheet.LocationDenied, vm.state.value.sheet)
    }

    @Test
    fun `approximate only opens the precise sheet, precise granted closes it and moves on`() {
        val vm = vm()
        vm.goTo(OnboardingStep.Location)
        vm.onLocationResult(PermissionState(approximateOnly = true))
        assertEquals(OnboardingSheet.PreciseLocation, vm.state.value.sheet)
        vm.onPreciseResult(fine)
        assertNull(vm.state.value.sheet)
        assertEquals(OnboardingStep.Motion, vm.step)
    }

    @Test
    fun `keeping approximate shows route only and Continue goes on to motion`() {
        val vm = vm()
        vm.goTo(OnboardingStep.Location)
        vm.onLocationResult(PermissionState(approximateOnly = true))
        vm.onKeepApproximate()
        assertTrue(vm.state.value.preciseRefused)
        vm.onPreciseContinue()
        assertEquals(OnboardingStep.Motion, vm.step)
        val context = FlowContext(vm.state.value.permissions)
        assertTrue("automatic trips need precise location", OnboardingFlow.skips(OnboardingStep.AutoTrips, context))
    }

    @Test
    fun `refusing precise when asked keeps the refused state and swiping the sheet away still goes on`() {
        val vm = vm()
        vm.goTo(OnboardingStep.Location)
        vm.onLocationResult(PermissionState(approximateOnly = true))
        vm.onPreciseResult(PermissionState(approximateOnly = true))
        assertTrue(vm.state.value.preciseRefused)
        vm.onDismissSheet()
        assertEquals(OnboardingStep.Motion, vm.step)
    }

    // endregion

    // region automatic trips

    private fun vmAtAutoTrips(sdk: Int = 34, oem: OemGuide? = null): OnboardingViewModel {
        val vm = vm(permissions = fine.copy(activityRecognition = true), sdk = sdk, oem = oem)
        vm.goTo(OnboardingStep.AutoTrips)
        return vm
    }

    @Test
    fun `automatic trips are off until the user turns them on`() {
        val vm = vmAtAutoTrips()
        assertFalse(vm.state.value.autoTripsOn)
        vm.onAutoTripsContinue()
        assertFalse(settings.automaticTripsEnabled)
        assertEquals("manual: straight on to notifications", OnboardingStep.Notifications, vm.step)
    }

    @Test
    fun `turning automatic trips on and continuing saves the opt-in and goes to the settings step on Android 11 plus`() {
        val vm = vmAtAutoTrips()
        vm.onAutoTripsToggled(true)
        assertFalse(vm.needsRuntimeBackgroundRequest)
        vm.onAutoTripsContinue()
        assertTrue(settings.automaticTripsEnabled)
        assertEquals(OnboardingStep.AllTheTime, vm.step)
    }

    @Test
    fun `the result of the settings step is read the moment the app resumes`() {
        val vm = vmAtAutoTrips()
        vm.onAutoTripsToggled(true)
        vm.onAutoTripsContinue()
        vm.onSettingsOpened()
        vm.onPermissionsChanged(fine.copy(activityRecognition = true, backgroundLocation = true))
        assertEquals(AllTheTimeResult.Granted, vm.state.value.allTheTime)
        assertEquals("the route moves on after a second", OnboardingStep.AllTheTime, vm.step)
        vm.onContinue()
        assertEquals(OnboardingStep.Notifications, vm.step)
    }

    @Test
    fun `coming back without the permission shows not granted, try again keeps the opt-in, manual turns it off`() {
        val vm = vmAtAutoTrips()
        vm.onAutoTripsToggled(true)
        vm.onAutoTripsContinue()
        vm.onSettingsOpened()
        vm.onPermissionsChanged(fine.copy(activityRecognition = true))
        assertEquals(AllTheTimeResult.NotGranted, vm.state.value.allTheTime)
        assertTrue(settings.automaticTripsEnabled)
        vm.onContinueInManualMode()
        assertFalse(settings.automaticTripsEnabled)
        assertFalse(vm.state.value.autoTripsOn)
        assertEquals(OnboardingStep.Notifications, vm.step)
    }

    @Test
    fun `a resume before the user has been to settings does not report a result`() {
        val vm = vmAtAutoTrips()
        vm.onAutoTripsToggled(true)
        vm.onAutoTripsContinue()
        vm.onPermissionsChanged(fine.copy(activityRecognition = true))
        assertEquals(AllTheTimeResult.Waiting, vm.state.value.allTheTime)
    }

    @Test
    fun `background location granted in settings outside the page is seen on resume`() {
        val vm = vmAtAutoTrips()
        vm.onAutoTripsToggled(true)
        vm.onAutoTripsContinue()
        vm.onPermissionsChanged(fine.copy(activityRecognition = true, backgroundLocation = true))
        assertEquals(AllTheTimeResult.Granted, vm.state.value.allTheTime)
    }

    @Test
    fun `Android 10 asks for background location in its runtime dialog and has no settings step`() {
        val vm = vmAtAutoTrips(sdk = 29)
        vm.onAutoTripsToggled(true)
        assertTrue(vm.needsRuntimeBackgroundRequest)
        vm.onBackgroundRuntimeResult(fine.copy(activityRecognition = true, backgroundLocation = true))
        assertTrue(settings.automaticTripsEnabled)
        assertEquals("Android 10 has no notification permission either", OnboardingStep.HelpImprove, vm.step)
        assertTrue(vm.state.value.history.none { it == OnboardingStep.AllTheTime })
    }

    @Test
    fun `Android 10 refusing background location falls back to manual mode with the banner and sheet`() {
        val vm = vmAtAutoTrips(sdk = 29)
        vm.onAutoTripsToggled(true)
        vm.onBackgroundRuntimeResult(fine.copy(activityRecognition = true))
        assertFalse(settings.automaticTripsEnabled)
        assertFalse(vm.state.value.autoTripsOn)
        assertTrue(vm.state.value.autoTripsDeclined)
        assertEquals(OnboardingSheet.ManualMode, vm.state.value.sheet)
        vm.onContinueInManualMode()
        assertEquals(OnboardingStep.HelpImprove, vm.step)
    }

    @Test
    fun `automatic trips are only effective with the opt-in and every permission behind them`() {
        val all = fine.copy(activityRecognition = true, backgroundLocation = true)
        assertTrue(OnboardingUiState(permissions = all, autoTripsOn = true).automaticEffective)
        assertFalse(OnboardingUiState(permissions = all, autoTripsOn = false).automaticEffective)
        // Activity denied, then background granted: the toggle is on but the wake path would stay off.
        val noActivity = OnboardingUiState(permissions = all.copy(activityRecognition = false), autoTripsOn = true)
        assertFalse(noActivity.automaticEffective)
        assertTrue(noActivity.automaticWaitingForPermission)
        assertFalse(OnboardingUiState(permissions = all, autoTripsOn = true).automaticWaitingForPermission)
    }

    // endregion

    // region motion and notifications

    @Test
    fun `refusing physical activity explains and Continue still goes on`() {
        val vm = vm(permissions = fine)
        vm.goTo(OnboardingStep.Motion)
        vm.onMotionResult(fine)
        assertEquals(OnboardingStep.Motion, vm.step)
        assertEquals(OnboardingStep.Motion, vm.state.value.deniedStep)
        vm.onContinue()
        assertEquals(OnboardingStep.AutoTrips, vm.step)
    }

    @Test
    fun `refusing notifications explains and Continue goes on, Not now skips the dialog`() {
        val vm = vm(permissions = fine.copy(activityRecognition = true))
        vm.goTo(OnboardingStep.Notifications)
        vm.onNotificationsResult(fine)
        assertEquals(OnboardingStep.Notifications, vm.state.value.deniedStep)
        vm.onContinue()
        assertEquals(OnboardingStep.HelpImprove, vm.step)
    }

    @Test
    fun `a permission granted in settings while on its page moves the flow on by itself`() {
        val vm = vm(permissions = fine)
        vm.goTo(OnboardingStep.Motion)
        vm.onPermissionsChanged(fine.copy(activityRecognition = true))
        assertEquals(OnboardingStep.AutoTrips, vm.step)
    }

    // endregion

    // region battery

    @Test
    fun `the battery step is shown for a listed brand with automatic trips on and not for a Pixel`() {
        val listed = vmAtAutoTrips(oem = samsung)
        listed.onAutoTripsToggled(true)
        listed.onAutoTripsContinue()
        listed.onPermissionsChanged(fine.copy(activityRecognition = true, backgroundLocation = true))
        listed.onContinue() // after the all-the-time result
        listed.onContinue() // notifications
        assertEquals(OnboardingStep.Battery, listed.step)

        val pixel = vmAtAutoTrips(oem = null)
        pixel.onAutoTripsToggled(true)
        pixel.onAutoTripsContinue()
        pixel.onPermissionsChanged(fine.copy(activityRecognition = true, backgroundLocation = true))
        pixel.onContinue()
        pixel.onContinue()
        assertEquals(OnboardingStep.HelpImprove, pixel.step)
    }

    @Test
    fun `done and remind me later are saved, remind later shows the reminder and then continues`() {
        val vm = vm(permissions = fine.copy(activityRecognition = true, backgroundLocation = true, notifications = true), oem = samsung)
        vm.goTo(OnboardingStep.AutoTrips)
        vm.onAutoTripsToggled(true)
        vm.onAutoTripsContinue()
        assertEquals(OnboardingStep.Battery, vm.step)
        vm.onBatteryRemindLater()
        assertEquals(ReliabilityGuideState.RemindLater, settings.guide)
        assertTrue(vm.state.value.batteryReminded)
        vm.onBatteryShowSteps()
        assertFalse(vm.state.value.batteryReminded)
        vm.onBatteryDone()
        assertEquals(ReliabilityGuideState.Done, settings.guide)
        assertEquals(OnboardingStep.HelpImprove, vm.step)
    }

    // endregion

    // region consent

    @Test
    fun `consent toggles are independent and committed only when the user continues`() {
        val vm = vm()
        vm.onCrashReportsChanged(true)
        assertTrue(vm.state.value.crashReports)
        assertFalse(vm.state.value.usageStatistics)
        vm.onUsageStatisticsChanged(true)
        vm.onCrashReportsChanged(false)
        assertTrue(commits.isEmpty())
        vm.goTo(OnboardingStep.HelpImprove)
        assertTrue("nothing is committed by arriving", commits.isEmpty())
        vm.onHelpContinue()
        assertEquals(listOf(ConsentChoices(crashReports = false, usageStatistics = true)), commits)
        assertEquals(OnboardingStep.Ready, vm.step)
    }

    @Test
    fun `continuing with nothing selected commits a decline, never an accept-all`() {
        val vm = vm()
        vm.goTo(OnboardingStep.HelpImprove)
        vm.onHelpContinue()
        assertEquals(listOf(ConsentChoices(crashReports = false, usageStatistics = false)), commits)
    }

    @Test
    fun `the last page finishes onboarding`() {
        val vm = vm()
        vm.goTo(OnboardingStep.Ready)
        vm.onContinue()
        assertTrue(vm.state.value.finished)
    }

    // endregion
}
