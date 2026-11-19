package com.driveperf.presentation.score

import com.driveperf.domain.score.TipCategory
import com.driveperf.domain.trip.EventType

/** One coaching tip's words (S-SCO-04, pipeline-and-scoring.md §6). [nextTime] is the "Next time, …" line. */
public data class TipText(
    val tipId: String,
    val category: TipCategory,
    val title: String,
    val nextTime: String,
    val why: String,
)

/**
 * The tip library's copy, by the `tipId` the pipeline chooses (`TipSelector`). A stub: DRIVE-87 fills [text] in from
 * `prototypes/shared/data.js`; until then every tip has no text and screens hide the tip card.
 */
public object TipCopy {
    /** The words for [tipId], or null for an unknown tip. */
    @Suppress("FunctionOnlyReturningConstant", "UnusedParameter") // a stub until DRIVE-87 adds the copy
    public fun text(tipId: String): TipText? = null

    /** The tip an event of [type] links to (S-SCO-03 "Related tip"): the first tip of its category. */
    public fun relatedTipId(type: EventType): String = when (type) {
        EventType.BRAKE -> "brake_light"
        EventType.ACCEL -> "accel_progressive"
        EventType.CORNER -> "corner_brake_before"
    }
}
