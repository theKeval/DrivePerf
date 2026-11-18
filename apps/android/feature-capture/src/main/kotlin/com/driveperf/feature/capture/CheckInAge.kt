package com.driveperf.feature.capture

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

/** How long ago the last check-in was, in the buckets "Last check-in" shows (S-HOME-02 copy "18 min ago"). */
sealed interface CheckInAge {
    data object JustNow : CheckInAge

    data class Minutes(val minutes: Long) : CheckInAge

    data class Hours(val hours: Long) : CheckInAge

    /** A day or more: shown as a date and time. */
    data object Older : CheckInAge

    companion object {
        private const val MINUTE_MS = 60_000L
        private const val HOUR_MS = 60 * MINUTE_MS
        private const val DAY_MS = 24 * HOUR_MS

        fun of(checkInMs: Long, nowMs: Long): CheckInAge {
            val age = (nowMs - checkInMs).coerceAtLeast(0)
            return when {
                age < MINUTE_MS -> JustNow
                age < HOUR_MS -> Minutes(age / MINUTE_MS)
                age < DAY_MS -> Hours(age / HOUR_MS)
                else -> Older
            }
        }
    }
}

/** "just now", "18 min ago", "3 h ago", or the date and time for a day or more. */
@Composable
internal fun checkInAgo(checkInMs: Long, nowMs: Long): String = when (val age = CheckInAge.of(checkInMs, nowMs)) {
    CheckInAge.JustNow -> stringResource(R.string.check_in_just_now)
    is CheckInAge.Minutes -> stringResource(R.string.check_in_minutes, age.minutes.toInt())
    is CheckInAge.Hours -> stringResource(R.string.check_in_hours, age.hours.toInt())
    CheckInAge.Older -> DateUtils.formatDateTime(
        LocalContext.current, checkInMs, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME,
    )
}

/** The clock time of the check-in, "7:12 pm", in the user's 12/24-hour setting. */
@Composable
internal fun checkInClock(checkInMs: Long): String =
    DateUtils.formatDateTime(LocalContext.current, checkInMs, DateUtils.FORMAT_SHOW_TIME)

/** The wall clock, refreshed every [REFRESH_MS] so "18 min ago" keeps up while the screen stays open. */
@Composable
internal fun rememberNowMs(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(REFRESH_MS)
            now = System.currentTimeMillis()
        }
    }
    return now
}

private const val REFRESH_MS = 30_000L
