package com.driveperf.feature.replay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpEventGlyph
import com.driveperf.designsystem.DpModalBottomSheet
import com.driveperf.presentation.evidence.EvidenceBanner
import com.driveperf.presentation.evidence.EvidenceBannerKind
import com.driveperf.presentation.evidence.EvidenceCopy
import com.driveperf.presentation.evidence.EvidenceFact
import com.driveperf.presentation.evidence.EvidenceIntent
import com.driveperf.presentation.evidence.EvidenceState
import com.driveperf.presentation.evidence.EvidenceTip
import com.driveperf.presentation.evidence.NeededSheet
import com.driveperf.presentation.evidence.NeededStep

internal const val EVIDENCE_SHEET_TAG = "evidence_sheet"
internal const val NEEDED_SHEET_TAG = "needed_sheet"
internal const val WAS_IT_NEEDED_TAG = "evidence_was_it_needed"
internal const val NOT_ACCURATE_TAG = "evidence_not_accurate"

/**
 * S-RPL-02, the event evidence sheet (F-RPL-04), over the paused replay: the event, its banners, the ±10 s trace (or
 * "Detailed trace not kept", EC-STO), the facts with their provenance, the related tip, "Was it needed?" (S-SCO-03 on
 * top), "Not accurate?" with its reasons, and the "‹ Event 1 of 2 ›" stepper. Every word comes from the shared
 * presenter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EvidenceSheet(state: EvidenceState, controls: ReplayControls) {
    DpModalBottomSheet(onDismissRequest = controls::closeEvidence, modifier = Modifier.testTag(EVIDENCE_SHEET_TAG)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                state.missing -> Text(stringResource(R.string.evidence_missing), style = MaterialTheme.typography.bodyLarge)
                else -> EvidenceBody(state, controls::evidence)
            }
        }
    }
    state.neededSheet?.let { NeededSheetView(it, state, controls::evidence) }
}

@Composable
private fun EvidenceBody(state: EvidenceState, send: (EvidenceIntent) -> Unit) {
    Header(state)
    for (banner in state.banners) Banner(banner, send)
    state.trace?.let { trace ->
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            EvidenceChart(trace, state.speedPerMps, state.traceSummary, Modifier.padding(12.dp))
        }
    }
    Facts(state.facts)
    state.tip?.let { TipCard(it, onOpen = { send(EvidenceIntent.OpenTip) }) }
    Actions(state, send)
    if (state.reasonsOpen) Reasons(state, send)
    Stepper(state, send)
}

@Composable
private fun Header(state: EvidenceState) {
    val type = state.type ?: return
    Row(
        Modifier.clearAndSetSemantics { contentDescription = state.accessibilityLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DpEventGlyph(type.dp(), needed = state.needed)
        Column {
            Text(state.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(state.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Banner(banner: EvidenceBanner, send: (EvidenceIntent) -> Unit) {
    val container = when (banner.kind) {
        EvidenceBannerKind.NEEDED -> MaterialTheme.colorScheme.primaryContainer
        EvidenceBannerKind.LOW_CONFIDENCE, EvidenceBannerKind.TRACE_REMOVED -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    Surface(shape = RoundedCornerShape(12.dp), color = container, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(banner.text, style = MaterialTheme.typography.bodyMedium)
            banner.action?.let { action ->
                TextButton(onClick = { send(EvidenceIntent.AskCountAgain) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
            }
        }
    }
}

/** Peak · Duration · Speed · Confidence: four columns, two at the largest font scales. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Facts(facts: List<EvidenceFact>) {
    val columns = if (LocalConfiguration.current.fontScale >= LARGE_FONT) 2 else facts.size.coerceAtLeast(1)
    FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = columns, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (fact in facts) {
            Column(Modifier.weight(1f).clearAndSetSemantics { contentDescription = fact.spoken }) {
                ProvenanceLabel(fact.provenance)
                Text(fact.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        fact.value,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (fact.struck) TextDecoration.LineThrough else null,
                    )
                    if (fact.unit.isNotEmpty()) {
                        Text(
                            " ${fact.unit}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private const val LARGE_FONT = 1.5f

@Composable
internal fun TipCard(tip: EvidenceTip, onOpen: (() -> Unit)?) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = TIP_TINT)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tip.kicker, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tip.text, style = MaterialTheme.typography.bodyLarge)
            onOpen?.let {
                TextButton(onClick = it, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(EvidenceCopy.WHY_THIS_TIP)
                    Icon(painterResource(R.drawable.ic_replay_chevron_right), contentDescription = null)
                }
            }
        }
    }
}

private const val TIP_TINT = 0.35f

@Composable
private fun Actions(state: EvidenceState, send: (EvidenceIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!state.needed) {
            FilledTonalButton(
                onClick = { send(EvidenceIntent.AskNeeded) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(WAS_IT_NEEDED_TAG),
            ) { Text(EvidenceCopy.WAS_IT_NEEDED) }
        }
        if (!state.reasonsOpen) {
            TextButton(
                onClick = { send(EvidenceIntent.OpenReasons) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(NOT_ACCURATE_TAG),
            ) { Text(EvidenceCopy.NOT_ACCURATE) }
        }
    }
}

/** "Not accurate?": the reasons as radios, then Send and Cancel (F-RPL-04 AC4). */
@Composable
private fun Reasons(state: EvidenceState, send: (EvidenceIntent) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(EvidenceCopy.REASONS_TITLE, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(EvidenceCopy.REASONS_BODY, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (option in state.reasons) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                    selected = option.reason == state.selectedReason,
                    role = Role.RadioButton,
                    onClick = { send(EvidenceIntent.SelectReason(option.reason)) },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option.reason == state.selectedReason, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(option.label, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { send(EvidenceIntent.SendReason) }, enabled = state.selectedReason != null && !state.busy) {
                Text(EvidenceCopy.SEND)
            }
            TextButton(onClick = { send(EvidenceIntent.CancelReasons) }) { Text(EvidenceCopy.CANCEL) }
        }
    }
}

