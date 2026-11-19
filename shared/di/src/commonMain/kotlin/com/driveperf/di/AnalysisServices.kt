package com.driveperf.di

import com.driveperf.presentation.analysis.AnalysisPresenter
import kotlinx.coroutines.CoroutineScope

/**
 * The shared Analysis presenter for [tripId] (S-ANL-01), running in a child of [scope]. Android passes
 * `viewModelScope`. A stub until DRIVE-45 fills it in. Call `close()` when the screen goes away.
 */
public fun CoreServices.analysisPresenter(tripId: String, scope: CoroutineScope): AnalysisPresenter =
    AnalysisPresenter(tripId, scope)

/** [analysisPresenter] on the main thread with its own scope, for Swift. Call `close()` on deinit. */
public fun CoreServices.analysisPresenter(tripId: String): AnalysisPresenter = AnalysisPresenter.onMain(tripId)
