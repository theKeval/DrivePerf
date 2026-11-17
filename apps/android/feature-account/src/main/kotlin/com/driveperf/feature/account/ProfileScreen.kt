package com.driveperf.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpIcons
import com.driveperf.designsystem.DpStatusChip
import com.driveperf.designsystem.DpSwitchRow

/** Profile (S-ACC-01), reached from the avatar on every tab root. Local mode only until accounts exist. */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onUsageStatisticsChanged: (Boolean) -> Unit,
    onCrashReportsChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onOpenRecording: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DpStatusChip(label = stringResource(R.string.account_mode_local))
        Text(text = stringResource(R.string.account_mode_body), style = MaterialTheme.typography.bodyLarge)
        Text(text = stringResource(R.string.account_settings_title), style = MaterialTheme.typography.titleLargeEmphasized)
        RecordingRow(onOpenRecording)
        Text(text = stringResource(R.string.account_privacy_title), style = MaterialTheme.typography.titleLargeEmphasized)
        DpSwitchRow(
            title = stringResource(R.string.account_stats_title),
            description = stringResource(R.string.account_stats_body),
            checked = state.usageStatistics,
            onCheckedChange = onUsageStatisticsChanged,
        )
        DpSwitchRow(
            title = stringResource(R.string.account_crash_title),
            description = stringResource(
                if (state.crashReportingAvailable) R.string.account_crash_body else R.string.account_crash_unavailable,
            ),
            checked = state.crashReports,
            onCheckedChange = onCrashReportsChanged,
        )
        Text(
            text = stringResource(R.string.account_version, state.versionName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = state.coreDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** S-SET-03 Recording settings: automatic trips on or off (F-SET-01 AC3). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecordingRow(onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        modifier = Modifier.clip(MaterialTheme.shapes.large),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = { Icon(painterResource(DpIcons.Drives), contentDescription = null) },
        supportingContent = { Text(stringResource(R.string.account_recording_body)) },
        trailingContent = { Icon(painterResource(DpIcons.ChevronRight), contentDescription = null) },
    ) { Text(stringResource(R.string.account_recording_title)) }
}
