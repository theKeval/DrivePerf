package com.driveperf.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.driveperf.app.R

/**
 * S-HOME-01 manualOnly: "Auto-detect off" when the user chose manual trips (F-ONB-04.6), with "Turn on" to Recording
 * settings (S-SET-03). A missing permission is not "off": it shows the attention banner instead (F-CAP-02).
 */
@Composable
fun AutoDetectNotice(onTurnOn: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.auto_detect_off_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.auto_detect_off_manual), style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onTurnOn) { Text(stringResource(R.string.auto_detect_turn_on)) }
        }
    }
}
