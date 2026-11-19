package com.driveperf.feature.replay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DoubleState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.driveperf.presentation.replay.ReplayOverlay
import com.driveperf.presentation.replay.ReplaySelection
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal const val EVENT_PIN_TAG = "replay_event_pin"
internal const val STOP_PIN_TAG = "replay_stop_pin"
internal const val MAX_SPEED_PIN_TAG = "replay_max_speed_pin"

/** Where a position is on the map in pixels, or null before the map can say (no camera yet). */
internal fun interface MapProjector {
    fun project(lat: Double, lng: Double): Offset?
}

/** Pins sit in 48 dp touch targets (accessibility.md: 44 pt / 48 dp), centred on their place. */
private val PIN_TARGET = 48.dp
private const val SELECTED_SCALE = 1.18f
private const val DIMMED_ALPHA = 0.5f
private const val PULSE_MS = 600

/**
 * The pins over the map (S-RPL-01): event pins by shape (F-RPL-03 AC1, hollow when "Needed", AC4), stop capsules and
 * the neutral max-speed triangle (F-RPL-02 AC2–3). They are buttons in time order, then stops, then max speed. A
 * selected pin grows 1.18× and the others dim. Each event pin pulses once as the marker passes it (AC2); reduced
 * motion makes the pulse an outline highlight with no scale.
 */
@Composable
internal fun ReplayPinLayer(
    overlay: ReplayOverlay,
    selection: ReplaySelection?,
    time: DoubleState,
    projector: MapProjector,
    controls: ReplayControls,
) {
    val pulses = rememberPulses(overlay, time)
    Box(Modifier.fillMaxSize()) {
        EventPins(overlay, selection, projector, pulses, controls)
        StopPins(overlay, selection, projector, controls)
        MaxSpeedPin(overlay, selection, projector, controls)
    }
}

/** How a pin shows against the selection: grown when it is the selected one, dimmed when another is. */
private enum class PinEmphasis { NORMAL, SELECTED, DIMMED }

private fun emphasis(selection: ReplaySelection?, mine: ReplaySelection): PinEmphasis = when (selection) {
    null -> PinEmphasis.NORMAL
    mine -> PinEmphasis.SELECTED
    else -> PinEmphasis.DIMMED
}

@Composable
private fun EventPins(
    overlay: ReplayOverlay,
    selection: ReplaySelection?,
    projector: MapProjector,
    pulses: Map<String, Animatable<Float, *>>,
    controls: ReplayControls,
) {
    for (pin in overlay.eventPins) {
        val m = pin.marker
        val at = m.lat?.let { lat -> m.lng?.let { lng -> projector.project(lat, lng) } } ?: continue
        val look = emphasis(selection, ReplaySelection.Event(m.eventId))
        PinTarget(PinPlace(at, look), pin.accessibilityLabel, EVENT_PIN_TAG, onClick = { controls.selectEvent(m.eventId) }) {
            EventPinFace(pin, pulses[m.eventId]?.value ?: 0f)
        }
    }
}

@Composable
private fun StopPins(overlay: ReplayOverlay, selection: ReplaySelection?, projector: MapProjector, controls: ReplayControls) {
    val count = overlay.details.stops.size
    for (stop in overlay.stopPins) {
        val at = stop.lat?.let { lat -> stop.lng?.let { lng -> projector.project(lat, lng) } } ?: continue
        val seconds = stop.durationS.roundToInt()
        val label = pluralStringResource(R.plurals.replay_stop_pin, seconds, stop.number, count, seconds)
        val look = emphasis(selection, ReplaySelection.Stop(stop.number))
        PinTarget(PinPlace(at, look), label, STOP_PIN_TAG, onClick = { controls.selectStop(stop.number) }) { StopPinFace() }
    }
}

@Composable
private fun MaxSpeedPin(overlay: ReplayOverlay, selection: ReplaySelection?, projector: MapProjector, controls: ReplayControls) {
    val max = overlay.maxSpeed ?: return
    val at = projector.project(max.lat, max.lng) ?: return
    val look = emphasis(selection, ReplaySelection.MaxSpeed)
    PinTarget(PinPlace(at, look), stringResource(R.string.replay_max_speed_pin), MAX_SPEED_PIN_TAG, onClick = controls::selectMaxSpeed) {
        MaxSpeedPinFace()
    }
}

/** Where a pin sits and how it shows against the selection. */
private class PinPlace(val at: Offset, val look: PinEmphasis)

@Composable
private fun PinTarget(
    place: PinPlace,
    label: String,
    tag: String,
    onClick: () -> Unit,
    face: @Composable () -> Unit,
) {
    val half = with(LocalDensity.current) { (PIN_TARGET / 2).toPx() }
    Box(
        Modifier
            .offset { IntOffset((place.at.x - half).roundToInt(), (place.at.y - half).roundToInt()) }
            .size(PIN_TARGET)
            .testTag(tag)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .clickable(onClick = onClick)
            .alpha(if (place.look == PinEmphasis.DIMMED) DIMMED_ALPHA else 1f)
            .scale(if (place.look == PinEmphasis.SELECTED) SELECTED_SCALE else 1f),
        contentAlignment = Alignment.Center,
    ) { face() }
}

/** One 0 → 1 animation per event, started when the playhead passes its peak going forward. */
@Composable
private fun rememberPulses(overlay: ReplayOverlay, time: DoubleState): Map<String, Animatable<Float, *>> {
    val pulses = remember(overlay) { overlay.eventPins.associate { it.marker.eventId to Animatable(0f) } }
    val details by rememberUpdatedState(overlay.details)
    LaunchedEffect(pulses) {
        var previous = time.doubleValue
        snapshotFlow { time.doubleValue }.collect { now ->
            for (marker in details.crossed(previous, now)) {
                val pulse = pulses[marker.eventId] ?: continue
                launch {
                    pulse.snapTo(0f)
                    pulse.animateTo(1f, tween(PULSE_MS, easing = FastOutSlowInEasing))
                    pulse.snapTo(0f)
                }
            }
            previous = now
        }
    }
    return pulses
}

