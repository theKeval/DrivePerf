package com.driveperf.presentation.analysis

import com.driveperf.presentation.BasePresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** S-ANL-01 Analysis for one trip. A stub until DRIVE-45 adds the channels, the cursor and the range statistics. */
public data class AnalysisState(val tripId: String, val loading: Boolean = true)

/** What the user does on S-ANL-01. */
public sealed interface AnalysisIntent {
    /** The back button. [AnalysisEffect.Close] leaves the screen. */
    public data object Back : AnalysisIntent
}

/** One-shot navigation. */
public sealed interface AnalysisEffect {
    public data object Close : AnalysisEffect
}

/**
 * The Analysis workspace (S-ANL-01, ADR-002 section 4): a placeholder so both apps can wire the screen before
 * DRIVE-45 fills it in. Opened from the trip detail through `DrivesEffect.OpenAnalysis`.
 */
public class AnalysisPresenter(tripId: String, scope: CoroutineScope) :
    BasePresenter<AnalysisState, AnalysisIntent, AnalysisEffect>(AnalysisState(tripId), scope) {
    /** The scope [onMain] made for this presenter alone; [close] cancels it too. */
    private var ownedScope: CoroutineScope? = null

    override fun dispatch(intent: AnalysisIntent) {
        when (intent) {
            AnalysisIntent.Back -> emit(AnalysisEffect.Close)
        }
    }

    override fun close() {
        super.close()
        ownedScope?.cancel()
    }

    public companion object {
        /**
         * A presenter on the main thread in a scope of its own, for Swift, which cannot make a `CoroutineScope`. The app
         * uses `CoreServices.analysisPresenter`. Call [close] on deinit; it cancels the scope.
         */
        public fun onMain(tripId: String): AnalysisPresenter {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            return AnalysisPresenter(tripId, scope).also { it.ownedScope = scope }
        }
    }
}
