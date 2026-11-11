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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.driveperf.app.R
import com.driveperf.app.core.AutoDetectOff
import com.driveperf.feature.onboarding.PermissionReader
import com.driveperf.platform.crash.ConsentSettings

/** Reads whether automatic trips are on, again every time the app comes back (the user may change it in Settings). */
@Composable
fun rememberAutoDetectOff(reader: PermissionReader, consent: ConsentSettings): AutoDetectOff? {
    var off by remember { mutableStateOf(AutoDetectOff.of(reader.read(), consent.automaticTripsEnabled)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { off = AutoDetectOff.of(reader.read(), consent.automaticTripsEnabled) }
    return off
}

/** "Auto-detect off" with the reason and, when Settings can fix it, a Fix button (F-ONB-04.6). */
@Composable
fun AutoDetectNotice(reason: AutoDetectOff, onFix: () -> Unit, modifier: Modifier = Modifier) {
    val text = when (reason) {
        AutoDetectOff.OptedOut -> R.string.auto_detect_off_manual
        AutoDetectOff.NoLocation, AutoDetectOff.NoBackgroundLocation -> R.string.auto_detect_off_location
        AutoDetectOff.NoActivityRecognition -> R.string.auto_detect_off_activity
    }
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
                Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
            }
            if (reason.fixableInSettings) {
                TextButton(onClick = onFix) { Text(stringResource(R.string.auto_detect_fix)) }
            }
        }
    }
}
