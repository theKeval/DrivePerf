package com.driveperf.feature.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpIcons
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.domain.capture.AutoDetectPauseLength
import java.time.ZoneId

/** The sheet's three states (S-HOME-03 default, pausedUntil, resume). */
private enum class PauseStep { PICK, CONFIRMED, RESUME }

/**
 * S-HOME-03 Pause auto-detect (F-CAP-15): a ModalBottomSheet that lets someone lending their phone, or wanting privacy,
 * stop automatic trips for a while. Opened while not paused it offers three choices; Pause shows the confirmation (Done,
 * Undo). Opened while paused ([pausedUntilMs] with [paused]) it offers Resume auto-detect and Keep paused.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PauseAutoDetectSheet(paused: Boolean, pausedUntilMs: Long?, actions: PauseSheetActions) {
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        PauseAutoDetectContent(paused, pausedUntilMs, actions)
    }
}

@Composable
internal fun PauseAutoDetectContent(paused: Boolean, pausedUntilMs: Long?, actions: PauseSheetActions) {
    var step by rememberSaveable { mutableStateOf(if (paused) PauseStep.RESUME else PauseStep.PICK) }
    var choice by rememberSaveable { mutableStateOf(PauseRules.DEFAULT) }
    var confirmedUntil by rememberSaveable { mutableStateOf<Long?>(null) }
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (step) {
            PauseStep.PICK -> Picker(
                choice = choice,
                onChoice = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    choice = it
                },
                onPause = { nowMs ->
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    val until = PauseRules.untilWallMs(choice, nowMs, ZoneId.systemDefault())
                    actions.onPause(choice, until)
                    confirmedUntil = until
                    step = PauseStep.CONFIRMED
                },
            )
            PauseStep.CONFIRMED -> Confirmation(
                untilMs = confirmedUntil,
                onDone = actions.onDismiss,
                onUndo = {
                    actions.onUndo()
                    step = PauseStep.PICK
                },
            )
            PauseStep.RESUME -> Resume(
                untilMs = pausedUntilMs,
                onResume = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    actions.onResume()
                },
                onKeepPaused = actions.onDismiss,
            )
        }
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Picker(choice: AutoDetectPauseLength, onChoice: (AutoDetectPauseLength) -> Unit, onPause: (Long) -> Unit) {
    val nowMs = rememberNowMs()
    SheetHeader(stringResource(R.string.pause_title), stringResource(R.string.pause_subtitle))
    val group = stringResource(R.string.pause_title)
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .selectableGroup()
            .semantics { contentDescription = group },
    ) {
        AutoDetectPauseLength.entries.forEach { length ->
            OptionRow(length, selected = length == choice, nowMs = nowMs, onClick = { onChoice(length) })
        }
    }
    Text(
        stringResource(R.string.pause_reassurance),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
        onClick = { onPause(System.currentTimeMillis()) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Icon(painterResource(DpIcons.Pause), contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(choice.button), modifier = Modifier.padding(start = 8.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OptionRow(length: AutoDetectPauseLength, selected: Boolean, nowMs: Long, onClick: () -> Unit) {
    // Not the `selected` ListItem: it tints the row, which S-HOME-03 does not show. The radio carries the state.
    ListItem(
        modifier = Modifier
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        supportingContent = { Text(optionDetail(length, nowMs)) },
    ) { Text(stringResource(length.title)) }
}

@Composable
private fun PauseDisc() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(56.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(DpIcons.Pause),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun Confirmation(untilMs: Long?, onDone: () -> Unit, onUndo: () -> Unit) {
    SheetHeader(stringResource(R.string.pause_confirmed_title), null)
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        PauseDisc()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(pausedUntilLine(untilMs), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.pause_confirmed_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(stringResource(R.string.pause_done))
    }
    TextButton(onClick = onUndo, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pause_undo)) }
}

@Composable
private fun Resume(untilMs: Long?, onResume: () -> Unit, onKeepPaused: () -> Unit) {
    SheetHeader(stringResource(R.string.pause_resume_title), pausedUntilLine(untilMs))
    Text(stringResource(R.string.pause_resume_body), style = MaterialTheme.typography.bodyMedium)
    Button(onClick = onResume, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Icon(painterResource(DpIcons.Play), contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.pause_resume), modifier = Modifier.padding(start = 8.dp))
    }
    FilledTonalButton(onClick = onKeepPaused, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.pause_keep_paused))
    }
}

@Preview
@Composable
private fun PauseAutoDetectPickerPreview() {
    DrivePerfTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column { PauseAutoDetectContent(paused = false, pausedUntilMs = null, actions = PauseSheetActions({}, { _, _ -> }, {}, {})) }
        }
    }
}

@Preview
@Composable
private fun PauseAutoDetectResumePreview() {
    DrivePerfTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column {
                PauseAutoDetectContent(
                    paused = true,
                    pausedUntilMs = System.currentTimeMillis() + 3_600_000L,
                    actions = PauseSheetActions({}, { _, _ -> }, {}, {}),
                )
            }
        }
    }
}
