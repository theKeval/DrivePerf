package com.driveperf.presentation.evidence

import com.driveperf.domain.score.TipCategory
import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.InaccurateReason

/** S-RPL-02, S-SCO-03 and S-SCO-04 words (DRIVE-39, DRIVE-97, DRIVE-102). English only (Gate 2). */
public object EvidenceCopy {
    public const val LOW_CONFIDENCE: String = "Your phone’s signal was weaker here, so treat this one lightly."
    public const val NEEDED: String = "You marked this as needed. It doesn’t count toward your score."
    public const val COUNT_IT_AGAIN: String = "Count it again"
    public const val TRACE_REMOVED: String = "Detailed trace not kept. The raw sensor data for this drive was removed to " +
        "save space. The summary values below stay."
    public const val THIS_DRIVES_TIP: String = "This drive’s tip"
    public const val NEXT_TIME: String = "Next time"
    public const val WHY_THIS_TIP: String = "Why this tip"
    public const val WAS_IT_NEEDED: String = "Was it needed?"
    public const val NOT_ACCURATE: String = "Not accurate?"
    public const val REASONS_TITLE: String = "What went wrong?"
    public const val REASONS_BODY: String = "We’ll leave this event out and use your answer to tune detection on this phone."
    public const val SEND: String = "Send"
    public const val CANCEL: String = "Cancel"
    public const val THANKS: String = "Thanks. This event won’t count. Your answer stays on this phone."
    public const val YES_NEEDED: String = "Yes, it was needed"
    public const val NO_COUNT_IT: String = "No, count it"
    public const val MARKED_NEEDED: String = "Marked as needed"
    public const val DONE: String = "Done"
    public const val COUNT_AGAIN_TITLE: String = "Count it again?"
    public const val COUNT_AGAIN_BODY: String = "You marked this as needed, so it doesn’t count. Count it again if it wasn’t."
    public const val KEEP_NEEDED: String = "Keep it as needed"
    public const val COUNTED_AGAIN: String = "Counted again · Smoothness recomputed"
    public const val SAVE_FAILED: String = "Couldn’t save that. Your drive is unchanged. Try again."

    /**
     * Decision D4 (pipeline-and-scoring.md, OQ-22), explained where the user marks events: more than 2 "needed" marks
     * keep a run from setting a route record, but its own score still updates.
     */
    public const val RECORD_CAP: String = "A drive with more than 2 events marked as needed can’t set a route record. " +
        "Its score still updates."

    /** The D4 note once this drive is over the cap. */
    public fun overRecordCap(marked: Int): String =
        "This drive now has $marked events marked as needed, so it can’t set a route record. Its score still updates."

    /** The S-SCO-03 question for an event of [type]. */
    public fun askBody(type: EventType): String = when (type) {
        EventType.BRAKE -> "If you braked for a good reason, like a light changing or someone stepping out, we won’t count it."
        EventType.ACCEL -> "If you sped up for a good reason, like merging or clearing a junction, we won’t count it."
        EventType.CORNER -> "If you turned hard for a good reason, like avoiding something in the road, we won’t count it."
    } + " You can change your mind any time."

    /** "this stop", "this one". */
    private fun thisOne(type: EventType): String = if (type == EventType.BRAKE) "this stop" else "this one"

    /** The S-SCO-03 done line. */
    public fun doneBody(type: EventType, smoothness: Int?): String {
        val score = smoothness?.let { " Smoothness recomputed without it: $it." } ?: ""
        return "It doesn’t count.$score Tips won’t be about ${thisOne(type)}."
    }

    public fun confidence(confidence: Confidence): String = when (confidence) {
        Confidence.HIGH -> "High"
        Confidence.MEDIUM -> "Medium"
        Confidence.LOW -> "Low"
    }

    public fun reason(reason: InaccurateReason): String = when (reason) {
        InaccurateReason.PHONE_MOVED -> "Phone moved"
        InaccurateReason.POTHOLE -> "Pothole or speed bump"
        InaccurateReason.NOT_ME -> "Not me driving"
        InaccurateReason.OTHER -> "Other"
    }

    /** The `reason` value of `event_reported_inaccurate`. */
    internal fun reasonKey(reason: InaccurateReason): String = reason.name.lowercase()

    /** The tip category a library tip of [type] belongs to. */
    public fun category(type: EventType): TipCategory = when (type) {
        EventType.BRAKE -> TipCategory.BRAKING
        EventType.ACCEL -> TipCategory.ACCELERATION
        EventType.CORNER -> TipCategory.CORNERING
    }

    // S-SCO-04

    public const val TIP_TITLE: String = "One thing for next time"
    public const val CLEANEST: String = "Cleanest stretch"
    public const val WHY_THIS_ONE: String = "Why this one"
    public const val HOW_TO_TRY: String = "How to try it"
    public const val SEE_THE_MOMENT: String = "See the moment"
    public const val TIP_FOOTER: String = "One tip per drive, always after something that went well. Tips never set speed targets."
    public const val NO_TIP_TITLE: String = "No tip this time."
    public const val WHY_NO_EVENT: String = "No stop crossed the hard-braking line on this drive, so this tip comes from " +
        "your firmest stop. Small things make the difference at this level."

    /** "We couldn't score this drive fairly (phone in a bag), so there's nothing fair to coach." */
    public fun noTipBody(reason: String): String =
        "We couldn’t score this drive fairly ($reason), so there’s nothing fair to coach."

    /** Three steps per tip category (S-SCO-04 "How to try it"; coaching copy, not fixtures). */
    public fun steps(category: TipCategory): List<String> = when (category) {
        TipCategory.BRAKING -> listOf(
            "Lift off a little earlier and let the car slow on its own.",
            "Brake gently first, firmer in the middle, then ease off in the last metre.",
            "Aim to stop where you planned, without a final nod.",
        )
        TipCategory.ANTICIPATION -> listOf(
            "Look further ahead for brake lights and changing signals.",
            "Leave a longer gap so you can lift off instead of braking.",
            "Coast up to the queue rather than closing it fast.",
        )
        TipCategory.ACCELERATION -> listOf(
            "Roll onto the throttle; squeeze rather than step.",
            "Build speed over a few seconds, not one.",
            "Let the car settle before adding more.",
        )
        TipCategory.CORNERING -> listOf(
            "Do your braking in a straight line before the turn.",
            "Turn in once, smoothly, and hold one steady arc.",
            "Unwind the wheel as you gently add throttle.",
        )
        TipCategory.CONSISTENCY -> listOf(
            "Pick a steady speed and hold it.",
            "Make small, early adjustments instead of big ones.",
            "Match the flow so you change speed less often.",
        )
    }
}
