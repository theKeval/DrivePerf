package com.driveperf.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DrivePerfTheme

/** The single scaffold screen (DRIVE-59). The text comes from shared code via `DrivePerfCore.hello()`. */
@Composable
fun HelloScreen(title: String, subtitle: String) {
    DrivePerfTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                Text(text = subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HelloScreenPreview() {
    HelloScreen(title = "Hello DrivePerf", subtitle = "Shared core running on Android 16 (API 36)")
}
