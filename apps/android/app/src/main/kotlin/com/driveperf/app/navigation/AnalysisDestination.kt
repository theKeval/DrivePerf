package com.driveperf.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.driveperf.feature.analysis.AnalysisRoute
import com.driveperf.feature.analysis.AnalysisViewModel
import com.driveperf.feature.evidence.EvidenceSheetsHost
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * S-ANL-01 Analysis for one trip (DRIVE-45, DRIVE-50), opened by `DrivesEffect.OpenAnalysis`. The title is the trip's
 * title, shown under "Analysis"; Replay opens S-RPL-01 for the same trip. Event ticks and G-G peak rows open S-RPL-02
 * over it (DRIVE-39 AC1); feedback given there reloads the analysis, so a "Not accurate?" event leaves the ticks.
 */
internal fun NavGraphBuilder.analysisDestination(env: AppEnv) {
    composable(
        Routes.ANALYSIS,
        arguments = listOf(
            navArgument(Routes.ANALYSIS_TRIP) { type = NavType.StringType },
            navArgument(Routes.ANALYSIS_TITLE) {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) { entry ->
        val tripId = entry.arguments?.getString(Routes.ANALYSIS_TRIP).orEmpty()
        val title = entry.arguments?.getString(Routes.ANALYSIS_TITLE).orEmpty()
        val viewModel: AnalysisViewModel = koinViewModel { parametersOf(tripId) }
        val sheets = rememberEvidenceSheets(tripId)
        LaunchedEffect(viewModel, sheets) { sheets.feedbackChanged.collect { viewModel.reload() } }
        Box(Modifier.fillMaxSize()) {
            AnalysisRoute(
                viewModel = viewModel,
                title = title,
                onBack = { env.navController.popBackStack() },
                onReplay = { id -> env.navController.navigate(Routes.replay(id, title)) },
                onEvidence = sheets::openEvidence,
            )
            EvidenceSheetsHost(sheets)
        }
    }
}
