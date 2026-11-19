package com.driveperf.feature.drives

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.driveperf.designsystem.DpEvent
import com.driveperf.designsystem.DpEventGlyph
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.domain.trip.EventType
import com.driveperf.presentation.score.ComparisonTone
import com.driveperf.presentation.score.ComparisonUi
import com.driveperf.presentation.score.ContributorUi
import com.driveperf.presentation.score.EventRowUi
import com.driveperf.presentation.score.ScoreBadgeUi

// The score components of the Drives screens (components.md §3, §4, §9.2, §12, §18). They live in feature-drives, not
// the design system: the token files are frozen in wave 1 and each session keeps its additions in its own module.

private const val FULL_CIRCLE = 360f
private const val TOP = -90f
private const val TRACK_FRACTION = 0.1f
private const val NOTCH_OVERHANG = 0.25f
private const val NEEDED_ALPHA = 0.6f
private const val MAX_SCORE = 100f

/**
 * The Smoothness ring (components.md §3) on its Cookie9Sided tonal container: track, gradient arc from 12 o'clock and
 * the baseline notch. [score] null draws the unscored ring: a dashed track with "—" and no arc.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ScoreRing(score: Int?, baseline: Int?, size: Dp, modifier: Modifier = Modifier, label: String? = null) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val notch = DrivePerfTheme.extendedColors.text2
    val primary = MaterialTheme.colorScheme.primary
    val arc = Brush.sweepGradient(listOf(primary.copy(alpha = 0.65f), primary, primary.copy(alpha = 0.85f), primary.copy(alpha = 0.65f)))
    Box(modifier.size(size).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialShapes.Cookie9Sided.toShape(),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
            modifier = Modifier.size(size),
        ) {}
        Canvas(Modifier.size(size * 0.82f)) {
            val stroke = this.size.minDimension * TRACK_FRACTION
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                track, 0f, FULL_CIRCLE, false, topLeft, arcSize,
                style = Stroke(stroke, pathEffect = if (score == null) PathEffect.dashPathEffect(floatArrayOf(stroke, stroke)) else null),
            )
            if (score != null) {
                rotate(TOP) {
                    drawArc(arc, 0f, FULL_CIRCLE * score / MAX_SCORE, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            if (score != null && baseline != null) {
                // A 2 dp tick across the track at the baseline, drawn at 12 o'clock and turned into place.
                rotate(FULL_CIRCLE * baseline / MAX_SCORE) {
                    val x = this.size.width / 2
                    drawLine(notch, Offset(x, -stroke * NOTCH_OVERHANG), Offset(x, stroke * (1 + NOTCH_OVERHANG)), strokeWidth = 2.dp.toPx())
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                score?.toString() ?: "—",
                style = MaterialTheme.typography.displaySmall.copy(fontSize = (size.value * 0.3f).sp),
                fontWeight = FontWeight.SemiBold,
            )
            label?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = DrivePerfTheme.extendedColors.text2) }
        }
    }
}

/** The comparison chip (components.md §4): better in the accent, lower in neutral grey (never red), dots while calibrating. */
@Composable
internal fun ComparisonChip(comparison: ComparisonUi, modifier: Modifier = Modifier) {
    val better = comparison.tone == ComparisonTone.BETTER
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (better) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (better) MaterialTheme.colorScheme.primary else DrivePerfTheme.extendedColors.text2,
    ) {
        Row(
            Modifier.heightIn(min = 28.dp).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (comparison.glyph.isNotEmpty()) Text(comparison.glyph, style = MaterialTheme.typography.labelMedium)
            Text(comparison.text, style = MaterialTheme.typography.labelLarge)
            if (comparison.tone == ComparisonTone.CALIBRATING && comparison.calibratingOf > 0) {
                CalibratingDots(comparison.calibratingK, comparison.calibratingOf)
            }
        }
    }
}

/** ●●○: "k of 3". */
@Composable
private fun CalibratingDots(k: Int, of: Int) {
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.outlineVariant
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(of) { i -> Surface(shape = CircleShape, color = if (i < k) on else off, modifier = Modifier.size(6.dp)) {} }
    }
}

/** A trip card's score badge (components.md §12): the score on a Cookie9Sided shape, the mini delta under it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ScoreBadge(badge: ScoreBadgeUi, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val score = badge.score
        if (score == null) {
            Text(
                badge.notScoredText.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = DrivePerfTheme.extendedColors.text2,
            )
            return@Column
        }
        Surface(
            shape = MaterialShapes.Cookie9Sided.toShape(),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(46.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(score, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (badge.newRecord) {
                Text("✪", style = MaterialTheme.typography.labelMedium, color = DrivePerfTheme.extendedColors.record)
            }
            badge.delta?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (badge.tone == ComparisonTone.BETTER) MaterialTheme.colorScheme.primary else DrivePerfTheme.extendedColors.text2,
                )
            }
        }
    }
}

/** A contributor row (components.md §18): label, band chip ▲/●/▼ with words, reason. */
@Composable
internal fun ContributorRow(contributor: ContributorUi, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clearAndSetSemantics { contentDescription = contributor.accessibilityLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(contributor.label, style = MaterialTheme.typography.titleSmall)
                contributor.bandText?.let { BandChip(contributor.bandGlyph.orEmpty(), it, contributor.band == com.driveperf.domain.score.Band.SMOOTHER) }
            }
            Text(contributor.reason, style = MaterialTheme.typography.bodyMedium, color = DrivePerfTheme.extendedColors.text2)
        }
        trailing?.invoke()
    }
}

@Composable
private fun BandChip(glyph: String, text: String, better: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (better) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (better) MaterialTheme.colorScheme.primary else DrivePerfTheme.extendedColors.text2,
    ) {
        Text("$glyph $text", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

/** An event row (components.md §9.2): glyph by shape, label and time, peak g and duration, its tag. */
@Composable
internal fun EventRow(event: EventRowUi, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clearAndSetSemantics { contentDescription = event.accessibilityLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DpEventGlyph(event.type.dp(), needed = event.needed, size = 28.dp)
        Column(Modifier.weight(1f).alpha(if (event.excluded) NEEDED_ALPHA else 1f)) {
            Text(event.label, style = MaterialTheme.typography.bodyLarge)
            Text(event.time, style = MaterialTheme.typography.bodySmall, color = DrivePerfTheme.extendedColors.text2)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                event.meta,
                style = MaterialTheme.typography.labelMedium,
                textDecoration = if (event.needed) TextDecoration.LineThrough else null,
            )
            event.tag?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = DrivePerfTheme.extendedColors.text2) }
        }
    }
}

internal fun EventType.dp(): DpEvent = when (this) {
    EventType.BRAKE -> DpEvent.BRAKE
    EventType.ACCEL -> DpEvent.ACCEL
    EventType.CORNER -> DpEvent.CORNER
}

/** A quiet card colour for the score surfaces. */
internal val cardColor: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer
