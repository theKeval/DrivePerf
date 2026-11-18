package com.driveperf.app

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.driveperf.app.core.autoDetectStatus
import com.driveperf.app.core.forCapture
import com.driveperf.app.navigation.EnableAutoTrips
import com.driveperf.app.navigation.FixStep
import com.driveperf.feature.capture.AttentionReason
import com.driveperf.feature.capture.AutoDetectMode
import com.driveperf.feature.capture.CapturePermissions
import com.driveperf.feature.onboarding.GuideLauncher
import com.driveperf.feature.onboarding.GuideLink
import com.driveperf.feature.onboarding.InAppBrowser
import com.driveperf.feature.onboarding.OnboardingLinks
import com.driveperf.feature.onboarding.PermissionState
import com.driveperf.platform.wake.WakeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `onboarding permissions map onto the capture rules`() {
        assertEquals(CapturePermissions.ALL, all.forCapture())
        val approximate = all.copy(fineLocation = false, approximateOnly = true).forCapture()
        assertEquals(CapturePermissions(location = true, precise = false, background = true, activity = true), approximate)
        assertEquals(false, PermissionState().forCapture().location)
    }

    @Test
    fun `one rule set decides both the status and whether wakes may start a trip`() {
        val status = autoDetectStatus(true, all.copy(activityRecognition = false), WakeStatus(false, false), lastCheckInMs = 7L)
        assertEquals(AutoDetectMode.NeedsAttention, status.mode)
        assertEquals(listOf(AttentionReason.NoActivityRecognition), status.reasons)
        assertEquals(7L, status.lastCheckInMs)
        assertEquals(AutoDetectMode.Ready, autoDetectStatus(true, all, WakeStatus(false, false), null).mode)
        assertEquals(AutoDetectMode.Off, autoDetectStatus(false, all, WakeStatus(false, false), null).mode)
    }

    @Test
    fun `each reason uses its onboarding step, the dialog while Android shows it`() {
        assertEquals(FixStep.LocationDialog, FixStep.of(AttentionReason.NoLocation, Build.VERSION_CODES.S))
        assertEquals(FixStep.LocationDialog, FixStep.of(AttentionReason.NoPreciseLocation, Build.VERSION_CODES.S))
        assertEquals(FixStep.BackgroundSettings, FixStep.of(AttentionReason.NoBackgroundLocation, Build.VERSION_CODES.S))
        assertEquals(FixStep.BackgroundDialog, FixStep.of(AttentionReason.NoBackgroundLocation, Build.VERSION_CODES.Q))
        assertEquals(FixStep.ActivityDialog, FixStep.of(AttentionReason.NoActivityRecognition, Build.VERSION_CODES.S))
        assertEquals(FixStep.None, FixStep.of(AttentionReason.ActivityUpdatesFailed, Build.VERSION_CODES.S))
    }

    @Test
    fun `App info opens only when the permission is still missing and Android will not ask again`() {
        assertTrue(FixStep.needsAppSettings(stillMissing = true, systemWouldAskAgain = false))
        assertFalse(FixStep.needsAppSettings(stillMissing = true, systemWouldAskAgain = true))
        assertFalse(FixStep.needsAppSettings(stillMissing = false, systemWouldAskAgain = false))
    }

    @Test
    fun `turning automatic trips on walks the missing permissions once each, then stops`() {
        val tried = mutableListOf<AttentionReason>()
        val session = EnableAutoTrips().apply { fix = { tried += it } }
        val missing = CapturePermissions(location = true, precise = true, background = false, activity = false)

        session.begin(missing)
        assertEquals(listOf(AttentionReason.NoBackgroundLocation), tried)
        session.continueWith(missing) // refused in Settings: move on, never ask again in this attempt
        assertEquals(listOf(AttentionReason.NoBackgroundLocation, AttentionReason.NoActivityRecognition), tried)
        session.continueWith(missing)
        session.continueWith(missing)
        assertEquals(2, tried.size)
    }

    @Test
    fun `nothing is asked when everything is granted, or after the switch is turned off`() {
        val tried = mutableListOf<AttentionReason>()
        val session = EnableAutoTrips().apply { fix = { tried += it } }
        session.begin(CapturePermissions.ALL)
        assertTrue(tried.isEmpty())

        session.begin(CapturePermissions.ALL.copy(activity = false, precise = false))
        session.cancel()
        session.continueWith(CapturePermissions.ALL.copy(activity = false, precise = false))
        assertEquals(listOf(AttentionReason.NoActivityRecognition), tried)
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
