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

// S-ANL-01's legend, its G-G card and its stat tiles.

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Legend(items: List<LegendItem>) {
    val colors = analysisColors()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Canvas(Modifier.size(width = 18.dp, height = 10.dp)) {
                    val c = colors.legend(item.key)
                    val y = size.height / 2
                    when (item.key) {
                        LegendKey.LAT_G, LegendKey.ESTIMATED -> {
                            val on = if (item.key == LegendKey.LAT_G) 6.dp.toPx() else 3.dp.toPx()
                            drawLine(c, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), pathEffect = dash(on, 3.dp.toPx()))
                        }
                        LegendKey.NO_GPS -> drawRect(c.copy(alpha = 0.4f))
                        LegendKey.NOT_SCORED -> {
                            drawRect(c, style = Stroke(1f))
                            hatch(Rect(Offset.Zero, size), c, 3.dp.toPx(), 1f)
                        }
                        LegendKey.ELEVATION -> drawRect(colors.elevationFill)
                        else -> drawLine(c, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                    }
                }
                Text(item.label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
internal fun BottomRow(state: AnalysisState, timeline: AnalysisTimeline, cursor: DoubleState, controls: AnalysisControls) {
    val xl = LocalDensity.current.fontScale >= 1.3f
    if (xl) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GgCard(state, timeline, cursor, controls, Modifier.fillMaxWidth().aspectRatio(1f))
            state.stats.forEach { StatTileView(it, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GgCard(state, timeline, cursor, controls, Modifier.size(158.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.stats.forEach { StatTileView(it, Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
internal fun GgCard(
    state: AnalysisState,
    timeline: AnalysisTimeline,
    cursor: DoubleState,
    controls: AnalysisControls,
    modifier: Modifier,
) {
    val gg = state.gg ?: return
    val open = stringResource(R.string.analysis_gg_open)
    Card(
        modifier.semantics(mergeDescendants = true) { contentDescription = "${gg.accessibilityLabel} $open" }
            .clickable(role = Role.Button, onClick = controls::expandGg),
        shape = RoundedCornerShape(16.dp),
    ) {
        if (gg.empty) {
            Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                Text(AnalysisCopy.GG_EMPTY, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            GgCanvas(gg, timeline, cursor, expanded = false, modifier = Modifier.fillMaxSize().padding(6.dp))
        }
    }
}

@Composable
private fun StatTileView(tile: StatTile, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).semantics(mergeDescendants = true) {}) {
            Text(tile.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tile.value, style = MaterialTheme.typography.titleLarge)
            tile.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
