package com.driveperf.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** What a health glyph says: OK, needs fixing, or off (the user's choice). */
public enum class DpHealth { Ok, Fix, Off }

/**
 * The health glyph of the capture status sheet and Recording settings (S-HOME-02, S-SET-03): a disc with a check, an
 * exclamation mark or a pulse line. The shape and [label] carry the state, never the colour alone.
 */
@Composable
public fun DpHealthGlyph(health: DpHealth, label: String, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val background = when (health) {
        DpHealth.Ok -> DrivePerfTheme.extendedColors.positive
        DpHealth.Fix -> MaterialTheme.colorScheme.error
        DpHealth.Off -> MaterialTheme.colorScheme.secondaryContainer
    }
    val ink = when (health) {
        DpHealth.Ok -> MaterialTheme.colorScheme.surface
        DpHealth.Fix -> MaterialTheme.colorScheme.onError
        DpHealth.Off -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val icon = when (health) {
        DpHealth.Ok -> DpIcons.Check
        DpHealth.Fix -> DpIcons.Exclamation
        DpHealth.Off -> DpIcons.Health
    }
    Box(
        modifier = modifier
            .size(size)
            .background(background, CircleShape)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = ink, modifier = Modifier.size(size * ICON_RATIO))
    }
}

private const val ICON_RATIO = 0.7f
