package com.driveperf.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.feature.replay.ReplayHeader
import com.driveperf.feature.replay.ReplayMapConfig
import com.driveperf.feature.replay.ReplayRoute
import com.driveperf.feature.replay.ReplayViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** DRIVE-200 / DRIVE-61: S-RPL-01 on the plain canvas (no Maps key, as in CI). Its entry from trip detail is in TripDetailUiTest. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ReplayUiTest {
    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private val analytics = object : Analytics {
        override fun track(event: AnalyticsEvent) {
            events += event.name
        }

        override fun setUsageConsent(allowed: Boolean) = Unit

        override fun resetId() = Unit
    }

    private fun replay(online: Boolean = true): ReplayViewModel {
        val vm = replayViewModel(
        ReplayMapConfig(hasMapsKey = false, mapId = null, online = MutableStateFlow(online)),
        analytics,
        compose,
    )
        compose.setContent {
            DrivePerfTheme(darkTheme = true) {
                ReplayRoute(vm, ReplayHeader("Thursday evening", mapId = null, onBack = {}), onShowDriveScreen = {})
            }
        }
        compose.waitForIdle()
        return vm
    }

    @Test fun `without a key the route draws on the canvas, paused at the start with the estimated-start label`() {
        replay()
        compose.onNode(hasTestTag("replay_canvas_map") and hasContentDescription("Map of your drive", substring = true))
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Play replay").assertIsDisplayed()
        compose.onNodeWithText("Play your drive").assertIsDisplayed()
        compose.onNodeWithText("Estimated start").assertIsDisplayed()
        compose.onNodeWithText("Map unavailable offline", substring = true).assertDoesNotExist()
    }

    @Test fun `play runs the marker from the frame clock, and pause stops it`() {
        val vm = replay()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("replay_play").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription("Pause replay").assertIsDisplayed()
        val played = vm.time.doubleValue
        assertTrue("Auto plays the 900 s trip at 15 s per second; got $played", played in 5.0..20.0)
        compose.onNodeWithTag("replay_play").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(played, vm.time.doubleValue, 0.5)
        compose.onNodeWithContentDescription("Play replay").assertIsDisplayed()
        assertTrue("replay_played" in events)
    }

    @Test fun `the span label follows the marker, and the legend opens in a sheet`() {
        val vm = replay()
        vm.seekTo(380.0)
        compose.waitForIdle()
        compose.onNodeWithText("No GPS").assertIsDisplayed()
        compose.onNodeWithText("—").assertIsDisplayed()
        vm.seekTo(630.0)
        compose.waitForIdle()
        compose.onNodeWithText("Phone moved — not scored").assertIsDisplayed()
        compose.onNodeWithContentDescription("What the line styles mean").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Line styles").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Line styles").assertDoesNotExist()
        assertEquals(1, events.count { it == "quality_overlay_legend_opened" })
    }

    @Test fun `offline the canvas says the map is unavailable`() {
        replay(online = false)
        compose.onNodeWithText("Map unavailable offline", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("replay_canvas_map").assertIsDisplayed()
    }
}
