package com.driveperf.feature.capture

import com.driveperf.domain.capture.AutoDetectPauseLength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** F-CAP-15 S-HOME-03: when each choice ends, in the phone's time zone. */
class PauseRulesTest {
    private val toronto = ZoneId.of("America/Toronto")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, toronto).toInstant().toEpochMilli()

    @Test
    fun `one hour ends an hour later`() {
        val now = at(2026, 11, 18, 19, 30)
        assertEquals(now + 3_600_000L, PauseRules.untilWallMs(AutoDetectPauseLength.ONE_HOUR, now, toronto))
    }

    @Test
    fun `until tomorrow morning ends at 06 00 on the next calendar day`() {
        assertEquals(at(2026, 11, 19, 6), PauseRules.untilWallMs(AutoDetectPauseLength.UNTIL_MORNING, at(2026, 11, 18, 19, 30), toronto))
        // Just after midnight "tomorrow" is still the next calendar day, as the option's label says.
        assertEquals(at(2026, 11, 19, 6), PauseRules.untilWallMs(AutoDetectPauseLength.UNTIL_MORNING, at(2026, 11, 18, 0, 10), toronto))
    }

    @Test
    fun `the morning end keeps local 06 00 across a clock change`() {
        // 1 November 2026: clocks go back at 02:00 in Toronto, so that night is 25 hours long.
        val evening = at(2026, 10, 31, 22)
        val until = PauseRules.untilWallMs(AutoDetectPauseLength.UNTIL_MORNING, evening, toronto)
        assertEquals(at(2026, 11, 1, 6), until)
        assertEquals("eight hours on the clock, nine in real time", 9L * 3_600_000L, (until ?: 0L) - evening)
    }

    @Test
    fun `until I turn it back on has no end`() {
        assertNull(PauseRules.untilWallMs(AutoDetectPauseLength.UNTIL_RESUMED, at(2026, 11, 18, 12), toronto))
    }

    @Test
    fun `the end is named by its day`() {
        val now = at(2026, 11, 18, 19, 30)
        assertEquals(PauseDay.TODAY, PauseRules.day(at(2026, 11, 18, 20, 30), now, toronto))
        assertEquals(PauseDay.TOMORROW, PauseRules.day(at(2026, 11, 19, 6), now, toronto))
        assertEquals(PauseDay.LATER, PauseRules.day(at(2026, 11, 21, 6), now, toronto))
    }

    @Test
    fun `the sheet opens on until tomorrow morning`() {
        assertEquals(AutoDetectPauseLength.UNTIL_MORNING, PauseRules.DEFAULT)
    }
}
