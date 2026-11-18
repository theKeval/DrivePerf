package com.driveperf.feature.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpModalBottomSheet
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.domain.replay.SegmentStyle

/** The legend for the line styles (F-RPL-08 AC1), in the shared sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LegendSheet(onClose: () -> Unit) {
    DpModalBottomSheet(onDismissRequest = onClose) {
        Column(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.replay_legend_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            for (style in SegmentStyle.entries) LegendRow(style)
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.replay_legend_done)) }
        }
    }
}

@Composable
private fun LegendRow(style: SegmentStyle) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier.size(width = 40.dp, height = 24.dp).background(DrivePerfTheme.dataColors.mapLand, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            LineSwatch(style, Modifier.width(32.dp).height(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(style.label()), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(style.legend()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
internal fun LoadingCard() {
    val loading = stringResource(R.string.replay_loading)
    Centered { CircularProgressIndicator(Modifier.semantics { contentDescription = loading }) }
}

/** S-RPL-01 Err: the recording is safe; Try again. */
@Composable
internal fun ErrorCard(onRetry: () -> Unit) = Centered {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.replay_error), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onRetry) { Text(stringResource(R.string.replay_try_again)) }
        }
    }
}

/** S-RPL-01 Lock (Free, older than 30 days), shown when [ReplayProLock] says so. See plans arrives with the paywall. */
@Composable
internal fun LockCard(onNotNow: () -> Unit) = Centered {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_replay_lock), contentDescription = null, tint = DrivePerfTheme.extendedColors.pro)
            Text(stringResource(R.string.replay_lock_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.replay_lock_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onNotNow) { Text(stringResource(R.string.replay_lock_not_now)) }
        }
    }
}
