package com.driveperf.app.navigation

import android.net.Uri
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
    const val SETTINGS = "settings"
    const val UNITS_DISPLAY = "units_display"
    const val RECORDING_SETTINGS = "recording_settings"

    const val REPLAY_TRIP = "tripId"
    const val REPLAY_TITLE = "title"

    /** S-RPL-01 Replay (DRIVE-200): the trip ID, and the trip sheet's title as the subtitle. */
    const val REPLAY = "replay/{$REPLAY_TRIP}?$REPLAY_TITLE={$REPLAY_TITLE}"

    fun replay(tripId: String, title: String): String = "replay/${Uri.encode(tripId)}?$REPLAY_TITLE=${Uri.encode(title)}"
}
