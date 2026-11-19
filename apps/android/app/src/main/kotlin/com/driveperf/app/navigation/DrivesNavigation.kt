package com.driveperf.app.navigation

import com.driveperf.presentation.drives.DrivesEffect

/**
 * The Drives presenter's navigation: a trip card pushes S-TRP-01, the score card pushes S-SCO-01 and S-SCO-02, Replay
 * opens S-RPL-01 and Analysis S-ANL-01. While a drive records, the in-drive gate covers the whole graph, so none of
 * them can open then (F-RPL-01 AC6, D1).
 */
internal fun AppEnv.onDrivesEffect(effect: DrivesEffect) {
    when (effect) {
        is DrivesEffect.OpenReplay -> navController.navigate(Routes.replay(effect.tripId, effect.title))
        is DrivesEffect.OpenAnalysis -> navController.navigate(Routes.analysis(effect.tripId, effect.title))
        is DrivesEffect.OpenTrip -> navController.navigate(Routes.tripDetail(effect.tripId)) { launchSingleTop = true }
        is DrivesEffect.OpenBreakdown -> navController.navigate(Routes.scoreBreakdown(effect.tripId)) { launchSingleTop = true }
        DrivesEffect.OpenHowScoresWork -> navController.navigate(Routes.HOW_SCORES_WORK) { launchSingleTop = true }
    }
}
