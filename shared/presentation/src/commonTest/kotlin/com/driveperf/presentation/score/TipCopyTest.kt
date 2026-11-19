package com.driveperf.presentation.score

import com.driveperf.domain.score.TipCategory
import com.driveperf.domain.trip.EventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TipCopyTest {
    /** The launch library (pipeline-and-scoring.md §6.1), with the category the pipeline gives each tip. */
    private val library = mapOf(
        "brake_light" to TipCategory.BRAKING, "brake_ease_end" to TipCategory.BRAKING, "brake_longer_ramp" to TipCategory.BRAKING,
        "accel_progressive" to TipCategory.ACCELERATION, "accel_first_second" to TipCategory.ACCELERATION,
        "accel_merge_build" to TipCategory.ACCELERATION,
        "corner_brake_before" to TipCategory.CORNERING, "corner_smooth_entry" to TipCategory.CORNERING,
        "corner_unwind_exit" to TipCategory.CORNERING,
        "consist_hold_steady" to TipCategory.CONSISTENCY, "consist_longer_look" to TipCategory.CONSISTENCY,
        "anticip_read_ahead" to TipCategory.ANTICIPATION, "anticip_coast_red" to TipCategory.ANTICIPATION,
        "anticip_leave_gap" to TipCategory.ANTICIPATION,
    )

    @Test
    fun eachEventTypeLinksToTheFirstTipOfItsCategory() {
        assertEquals("brake_light", TipCopy.relatedTipId(EventType.BRAKE))
        assertEquals("accel_progressive", TipCopy.relatedTipId(EventType.ACCEL))
        assertEquals("corner_brake_before", TipCopy.relatedTipId(EventType.CORNER))
    }

    @Test
    fun everyLibraryTipHasCopyInItsCategory() {
        assertEquals(library.keys.toList(), TipCopy.tipIds)
        library.forEach { (id, category) ->
            val text = assertNotNull(TipCopy.text(id), id)
            assertEquals(id, text.tipId)
            assertEquals(category, text.category, id)
            assertTrue(text.nextTime.startsWith("Next time, "), id)
            assertTrue(text.title.isNotBlank() && text.why.isNotBlank(), id)
        }
        EventType.entries.forEach { assertNotNull(TipCopy.text(TipCopy.relatedTipId(it))) }
    }

    @Test
    fun noTipNamesAPlaceOrSetsASpeedTarget() {
        TipCopy.tipIds.mapNotNull(TipCopy::text).forEach { tip ->
            val words = "${tip.title} ${tip.nextTime} ${tip.why}"
            assertFalse('{' in words || '}' in words, tip.tipId)
            assertFalse(Regex("""\d+\s*(km/h|mph)""").containsMatchIn(words), tip.tipId)
        }
    }

    @Test
    fun anUnknownTipHasNoCopy() {
        assertNull(TipCopy.text("nope"))
    }
}
