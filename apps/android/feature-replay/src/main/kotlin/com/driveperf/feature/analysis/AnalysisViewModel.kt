package com.driveperf.feature.analysis

import androidx.compose.runtime.DoubleState
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driveperf.domain.analysis.AnalysisAxis
import com.driveperf.domain.analysis.AnalysisChannel
import com.driveperf.domain.trip.EventType
import com.driveperf.presentation.analysis.AnalysisEffect
import com.driveperf.presentation.analysis.AnalysisIntent
import com.driveperf.presentation.analysis.AnalysisPresenter
import com.driveperf.presentation.analysis.AnalysisState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** What S-ANL-01/02/03's controls do; the view model implements it. [None] does nothing (previews). */
@Suppress("TooManyFunctions") // one per control
interface AnalysisControls {
    /**
     * The finger is at [x] (a share of the timeline width); within [tolerance] of a tick it snaps to its peak. Returns
     * the event it snapped to, so the screen can play the selection haptic once per new snap.
     */
    fun drag(x: Double, tolerance: Double): String?

    fun dragEnd()

    /** The map was tapped at [xM], [yM] metres (AnalysisMap's frame). */
    fun tapMap(xM: Double, yM: Double)

    fun jumpTo(tS: Double)

    fun setAxis(axis: AnalysisAxis)

    fun zoomIn()

    fun zoomOut()

    fun resetZoom()

    fun pinch(factor: Double)

    fun toggle(channel: AnalysisChannel)

    fun expandGg()

    fun collapseGg()

    fun filterGg(type: EventType?)

    fun showEvent(eventId: String)

    fun openReplay()

    companion object {
        /** Controls that do nothing, for previews. */
        val None: AnalysisControls = object : AnalysisControls {
            override fun drag(x: Double, tolerance: Double): String? = null
            override fun dragEnd() = Unit
            override fun tapMap(xM: Double, yM: Double) = Unit
            override fun jumpTo(tS: Double) = Unit
            override fun setAxis(axis: AnalysisAxis) = Unit
            override fun zoomIn() = Unit
            override fun zoomOut() = Unit
            override fun resetZoom() = Unit
            override fun pinch(factor: Double) = Unit
            override fun toggle(channel: AnalysisChannel) = Unit
            override fun expandGg() = Unit
            override fun collapseGg() = Unit
            override fun filterGg(type: EventType?) = Unit
            override fun showEvent(eventId: String) = Unit
            override fun openReplay() = Unit
        }
    }
}

/**
 * The Android side of S-ANL-01 (F-RPL-05, F-RPL-06): a thin wrapper around the shared [AnalysisPresenter] (ADR-002
 * section 4), which owns loading, the window, zoom, axis, channels, persistence and analytics. This keeps only the
 * cursor as Compose snapshot state, so a drag redraws the lanes, the map marker and the G-G dot in the draw phase of the
 * same frame (AC2, ≤ 100 ms) without recomposing or touching the presenter until the finger lifts.
 */
@Suppress("TooManyFunctions") // one override per screen control
class AnalysisViewModel(presenterFactory: (CoroutineScope) -> AnalysisPresenter) : ViewModel(), AnalysisControls {
    private val presenter = presenterFactory(viewModelScope)
    private val position = mutableDoubleStateOf(0.0)

    val state: StateFlow<AnalysisState> = presenter.state

    val effects: Flow<AnalysisEffect> = presenter.effects

    /** The cursor in trip seconds. */
    val cursor: DoubleState get() = position

    init {
        // A cursor the presenter moved (a G-G peak row) moves the drawn cursor too.
        presenter.state.distinctUntilChangedBy { it.cursorS }.onEach { position.doubleValue = it.cursorS }.launchIn(viewModelScope)
    }

    override fun drag(x: Double, tolerance: Double): String? {
        val snap = state.value.timeline?.snap(x, tolerance) ?: return null
        position.doubleValue = snap.tS
        return snap.eventId
    }

    override fun dragEnd() = send(AnalysisIntent.MoveCursor(position.doubleValue))

    override fun tapMap(xM: Double, yM: Double) {
        val t = state.value.map?.nearestTime(xM, yM) ?: return
        jumpTo(t)
    }

    override fun jumpTo(tS: Double) {
        position.doubleValue = tS
        send(AnalysisIntent.MoveCursor(tS))
    }

    override fun setAxis(axis: AnalysisAxis) = send(AnalysisIntent.SetAxis(axis))

    override fun zoomIn() = send(AnalysisIntent.ZoomIn)

    override fun zoomOut() = send(AnalysisIntent.ZoomOut)

    override fun resetZoom() = send(AnalysisIntent.ResetZoom)

    override fun pinch(factor: Double) = send(AnalysisIntent.Pinch(factor))

    override fun toggle(channel: AnalysisChannel) = send(AnalysisIntent.ToggleChannel(channel))

    override fun expandGg() = send(AnalysisIntent.ExpandGg)

    override fun collapseGg() = send(AnalysisIntent.CollapseGg)

    override fun filterGg(type: EventType?) = send(AnalysisIntent.FilterGg(type))

    override fun showEvent(eventId: String) = send(AnalysisIntent.ShowEvent(eventId))

    override fun openReplay() = send(AnalysisIntent.OpenReplay)

    fun back() = send(AnalysisIntent.Back)

    private fun send(intent: AnalysisIntent) = presenter.dispatch(intent)
}
