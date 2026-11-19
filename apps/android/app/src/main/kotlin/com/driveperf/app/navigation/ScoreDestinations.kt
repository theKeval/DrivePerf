package com.driveperf.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.driveperf.app.R
import com.driveperf.feature.drives.DrivesViewModel
import com.driveperf.feature.drives.HowScoresWorkScreen
import com.driveperf.feature.drives.ScoreBreakdownScreen
import com.driveperf.feature.drives.TripDetailScreen
import com.driveperf.feature.evidence.EvidenceSheetsHost
import com.driveperf.feature.evidence.EvidenceSheetsViewModel
import com.driveperf.presentation.drives.DrivesEffect
import com.driveperf.presentation.drives.DrivesIntent
import org.koin.compose.viewmodel.koinViewModel

/**
 * The score screens, each a push with its own Drives presenter: S-TRP-01 trip detail ([Routes.TRIP_DETAIL], DRIVE-55),
 * S-SCO-01 Smoothness breakdown ([Routes.SCORE_BREAKDOWN], DRIVE-87) and S-SCO-02 How scores work
 * ([Routes.HOW_SCORES_WORK], DRIVE-113). Trip cards reach them through `DrivesEffect.OpenTrip` (DrivesNavigation.kt).
 * Trip detail and the breakdown open S-RPL-02 for an event and S-SCO-04 for the trip's tip as sheets over themselves
 * (DRIVE-87 AC2, DRIVE-55, DRIVE-102), as Replay does.
 */
internal fun NavGraphBuilder.scoreDestinations(env: AppEnv) {
    val tripArgument = listOf(navArgument(Routes.SCORE_TRIP) { type = NavType.StringType })
    composable(Routes.TRIP_DETAIL, arguments = tripArgument) { entry -> TripDetailDestination(env, entry.tripId) }
    composable(Routes.SCORE_BREAKDOWN, arguments = tripArgument) { entry -> BreakdownDestination(env, entry.tripId) }
    composable(Routes.HOW_SCORES_WORK) { HowScoresWorkDestination(env) }
}

private val NavBackStackEntry.tripId: String get() = arguments?.getString(Routes.SCORE_TRIP).orEmpty()

@Composable
private fun TripDetailDestination(env: AppEnv, tripId: String) {
    val viewModel: DrivesViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheets = rememberEvidenceSheets(tripId)
    LaunchedEffect(viewModel, tripId) { viewModel.onAction(DrivesIntent.Open(tripId)) }
    LaunchedEffect(viewModel, sheets) { viewModel.effects.collect { env.onScoreEffect(it, sheets) } }
    Box(Modifier.fillMaxSize()) {
        DetailScaffold(title = "", onBack = { env.navController.popBackStack() }) { padding ->
            TripDetailScreen(state, tripId, Modifier.padding(padding), viewModel::onAction)
        }
        EvidenceSheetsHost(sheets)
    }
}

@Composable
private fun BreakdownDestination(env: AppEnv, tripId: String) {
    val viewModel: DrivesViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheets = rememberEvidenceSheets(tripId)
    LaunchedEffect(viewModel, tripId) { viewModel.onAction(DrivesIntent.ShowBreakdown(tripId)) }
    LaunchedEffect(viewModel, sheets) { viewModel.effects.collect { env.onScoreEffect(it, sheets) } }
    Box(Modifier.fillMaxSize()) {
        DetailScaffold(title = stringResource(R.string.score_breakdown_title), onBack = { env.navController.popBackStack() }) { padding ->
            ScoreBreakdownScreen(state, tripId, Modifier.padding(padding), viewModel::onAction)
        }
        EvidenceSheetsHost(sheets)
    }
}

/** S-RPL-02 and S-SCO-04 open over the screen; everything else navigates (DrivesNavigation.kt). */
private fun AppEnv.onScoreEffect(effect: DrivesEffect, sheets: EvidenceSheetsViewModel) {
    when (effect) {
        is DrivesEffect.OpenEvidence -> sheets.openEvidence(effect.eventId)
        is DrivesEffect.OpenTip -> sheets.openTip(tipId = null, eventId = null)
        else -> onDrivesEffect(effect)
    }
}

@Composable
private fun HowScoresWorkDestination(env: AppEnv) {
    val viewModel: DrivesViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    DetailScaffold(title = stringResource(R.string.how_scores_work_title), onBack = { env.navController.popBackStack() }) { padding ->
        state.explainer?.let { HowScoresWorkScreen(it, Modifier.padding(padding)) }
    }
}
