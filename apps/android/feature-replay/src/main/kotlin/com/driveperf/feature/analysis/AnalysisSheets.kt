package com.driveperf.feature.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DoubleState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.driveperf.designsystem.DpModalBottomSheet
import com.driveperf.domain.analysis.AnalysisChannel
import com.driveperf.feature.replay.R
import com.driveperf.presentation.analysis.AnalysisCopy
import com.driveperf.presentation.analysis.AnalysisTimeline
import com.driveperf.presentation.analysis.ChannelOption
import com.driveperf.presentation.analysis.GgView

/** S-ANL-03: channel toggles; the choice persists per user (F-RPL-05 AC4). Jerk and sample quality carry PRO. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChannelSheet(options: List<ChannelOption>, onToggle: (AnalysisChannel) -> Unit, onDismiss: () -> Unit) {
    DpModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(AnalysisCopy.CHANNELS_TITLE, style = MaterialTheme.typography.titleLarge)
            Text(AnalysisCopy.CHANNELS_SUB, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            options.filterNot { it.pro }.forEach { ChannelRow(it, onToggle) }
            Spacer(Modifier.height(16.dp))
            Text(AnalysisCopy.CHANNELS_PRO_SECTION, style = MaterialTheme.typography.titleSmall)
            options.filter { it.pro }.forEach { ChannelRow(it, onToggle) }
            val footer = MaterialTheme.colorScheme.onSurfaceVariant
            Text(AnalysisCopy.CHANNELS_FOOTER, style = MaterialTheme.typography.bodySmall, color = footer)
        }
    }
}

@Composable
private fun ChannelRow(option: ChannelOption, onToggle: (AnalysisChannel) -> Unit) {
    ListItem(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = option.accessibilityLabel },
        supportingContent = { Text(option.description) },
        trailingContent = { Switch(checked = option.on, onCheckedChange = { onToggle(option.channel) }, enabled = option.enabled) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(option.title)
            if (option.pro) {
                ProBadge()
            }
        }
    }
}

@Composable
private fun ProBadge() {
    Surface(Modifier.padding(start = 8.dp), shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(stringResource(R.string.analysis_pro), Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.labelSmall)
    }
}

/** S-ANL-02: the friction circle full screen, with the event-type filter (F-RPL-06 AC2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GgDialog(gg: GgView, timeline: AnalysisTimeline?, cursor: DoubleState, subtitle: String, controls: AnalysisControls) {
    Dialog(onDismissRequest = controls::collapseGg, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(AnalysisCopy.GG_TITLE)
                            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = controls::collapseGg) {
                            Icon(painterResource(R.drawable.ic_analysis_close), stringResource(R.string.analysis_close))
                        }
                    },
                )
                GgBody(gg, timeline, cursor, controls)
            }
        }
    }
}

@Composable
private fun GgBody(gg: GgView, timeline: AnalysisTimeline?, cursor: DoubleState, controls: AnalysisControls) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.widthIn(max = 372.dp).fillMaxWidth().aspectRatio(1f)) {
                    GgCanvas(gg, timeline, cursor, expanded = true, modifier = Modifier.fillMaxSize())
                }
                Text(AnalysisCopy.GG_AXES, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(gg.caption ?: AnalysisCopy.GG_EMPTY, style = MaterialTheme.typography.bodyMedium)
                gg.filterCaption?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            gg.filters.forEach { chip ->
                FilterChip(
                    selected = chip.selected,
                    onClick = { controls.filterGg(chip.type) },
                    label = { Text(chip.label) },
                    leadingIcon = chip.type?.let { type ->
                        {
                            val colors = analysisColors()
                            androidx.compose.foundation.Canvas(Modifier.size(14.dp)) {
                                eventMark(type, false, center, size.minDimension, colors.event(type), colors.surface)
                            }
                        }
                    },
                )
            }
        }
        gg.partBanner?.let { banner ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(banner, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (gg.peaks.isNotEmpty()) {
            Text(AnalysisCopy.GG_PEAKS_TITLE, style = MaterialTheme.typography.titleMedium)
            Card(shape = RoundedCornerShape(16.dp)) {
                gg.peaks.forEach { row ->
                    ListItem(
                        modifier = Modifier.heightIn(min = 56.dp),
                        supportingContent = { Text(row.detail) },
                        trailingContent = { TextButton(onClick = { controls.openEvidence(row.eventId) }) { Text(row.value) } },
                    ) { Text(row.title) }
                }
            }
            Text(AnalysisCopy.GG_FOOTER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(AnalysisCopy.GG_HOW_TITLE, style = MaterialTheme.typography.titleMedium)
        Text(AnalysisCopy.GG_HOW_1, style = MaterialTheme.typography.bodyMedium)
        Text(AnalysisCopy.GG_HOW_2, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The "View as table" accessibility action (S-ANL-01): time, speed, long G and lat G every 30 s, plus every event. */
@Composable
internal fun TableDialog(timeline: AnalysisTimeline, onDismiss: () -> Unit) {
    val rows = timeline.tableRows()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.analysis_close)) } },
        title = { Text(stringResource(R.string.analysis_table_title)) },
        text = {
            Column {
                Text(stringResource(R.string.analysis_table_header), style = MaterialTheme.typography.labelMedium)
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(rows) { r ->
                        Text(
                            listOfNotNull(r.time, r.speed, r.lonG, r.latG, r.note).joinToString(" · "),
                            Modifier.padding(vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
    )
}
