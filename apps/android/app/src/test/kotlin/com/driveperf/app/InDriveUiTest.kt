package com.driveperf.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.feature.capture.CaptureUiState
import com.driveperf.feature.capture.DriveCover
import com.driveperf.feature.capture.DriveCoverHost
import com.driveperf.feature.capture.GpsHealth
import com.driveperf.feature.capture.HeldNoun
import com.driveperf.feature.capture.InDriveActions
import com.driveperf.feature.capture.PassengerActions
import com.driveperf.feature.capture.PassengerStep
import com.driveperf.feature.capture.SavedKind
import com.driveperf.feature.capture.StopActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Not DrivePerfApplication: no Koin or services in UI tests. A phone-sized screen, so the whole bottom sheet shows.
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class InDriveUiTest {
    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private val actions = InDriveActions(
        stop = StopActions(
            onRequested = { events += "stopRequested" },
            onDismissed = { events += "stopDismissed" },
            onConfirmed = { events += "stopConfirmed" },
        ),
        passenger = PassengerActions(
            onRequested = { events += "passengerRequested" },
            onDismissed = { events += "passengerDismissed" },
            onKept = { events += "passengerKept" },
            onDiscardRequested = { events += "discardRequested" },
            onDiscardConfirmed = { events += "discardConfirmed" },
        ),
        onCoverDismissed = { events += "coverDismissed" },
    )

    private fun recording(
        confirming: Boolean = false,
        gps: GpsHealth = GpsHealth.GOOD,
        step: PassengerStep = PassengerStep.HIDDEN,
        shortTrip: Boolean = false,
        carName: String? = null,
    ) = CaptureUiState.Recording(
        elapsed = "24:05",
        distanceKm = "12.4",
        gps = gps,
        confirmingStop = confirming,
        startedAtLabel = "5:31 PM",
        shortTrip = shortTrip,
        carName = carName,
        passengerStep = step,
    )

    private fun show(cover: DriveCover) {
        compose.setContent { DrivePerfTheme(darkTheme = true) { DriveCoverHost(cover, actions) } }
    }

    private fun show(state: CaptureUiState.Recording) = show(DriveCover.InDrive(state))

    @Test
    fun `shows elapsed time, distance, GPS health and how the trip started`() {
        show(recording())
        compose.onNodeWithText("Recording your drive").assertIsDisplayed()
        compose.onNodeWithText("24:05").assertIsDisplayed()
        compose.onNodeWithText("12.4 km").assertIsDisplayed()
        compose.onNodeWithText("GPS signal good").assertIsDisplayed()
        compose.onNodeWithText("Started by you · 5:31 PM").assertIsDisplayed()
    }

    @Test
    fun `GPS trouble is stated in words`() {
        show(recording(gps = GpsHealth.LOST))
        compose.onNodeWithText("GPS signal lost").assertIsDisplayed()
    }

    @Test
    fun `shows no score, speed, events or tips while driving`() {
        show(recording())
        for (forbidden in listOf("km/h", "mph", "Score", "Smoothness", "event", "tip", "Story")) {
            val found = compose.onAllNodesWithText(forbidden, substring = true, ignoreCase = true).fetchSemanticsNodes().size
            assertEquals("must not show \"$forbidden\" while driving", 0, found)
        }
    }

    @Test
    fun `the car name shows only when there is one`() {
        show(recording(carName = "GTI"))
        compose.onNodeWithText("GTI").assertIsDisplayed()
    }

    @Test
    fun `Stop and I'm a passenger are both large touch targets`() {
        show(recording())
        for (label in listOf("Stop", "I'm a passenger")) {
            val bounds = compose.onNodeWithText(label).getBoundsInRoot()
            assertTrue("$label is at least 64 dp tall", bounds.bottom - bounds.top >= 64.dp)
        }
    }

    @Test
    fun `tapping Stop asks first and does not end the drive`() {
        show(recording())
        compose.onNodeWithText("Stop").performClick()
        assertEquals(listOf("stopRequested"), events)
    }

    @Test
    fun `the confirmation can end the drive or keep recording`() {
        show(recording(confirming = true))
        compose.onNodeWithText("End this drive?").assertIsDisplayed()
        compose.onNodeWithText("Keep recording").performClick()
        compose.onNodeWithText("End drive").performClick()
        assertEquals(listOf("stopDismissed", "stopConfirmed"), events)
    }

    @Test
    fun `a very short trip is warned about in the confirmation`() {
        show(recording(confirming = true, shortTrip = true))
        compose.onNodeWithText("End anyway").assertIsDisplayed()
        compose.onNodeWithText("Short trip", substring = true).assertIsDisplayed()
    }

    // ---- passenger sheet (S-DRV-03) ----

    @Test
    fun `the passenger button opens the sheet request`() {
        show(recording())
        compose.onNodeWithText("I'm a passenger").performClick()
        assertEquals(listOf("passengerRequested"), events)
    }

    @Test
    fun `the passenger sheet offers keep, don't save and cancel`() {
        show(recording(step = PassengerStep.CHOOSE))
        compose.onNodeWithText("Keep the trip as a passenger trip").assertIsDisplayed()
        compose.onNodeWithText("Don't save this trip").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Keep the trip as a passenger trip").performClick()
        compose.onNodeWithText("Don't save this trip").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf("passengerKept", "discardRequested", "passengerDismissed"), events)
    }

    @Test
    fun `deleting asks a second time`() {
        show(recording(step = PassengerStep.CONFIRM_DISCARD))
        compose.onNodeWithText("Delete this trip?").assertIsDisplayed()
        compose.onNodeWithText("Delete this trip").performClick()
        assertEquals(listOf("discardConfirmed"), events)
    }

    // ---- S-DRV-05 and S-DRV-04 ----

    @Test
    fun `held content says the Story will be ready when you've parked, and nothing else`() {
        show(DriveCover.Held(HeldNoun.STORY, PassengerStep.HIDDEN, canMarkPassenger = true))
        compose.onNodeWithText("Your Story will be ready when you've parked.").assertIsDisplayed()
        compose.onNodeWithText("I'm a passenger").performClick()
        assertEquals(listOf("passengerRequested"), events)
    }

    @Test
    fun `a held Replay is named, and after the trip there is no passenger button`() {
        show(DriveCover.Held(HeldNoun.REPLAY, PassengerStep.HIDDEN, canMarkPassenger = false))
        compose.onNodeWithText("Your Replay will be ready when you've parked.").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("I'm a passenger").fetchSemanticsNodes().size)
    }

    @Test
    fun `the decoding screen can be left`() {
        show(DriveCover.Decoding("t1", passenger = false))
        compose.onNodeWithText("Decoding your drive…").assertIsDisplayed()
        compose.onNodeWithText("Back to Drives").performClick()
        assertEquals(listOf("coverDismissed"), events)
    }

    @Test
    fun `a saved drive shows its numbers and the timeout note when the timeout ended it`() {
        show(DriveCover.Saved("t1", "12.4", 24, SavedKind.DRIVE, autoEnded = true))
        compose.onNodeWithText("Drive saved").assertIsDisplayed()
        compose.onNodeWithText("12.4 km · 24 min").assertIsDisplayed()
        compose.onNodeWithText("There was no movement for 15 minutes", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Back to Drives").performClick()
        assertEquals(listOf("coverDismissed"), events)
    }

    @Test
    fun `a saved passenger trip says it is not scored, a deleted one says nothing was kept`() {
        show(DriveCover.Saved("t1", "3.0", 8, SavedKind.PASSENGER, autoEnded = false))
        compose.onNodeWithText("Passenger trip saved · not scored").assertIsDisplayed()
    }

    @Test
    fun `a deleted trip says nothing was kept`() {
        show(DriveCover.NotSaved)
        compose.onNodeWithText("Trip not saved").assertIsDisplayed()
        compose.onNodeWithText("Nothing was kept.").assertIsDisplayed()
    }
}
