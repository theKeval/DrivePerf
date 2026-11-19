package com.driveperf.feature.evidence

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.driveperf.domain.replay.PlaybackSpeed
import com.driveperf.feature.replay.EvidenceSheet
import com.driveperf.feature.replay.ReplayControls
import com.driveperf.feature.replay.TipDetailSheet
import com.driveperf.presentation.evidence.EvidenceEffect
import com.driveperf.presentation.evidence.EvidenceIntent
import com.driveperf.presentation.evidence.EvidencePresenter
import com.driveperf.presentation.evidence.EvidenceState
import com.driveperf.presentation.evidence.TipDetailEffect
import com.driveperf.presentation.evidence.TipDetailIntent
import com.driveperf.presentation.evidence.TipDetailPresenter
import com.driveperf.presentation.evidence.TipDetailState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Makes the shared S-RPL-02 and S-SCO-04 presenters of one trip (`CoreServices.evidencePresenter` and
 * `CoreServices.tipDetailPresenter`, B's factories). A null tip ID and event is the trip's own tip.
 */
class TripSheetFactories(
    val evidence: (eventId: String, scope: CoroutineScope) -> EvidencePresenter,
    val tip: (tipId: String?, eventId: String?, scope: CoroutineScope) -> TipDetailPresenter,
)

/**
 * S-RPL-02 (with S-SCO-03) and S-SCO-04 opened from outside Replay (DRIVE-87 AC2, DRIVE-55, DRIVE-102, DRIVE-39 AC1):
 * trip detail's event rows and tip card, the breakdown's contributor events, and Analysis's ticks and G-G peaks. The
 * sheets are Replay's own ([EvidenceSheet], [TipDetailSheet]); with no replay underneath, stepping between events stays
 * inside the sheet, and "See the moment" on the tip opens its event's evidence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EvidenceSheetsViewModel(private val factories: TripSheetFactories) : ViewModel() {
    private val evidencePresenter = MutableStateFlow<EvidencePresenter?>(null)
    private val tipPresenter = MutableStateFlow<TipDetailPresenter?>(null)
    private val toasts = Channel<String>(Channel.BUFFERED)
    private val changes = Channel<Unit>(Channel.CONFLATED)

    /** S-RPL-02 while it is open. */
    val evidence: StateFlow<EvidenceState?> =
        evidencePresenter.flatMapLatest { it?.state ?: flowOf(null) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** S-SCO-04 while it is open. */
    val tip: StateFlow<TipDetailState?> =
        tipPresenter.flatMapLatest { it?.state ?: flowOf(null) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** "Thanks. This event won't count. …" and the other confirmations. */
    val messages: Flow<String> = toasts.receiveAsFlow()

    /** Feedback on an event changed and the trip re-scored: the screen underneath reads its data again. */
    val feedbackChanged: Flow<Unit> = changes.receiveAsFlow()

    init {
        evidencePresenter.flatMapLatest { it?.effects ?: emptyFlow() }.onEach(::onEvidenceEffect).launchIn(viewModelScope)
        tipPresenter.flatMapLatest { it?.effects ?: emptyFlow() }.onEach(::onTipEffect).launchIn(viewModelScope)
    }

    /** Opens S-RPL-02 for [eventId], replacing any open one. */
    fun openEvidence(eventId: String) {
        evidencePresenter.value?.close()
        evidencePresenter.value = factories.evidence(eventId, viewModelScope)
    }

    /** Opens S-SCO-04: the trip's own tip when [tipId] is null, else the library tip about [eventId]. */
    fun openTip(tipId: String?, eventId: String?) {
        tipPresenter.value?.close()
        tipPresenter.value = factories.tip(tipId, eventId, viewModelScope)
    }

    fun evidence(intent: EvidenceIntent) {
        evidencePresenter.value?.dispatch(intent)
    }

    fun closeEvidence() {
        evidencePresenter.value?.close()
        evidencePresenter.value = null
    }

    fun tip(intent: TipDetailIntent) {
        tipPresenter.value?.dispatch(intent)
    }

    fun closeTip() {
        tipPresenter.value?.close()
        tipPresenter.value = null
    }

    private fun onEvidenceEffect(effect: EvidenceEffect) {
        when (effect) {
            // The sheet steps by itself; there is no replay marker underneath to move.
            is EvidenceEffect.ShowEvent -> Unit
            EvidenceEffect.FeedbackChanged -> changes.trySend(Unit)
            is EvidenceEffect.OpenTip -> openTip(effect.tipId, effect.eventId)
            is EvidenceEffect.Toast -> toasts.trySend(effect.text)
            EvidenceEffect.Close -> closeEvidence()
        }
    }

    private fun onTipEffect(effect: TipDetailEffect) {
        when (effect) {
            // "See the moment": back to the evidence underneath when it is that event's, else open that event's.
            is TipDetailEffect.OpenEvidence -> {
                closeTip()
                if (evidence.value?.eventId != effect.eventId) openEvidence(effect.eventId)
            }
            TipDetailEffect.Close -> closeTip()
        }
    }

    override fun onCleared() {
        closeTip()
        closeEvidence()
    }
}

/**
 * Draws [viewModel]'s sheets over the screen it is placed on, with a snackbar for their confirmations. Put it last in
 * a full-size box, over the screen's own content.
 */
@Composable
fun EvidenceSheetsHost(viewModel: EvidenceSheetsViewModel, modifier: Modifier = Modifier) {
    val evidence by viewModel.evidence.collectAsStateWithLifecycle()
    val tip by viewModel.tip.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val controls = remember(viewModel) { SheetControls(viewModel) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    evidence?.let { EvidenceSheet(it, controls) }
    tip?.let { TipDetailSheet(it, controls) }
    Box(modifier.fillMaxSize()) {
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
    }
}

/** Replay's sheet controls without a replay: only the sheets' own actions do anything. */
@Suppress("TooManyFunctions") // ReplayControls' members, most of them no-ops here
private class SheetControls(private val sheets: EvidenceSheetsViewModel) : ReplayControls {
    override fun togglePlay() = Unit

    override fun setSpeed(speed: PlaybackSpeed) = Unit

    override fun onFrame() = Unit

    override fun scrubStart() = Unit

    override fun scrubTo(tS: Double) = Unit

    override fun scrubEnd() = Unit

    override fun openLegend() = Unit

    override fun closeLegend() = Unit

    override fun onMapTilesFailed() = Unit

    override fun retry() = Unit

    override fun evidence(intent: EvidenceIntent) = sheets.evidence(intent)

    override fun closeEvidence() = sheets.closeEvidence()

    override fun tip(intent: TipDetailIntent) = sheets.tip(intent)

    override fun closeTip() = sheets.closeTip()
}
