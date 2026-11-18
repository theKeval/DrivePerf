package com.driveperf.feature.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpHealth
import com.driveperf.designsystem.DpHealthGlyph
import com.driveperf.designsystem.DpIcons
import com.driveperf.designsystem.DrivePerfTheme

/**
 * S-HOME-02: answers "will my next drive be recorded?" from the floating toolbar. A ModalBottomSheet with the summary,
 * "Last check-in" (the shared core's), the checks (failing rows first, each with a tonal Fix, then the passing ones), and a
 * row to Recording settings (F-CAP-02, F-SET-02).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureStatusSheet(status: AutoDetectStatus, actions: CaptureStatusActions) {
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        CaptureStatusContent(status, actions)
    }
}

@Composable
internal fun CaptureStatusContent(status: AutoDetectStatus, actions: CaptureStatusActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(status.mode)
        Summary(status)
        CheckInCard(status.lastCheckInMs)
        Checks(status, actions.onFix)
        RecordingSettingsRow(actions.onOpenRecordingSettings)
        Text(
            text = stringResource(R.string.status_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Header(mode: AutoDetectMode) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.status_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        val subtitle = when (mode) {
            AutoDetectMode.Ready -> R.string.status_subtitle_ready
            AutoDetectMode.NeedsAttention -> R.string.status_subtitle_attention
            AutoDetectMode.Off -> R.string.status_subtitle_off
        }
        Text(stringResource(subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Summary(status: AutoDetectStatus) {
    val (title, detail) = when (status.mode) {
        AutoDetectMode.Ready -> stringResource(R.string.status_summary_ready) to stringResource(R.string.status_summary_ready_detail)
        AutoDetectMode.NeedsAttention -> stringResource(R.string.status_summary_attention) to
            status.reasons.firstOrNull()?.let { stringResource(it.explanation) }
        AutoDetectMode.Off -> stringResource(R.string.status_summary_off) to stringResource(R.string.status_summary_off_detail)
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        status.mode.health().let { DpHealthGlyph(it, it.label(), size = 36.dp) }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CheckInCard(lastCheckInMs: Long?) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp).semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(stringResource(R.string.status_last_check_in), style = MaterialTheme.typography.labelLarge)
            if (lastCheckInMs == null) {
                Text(stringResource(R.string.status_last_check_in_none), style = MaterialTheme.typography.titleLarge)
            } else {
                Text(checkInAgo(lastCheckInMs, rememberNowMs()), style = MaterialTheme.typography.titleLarge)
                Text(
                    checkInClock(lastCheckInMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.status_last_check_in_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun Checks(status: AutoDetectStatus, onFix: (AttentionReason) -> Unit) {
    val failing = status.checks.count { !it.ok }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (failing > 0) {
                stringResource(R.string.status_checks_to_fix, failing)
            } else {
                stringResource(R.string.status_checks_all_ok, status.checks.size)
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 4.dp).semantics { heading() },
        )
        Column(modifier = Modifier.clip(MaterialTheme.shapes.large)) {
            status.checks.forEach { CheckRow(it, onFix) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CheckRow(check: CaptureCheck, onFix: (AttentionReason) -> Unit) {
    val reason = check.reason
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = {
            val health = if (reason == null) DpHealth.Ok else DpHealth.Fix
            DpHealthGlyph(health, health.label())
        },
        supportingContent = { Text(stringResource(reason?.checkValue ?: check.kind.okValue)) },
        trailingContent = {
            val fix = reason?.takeIf { it.fix != null }
            if (fix != null) FilledTonalButton(onClick = { onFix(fix) }) { Text(stringResource(R.string.attention_fix)) }
        },
    ) { Text(stringResource(check.kind.title)) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecordingSettingsRow(onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        modifier = Modifier.clip(MaterialTheme.shapes.large),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = { Icon(painterResource(DpIcons.Health), contentDescription = null) },
        supportingContent = { Text(stringResource(R.string.status_recording_settings_detail)) },
        trailingContent = { Icon(painterResource(DpIcons.ChevronRight), contentDescription = null) },
    ) { Text(stringResource(R.string.status_recording_settings)) }
}

@Preview
@Composable
private fun CaptureStatusAttentionPreview() {
    DrivePerfTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            CaptureStatusContent(
                AutoDetectReadiness.status(
                    optedIn = true,
                    permissions = CapturePermissions.ALL.copy(background = false),
                    lastCheckInMs = System.currentTimeMillis() - 18 * 60_000L,
                ),
                CaptureStatusActions({}, {}, {}),
            )
        }
    }
}
