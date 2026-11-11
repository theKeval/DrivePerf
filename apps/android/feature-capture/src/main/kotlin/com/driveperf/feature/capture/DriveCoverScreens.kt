package com.driveperf.feature.capture

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Draws whichever [DriveCover] is showing. Null means the app's own navigation is free. */
@Composable
fun DriveCoverHost(cover: DriveCover, actions: InDriveActions, modifier: Modifier = Modifier) {
    when (cover) {
        is DriveCover.InDrive -> InDriveScreen(cover.state, actions, modifier)
        is DriveCover.Held -> HeldContentScreen(cover, actions, modifier)
        is DriveCover.Decoding -> DecodingScreen(cover, actions.onCoverDismissed, modifier)
        is DriveCover.Saved -> SavedScreen(cover, actions.onCoverDismissed, modifier)
        DriveCover.NotSaved -> NotSavedScreen(actions.onCoverDismissed, modifier)
    }
}

/**
 * S-DRV-05: what anyone sees when they ask for a Story, Replay or Analysis while a driver trip is on or the phone has
 * not been parked for 60 s since. It refuses calmly and opens the content by itself afterwards. A passenger can still
 * escape from here while the trip records.
 */
@Composable
fun HeldContentScreen(held: DriveCover.Held, actions: InDriveActions, modifier: Modifier = Modifier) {
    DriveSurface(modifier) {
        BackHandler(enabled = true) {} // system back is consumed while held
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.drive_held_title, stringResource(held.noun.label())),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.drive_held_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (held.canMarkPassenger) {
                Box(modifier = Modifier.align(Alignment.BottomCenter)) { PassengerButton(actions.passenger.onRequested) }
            }
        }
        if (held.canMarkPassenger) PassengerSheet(held.passengerStep, actions)
    }
}

private fun HeldNoun.label(): Int = when (this) {
    HeldNoun.STORY -> R.string.drive_noun_story
    HeldNoun.REPLAY -> R.string.drive_noun_replay
    HeldNoun.ANALYSIS -> R.string.drive_noun_analysis
    HeldNoun.RECAP -> R.string.drive_noun_recap
    HeldNoun.WRAPPED -> R.string.drive_noun_wrapped
}

/** S-DRV-04 while the trip is finalised (a few seconds). It never shows a score. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DecodingScreen(cover: DriveCover.Decoding, onBack: () -> Unit, modifier: Modifier = Modifier) {
    DriveSurface(modifier) {
        BackHandler(enabled = true, onBack = onBack)
        CenteredColumn {
            ContainedLoadingIndicator(modifier = Modifier.size(132.dp))
            Text(
                text = stringResource(if (cover.passenger) R.string.saved_passenger_decoding else R.string.decoding_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.decoding_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = DriveTouchMin)) {
                Text(stringResource(R.string.saved_back))
            }
        }
    }
}

/**
 * S-DRV-04 done, as a placeholder until the Story exists (DRIVE-182): the trip is saved and listed. When the 15-minute
 * stationary timeout ended it, it says so.
 */
@Composable
fun SavedScreen(cover: DriveCover.Saved, onBack: () -> Unit, modifier: Modifier = Modifier) {
    DriveSurface(modifier) {
        BackHandler(enabled = true, onBack = onBack)
        CenteredColumn {
            Text(
                text = stringResource(
                    when (cover.kind) {
                        SavedKind.DRIVE -> R.string.saved_title
                        SavedKind.SHORT -> R.string.saved_title_short
                        SavedKind.PASSENGER -> R.string.saved_title_passenger
                    },
                ),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            if (cover.distanceKm != null && cover.minutes != null) {
                Text(
                    text = stringResource(R.string.saved_summary, cover.distanceKm, cover.minutes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (cover.autoEnded) NoteText(R.string.saved_auto_ended)
            NoteText(if (cover.kind == SavedKind.PASSENGER) R.string.saved_passenger_note else R.string.saved_note)
            BackButton(onBack)
        }
    }
}

/** After "Don't save this trip": nothing was kept. */
@Composable
fun NotSavedScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    DriveSurface(modifier) {
        BackHandler(enabled = true, onBack = onBack)
        CenteredColumn {
            Text(
                text = stringResource(R.string.not_saved_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            NoteText(R.string.not_saved_body)
            BackButton(onBack)
        }
    }
}

@Composable
private fun CenteredColumn(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}

@Composable
private fun NoteText(text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    Button(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = DriveTouchMin)) {
        Text(stringResource(R.string.saved_back))
    }
}
