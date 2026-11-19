package com.driveperf.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.driveperf.app.R

/**
 * S-ANL-01 Analysis for one trip, opened by `DrivesEffect.OpenAnalysis`. A placeholder until DRIVE-45 adds the screen;
 * nothing dispatches `DrivesIntent.OpenAnalysis` yet.
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
    ) {
        DetailScaffold(title = stringResource(R.string.analysis_title), onBack = { env.navController.popBackStack() }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.analysis_placeholder), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
