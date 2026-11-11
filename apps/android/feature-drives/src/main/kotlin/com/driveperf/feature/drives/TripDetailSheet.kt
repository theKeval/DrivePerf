package com.driveperf.feature.drives

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipScope
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpEstimated
import com.driveperf.designsystem.DpProvenance
import com.driveperf.designsystem.DpProvenanceGlyph
import com.driveperf.designsystem.DpQuality
import com.driveperf.designsystem.DpQualityGlyph
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.designsystem.dpEstimated
import com.driveperf.designsystem.dpQualityColor
import com.driveperf.domain.trip.OverlayStyle
import com.driveperf.domain.trip.Provenance
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.TripCorrection
import kotlinx.coroutines.launch

/** The trip sheet and the data-quality sheet, when the state opens them. */
@Composable
internal fun DrivesSheets(state: DrivesUiState, onAction: (DrivesAction) -> Unit) {
    state.detail?.let { TripDetailSheet(it, state.canCorrect, onAction) }
    state.quality?.let { QualitySheet(it, onDismiss = { onAction(DrivesAction.CloseQuality) }) }
}

/**
 * Trip sheet (S-TRP-01, the parts the shell has): banners (question, passenger or not a drive, interrupted, short,
 * unscorable), stat tiles with provenance on long-press (F-DQ-05), the data-quality row (→ S-DQ-01) and "What was
 * this?" (S-TRP-02). Map, score and events arrive with their own stories.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TripDetailSheet(detail: TripDetailUi, canCorrect: Boolean, onAction: (DrivesAction) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onAction(DrivesAction.CloseDetail) },
        sheetState = fullHeightSheetState(),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(detail.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            detail.banners.forEach { Banner(it, detail.id, onAction) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                detail.tiles.forEach { StatTile(it, Modifier.width(104.dp)) }
            }
            Text(
                stringResource(R.string.drives_provenance_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column {
                    detail.quality?.let { badge ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable { onAction(DrivesAction.OpenQuality(detail.id)) }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.drives_data_quality), Modifier.weight(1f))
                            QualityBadgeChip(badge)
                        }
                    }
                    if (canCorrect) {
                        if (detail.quality != null) HorizontalDivider()
                        CorrectionRow(detail.correction) { onAction(DrivesAction.Correct(detail.id, it)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Banner(banner: TripBannerUi, tripId: String, onAction: (DrivesAction) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (banner.attention) DrivePerfTheme.extendedColors.warningContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(banner.text, style = MaterialTheme.typography.bodyMedium)
            when (banner.action) {
                is BannerAction.Answer -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onAction(DrivesAction.Answer(tripId, true)) }) { Text(stringResource(R.string.drives_yes)) }
                    Button(onClick = { onAction(DrivesAction.Answer(tripId, false)) }) { Text(stringResource(R.string.drives_no)) }
                }
                BannerAction.Restore -> TextButton(onClick = { onAction(DrivesAction.Correct(tripId, TripCorrection.DRIVER)) }) {
                    Text(stringResource(R.string.drives_restore))
                }
                null -> Unit
            }
        }
    }
}

@Composable
private fun CorrectionRow(current: TripCorrection, onPick: (TripCorrection) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable { open = true }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.drives_what_was_this), Modifier.weight(1f))
            Text(TripPresenter.correctionTitle(current), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CorrectionMenu(expanded = open, onDismiss = { open = false }, onPick = onPick)
    }
}

/** One number tile (components.md §5) with its provenance glyph; long-press shows the provenance tooltip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatTile(value: ProvenancedValue, modifier: Modifier = Modifier) {
    val tooltip = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    val showHow = stringResource(R.string.drives_show_how_measured)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { ProvenanceTooltip(value) },
        state = tooltip,
    ) {
        Surface(
            modifier = modifier.clearAndSetSemantics {
                contentDescription = value.contentDescription
                customActions = listOf(CustomAccessibilityAction(showHow) { scope.launch { tooltip.show() }; true })
            },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(value.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    DpProvenanceGlyph(value.provenance.dp(), MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.dpEstimated(value.estimated), verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (value.estimated) DpEstimated.text(value.value) else value.value,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (value.unit.isNotEmpty()) {
                        Text(
                            " ${value.unit}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                value.note?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The provenance tooltip (components.md §10.2, Android RichTooltip): glyph + word + legend line + metric line. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TooltipScope.ProvenanceTooltip(value: ProvenancedValue) {
    RichTooltip(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DpProvenanceGlyph(value.provenance.dp(), MaterialTheme.colorScheme.onSurface, size = 14.dp)
                Text(value.provenance.label)
            }
        },
    ) {
        Text(listOf(value.provenance.meaning, value.meaning).filter { it.isNotEmpty() }.joinToString("\n"))
    }
}

/** A modal sheet that opens fully expanded (no half-height stop): S-DQ-01 and the trip sheet are full height. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun fullHeightSheetState(): SheetState =
    rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

internal fun QualityBadge.dp(): DpQuality = when (this) {
    QualityBadge.GOOD -> DpQuality.GOOD
    QualityBadge.USABLE -> DpQuality.USABLE
    QualityBadge.LIMITED -> DpQuality.LIMITED
    QualityBadge.UNSCORABLE -> DpQuality.UNSCORABLE
}

internal fun Provenance.dp(): DpProvenance = when (this) {
    Provenance.OBSERVED -> DpProvenance.OBSERVED
    Provenance.DERIVED -> DpProvenance.DERIVED
    Provenance.ESTIMATED -> DpProvenance.ESTIMATED
}

