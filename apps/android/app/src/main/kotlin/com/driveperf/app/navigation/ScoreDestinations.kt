package com.driveperf.app.navigation

import androidx.navigation.NavGraphBuilder

/**
 * The score screens: S-TRP-01 trip detail ([Routes.TRIP_DETAIL]), S-SCO-01 Smoothness breakdown
 * ([Routes.SCORE_BREAKDOWN]) and S-SCO-02 How scores work ([Routes.HOW_SCORES_WORK]). Empty until DRIVE-55, DRIVE-87
 * and DRIVE-113 add them; nothing navigates to these routes yet.
 */
@Suppress("UnusedParameter", "UnusedReceiverParameter") // a seam: DRIVE-55 fills it in
internal fun NavGraphBuilder.scoreDestinations(env: AppEnv) {
    // DRIVE-55, DRIVE-87, DRIVE-113: composable(Routes.TRIP_DETAIL) { … } and the others.
}
