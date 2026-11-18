package com.driveperf.feature.capture

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.driveperf.domain.capture.AutoDetectPauseLength
import java.time.ZoneId

/** The clock time of [wallMs] in the user's 12/24-hour setting ("8:30 pm", "06:00"). */
@Composable
internal fun pauseClock(wallMs: Long): String =
    DateUtils.formatDateTime(LocalContext.current, wallMs, DateUtils.FORMAT_SHOW_TIME)

@Composable
private fun weekday(wallMs: Long): String =
    DateUtils.formatDateTime(LocalContext.current, wallMs, DateUtils.FORMAT_SHOW_WEEKDAY)

/**
 * The end of a pause as a phrase: "until 8:30 pm", "until 6:00 tomorrow", "until Saturday 6:00", or "until you turn it
 * back on" when [untilMs] is null.
 */
@Composable
fun pausedUntilPhrase(untilMs: Long?, nowMs: Long = rememberNowMs()): String {
    if (untilMs == null) return stringResource(R.string.pause_until_resumed)
    val time = pauseClock(untilMs)
    return when (PauseRules.day(untilMs, nowMs, ZoneId.systemDefault())) {
        PauseDay.TODAY -> stringResource(R.string.pause_until_today, time)
        PauseDay.TOMORROW -> stringResource(R.string.pause_until_tomorrow, time)
        PauseDay.LATER -> stringResource(R.string.pause_until_later, weekday(untilMs), time)
    }
}

/** [pausedUntilPhrase] starting a line: "Until 6:00 tomorrow". */
@Composable
fun pausedUntilLine(untilMs: Long?, nowMs: Long = rememberNowMs()): String {
    val locale = LocalConfiguration.current.locales[0]
    return pausedUntilPhrase(untilMs, nowMs).replaceFirstChar { it.titlecase(locale) }
}

/** The option row's title. */
@get:StringRes
internal val AutoDetectPauseLength.title: Int
    get() = when (this) {
        AutoDetectPauseLength.ONE_HOUR -> R.string.pause_option_hour
        AutoDetectPauseLength.UNTIL_MORNING -> R.string.pause_option_morning
        AutoDetectPauseLength.UNTIL_RESUMED -> R.string.pause_option_resumed
    }

/** The primary button's label, which follows the choice. */
@get:StringRes
internal val AutoDetectPauseLength.button: Int
    get() = when (this) {
        AutoDetectPauseLength.ONE_HOUR -> R.string.pause_button_hour
        AutoDetectPauseLength.UNTIL_MORNING -> R.string.pause_button_morning
        AutoDetectPauseLength.UNTIL_RESUMED -> R.string.pause_button_resumed
    }

/** The option row's second line: "Until 8:30 pm", "Saturday, 6:00", "Manual drives still work". */
@Composable
internal fun optionDetail(length: AutoDetectPauseLength, nowMs: Long): String {
    val until = PauseRules.untilWallMs(length, nowMs, ZoneId.systemDefault())
        ?: return stringResource(R.string.pause_option_resumed_detail)
    return when (length) {
        AutoDetectPauseLength.UNTIL_MORNING -> stringResource(R.string.pause_option_morning_detail, weekday(until), pauseClock(until))
        else -> stringResource(R.string.pause_option_hour_detail, pauseClock(until))
    }
}
