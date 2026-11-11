package com.driveperf.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.material3.Text
import com.driveperf.app.navigation.ShellCallbacks
import com.driveperf.app.navigation.Tab
import com.driveperf.app.navigation.TabScaffold
import com.driveperf.designsystem.DrivePerfTheme
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

    private fun show(tab: Tab = Tab.Drives, capture: CaptureUiState = CaptureUiState.Ready) {
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
}
