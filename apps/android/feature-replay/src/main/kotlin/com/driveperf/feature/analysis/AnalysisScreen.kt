package com.driveperf.feature.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DoubleState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driveperf.designsystem.DpEmptyState
import com.driveperf.designsystem.DpIcons
import com.driveperf.domain.analysis.AnalysisAxis
import com.driveperf.feature.replay.R
import com.driveperf.presentation.analysis.AnalysisCopy
import com.driveperf.presentation.analysis.AnalysisEffect
import com.driveperf.presentation.analysis.AnalysisState
import com.driveperf.presentation.analysis.AnalysisTimeline
import com.driveperf.presentation.analysis.AnalysisUnavailable
import com.driveperf.presentation.analysis.LegendItem
import com.driveperf.presentation.analysis.LegendKey
import com.driveperf.presentation.analysis.StatTile

internal const val ANALYSIS_TAG = "analysis_screen"

/**
 * S-ANL-01 Analysis, bound to its view model. [title] is the trip's title (part of day · label), shown under
 * "Analysis". [onReplay] opens S-RPL-01 for the trip; [onEvidence] opens S-RPL-02 for an event of it (DRIVE-39 AC1).
 */
@Composable
fun AnalysisRoute(
    viewModel: AnalysisViewModel,
    title: String,
    onBack: () -> Unit,
    onReplay: (String) -> Unit,
    onEvidence: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val back by rememberUpdatedState(onBack)
    val replay by rememberUpdatedState(onReplay)
    val evidence by rememberUpdatedState(onEvidence)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                AnalysisEffect.Close -> back()
                is AnalysisEffect.OpenReplay -> replay(effect.tripId)
                is AnalysisEffect.Message -> snackbar.showSnackbar(effect.text)
                is AnalysisEffect.OpenEvidence -> evidence(effect.eventId)
            }
        }
    }
    AnalysisScreen(state, viewModel.cursor, title, viewModel, onBack = viewModel::back, snackbar = snackbar)
}

/** The stateless screen. */
@Suppress("LongParameterList") // the state, the cursor and the screen's callbacks
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    state: AnalysisState,
    cursor: DoubleState,
    title: String,
    controls: AnalysisControls,
    onBack: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var channelsOpen by rememberSaveable { mutableStateOf(false) }
    var tableOpen by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.testTag(ANALYSIS_TAG),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(AnalysisCopy.TITLE, maxLines = 1)
                        if (title.isNotBlank()) {
                            Text(title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(DpIcons.Back), contentDescription = stringResource(R.string.analysis_back))
                    }
                },
                actions = {
                    if (state.timeline != null) {
                        IconButton(onClick = { channelsOpen = true }) {
                            Icon(painterResource(R.drawable.ic_analysis_channels), stringResource(R.string.analysis_channels))
                        }
                        IconButton(onClick = controls::openReplay) {
                            Icon(painterResource(R.drawable.ic_analysis_replay), stringResource(R.string.analysis_replay))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val timeline = state.timeline
        when {
            state.unavailable == AnalysisUnavailable.NOT_FOUND || state.unavailable == AnalysisUnavailable.LOCKED ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    DpEmptyState(title = AnalysisCopy.TITLE, body = AnalysisCopy.NOT_FOUND)
                }
            timeline == null -> Loading(state.loadingText, padding)
            else -> Workspace(state, timeline, cursor, controls, padding, onViewTable = { tableOpen = true })
        }
    }
    if (channelsOpen) ChannelSheet(state.channelOptions, controls::toggle, onDismiss = { channelsOpen = false })
    val gg = state.gg
    if (state.ggExpanded && gg != null) GgDialog(gg, state.timeline, cursor, title, controls)
    val timeline = state.timeline
    if (tableOpen && timeline != null) TableDialog(timeline, onDismiss = { tableOpen = false })
}

/** S-ANL-01 Load: skeletons that mirror the lanes, and the loader line. */
@Composable
private fun Loading(text: String, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(176.dp, 44.dp, 64.dp, 64.dp).forEach { h ->
            val skeleton = MaterialTheme.colorScheme.surfaceContainerHigh
            Surface(Modifier.fillMaxWidth().height(h), shape = RoundedCornerShape(12.dp), color = skeleton) {}
        }
    }
}

