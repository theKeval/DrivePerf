package com.driveperf.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NightDriveColorScheme = darkColorScheme(
    primary = DrivePerfColors.Glide,
    onPrimary = DrivePerfColors.Night,
    background = DrivePerfColors.Night,
    onBackground = DrivePerfColors.OnNight,
    surface = DrivePerfColors.Night,
    onSurface = DrivePerfColors.OnNight,
)

/** Dark-first theme for every DrivePerf screen. The full token set arrives with the design-system ticket. */
@Composable
public fun DrivePerfTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NightDriveColorScheme, content = content)
}
