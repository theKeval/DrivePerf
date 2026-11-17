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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DrivePerfTheme

/**
 * S-HOME-01 needsAttention: the banner above the Drives tab while the user is opted in and something blocks automatic
 * trips. A triangle glyph and text carry the state, never the colour alone; critical (location lost) uses the error
 * container, the rest the warning container (components.md section 22). Fix opens the capture status sheet (S-HOME-02),
 * as on iOS.
 */
@Composable
fun AttentionBanner(reason: AttentionReason, onFix: () -> Unit, modifier: Modifier = Modifier) {
    val (container, content) = bannerColors(reason.critical)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_attention_triangle),
                contentDescription = null,
                modifier = Modifier.size(24.dp).align(Alignment.Top),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.attention_banner_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(reason.explanation), style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onFix) { Text(stringResource(R.string.attention_fix)) }
        }
    }
}

@Composable
private fun bannerColors(critical: Boolean): Pair<Color, Color> = if (critical) {
    MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
} else {
    DrivePerfTheme.extendedColors.warningContainer to DrivePerfTheme.extendedColors.warning
}

@Preview
@Composable
private fun AttentionBannerPreview() {
    DrivePerfTheme { AttentionBanner(AttentionReason.NoBackgroundLocation, onFix = {}) }
}
