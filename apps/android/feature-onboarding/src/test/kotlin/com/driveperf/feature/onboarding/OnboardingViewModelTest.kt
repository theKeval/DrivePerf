package com.driveperf.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingViewModelTest {
    private fun vm(permissions: PermissionState = PermissionState()) = OnboardingViewModel(permissions, sdkInt = 34)

    private fun OnboardingViewModel.goTo(step: OnboardingStep) {
        while (state.value.step != step) onContinue()
    }

    @Test
    fun `consent starts off and is never pre-selected`() {
        val state = vm().state.value
        assertFalse(state.crashReports)
        assertFalse(state.usageStatistics)
    }

    @Test
    fun `consent toggles are independent`() {
        val vm = vm()
        vm.onCrashReportsChanged(true)
        assertTrue(vm.state.value.crashReports)
        assertFalse(vm.state.value.usageStatistics)
        vm.onUsageStatisticsChanged(true)
        vm.onCrashReportsChanged(false)
        assertTrue(vm.state.value.usageStatistics)
        assertFalse(vm.state.value.crashReports)
    }

    @Test
    fun `granting location on the location page moves to the all-the-time page`() {
        val vm = vm()
        vm.goTo(OnboardingStep.Location)
        vm.onPermissionsChanged(PermissionState(fineLocation = true))
        assertEquals(OnboardingStep.BackgroundLocation, vm.state.value.step)
    }

    @Test
    fun `a refused permission keeps the page and marks it denied`() {
        val vm = vm()
        vm.goTo(OnboardingStep.Location)
        vm.onPermissionsChanged(PermissionState())
        vm.onPermissionDenied(OnboardingStep.Location)
        assertEquals(OnboardingStep.Location, vm.state.value.step)
        assertEquals(OnboardingStep.Location, vm.state.value.deniedStep)
    }

    @Test
    fun `continuing past a refused location page skips the pages that need it`() {
        val vm = vm()
        vm.goTo(OnboardingStep.Location)
        vm.onPermissionDenied(OnboardingStep.Location)
        vm.onContinue()
        assertEquals(OnboardingStep.HelpImprove, vm.state.value.step)
        assertNull(vm.state.value.deniedStep)
    }

    @Test
    fun `granting from Settings while on a permission page moves on when the app resumes`() {
        val vm = vm()
        vm.goTo(OnboardingStep.Location)
        vm.onPermissionDenied(OnboardingStep.Location)
        vm.onPermissionsChanged(PermissionState(fineLocation = true)) // resumed from Settings
        assertEquals(OnboardingStep.BackgroundLocation, vm.state.value.step)
        assertNull(vm.state.value.deniedStep)
    }

    @Test
    fun `resuming on a non-permission page changes nothing`() {
        val vm = vm()
        vm.onPermissionsChanged(PermissionState(fineLocation = true))
        assertEquals(OnboardingStep.Welcome, vm.state.value.step)
    }

    @Test
    fun `back returns to the previous page`() {
        val vm = vm()
        vm.onContinue()
        vm.onBack()
        assertEquals(OnboardingStep.Welcome, vm.state.value.step)
    }

    @Test
    fun `finishing the last page marks onboarding finished`() {
        val vm = vm(PermissionState(fineLocation = true, backgroundLocation = true, activityRecognition = true, notifications = true))
        vm.goTo(OnboardingStep.Ready)
        assertFalse(vm.state.value.finished)
        vm.onContinue()
        assertTrue(vm.state.value.finished)
    }
}
