package com.driveperf.app.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.driveperf.app.R
import com.driveperf.designsystem.DpIcons

/** The four tab roots (S-NAV-01). Profile is an avatar, not a tab (decisions log, Gate 3). */
enum class Tab(val route: String, @StringRes val label: Int, @DrawableRes val icon: Int) {
    Drives("drives", R.string.tab_drives, DpIcons.Drives),
    Routes("routes", R.string.tab_routes, DpIcons.Routes),
    Insights("insights", R.string.tab_insights, DpIcons.Insights),
    Garage("garage", R.string.tab_garage, DpIcons.Garage),
    ;

    companion object {
        fun fromRoute(route: String?): Tab? = entries.firstOrNull { it.route == route }
    }
}

/** Non-tab destinations. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val ALL_DRIVES = "all_drives"
    const val PROFILE = "profile"
}
