package com.driveperf

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.driveperf.navigation.DrivePerfNavHost
import com.driveperf.navigation.Screen
import com.driveperf.ui.theme.DrivePerfTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs: SharedPreferences = getSharedPreferences("app_state", MODE_PRIVATE)
        val onboardingComplete = prefs.getBoolean("onboarding_complete", false)

        setContent {
            DrivePerfTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    DrivePerfNavHost(
                        navController = navController,
                        startDestination = if (onboardingComplete) {
                            Screen.TripList.route
                        } else {
                            Screen.Onboarding.route
                        },
                    )
                }
            }
        }
    }
}
