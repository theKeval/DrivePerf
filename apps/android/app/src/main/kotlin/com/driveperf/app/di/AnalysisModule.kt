package com.driveperf.app.di

import com.driveperf.di.CoreServices
import com.driveperf.di.analysisPresenter
import com.driveperf.feature.analysis.AnalysisViewModel
import com.driveperf.presentation.analysis.AnalysisSource
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.util.Locale

/** DRIVE-45 / DRIVE-50: S-ANL-01 Analysis and S-ANL-02 G-G, from the shared presenter (ADR-002 section 4). */
val analysisModule = module {
    viewModel { (tripId: String) ->
        val core = get<CoreServices>()
        // The region picks the default units until the user chooses some, like the app's other unit flows.
        val region = Locale.getDefault().country
        AnalysisViewModel { scope -> core.analysisPresenter(tripId, AnalysisSource.TRIP_DETAIL, region, scope) }
    }
}
