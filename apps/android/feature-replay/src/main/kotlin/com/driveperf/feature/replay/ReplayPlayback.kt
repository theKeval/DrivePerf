package com.driveperf.feature.replay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DoubleState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpIcons
import com.driveperf.domain.replay.PlaybackSpeed

internal const val PLAY_BUTTON_TAG = "replay_play"
internal const val STATS_TAG = "replay_stats"

/** Play/Pause, the speed group, the scrubber and the time labels (M3E floating toolbar, radius 28). */
@Composable
internal fun PlaybackBar(state: ReplayUiState.Ready, time: DoubleState, controls: ReplayControls) {
    val atStart by remember { derivedStateOf { time.doubleValue == 0.0 } }
    if (atStart && !state.playing) Note(stringResource(R.string.replay_hint))
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayButton(state.playing, controls::togglePlay)
                SpeedGroup(state.speed, controls::setSpeed, Modifier.weight(1f))
            }
            val overlay = state.overlay
            val ticks = remember(overlay) {
                overlay?.let { o -> ScrubberTicks(o.ticks.map { it.marker }, o.details.stops) } ?: ScrubberTicks.None
            }
            ReplayScrubber(time, state.replay, state.scrubbing, controls, ticks = ticks)
            TimeLabels(time, state.replay.durationS)
            overlay?.details?.elevation?.let { profile ->
                ElevationRow(profile, state.replay.durationS, overlay.elevationText.orEmpty(), time)
            }
            overlay?.let { o ->
                Text(
                    o.statsLine(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(STATS_TAG),
                )
            }
        }
    }
}

@Composable
private fun TimeLabels(time: DoubleState, durationS: Double) {
    val elapsed by remember { derivedStateOf { ReplayFormatting.clock(time.doubleValue) } }
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth()) {
        Text(elapsed, style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.weight(1f))
        Text(ReplayFormatting.clock(durationS), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** FAB-style play (primaryContainer, radius 16). */
@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(56.dp).testTag(PLAY_BUTTON_TAG),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(if (playing) DpIcons.Pause else DpIcons.Play),
                contentDescription = stringResource(if (playing) R.string.replay_pause else R.string.replay_play),
            )
        }
    }
}

private val SPEEDS = listOf(
    PlaybackSpeed.AUTO to R.string.replay_speed_auto,
    PlaybackSpeed.X1 to R.string.replay_speed_x1,
    PlaybackSpeed.X10 to R.string.replay_speed_x10,
    PlaybackSpeed.X30 to R.string.replay_speed_x30,
)

/** Auto · 1× · 10× · 30× as an M3E connected button group; each is a tab for TalkBack. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SpeedGroup(selected: PlaybackSpeed, onSpeed: (PlaybackSpeed) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        SPEEDS.forEachIndexed { i, (speed, label) ->
            ToggleButton(
                checked = speed == selected,
                onCheckedChange = { onSpeed(speed) },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    SPEEDS.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { role = Role.Tab },
            ) {
                Text(stringResource(label), maxLines = 1)
            }
        }
    }
}
