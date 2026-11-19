package com.driveperf.feature.drives

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.presentation.drives.DeleteTripUi
import com.driveperf.presentation.drives.TripEditAction

// S-TRP-03 Delete trip confirmation (DRIVE-131, F-HIS-02 AC3): a real, irreversible deletion.

/** S-TRP-03: an M3 alert with the trash icon, the drive, and Cancel · Delete (error colour). */
@Composable
internal fun DeleteTripDialog(dialog: DeleteTripUi, send: (TripEditAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { send(TripEditAction.CancelDelete) },
        icon = { Icon(painterResource(R.drawable.ic_trip_edit_trash), null, Modifier.size(24.dp)) },
        title = { Text(dialog.title, textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(dialog.message)
                Column {
                    Text(dialog.tripTitle, style = MaterialTheme.typography.titleSmall)
                    Text(dialog.tripSubtitle, style = MaterialTheme.typography.bodySmall, color = DrivePerfTheme.extendedColors.text2)
                }
                dialog.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { send(TripEditAction.ConfirmDelete) }, enabled = !dialog.deleting) {
                Text(dialog.confirm, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { send(TripEditAction.CancelDelete) }, enabled = !dialog.deleting) { Text(dialog.cancel) }
        },
    )
}