/** "‹ Event 1 of 2 ›": the gesture-free way to the other events (accessibility.md §6). */
@Composable
private fun Stepper(state: EvidenceState, send: (EvidenceIntent) -> Unit) {
    if (state.position.isEmpty()) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { send(EvidenceIntent.Previous) }, enabled = state.hasPrevious && !state.busy) {
            Icon(painterResource(R.drawable.ic_replay_chevron_left), contentDescription = stringResource(R.string.evidence_previous))
        }
        Text(
            state.position,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        FilledTonalIconButton(onClick = { send(EvidenceIntent.Next) }, enabled = state.hasNext && !state.busy) {
            Icon(painterResource(R.drawable.ic_replay_chevron_right), contentDescription = stringResource(R.string.evidence_next))
        }
    }
}

/** S-SCO-03 "Was it needed?" (F-SCO-05): a small sheet over the evidence, with the D4 route-record note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NeededSheetView(sheet: NeededSheet, state: EvidenceState, send: (EvidenceIntent) -> Unit) {
    DpModalBottomSheet(onDismissRequest = { send(EvidenceIntent.DismissNeeded) }, modifier = Modifier.testTag(NEEDED_SHEET_TAG)) {
        Column(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(sheet.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            state.type?.let { type ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DpEventGlyph(type.dp(), needed = sheet.step == NeededStep.DONE || state.needed, size = 30.dp)
                    Column {
                        Text(state.title, style = MaterialTheme.typography.titleSmall)
                        Text(state.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            sheet.score?.let { score ->
                val smoothness = stringResource(R.string.evidence_smoothness, score)
                Text(
                    "$score",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { contentDescription = smoothness },
                )
            }
            Text(sheet.body, style = MaterialTheme.typography.bodyLarge)
            Text(sheet.capNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val primary: EvidenceIntent = when (sheet.step) {
                NeededStep.ASK -> EvidenceIntent.ConfirmNeeded
                NeededStep.DONE -> EvidenceIntent.DismissNeeded
                NeededStep.COUNT_AGAIN -> EvidenceIntent.ConfirmCountAgain
            }
            Button(
                onClick = { send(primary) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(sheet.primary) }
            sheet.secondary?.let {
                TextButton(onClick = { send(EvidenceIntent.DismissNeeded) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(it)
                }
            }
        }
    }
}
