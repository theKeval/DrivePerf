package com.driveperf.feature.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * S-DRV-03: the one-tap escape for a passenger. "Keep the trip as a passenger trip" saves it without a score and lifts
 * the lock; "Don't save this trip" asks a second time, then the core deletes the samples. Cancel closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PassengerSheet(step: PassengerStep, actions: InDriveActions) {
    if (step == PassengerStep.HIDDEN) return
    ModalBottomSheet(onDismissRequest = actions.passenger.onDismissed) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (step == PassengerStep.CHOOSE) ChooseContent(actions) else ConfirmDiscardContent(actions)
        }
    }
}

@Composable
private fun ChooseContent(actions: InDriveActions) {
    Text(text = stringResource(R.string.passenger_title), style = MaterialTheme.typography.titleLarge)
    Text(
        text = stringResource(R.string.passenger_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OptionRow(R.string.passenger_keep, R.string.passenger_keep_detail, actions.passenger.onKept)
    OptionRow(R.string.passenger_discard, R.string.passenger_discard_detail, actions.passenger.onDiscardRequested)
    FilledTonalButton(
        onClick = actions.passenger.onDismissed,
        modifier = Modifier.fillMaxWidth().heightIn(min = DriveTouchMin),
    ) { Text(stringResource(R.string.passenger_cancel)) }
}

@Composable
private fun ConfirmDiscardContent(actions: InDriveActions) {
    Text(text = stringResource(R.string.passenger_discard_confirm_title), style = MaterialTheme.typography.titleLarge)
    Text(
        text = stringResource(R.string.passenger_discard_confirm_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
        onClick = actions.passenger.onDiscardConfirmed,
        modifier = Modifier.fillMaxWidth().heightIn(min = DriveTouchMin),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) { Text(stringResource(R.string.passenger_discard_confirm)) }
    FilledTonalButton(
        onClick = actions.passenger.onDismissed,
        modifier = Modifier.fillMaxWidth().heightIn(min = DriveTouchMin),
    ) { Text(stringResource(R.string.drive_stop_keep)) }
}

@Composable
private fun OptionRow(title: Int, detail: Int, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(detail), style = MaterialTheme.typography.bodySmall)
        }
    }
}

