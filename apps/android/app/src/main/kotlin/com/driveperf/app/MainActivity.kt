package com.driveperf.app

import android.os.Bundle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driveperf.domain.settings.Appearance
import com.driveperf.domain.settings.SettingsRepository
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.driveperf.app.bridge.PlatformCaptureController
import com.driveperf.app.navigation.DrivePerfApp
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.domain.capture.TripRecorder
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val capture: PlatformCaptureController by inject()
    private val settings: SettingsRepository by inject()
    private val recorder: TripRecorder by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences by settings.state.collectAsStateWithLifecycle()
            val dark = when (preferences.appearance) {
                Appearance.SYSTEM -> isSystemInDarkTheme()
                Appearance.LIGHT -> false
                Appearance.DARK -> true
            }
            DrivePerfTheme(darkTheme = dark, dynamicColor = preferences.dynamicColour) { DrivePerfApp() }
        }
    }

    override fun onResume() {
        super.onResume()
        capture.refreshPermissions() // permissions can change in Settings while the app is in the background
        recorder.onForeground() // a check-in (F-CAP-14): re-arms the parked region and records the time
    }
}
