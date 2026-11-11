package com.driveperf.feature.onboarding

import com.driveperf.feature.onboarding.OnboardingStep.AgeTerms
import com.driveperf.feature.onboarding.OnboardingStep.AllTheTime
import com.driveperf.feature.onboarding.OnboardingStep.AutoTrips
import com.driveperf.feature.onboarding.OnboardingStep.Battery
import com.driveperf.feature.onboarding.OnboardingStep.CapturePromise
import com.driveperf.feature.onboarding.OnboardingStep.HelpImprove
import com.driveperf.feature.onboarding.OnboardingStep.Location
import com.driveperf.feature.onboarding.OnboardingStep.Motion
import com.driveperf.feature.onboarding.OnboardingStep.Notifications
import com.driveperf.feature.onboarding.OnboardingStep.Privacy
import com.driveperf.feature.onboarding.OnboardingStep.Ready
import com.driveperf.feature.onboarding.OnboardingStep.StoryPreview
import com.driveperf.feature.onboarding.OnboardingStep.Welcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingFlowTest {
    private val android14 = 34
    private val android11 = 30
    private val android10 = 29
    private val android12 = 31
    private val samsung = OemGuides.forDevice("samsung")
    private val nothing = PermissionState()
    private val fine = PermissionState(fineLocation = true)

    private fun ctx(p: PermissionState = nothing, auto: Boolean = false, oem: OemGuide? = null, sdk: Int = android14) =
        FlowContext(p, auto, oem, sdk)

    private fun walk(context: FlowContext): List<OnboardingStep> {
        val steps = mutableListOf(Welcome)
        while (true) steps += OnboardingFlow.next(steps.last(), context) ?: break
        return steps
    }

    @Test
    fun `a fresh install on Android 14 sees every page up to the choices in the order of the PRD`() {
        // Nothing granted yet: the pages that depend on a grant are not reachable until it happens.
        assertEquals(
            listOf(Welcome, StoryPreview, Privacy, AgeTerms, CapturePromise, Location, Notifications, HelpImprove, Ready),
            walk(ctx()),
        )
    }

    @Test
    fun `with precise location and the opt-in the whole Android 11 plus flow is shown, battery for a listed brand`() {
        val context = ctx(fine, auto = true, oem = samsung)
        assertEquals(
            listOf(
                Welcome, StoryPreview, Privacy, AgeTerms, CapturePromise, Motion, AutoTrips, AllTheTime, Notifications, Battery,
                HelpImprove, Ready,
            ),
            walk(context),
        )
    }

    @Test
    fun `automatic trips off skips the all-the-time and battery pages`() {
        val order = walk(ctx(fine, auto = false, oem = samsung))
        assertTrue(AllTheTime !in order)
        assertTrue(Battery !in order)
        assertTrue(AutoTrips in order)
    }

    @Test
    fun `a Pixel or any unlisted brand skips the battery page and it stays out of the flow`() {
        assertTrue(Battery !in walk(ctx(fine, auto = true, oem = null)))
        assertNull(OemGuides.forDevice("Google", "google"))
    }

    @Test
    fun `Android 10 has no separate settings step, its runtime dialog offers background location`() {
        assertTrue(AllTheTime !in walk(ctx(fine, auto = true, sdk = android10)))
        assertTrue(AllTheTime in walk(ctx(fine, auto = true, sdk = android11)))
    }

    @Test
    fun `background location already granted skips the settings step`() {
        assertTrue(AllTheTime !in walk(ctx(fine.copy(backgroundLocation = true), auto = true)))
    }

    @Test
    fun `without location nothing but notifications and the choices follow`() {
        val order = walk(ctx(nothing))
        assertTrue(listOf(Motion, AutoTrips, AllTheTime, Battery).none { it in order })
    }

    @Test
    fun `approximate location continues to motion but not to automatic trips`() {
        val order = walk(ctx(PermissionState(approximateOnly = true), auto = true, oem = samsung))
        assertTrue(Motion in order)
        assertTrue(AutoTrips !in order)
        assertTrue(Battery !in order)
    }

    @Test
    fun `granted permissions are not asked for again`() {
        val all = PermissionState(fineLocation = true, backgroundLocation = true, activityRecognition = true, notifications = true)
        assertEquals(
            listOf(Welcome, StoryPreview, Privacy, AgeTerms, CapturePromise, AutoTrips, HelpImprove, Ready),
            walk(ctx(all, auto = false)),
        )
    }

    @Test
    fun `notifications need no request below Android 13`() {
        assertTrue(Notifications !in walk(ctx(fine, sdk = android12)))
        assertTrue(Notifications in walk(ctx(fine, sdk = android14)))
    }

    @Test
    fun `the last page has no next`() {
        assertNull(OnboardingFlow.next(Ready, ctx(fine)))
    }

    @Test
    fun `back skips the same pages forward would`() {
        assertEquals(Notifications, OnboardingFlow.previous(HelpImprove, ctx(nothing)))
        assertNull(OnboardingFlow.previous(Welcome, ctx()))
    }

    @Test
    fun `setup positions count the numbered pages from age to the analytics choice`() {
        val context = ctx(fine, auto = true, oem = samsung)
        val setup = OnboardingFlow.shown(context).filter { OnboardingFlow.setupPosition(it, context) != null }
        assertEquals(listOf(AgeTerms), setup.take(1))
        assertEquals(SetupPosition(1, setup.size), OnboardingFlow.setupPosition(AgeTerms, context))
        assertEquals(SetupPosition(setup.size, setup.size), OnboardingFlow.setupPosition(HelpImprove, context))
        assertNull(OnboardingFlow.setupPosition(Welcome, context))
        assertNull(OnboardingFlow.setupPosition(Ready, context))
    }
}
