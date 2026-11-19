package com.driveperf.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.feature.drives.DrivesHomeScreen
import com.driveperf.feature.drives.REPLAY_BUTTON_TAG
import com.driveperf.feature.replay.ReplayHeader
import com.driveperf.feature.replay.ReplayMapConfig
import com.driveperf.feature.replay.ReplayRoute
import com.driveperf.feature.replay.ReplayViewModel
import com.driveperf.presentation.drives.DrivesIntent
import com.driveperf.presentation.drives.DrivesState
import com.driveperf.presentation.drives.TripDetailUi
import com.driveperf.presentation.drives.TripRowUi
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** DRIVE-200 / DRIVE-61: S-RPL-01 on the plain canvas (no Maps key, as in CI) and its entry from the trip sheet. */
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

    private fun tripSheet(actions: MutableList<DrivesIntent>, open: () -> Boolean = { true }) {
        val detail = TripDetailUi("trip-7", "Tuesday morning", emptyList(), emptyList(), null, TripCorrection.DRIVER)
        compose.setContent {
            DrivePerfTheme(darkTheme = true) {
                DrivesHomeScreen(
                    state = DrivesState(
                        loaded = true,
                        trips = listOf(TripRowUi("trip-7", "Tuesday morning", "10 Nov · 8:05 AM", "8.1 km", "12 min")),
                        detail = detail.takeIf { open() },
                    ),
                    onSeeAll = {},
                    onAction = { actions += it },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test fun `Replay on the trip sheet closes the sheet, then opens Replay for that trip`() {
        val actions = mutableListOf<DrivesIntent>()
        tripSheet(actions)
        compose.onNodeWithTag(REPLAY_BUTTON_TAG).assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(listOf<DrivesIntent>(DrivesIntent.OpenReplay("trip-7")), actions)
    }

    @Test fun `Replay is disabled while the sheet slides away, so it opens once`() {
        val actions = mutableListOf<DrivesIntent>()
        tripSheet(actions)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(REPLAY_BUTTON_TAG).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(REPLAY_BUTTON_TAG).assertIsNotEnabled()
        assertEquals("nothing goes out before the hide completes", emptyList<DrivesIntent>(), actions)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(listOf<DrivesIntent>(DrivesIntent.OpenReplay("trip-7")), actions)
    }

    @Test fun `a hide cut short when the sheet goes away opens nothing`() {
        val actions = mutableListOf<DrivesIntent>()
        var open by mutableStateOf(true)
        tripSheet(actions) { open }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(REPLAY_BUTTON_TAG).performClick()
        compose.mainClock.advanceTimeByFrame()
        open = false
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(emptyList<DrivesIntent>(), actions)
    }
}
