package com.driveperf.app

import com.driveperf.feature.onboarding.OnboardingActions

import com.driveperf.feature.onboarding.OnboardingStep

import com.driveperf.feature.onboarding.OnboardingUiState

import com.driveperf.feature.onboarding.OnboardingScreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.domain.settings.Appearance
import com.driveperf.domain.settings.Settings
import com.driveperf.domain.settings.SettingsVehicle
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.feature.settings.RecordingSettingsActions
import com.driveperf.feature.settings.RecordingSettingsScreen
import com.driveperf.feature.settings.RecordingSettingsUiState
import com.driveperf.feature.settings.SettingsHubScreen
import com.driveperf.feature.settings.UnitsAndDisplayScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DisplaySettingsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unitsAndAppearanceApplyImmediatelyWithAGPreview() {
        var latest = Settings()
        compose.setContent {
            var state by remember { mutableStateOf(latest) }
            DrivePerfTheme(darkTheme = state.appearance != Appearance.LIGHT) {
                UnitsAndDisplayScreen(state, UnitSystem.METRIC, { change -> state = change(state); latest = state })
            }
        }
        compose.onNodeWithText("Imperial").performClick().assertIsSelected()
        compose.onNodeWithText("g, always").assertIsDisplayed()
        compose.onNodeWithText("23.5 mi · 30 min · ↗ 295 ft").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Light").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle {
            assertEquals(UnitSystem.IMPERIAL, latest.units)
            assertEquals(Appearance.LIGHT, latest.appearance)
        }
        compose.onNodeWithText("Use wallpaper colours").performScrollTo().performClick().assertIsOn()
        compose.runOnIdle { assertEquals(true, latest.dynamicColour) }
    }

    @Test fun hubOpensTheRequestedSections() {
        var opened = ""
        compose.setContent { DrivePerfTheme {
            SettingsHubScreen("Metric · System", { opened = "units" }, { opened = "recording" }, { opened = "health" })
        } }
        compose.onNodeWithText("Units and display").performClick()
        compose.runOnIdle { assertEquals("units", opened) }
        compose.onNodeWithText("Recording").performClick()
        compose.runOnIdle { assertEquals("recording", opened) }
        compose.onNodeWithText("Health Check").performClick()
        compose.runOnIdle { assertEquals("health", opened) }
    }

    @Test fun recordingPicksGarageCarAndBatterySaver() {
        var latest = Settings()
        compose.setContent {
            var settings by remember { mutableStateOf(latest) }
            DrivePerfTheme {
                RecordingSettingsScreen(
                    RecordingSettingsUiState(false, null, preferences = settings, cars = listOf(SettingsVehicle("car", "My car"))),
                    RecordingSettingsActions(onPreferencesChanged = { change -> settings = change(settings); latest = settings }),
                )
            }
        }
        compose.onNodeWithText("My car").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Battery saver recording").performScrollTo().performClick().assertIsOn()
        compose.runOnIdle { assertEquals("car", latest.defaultVehicleId); assertEquals(true, latest.batterySaverRecording) }
    }

    @Test fun onboardingNotificationPreviewFollowsImperialUnits() {
        compose.setContent { DrivePerfTheme {
            OnboardingScreen(
                OnboardingUiState(step = OnboardingStep.Notifications),
                OnboardingActions(), units = UnitSystem.IMPERIAL,
            )
        } }
        compose.onNodeWithText("4 drives · 39.1 mi").performScrollTo().assertIsDisplayed()
    }

    @Test fun emptyGarageOffersItsDestination() {
        var garage = false
        compose.setContent { DrivePerfTheme {
            RecordingSettingsScreen(RecordingSettingsUiState(false, null), RecordingSettingsActions(onGarageClick = { garage = true }))
        } }
        compose.onNodeWithText("Add a car in Garage").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, garage) }
    }
}
