package com.driveperf.domain.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalysisChannelTest {
    @Test
    fun keysRoundTripAndProChannelsAreMarked() {
        AnalysisChannel.entries.forEach { assertEquals(it, AnalysisChannel.fromKey(it.key)) }
        assertNull(AnalysisChannel.fromKey("gone"))
        assertEquals(setOf(AnalysisChannel.JERK, AnalysisChannel.QUALITY), AnalysisChannel.entries.filter { it.pro }.toSet())
        AnalysisAxis.entries.forEach { assertEquals(it, AnalysisAxis.fromKey(it.key)) }
        assertNull(AnalysisAxis.fromKey(null))
    }

    @Test
    fun aNewUserSeesSpeedAndBothGLanesOnTime() {
        val choices = AnalysisChoices()
        assertEquals(AnalysisAxis.TIME, choices.axis)
        assertTrue(AnalysisChannel.SPEED in choices.channels)
        assertFalse(AnalysisChannel.ELEVATION in choices.channels)
        assertEquals(3, choices.channels.size)
    }
}
