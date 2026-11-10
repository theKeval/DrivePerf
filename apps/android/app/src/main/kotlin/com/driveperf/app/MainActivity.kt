package com.driveperf.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.driveperf.app.bridge.PlatformCaptureController
import com.driveperf.app.navigation.DrivePerfApp
import com.driveperf.designsystem.DrivePerfTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val capture: PlatformCaptureController by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DrivePerfTheme { DrivePerfApp() } }
    }

    override fun onResume() {
        super.onResume()
        capture.refreshPermissions() // permissions can change in Settings while the app is in the background
    }
}
