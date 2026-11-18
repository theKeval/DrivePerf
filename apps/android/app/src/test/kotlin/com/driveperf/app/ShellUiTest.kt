package com.driveperf.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.material3.Text
import com.driveperf.app.navigation.ShellCallbacks
import com.driveperf.app.navigation.Tab
import com.driveperf.app.navigation.TabScaffold
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.feature.capture.AttentionReason
import com.driveperf.feature.capture.AutoDetectReadiness
import com.driveperf.feature.capture.AutoDetectStatus
import com.driveperf.feature.capture.CapturePermissions
import com.driveperf.feature.capture.CaptureUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class) // not DrivePerfApplication: no Koin or services in UI tests
class ShellUiTest {
    @get:Rule val compose = createComposeRule()

    private var selected: Tab? = null
    private var profileClicks = 0
    private var starts = 0
    private var locationFixes = 0
    private val fixed = mutableListOf<AttentionReason>()
    private var recordingSettingsOpened = 0

    private fun show(
        tab: Tab = Tab.Drives,
        capture: CaptureUiState = CaptureUiState.Ready,
        autoDetect: AutoDetectStatus? = null,
    ) {
        compose.setContent {
            DrivePerfTheme(darkTheme = true) {
                TabScaffold(
                    selected = tab,
                    capture = capture,
                    callbacks = ShellCallbacks(
                        onTabSelected = { selected = it },
                        onProfile = { profileClicks++ },
                        onStart = { starts++ },
                        onOpenDrive = {},
                        onFixLocation = { locationFixes++ },
                        onFixReason = { fixed += it },
                        onOpenRecordingSettings = { recordingSettingsOpened++ },
                        autoDetect = autoDetect,
                    ),
                ) { Text("tab content") }
            }
        }
    }

    @Test
    fun `the bar has exactly the four tabs`() {
        show()
        for (label in listOf("Drives", "Routes", "Insights", "Garage")) {
            assertTrue("missing tab $label", compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty())
        }
        assertEquals("Profile is an avatar, never a fifth tab", 0, compose.onAllNodesWithText("Profile").fetchSemanticsNodes().size)
    }

    @Test
    fun `the profile avatar is in the top bar`() {
        show()
        compose.onNodeWithContentDescription("Profile").assertIsDisplayed().performClick()
        assertEquals(1, profileClicks)
    }

    @Test
    fun `tapping a tab reports it`() {
        show()
        compose.onNodeWithText("Insights").performClick()
        assertEquals(Tab.Insights, selected)
    }

    @Test
    fun `the floating capture toolbar sits on the Drives tab`() {
        show(Tab.Drives)
        compose.onNodeWithText("Ready to record").assertIsDisplayed()
    }

    @Test
    fun `the toolbar is not shown on the other tabs`() {
        show(Tab.Routes)
        assertEquals(0, compose.onAllNodesWithText("Ready to record").fetchSemanticsNodes().size)
    }

    @Test
    fun `the Start button starts a drive`() {
        show()
        compose.onNodeWithContentDescription("Start recording a drive").performClick()
        assertEquals(1, starts)
        assertEquals(0, locationFixes)
    }

    @Test
    fun `without location the button offers to fix it and does not start`() {
        show(capture = CaptureUiState.NeedsLocation)
        compose.onNodeWithText("Location needed to record").assertIsDisplayed()
        compose.onNodeWithContentDescription("Allow location to record").performClick()
        assertEquals(1, locationFixes)
        assertEquals(0, starts)
    }

    @Test
    fun `needs attention names the reason on the toolbar and the banner, and Fix opens the status sheet`() {
        val whileUsing = AutoDetectReadiness.status(optedIn = true, permissions = CapturePermissions.ALL.copy(background = false))
        show(autoDetect = whileUsing)
        compose.onNodeWithText("Location is set to While using the app").assertIsDisplayed()
        assertEquals("toolbar and banner", 2, compose.onAllNodesWithText("Auto-detect needs attention").fetchSemanticsNodes().size)
        compose.onNodeWithText("Fix").performClick()

        compose.onNodeWithText("Capture status").assertIsDisplayed()
        compose.onNodeWithText("1 to fix").assertExists()
        compose.onNodeWithText("While using the app only").assertExists()
        compose.onAllNodesWithText("Fix")[1].performClick() // [0] is the banner's
        assertEquals(listOf(AttentionReason.NoBackgroundLocation), fixed)
    }

    @Test
    fun `the toolbar opens the status sheet, which links to Recording settings`() {
        val now = System.currentTimeMillis()
        val ready = AutoDetectReadiness.status(optedIn = true, permissions = CapturePermissions.ALL, lastCheckInMs = now)
        show(autoDetect = ready)
        compose.onNodeWithText("Last check-in just now").assertIsDisplayed()
        compose.onNodeWithText("Auto-detect on · ready").performClick()
        compose.onNodeWithText("Checks · all 3 OK").assertExists()
        compose.onNodeWithText("Recording settings").performScrollTo().performClick()
        assertEquals(1, recordingSettingsOpened)
    }

    @Test
    fun `manual trips say auto-detect is off and offer Turn on, not a permission fix`() {
        show(autoDetect = AutoDetectReadiness.status(optedIn = false, permissions = CapturePermissions.ALL))
        compose.onNodeWithText("Auto-detect off").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Fix").fetchSemanticsNodes().size)
        compose.onNodeWithText("Turn on").performClick()
        assertEquals(1, recordingSettingsOpened)
    }

    @Test
    fun `the status sheet opens fully expanded, so a swipe never leaves it settling between stops`() {
        val activityOff = AutoDetectReadiness.status(optedIn = true, permissions = CapturePermissions.ALL.copy(activity = false))
        show(autoDetect = activityOff)
        compose.onNodeWithText("Physical activity is off").performClick() // the toolbar's second line
        compose.waitForIdle()

        val sheetRoot = compose.onAllNodes(isRoot()).fetchSemanticsNodes().last().size.height
        val title = compose.onNodeWithText("Capture status").getUnclippedBoundsInRoot()
        with(compose.density) {
            assertTrue("sheet title at ${title.top.toPx()} of $sheetRoot px", title.top.toPx() < sheetRoot / 4f)
        }
        compose.onAllNodesWithText("Fix")[1].performScrollTo().performClick() // [0] is the banner's
        assertEquals(listOf(AttentionReason.NoActivityRecognition), fixed)
    }
}
