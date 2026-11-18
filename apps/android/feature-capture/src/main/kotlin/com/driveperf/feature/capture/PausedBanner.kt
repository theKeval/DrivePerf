package com.driveperf.feature.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpIcons
import com.driveperf.designsystem.DrivePerfTheme

/**
 * S-HOME-01 autoPaused: "Auto-detect paused until 6:00 tomorrow" above the Drives tab while the user has paused automatic
 * trips (F-CAP-15 AC1), with Resume. A pause glyph and the words carry the state, never the colour alone.
 */
@Composable
fun PausedBanner(pausedUntilMs: Long?, onResume: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(DpIcons.Pause),
                contentDescription = null,
                modifier = Modifier.size(24.dp).align(Alignment.Top),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.paused_banner_title, pausedUntilPhrase(pausedUntilMs)),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.paused_banner_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onResume) { Text(stringResource(R.string.paused_banner_resume)) }
        }
    }
}

@Preview
@Composable
private fun PausedBannerPreview() {
    DrivePerfTheme { PausedBanner(System.currentTimeMillis() + 8 * 3_600_000L, onResume = {}) }
}
