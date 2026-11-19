package com.driveperf.feature.replay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpModalBottomSheet
import com.driveperf.presentation.evidence.EvidenceCopy
import com.driveperf.presentation.evidence.TipDetailIntent
import com.driveperf.presentation.evidence.TipDetailState

internal const val TIP_SHEET_TAG = "tip_sheet"

/**
 * S-SCO-04 "One thing for next time" (F-SCO-06), as a full-height sheet over Replay (screen map: "Push / sheet"): the
 * positive card first, the tip, why this one (with the longitudinal-G sparkline and "See the moment"), and three steps.
 * The tip's hero card waits for the tip library's words (`TipCopy.text`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TipDetailSheet(state: TipDetailState, controls: ReplayControls) {
    DpModalBottomSheet(onDismissRequest = controls::closeTip, modifier = Modifier.testTag(TIP_SHEET_TAG)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                state.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 8.dp).semantics { heading() },
            )
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                state.noTip != null -> Card {
                    Text(EvidenceCopy.NO_TIP_TITLE, style = MaterialTheme.typography.titleMedium)
                    Text(state.noTip.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
                else -> TipBody(state) { controls.tip(it) }
            }
        }
    }
}

@Composable
private fun TipBody(state: TipDetailState, send: (TipDetailIntent) -> Unit) {
    state.cleanest?.let { cleanest ->
        Card {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.ic_replay_sparkle), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(EvidenceCopy.CLEANEST, style = MaterialTheme.typography.labelMedium)
                    Text(cleanest, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    state.tipText?.let { text ->
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.kicker, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(text, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
    Section(EvidenceCopy.WHY_THIS_ONE) {
        Text(state.why, style = MaterialTheme.typography.bodyMedium)
        if (state.sparkline.isNotEmpty()) Sparkline(state.sparkline, state.sparklinePeakIndex)
        if (state.eventId != null) {
            FilledTonalButton(
                onClick = { send(TipDetailIntent.SeeTheMoment) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(EvidenceCopy.SEE_THE_MOMENT) }
        }
    }
    Section(EvidenceCopy.HOW_TO_TRY) {
        state.steps.forEachIndexed { i, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(step, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Text(
        state.footer,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp).semantics { heading() },
        )
        Card(content = content)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}
