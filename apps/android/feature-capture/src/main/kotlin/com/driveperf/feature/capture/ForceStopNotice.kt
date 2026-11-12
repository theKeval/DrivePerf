package com.driveperf.feature.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.driveperf.designsystem.DrivePerfTheme

@Composable
fun ForceStopNotice(rearmed: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_force_stop_shield),
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(
                        if (rearmed) R.string.capture_force_stop_title else R.string.capture_auto_detect_attention,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(
                        if (rearmed) R.string.capture_force_stop_rearmed else R.string.capture_force_stop_paused,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                FilledTonalButton(onClick = onDismiss, modifier = Modifier.align(Alignment.Start)) {
                    Text(stringResource(R.string.capture_force_stop_dismiss))
                }
            }
        }
    }
}

@Preview
@Composable
private fun ForceStopNoticePreview() {
    DrivePerfTheme { ForceStopNotice(rearmed = true, onDismiss = {}) }
}
