package com.driveperf.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.driveperf.app.navigation.DetailScaffold
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.domain.settings.Settings
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.feature.settings.RecordingSettingsActions
import com.driveperf.feature.settings.RecordingSettingsScreen
import com.driveperf.feature.settings.RecordingSettingsUiState
import com.driveperf.feature.settings.SettingsHubScreen
import com.driveperf.feature.settings.UnitsAndDisplayScreen
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w393dp-h852dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsRenderInBothThemesAndAtLargeText() {
        var page by mutableStateOf("units")
        var dark by mutableStateOf(true)
        var scale by mutableStateOf(1f)
        compose.setContent { SettingsRenderContent(page, dark, scale) }
        for (theme in listOf(true, false)) for (fontScale in listOf(1f, 2f))
            for (screen in listOf("hub", "units", "imperial", "recording")) {
            compose.runOnIdle { page = screen; dark = theme; scale = fontScale }
            compose.waitForIdle()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            assertTrue(bitmap.width > 0 && bitmap.height > 0)
            save(bitmap, screen, theme, fontScale)
        }
    }
}

private fun save(bitmap: Bitmap, screen: String, dark: Boolean, scale: Float) {
    val target = File("build/reports/DRIVE-153/$screen-${if (dark) "dark" else "light"}-$scale.png")
    target.parentFile?.mkdirs()
    target.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
}

@androidx.compose.runtime.Composable
private fun SettingsRenderContent(page: String, dark: Boolean, scale: Float) {
    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
        DrivePerfTheme(darkTheme = dark) { SettingsRenderPage(page) }
    }
}

@androidx.compose.runtime.Composable
private fun SettingsRenderPage(page: String) {
    val title = when (page) {
        "hub" -> "Settings"
        "recording" -> "Recording"
        else -> "Units and display"
    }
    DetailScaffold(title, {}) { padding ->
        val modifier = Modifier.padding(padding)
        when (page) {
            "units" -> UnitsAndDisplayScreen(Settings(), UnitSystem.METRIC, {}, modifier)
            "imperial" -> UnitsAndDisplayScreen(Settings(units = UnitSystem.IMPERIAL), UnitSystem.METRIC, {}, modifier)
            "hub" -> SettingsHubScreen("Metric · System", {}, {}, {}, modifier)
            else -> RecordingSettingsScreen(RecordingSettingsUiState(false, null), RecordingSettingsActions(), modifier)
        }
    }
}