@Suppress("LongParameterList") // the state, the cursor and the screen's callbacks
@Composable
private fun Workspace(
    state: AnalysisState,
    timeline: AnalysisTimeline,
    cursor: DoubleState,
    controls: AnalysisControls,
    padding: PaddingValues,
    onViewTable: () -> Unit,
) {
    val viewTable = stringResource(R.string.analysis_view_table)
    BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
        // F-RPL-05 AC5: in landscape the timeline takes the full width and the map shrinks to an inset.
        val landscape = maxWidth > maxHeight && maxWidth >= 560.dp
        if (landscape) {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(0.7f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Toolbar(state, controls)
                    ReadoutRow(timeline, cursor)
                    TimelineCard(timeline, cursor, controls, onViewTable, viewTable)
                    Legend(timeline.legend)
                }
                Column(Modifier.weight(0.3f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MapCard(state, timeline, cursor, controls, Modifier.fillMaxWidth().aspectRatio(1.2f))
                    GgCard(state, timeline, cursor, controls, Modifier.fillMaxWidth().aspectRatio(1f))
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.summaryOnly) Text(AnalysisCopy.SUMMARY_ONLY, style = MaterialTheme.typography.bodySmall)
                MapCard(state, timeline, cursor, controls, Modifier.fillMaxWidth().height(176.dp))
                Toolbar(state, controls)
                ReadoutRow(timeline, cursor)
                TimelineCard(timeline, cursor, controls, onViewTable, viewTable)
                Legend(timeline.legend)
                BottomRow(state, timeline, cursor, controls)
                Text(AnalysisCopy.FOOTNOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MapCard(
    state: AnalysisState,
    timeline: AnalysisTimeline,
    cursor: DoubleState,
    controls: AnalysisControls,
    modifier: Modifier,
) {
    Card(modifier, shape = RoundedCornerShape(16.dp)) {
        val map = state.map
        if (map == null) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(AnalysisCopy.NO_MAP, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            AnalysisMapCanvas(
                map, timeline, cursor, stringResource(R.string.analysis_map_description),
                onTap = { p -> controls.tapMap(p.x, p.y) }, modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Toolbar(state: AnalysisState, controls: AnalysisControls) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            AnalysisAxis.entries.forEachIndexed { i, axis ->
                SegmentedButton(
                    selected = state.axis == axis,
                    onClick = { controls.setAxis(axis) },
                    shape = SegmentedButtonDefaults.itemShape(i, AnalysisAxis.entries.size),
                ) { Text(if (axis == AnalysisAxis.TIME) AnalysisCopy.AXIS_TIME else AnalysisCopy.AXIS_DISTANCE) }
            }
        }
        Spacer(Modifier.width(12.dp))
        FilledTonalIconButton(onClick = controls::zoomOut, enabled = state.canZoomOut, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_analysis_minus), stringResource(R.string.analysis_zoom_out))
        }
        Text(state.zoomLabel, Modifier.width(44.dp), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        FilledTonalIconButton(onClick = controls::zoomIn, enabled = state.canZoomIn, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_analysis_plus), stringResource(R.string.analysis_zoom_in))
        }
    }
}

/** "12:04 elapsed · 9.8 km" and the span chip. It reads the cursor, so only this row recomposes on a drag. */
@Composable
private fun ReadoutRow(timeline: AnalysisTimeline, cursor: DoubleState) {
    val readout = timeline.readout(cursor.doubleValue)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(readout.headline, style = MaterialTheme.typography.titleSmall)
        readout.chip?.let { chip ->
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(chip, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun TimelineCard(
    timeline: AnalysisTimeline,
    cursor: DoubleState,
    controls: AnalysisControls,
    onViewTable: () -> Unit,
    viewTable: String,
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        AnalysisTimelineChart(timeline, cursor, controls, onViewTable, viewTable, Modifier.padding(12.dp))
    }
}
