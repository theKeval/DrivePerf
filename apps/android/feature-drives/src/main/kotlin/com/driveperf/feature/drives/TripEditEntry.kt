package com.driveperf.feature.drives

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.driveperf.presentation.drives.DrivesIntent
import com.driveperf.presentation.drives.TripEditAction
import com.driveperf.presentation.drives.TripEditPage

/** "Edit" in trip detail's action row (S-TRP-01 → S-TRP-02). */
@Composable
internal fun TripEditButton(tripId: String, onAction: (DrivesIntent) -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = { onAction(DrivesIntent.Edit(TripEditAction.Open(tripId, TripEditPage.MAIN))) },
        modifier = modifier.heightIn(min = 56.dp).testTag(TRIP_EDIT_BUTTON_TAG),
    ) {
        Text(stringResource(R.string.trip_edit_action))
    }
}
