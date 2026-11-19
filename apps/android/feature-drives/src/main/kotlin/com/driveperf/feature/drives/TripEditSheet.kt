package com.driveperf.feature.drives

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpIcons
import com.driveperf.designsystem.DpModalBottomSheet
import com.driveperf.designsystem.DpStatusChip
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.presentation.drives.DeleteTripUi
import com.driveperf.presentation.drives.DrivesIntent
import com.driveperf.presentation.drives.DrivesState
import com.driveperf.presentation.drives.TripEditAction
import com.driveperf.presentation.drives.TripEditMessageUi
import com.driveperf.presentation.drives.TripEditPage
import com.driveperf.presentation.drives.TripEditUi
import kotlinx.coroutines.delay

// S-TRP-02 Edit trip, S-TRP-03 Delete trip and the saved toast (DRIVE-131, F-HIS-02), drawn from the shared presenter's
// state: every word is already formatted there (decision 3).

/** Test tag of trip detail's Edit button. */
const val TRIP_EDIT_BUTTON_TAG: String = "trip_detail_edit"

/** Test tag of S-TRP-02's Save button. */
const val TRIP_EDIT_SAVE_TAG: String = "trip_edit_save"

/** Test tag of S-TRP-02's "Delete drive" row. */
const val TRIP_EDIT_DELETE_TAG: String = "trip_edit_delete"

/** How long the saved toast stays (M3 snackbar with an action: long). */
private const val MESSAGE_MS = 10_000L

/** S-TRP-02, S-TRP-03 and the saved toast, when the state opens them. */
@Composable
internal fun TripEditOverlays(state: DrivesState, onAction: (DrivesIntent) -> Unit) {
    val send = { action: TripEditAction -> onAction(DrivesIntent.Edit(action)) }
    state.edit?.let { TripEditSheet(it, send) }
    state.deleting?.let { DeleteTripDialog(it, send) }
    state.editMessage?.let { message ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { TripEditMessage(message, onAction) }
    }
}

/** "Marked as passenger · scores updated" with Undo (or a failed Undo), at the bottom of trip detail. */
@Composable
internal fun TripEditMessage(message: TripEditMessageUi, onAction: (DrivesIntent) -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(message) {
        delay(MESSAGE_MS)
        onAction(DrivesIntent.Edit(TripEditAction.DismissMessage))
    }
    Snackbar(
        modifier = modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
        action = message.undoLabel?.let { undo ->
            { TextButton(onClick = { onAction(DrivesIntent.Edit(TripEditAction.Undo)) }) { Text(undo) } }
        },
    ) {
        Text(message.text)
    }
}

/** S-TRP-02: a full-height sheet with × and Save, whose type and car pickers push inside it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripEditSheet(edit: TripEditUi, send: (TripEditAction) -> Unit) {
    DpModalBottomSheet(
        onDismissRequest = { send(TripEditAction.Close) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        BackHandler(enabled = edit.page != TripEditPage.MAIN) { send(TripEditAction.ShowPage(TripEditPage.MAIN)) }
        SheetHeader(edit, send)
        AnimatedContent(targetState = edit.page, label = "trip edit page") { page ->
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (page) {
                    TripEditPage.MAIN -> MainPage(edit, send)
                    TripEditPage.TYPE -> TypePage(edit, send)
                    TripEditPage.CAR -> CarPage(edit, send)
                }
            }
        }
    }
}

/** × (or back on a picker), the title, and Save. */
@Composable
private fun SheetHeader(edit: TripEditUi, send: (TripEditAction) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (edit.page == TripEditPage.MAIN) {
            IconButton(onClick = { send(TripEditAction.Close) }) {
                Icon(painterResource(R.drawable.ic_trip_edit_close), stringResource(R.string.trip_edit_close))
            }
        } else {
            IconButton(onClick = { send(TripEditAction.ShowPage(TripEditPage.MAIN)) }) {
                Icon(painterResource(DpIcons.Back), stringResource(R.string.trip_edit_back))
            }
        }
        val title = when (edit.page) {
            TripEditPage.MAIN -> edit.title
            TripEditPage.TYPE -> stringResource(R.string.trip_edit_type)
            TripEditPage.CAR -> stringResource(R.string.trip_edit_car)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        TextButton(onClick = { send(TripEditAction.Save) }, modifier = Modifier.testTag(TRIP_EDIT_SAVE_TAG)) {
            Text(stringResource(R.string.trip_edit_save))
        }
    }
}

