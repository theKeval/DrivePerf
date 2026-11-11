package com.driveperf.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.feature.capture.CaptureUiState
import com.driveperf.feature.capture.GpsHealth
import com.driveperf.feature.capture.InDriveScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class) // not DrivePerfApplication: no Koin or services in UI tests
class InDriveUiTest {
    @get:Rule val compose = createComposeRule()

    private var requested = 0
    private var dismissed = 0
    private var confirmed = 0

    private fun show(confirming: Boolean = false, gps: GpsHealth = GpsHealth.GOOD) {
        compose.setContent {
            DrivePerfTheme(darkTheme = true) {
                InDriveScreen(
                    state = CaptureUiState.Recording(elapsed = "24:05", distanceKm = "12.4", gps = gps, confirmingStop = confirming),
                    onStopRequested = { requested++ },
                    onStopDismissed = { dismissed++ },
                    onStopConfirmed = { confirmed++ },
                )
            }
        }
    }

    @Test
    fun `shows elapsed time, distance and GPS health`() {
        show()
        compose.onNodeWithText("24:05").assertIsDisplayed()
        compose.onNodeWithText("12.4 km").assertIsDisplayed()
        compose.onNodeWithText("GPS signal good").assertIsDisplayed()
    }

    @Test
    fun `GPS trouble is stated in words`() {
        show(gps = GpsHealth.LOST)
        compose.onNodeWithText("GPS signal lost").assertIsDisplayed()
    }

    @Test
    fun `shows no score and no speed while driving`() {
        show()
        for (forbidden in listOf("km/h", "mph", "Score", "Smoothness")) {
            val found = compose.onAllNodesWithText(forbidden, substring = true).fetchSemanticsNodes().size
            assertEquals("must not show \"$forbidden\" while driving", 0, found)
        }
    }

    @Test
    fun `tapping Stop asks first and does not end the drive`() {
        show()
        compose.onNodeWithContentDescription("Stop").performClick()
        assertEquals(1, requested)
        assertEquals(0, confirmed)
    }

    @Test
    fun `the confirmation can end the drive or keep recording`() {
        show(confirming = true)
        compose.onNodeWithText("End this drive?").assertIsDisplayed()
        compose.onNodeWithText("Keep recording").performClick()
        assertEquals(1, dismissed)
        compose.onNodeWithText("End drive").performClick()
        assertEquals(1, confirmed)
    }
}
