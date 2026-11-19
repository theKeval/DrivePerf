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

    const val ANALYSIS_TRIP = "tripId"
    const val ANALYSIS_TITLE = "title"

    /** S-ANL-01 Analysis (DRIVE-45): the trip ID, and the trip detail's title as the subtitle. */
    const val ANALYSIS = "analysis/{$ANALYSIS_TRIP}?$ANALYSIS_TITLE={$ANALYSIS_TITLE}"

    fun analysis(tripId: String, title: String): String =
        "analysis/${Uri.encode(tripId)}?$ANALYSIS_TITLE=${Uri.encode(title)}"

    const val SCORE_TRIP = "tripId"

    /** S-TRP-01 trip detail as a push screen (DRIVE-55, coordinator decision 1). */
    const val TRIP_DETAIL = "trip/{$SCORE_TRIP}"

    fun tripDetail(tripId: String): String = "trip/${Uri.encode(tripId)}"

    /** S-SCO-01 Smoothness breakdown for one trip (DRIVE-77, DRIVE-82, DRIVE-87). */
    const val SCORE_BREAKDOWN = "score/{$SCORE_TRIP}"

    fun scoreBreakdown(tripId: String): String = "score/${Uri.encode(tripId)}"

    /** S-SCO-02 How scores work (DRIVE-113, DRIVE-108). */
    const val HOW_SCORES_WORK = "how_scores_work"
}