/** The trip, its type and car rows, the caption, and "Delete drive". */
@Composable
private fun MainPage(edit: TripEditUi, send: (TripEditAction) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(edit.tripTitle, style = MaterialTheme.typography.titleMedium)
            Text(edit.tripSubtitle, style = MaterialTheme.typography.bodyMedium, color = DrivePerfTheme.extendedColors.text2)
            Text(edit.tripStats, style = MaterialTheme.typography.bodyMedium, color = DrivePerfTheme.extendedColors.text2)
        }
        edit.score?.let { ScoreBadge(it) }
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            NavRow(stringResource(R.string.trip_edit_type), edit.typeValue, edit.typeDetail) {
                send(TripEditAction.ShowPage(TripEditPage.TYPE))
            }
            if (edit.hasCars) {
                HorizontalDivider()
                NavRow(stringResource(R.string.trip_edit_car), edit.carValue.orEmpty(), edit.carDetail) {
                    send(TripEditAction.ShowPage(TripEditPage.CAR))
                }
            }
        }
    }
    edit.changedBanner?.let { InfoBanner(it) }
    Text(edit.caption, style = MaterialTheme.typography.bodySmall, color = DrivePerfTheme.extendedColors.text2)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable { send(TripEditAction.AskDelete(edit.tripId)) }
                .padding(horizontal = 16.dp)
                .testTag(TRIP_EDIT_DELETE_TAG),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_trip_edit_trash), null, tint = MaterialTheme.colorScheme.error)
            Text(edit.deleteLabel, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** "Trip type · Driver · Scored ›" */
@Composable
private fun NavRow(title: String, value: String, detail: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clearAndSetSemantics { contentDescription = listOfNotNull(title, value, detail).joinToString(", ") },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = DrivePerfTheme.extendedColors.text2) }
        }
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(" ›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Driver · Passenger · Bus or train · Taxi or rideshare · Other, each with its consequence. */
@Composable
private fun TypePage(edit: TripEditUi, send: (TripEditAction) -> Unit) {
    Text(edit.tripTitle, style = MaterialTheme.typography.bodyMedium, color = DrivePerfTheme.extendedColors.text2)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.selectableGroup()) {
            edit.types.forEachIndexed { index, option ->
                if (index > 0) HorizontalDivider()
                RadioRow(option.title, option.detail, option.selected, option.accessibilityLabel,
                    tag = stringResource(R.string.trip_edit_current).takeIf { option.current }) {
                    send(TripEditAction.PickType(option.type))
                }
            }
        }
    }
    edit.typeBanner?.let { InfoBanner(it) }
    Text(edit.typeFooter, style = MaterialTheme.typography.bodySmall, color = DrivePerfTheme.extendedColors.text2)
}

/** The user's cars, the default one tagged. */
@Composable
private fun CarPage(edit: TripEditUi, send: (TripEditAction) -> Unit) {
    Text(edit.tripTitle, style = MaterialTheme.typography.bodyMedium, color = DrivePerfTheme.extendedColors.text2)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.selectableGroup()) {
            edit.cars.forEachIndexed { index, car ->
                if (index > 0) HorizontalDivider()
                RadioRow(car.name, car.detail, car.selected, car.accessibilityLabel,
                    tag = stringResource(R.string.trip_edit_default).takeIf { car.isDefault }) {
                    send(TripEditAction.PickCar(car.id))
                }
            }
        }
    }
    Text(edit.carNote, style = MaterialTheme.typography.bodySmall, color = DrivePerfTheme.extendedColors.text2)
}

@Suppress("LongParameterList") // one row's content
@Composable
private fun RadioRow(title: String, detail: String?, selected: Boolean, label: String, tag: String?, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clearAndSetSemantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = DrivePerfTheme.extendedColors.text2) }
        }
        tag?.let { DpStatusChip(label = it) }
    }
}

@Composable
private fun InfoBanner(text: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(12.dp))
    }
}
