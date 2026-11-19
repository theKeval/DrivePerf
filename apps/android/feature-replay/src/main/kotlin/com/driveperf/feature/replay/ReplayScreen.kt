package com.driveperf.feature.replay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DoubleState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.domain.replay.Replay
import com.driveperf.domain.replay.ReplayPoint
import com.driveperf.domain.replay.ReplaySpan
import com.driveperf.domain.replay.SegmentStyle
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.BoundingBox
import com.driveperf.presentation.replay.ReplayEffect

/**
 * S-RPL-01 Trip Replay, bound to its view model. [header] carries the trip sheet's title as the subtitle.
 * [onShowDriveScreen] runs when the trip turns out to be recording (F-RPL-01 AC6).
 */
@Composable
fun ReplayRoute(viewModel: ReplayViewModel, header: ReplayHeader, onShowDriveScreen: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showDrive by rememberUpdatedState(onShowDriveScreen)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect -> if (effect == ReplayEffect.ShowDriveScreen) showDrive() }
    }
    ReplayScreen(state, viewModel.time, header, viewModel)
}

/** What the screen shows around the trip: the title's subtitle, the Night Drive Map ID, and Back. */
class ReplayHeader(val subtitle: String, val mapId: String?, val onBack: () -> Unit)

/** The stateless screen: an immersive full-bleed map with the top cluster and the playback bar floating over it. */
@Composable
fun ReplayScreen(state: ReplayUiState, time: DoubleState, header: ReplayHeader, controls: ReplayControls) {
    Surface(color = DrivePerfTheme.dataColors.mapLand, modifier = Modifier.fillMaxSize()) {
        when (state) {
            ReplayUiState.Loading -> LoadingCard()
            ReplayUiState.Error -> ErrorCard(controls::retry)
            ReplayUiState.Locked -> LockCard(header.onBack)
            is ReplayUiState.Ready -> Ready(state, time, header, controls)
        }
        if (state !is ReplayUiState.Ready) TopBar(header, onLegend = null)
    }
}

/** The route fits between the top cluster and the playback bar (S-RPL-01 layout). */
private val MapInsets = PaddingValues(start = 24.dp, top = 220.dp, end = 24.dp, bottom = 260.dp)

@Composable
private fun Ready(state: ReplayUiState.Ready, time: DoubleState, header: ReplayHeader, controls: ReplayControls) {
    if (state.playing) PlaybackLoop(controls::onFrame)
    val label = stringResource(
        R.string.replay_map_label,
        ReplayFormatting.distance(state.timeline.totalM, state.units),
        ReplayFormatting.distanceUnit(state.units),
    )
    var mapLoaded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        if (state.renderer == MapRenderer.GOOGLE) {
            ReplayGoogleMap(
                replay = state.replay,
                time = time,
                look = GoogleMapLook(header.mapId, MaterialTheme.colorScheme.surface.luminance() < DARK_LUMINANCE, MapInsets, label),
                events = MapEvents(onLoaded = { mapLoaded = true }, onTilesFailed = controls::onMapTilesFailed),
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (state.renderer != MapRenderer.GOOGLE || !mapLoaded) {
            ReplayCanvas(state.replay, time, MapInsets, label, Modifier.fillMaxSize().testTag(CANVAS_MAP_TAG))
        }
        Column(Modifier.fillMaxWidth()) {
            TopBar(header, onLegend = controls::openLegend)
            Readouts(state, time, Modifier.padding(horizontal = 16.dp))
            SpanChip(state, time, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                state.renderer == MapRenderer.CANVAS_OFFLINE -> Note(stringResource(R.string.replay_offline))
                state.renderer == MapRenderer.GOOGLE && !mapLoaded -> Note(stringResource(R.string.replay_loading_map))
                state.replay.summaryOnly -> Note(stringResource(R.string.replay_summary_only))
            }
            PlaybackBar(state, time, controls)
        }
    }
    if (state.legendOpen) LegendSheet(controls::closeLegend)
}

private const val DARK_LUMINANCE = 0.5f
/**
 * The display-linked clock (AC4): one `withFrameNanos` per frame while playing. The playhead comes from the shared
 * presenter's clock, so the frame time itself is not used; the frame callback is one object, built once.
 */
@Composable
private fun PlaybackLoop(onFrame: () -> Unit) {
    val latest by rememberUpdatedState(onFrame)
    LaunchedEffect(Unit) {
        val frame: (Long) -> Unit = { latest() }
        while (true) withFrameNanos(frame)
    }
}

@Preview
@Composable
private fun ReplayScreenPreview() {
    DrivePerfTheme(darkTheme = true) {
        val replay = remember { previewReplay() }
        ReplayScreen(
            state = ReplayUiState.Ready(replay, ReplayTimeline(replay), units = UnitSystem.METRIC),
            time = remember { mutableDoubleStateOf(0.0) },
            header = ReplayHeader("Thursday evening · Work → Home", mapId = null, onBack = {}),
            controls = ReplayControls.None,
        )
    }
}

/** A synthetic trip for the preview (no real locations). */
private fun previewReplay(): Replay {
    val points = (0..60).map { i -> ReplayPoint(i * 10.0, 43.0 + i * 0.001, -79.0 + i * 0.0015, 12.0) }
    return Replay(
        tripId = "preview",
        bbox = BoundingBox(43.0, -79.0, 43.06, -78.91),
        durationS = 600.0,
        spans = listOf(ReplaySpan(SegmentStyle.ESTIMATED, 0.0, 60.0)),
        summaryOnly = false,
        track = points,
    )
}
