package com.driveperf.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

private data class OnboardingPage(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val body: String,
)

private val pages = listOf(
    OnboardingPage(
        Icons.Outlined.DirectionsCar,
        "Automatic trip detection",
        "DrivePerf automatically detects when you start driving and records each trip — no manual start required.",
    ),
    OnboardingPage(
        Icons.Outlined.Speed,
        "Understand your driving",
        "Each trip gets a Safety, Smoothness, Efficiency, and overall Quality score with clear explanations so you can improve over time.",
    ),
    OnboardingPage(
        Icons.Outlined.Lock,
        "Your data, your control",
        "All data is stored locally on your device. You can delete any trip, turn off tracking at any time, and export or erase your history.",
    ),
)

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val page = pages[state.currentPage]

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(96.dp),
            )

            Spacer(Modifier.height(32.dp))

            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = page.body,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(48.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.currentPage > 0) {
                    TextButton(onClick = { viewModel.previousPage() }) {
                        Text("Back")
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                Button(onClick = {
                    val finished = viewModel.nextPage()
                    if (finished) onFinish()
                }) {
                    Text(if (state.currentPage < state.totalPages - 1) "Next" else "Get started")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Page indicator dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(state.totalPages) { index ->
                    val color = if (index == state.currentPage)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outlineVariant
                    Surface(
                        modifier = Modifier.size(if (index == state.currentPage) 10.dp else 8.dp),
                        shape = MaterialTheme.shapes.small,
                        color = color,
                    ) {}
                }
            }
        }
    }
}
