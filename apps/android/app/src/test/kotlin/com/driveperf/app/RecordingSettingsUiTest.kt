package com.driveperf.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.driveperf.designsystem.DpHealth
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.feature.settings.RecordingSettingsScreen
import com.driveperf.feature.settings.RecordingSettingsUiState
import com.driveperf.feature.settings.RecordingStatusLine
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RecordingSettingsUiTest {
    @get:Rule val compose = createComposeRule()

    private val changes = mutableListOf<Boolean>()
    private var statusClicks = 0

    private fun show(state: RecordingSettingsUiState) {
        compose.setContent {
            DrivePerfTheme(darkTheme = true) {
                RecordingSettingsScreen(state, onAutomaticTripsChanged = { changes += it }, onStatusClick = { statusClicks++ })
            }
        }
    }

    @Test
    fun `off shows manual mode and the switch turns automatic trips on`() {
        show(RecordingSettingsUiState(automaticTrips = false, status = null))
        compose.onNodeWithText("Manual mode. Tap Start in the app when you set off.").assertIsDisplayed()
        compose.onNodeWithText("Automatic trips").assertIsOff().performClick()
        assertEquals(listOf(true), changes)
    }

    @Test
    fun `on shows the status row, which opens the capture status sheet, and the switch turns it off`() {
        val attention = RecordingStatusLine(DpHealth.Fix, "Needs fixing", "Auto-detect needs attention", "Physical activity is off")
        show(RecordingSettingsUiState(automaticTrips = true, status = attention))
        assertEquals(0, compose.onAllNodesWithText("Manual mode", substring = true).fetchSemanticsNodes().size)
        compose.onNodeWithText("Physical activity is off").performClick()
        assertEquals(1, statusClicks)
        compose.onNodeWithText("Automatic trips").assertIsOn().performClick()
        assertEquals(listOf(false), changes)
    }
}
