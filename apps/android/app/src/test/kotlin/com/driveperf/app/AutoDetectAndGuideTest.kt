package com.driveperf.app

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.driveperf.app.core.AutoDetectOff
import com.driveperf.feature.onboarding.GuideLauncher
import com.driveperf.feature.onboarding.GuideLink
import com.driveperf.feature.onboarding.InAppBrowser
import com.driveperf.feature.onboarding.OnboardingLinks
import com.driveperf.feature.onboarding.PermissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AutoDetectAndGuideTest {
    private val all = PermissionState(fineLocation = true, backgroundLocation = true, activityRecognition = true)

    @Test
    fun `auto-detect is on only with the opt-in and every permission`() {
        assertNull(AutoDetectOff.of(all, optedIn = true))
    }

    @Test
    fun `auto-detect off says why`() {
        assertEquals(AutoDetectOff.OptedOut, AutoDetectOff.of(all, optedIn = false))
        assertEquals(AutoDetectOff.NoLocation, AutoDetectOff.of(all.copy(fineLocation = false), optedIn = true))
        assertEquals(AutoDetectOff.NoBackgroundLocation, AutoDetectOff.of(all.copy(backgroundLocation = false), optedIn = true))
        assertEquals(AutoDetectOff.NoActivityRecognition, AutoDetectOff.of(all.copy(activityRecognition = false), optedIn = true))
    }

    @Test
    fun `only permission reasons have a fix in settings`() {
        assertFalse(AutoDetectOff.OptedOut.fixableInSettings)
        assertTrue(AutoDetectOff.NoBackgroundLocation.fixableInSettings)
    }

    @Test
    fun `terms, privacy and how-it-records open in the in-app browser, only over https`() {
        listOf(OnboardingLinks.TERMS, OnboardingLinks.PRIVACY, OnboardingLinks.HOW_IT_RECORDS).forEach {
            assertTrue(it, InAppBrowser.isAllowed(it))
        }
        assertFalse(InAppBrowser.isAllowed("http://driveperf.com/terms"))
        assertFalse(InAppBrowser.isAllowed("intent://settings"))
    }

    @Test
    fun `guide links resolve to a brand component, battery optimisation, or the app info page`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val component = GuideLauncher.intentFor(context, GuideLink.Component("com.samsung.android.lool", "X"))
        assertEquals("com.samsung.android.lool", component.component?.packageName)
        val battery = GuideLauncher.intentFor(context, GuideLink.BatteryOptimisationSettings)
        assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, battery.action)
        val details = GuideLauncher.intentFor(context, GuideLink.AppDetails)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, details.action)
        assertEquals("package:${context.packageName}", details.data.toString())
    }

    @Test
    fun `the battery exemption request is never the action while OQ-12 is open`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val links = listOf(GuideLink.BatteryOptimisationSettings, GuideLink.AppDetails)
        val actions = links.map { GuideLauncher.intentFor(context, it).action }
        assertFalse(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS in actions)
    }
}
