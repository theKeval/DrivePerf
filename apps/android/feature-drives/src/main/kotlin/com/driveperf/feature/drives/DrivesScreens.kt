package com.driveperf.feature.drives

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpEmptyState
import com.driveperf.designsystem.DpStatusChip

private const val LATEST_COUNT = 5

/**
 * The Drives tab root (S-HOME-01): the latest drives, with a way to all of them. [bottomPadding] leaves room for the
 * floating capture toolbar so the last row is never hidden behind it.
 */
@Composable
fun DrivesHomeScreen(
    state: DrivesUiState,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.isEmpty) {
            item {
                DpEmptyState(
                    title = stringResource(R.string.drives_empty_title),
                    body = stringResource(R.string.drives_empty_body),
                )
            }
        } else {
            item {
                Text(
                    text = stringResource(R.string.drives_latest),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
            }
            items(state.trips.take(LATEST_COUNT), key = { it.id }) { TripRow(it) }
            if (state.trips.size > LATEST_COUNT) {
                item { TextButton(onClick = onSeeAll) { Text(stringResource(R.string.drives_see_all)) } }
            }
            item {
                Text(
                    text = stringResource(R.string.drives_notice_analysis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Every recorded drive, newest first (S-HIS-01). */
@Composable
fun AllDrivesScreen(state: DrivesUiState, modifier: Modifier = Modifier, bottomPadding: Dp = 0.dp) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.isEmpty) {
            item {
                DpEmptyState(
                    title = stringResource(R.string.drives_empty_title),
                    body = stringResource(R.string.drives_empty_body),
                )
            }
        }
        items(state.trips, key = { it.id }) { TripRow(it) }
    }
}

@Composable
private fun TripRow(trip: TripRowUi, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = trip.start, style = MaterialTheme.typography.titleMediumEmphasized)
            Text(text = trip.summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DpStatusChip(label = stringResource(R.string.drives_recorded))
        }
    }
}
