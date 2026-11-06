package com.driveperf.feature.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.driveperf.domain.model.DistanceUnit
import com.driveperf.domain.model.DrivingEvent
import com.driveperf.domain.model.EventType
import com.driveperf.domain.model.TripScore
import com.driveperf.ui.format.formatDistance
import com.driveperf.ui.format.formatDrivingEventDetails
import com.driveperf.ui.theme.ScoreExcellent
import com.driveperf.ui.theme.ScoreFair
import com.driveperf.ui.theme.ScoreGood
import com.driveperf.ui.theme.ScorePoor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    tripId: Long,
    onBack: () -> Unit,
    viewModel: TripDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPassengerDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showPassengerDialog = true }) {
                        Icon(Icons.Outlined.PersonOff, contentDescription = "Mark as passenger")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete trip")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.trip?.let { trip ->
                val formatter = SimpleDateFormat("EEEE, MMMM d, yyyy · h:mm a", Locale.getDefault())
                Text(formatter.format(Date(trip.startTimeMs)), style = MaterialTheme.typography.titleMedium)
                Text(
                    formatDistance(trip.distanceMeters, state.distanceUnit),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.score?.let { score ->
                ScoreBreakdownCard(score = score)
            }

            if (state.events.isNotEmpty()) {
                EventsCard(
                    events = state.events,
                    distanceUnit = state.distanceUnit,
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete trip?") },
            text = { Text("This will permanently remove the trip and all its data.") },
            confirmButton = {
                Button(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteTrip(onDeleted = onBack)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showPassengerDialog) {
        AlertDialog(
            onDismissRequest = { showPassengerDialog = false },
            title = { Text("Were you a passenger?") },
            text = { Text("This trip will be marked as passenger-only and excluded from your scores.") },
            confirmButton = {
                Button(onClick = {
                    showPassengerDialog = false
                    viewModel.markAsPassenger()
                    onBack()
                }) { Text("Yes, I was a passenger") }
            },
            dismissButton = {
                TextButton(onClick = { showPassengerDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ScoreBreakdownCard(score: TripScore) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Score breakdown", style = MaterialTheme.typography.titleMedium)
            ScoreRow("Safety", score.safetyScore)
            ScoreRow("Smoothness", score.smoothnessScore)
            ScoreRow("Efficiency", score.efficiencyScore)
            ScoreRow("Overall quality", score.tripQualityScore)
            Text(
                "★ ${"%.1f".format(score.starRating)} stars",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (score.signalConfidence < 70f) {
                Text(
                    "⚠ GPS signal confidence was ${score.signalConfidence.toInt()}% — scores may be less accurate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ScoreRow(label: String, score: Float) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("${score.toInt()}/100", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { score / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = scoreColor(score),
        )
    }
}

private fun scoreColor(score: Float): Color = when {
    score >= 85 -> ScoreExcellent
    score >= 70 -> ScoreGood
    score >= 50 -> ScoreFair
    else -> ScorePoor
}

@Composable
private fun EventsCard(
    events: List<DrivingEvent>,
    distanceUnit: DistanceUnit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Driving events", style = MaterialTheme.typography.titleMedium)
            events.forEach { event ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(event.type.displayName(), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        formatDrivingEventDetails(event, distanceUnit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun EventType.displayName() = when (this) {
    EventType.HARD_BRAKING -> "Hard braking"
    EventType.HARSH_ACCELERATION -> "Harsh acceleration"
    EventType.AGGRESSIVE_CORNERING -> "Aggressive cornering"
    EventType.SPEEDING -> "Speeding"
    EventType.PHONE_DISTRACTION -> "Phone distraction"
    EventType.NIGHT_DRIVING -> "Night driving"
    EventType.LONG_TRIP -> "Long trip"
    EventType.IDLING -> "Idling"
    EventType.STOP_START -> "Excessive stop-start"
}
