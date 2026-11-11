package com.driveperf.feature.onboarding

import com.driveperf.feature.onboarding.OnboardingStep.ActivityRecognition
import com.driveperf.feature.onboarding.OnboardingStep.BackgroundLocation
import com.driveperf.feature.onboarding.OnboardingStep.Battery
import com.driveperf.feature.onboarding.OnboardingStep.HelpImprove
import com.driveperf.feature.onboarding.OnboardingStep.HowItWorks
import com.driveperf.feature.onboarding.OnboardingStep.Location
import com.driveperf.feature.onboarding.OnboardingStep.Notifications
import com.driveperf.feature.onboarding.OnboardingStep.Ready
import com.driveperf.feature.onboarding.OnboardingStep.Welcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingFlowTest {
    private val android14 = 34
    private val android12 = 31
    private val nothing = PermissionState()
    private val fine = PermissionState(fineLocation = true)

    private fun walk(permissions: () -> PermissionState, sdk: Int): List<OnboardingStep> {
        val steps = mutableListOf(Welcome)
        while (true) steps += OnboardingFlow.next(steps.last(), permissions(), sdk) ?: break
        return steps
    }

    @Test
    fun `fine location comes first, then all-the-time as its own step, then activity, then notifications`() {
        // As the user grants each permission in turn.
        var granted = nothing
        val seen = mutableListOf(Welcome)
        while (true) {
            val next = OnboardingFlow.next(seen.last(), granted, android14) ?: break
            seen += next
            granted = when (next) {
                Location -> granted.copy(fineLocation = true)
                BackgroundLocation -> granted.copy(backgroundLocation = true)
                ActivityRecognition -> granted.copy(activityRecognition = true)
                Notifications -> granted.copy(notifications = true)
                else -> granted
            }
        }
        assertEquals(
            listOf(Welcome, HowItWorks, Location, BackgroundLocation, ActivityRecognition, Notifications, Battery, HelpImprove, Ready),
            seen,
        )
    }

    @Test
    fun `background location is never offered before fine location`() {
        val order = walk({ nothing }, android14)
        assertTrue(BackgroundLocation !in order)
        assertEquals(listOf(Welcome, HowItWorks, Location, HelpImprove, Ready), order)
    }

    @Test
    fun `granted permissions are not asked for again`() {
        val all = PermissionState(fineLocation = true, backgroundLocation = true, activityRecognition = true, notifications = true)
        assertEquals(listOf(Welcome, HowItWorks, Battery, HelpImprove, Ready), walk({ all }, android14))
    }

    @Test
    fun `notifications need no request below Android 13`() {
        assertTrue(Notifications !in walk({ fine }, android12))
        assertTrue(Notifications in walk({ fine }, android14))
    }

    @Test
    fun `the last page has no next`() {
        assertNull(OnboardingFlow.next(Ready, fine, android14))
    }

    @Test
    fun `back skips the same pages forward would`() {
        assertEquals(Location, OnboardingFlow.previous(HelpImprove, nothing, android14))
        assertNull(OnboardingFlow.previous(Welcome, nothing, android14))
    }

    @Test
    fun `progress rises to one on the last page`() {
        val first = OnboardingFlow.progress(Welcome, fine, android14)
        val last = OnboardingFlow.progress(Ready, fine, android14)
        assertEquals(1f, last, 1e-6f)
        assertTrue(first in 0f..0.5f)
    }
}
