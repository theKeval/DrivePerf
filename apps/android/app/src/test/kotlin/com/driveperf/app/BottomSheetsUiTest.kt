package com.driveperf.app

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.driveperf.designsystem.DpModalBottomSheet
import com.driveperf.designsystem.DpSheetValues
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.designsystem.rememberDpSheetState
import com.driveperf.domain.capture.AutoDetectPauseLength
import com.driveperf.feature.capture.PauseAutoDetectSheet
import com.driveperf.feature.capture.PauseSheetActions
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DRIVE-215: every modal sheet is a DpModalBottomSheet, which opens fully expanded and has no half-open stop. With that
 * stop, a scrim tap while a swipe was still settling could leave M3's ModalBottomSheet stuck "animating", taking every
 * touch as a drag so no control in it responded (DRIVE-90).
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class BottomSheetsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the shared sheet only allows hidden and expanded`() {
        assertEquals(setOf(SheetValue.Hidden, SheetValue.Expanded), DpSheetValues)
    }

    @Test
    fun `the shared sheet opens expanded, and its controls respond`() {
        var taps = 0
        var state: androidx.compose.material3.SheetState? = null
        compose.setContent {
            DrivePerfTheme(darkTheme = true) {
                val sheetState = rememberDpSheetState().also { state = it }
                DpModalBottomSheet(onDismissRequest = {}, sheetState = sheetState) {
                    Column { TextButton(onClick = { taps++ }) { Text("Tap me") } }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(SheetValue.Expanded, state?.currentValue)
        assertEquals(false, state?.hasPartiallyExpandedState)
        compose.onNodeWithText("Tap me").assertIsDisplayed().performClick()
        assertEquals(1, taps)
    }

    @Test
    fun `the Pause sheet opens and its choices and Pause button respond`() {
        val paused = mutableListOf<AutoDetectPauseLength>()
        compose.setContent {
            DrivePerfTheme(darkTheme = true) {
                PauseAutoDetectSheet(
                    paused = false,
                    pausedUntilMs = null,
                    actions = PauseSheetActions(onDismiss = {}, onPause = { length, _ -> paused += length }, onUndo = {}, onResume = {}),
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Pause auto-detect").assertIsDisplayed()
        compose.onNodeWithText("Until tomorrow morning").performClick()
        compose.onNodeWithText("Pause until tomorrow").performClick()
        assertEquals(1, paused.size)
        compose.onNodeWithText("Auto-detect paused").assertIsDisplayed() // the confirmation step replaced the picker
    }

    @Test
    fun `the Pause sheet opened while paused offers Resume`() {
        var resumed = 0
        compose.setContent {
            DrivePerfTheme(darkTheme = true) {
                PauseAutoDetectSheet(
                    paused = true,
                    pausedUntilMs = null,
                    actions = PauseSheetActions(onDismiss = {}, onPause = { _, _ -> }, onUndo = {}, onResume = { resumed++ }),
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Resume auto-detect").performClick()
        assertEquals(1, resumed)
    }

    /**
     * The sheets in feature modules (trip detail, data quality, passenger, status, onboarding) can't be built from
     * here, so this keeps them honest.
     */
    @Test
    fun `no screen builds a raw ModalBottomSheet`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.firstOrNull { File(it, "designsystem").isDirectory }
        if (root == null) return // not run from inside apps/android: nothing to scan
        val offenders = root.walkTopDown()
            .onEnter { it.name != "build" && !it.name.startsWith(".") }
            .filter { it.isFile && it.extension == "kt" && "/src/main/" in it.path && !it.path.contains("/designsystem/") }
            .filter { Regex("""(?<![A-Za-z])ModalBottomSheet\(""").containsMatchIn(it.readText()) }
            .map { it.name }
            .toList()
        assertTrue("use DpModalBottomSheet instead of ModalBottomSheet in: $offenders", offenders.isEmpty())
    }
}
