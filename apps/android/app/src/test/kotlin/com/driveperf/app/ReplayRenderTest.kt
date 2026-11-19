package com.driveperf.app

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.feature.replay.ReplayMapConfig
import com.driveperf.feature.replay.ReplayHeader
import com.driveperf.feature.replay.ReplayRoute
import com.driveperf.feature.replay.ReplayViewModel
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRIVE-200 / DRIVE-61: renders S-RPL-01 on the plain canvas with the synthetic trip (no real locations) at the
 * start, in the no-GPS gap and in the excluded span, dark and light, and at XL text. PNGs go to build/reports/DRIVE-200.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReplayRenderTest {
    @get:Rule val compose = createComposeRule()

    private val analytics = object : Analytics {
        override fun track(event: AnalyticsEvent) = Unit

        override fun setUsageConsent(allowed: Boolean) = Unit

        override fun resetId() = Unit
    }

    @Test fun replayRendersInBothThemesAndAtLargeText() {
        var case by mutableStateOf(CASES.first())
        var vm by mutableStateOf(viewModel(case.online))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, case.scale)) {
                DrivePerfTheme(darkTheme = case.dark) {
                    key(vm) {
                        ReplayRoute(vm, ReplayHeader("Synthetic trip", mapId = null, onBack = {}), onShowDriveScreen = {})
                    }
                }
            }
        }
        for (next in CASES) {
            compose.runOnIdle {
                case = next
                vm = viewModel(next.online)
            }
            compose.waitForIdle()
            for ((moment, t) in MOMENTS) {
                compose.runOnIdle { vm.seekTo(t) }
                compose.waitForIdle()
                val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
                assertTrue(bitmap.width > 0 && bitmap.height > 0)
                save(bitmap, "${next.name}-$moment")
            }
        }
    }

    private fun viewModel(online: Boolean) = replayViewModel(
        ReplayMapConfig(hasMapsKey = false, mapId = null, online = MutableStateFlow(online)),
        analytics,
        compose,
    )

    private data class Case(val name: String, val dark: Boolean, val scale: Float, val online: Boolean)

    private companion object {
        val CASES = listOf(
            Case("dark", dark = true, scale = 1f, online = true),
            Case("light", dark = false, scale = 1f, online = true),
            Case("dark-xl", dark = true, scale = 2f, online = true),
            Case("dark-offline", dark = true, scale = 1f, online = false),
        )
        val MOMENTS = listOf("start" to 0.0, "estimated" to 30.0, "no-gps" to 380.0, "excluded" to 630.0)
    }
}

private fun save(bitmap: Bitmap, name: String) {
    val target = File("build/reports/DRIVE-200/$name.png")
    target.parentFile?.mkdirs()
    target.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
}
