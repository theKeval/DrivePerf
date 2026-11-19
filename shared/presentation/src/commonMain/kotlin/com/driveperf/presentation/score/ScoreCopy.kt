package com.driveperf.presentation.score

import com.driveperf.domain.score.Band
import com.driveperf.domain.score.ContributorId
import com.driveperf.domain.trip.EventType

/**
 * The score surfaces' fixed words (docs/design DRIVE-77, DRIVE-82, DRIVE-87; components.md §3, §4, §18). Reasons never
 * name a street or place: v1 has no geocoder (coordinator decision 2), so they use the design's fallback without one.
 */
public object ScoreCopy {
    public fun contributorLabel(id: ContributorId): String = when (id) {
        ContributorId.BRAKING -> "Braking"
        ContributorId.ACCELERATION -> "Acceleration"
        ContributorId.CORNERING -> "Cornering"
        ContributorId.CONSISTENCY -> "Consistency"
    }

    /** "Smoother than usual" / "Usual" / "Rougher than usual". Rougher is neutral grey, never red. */
    public fun bandText(band: Band): String = when (band) {
        Band.SMOOTHER -> "Smoother than usual"
        Band.USUAL -> "Usual"
        Band.ROUGHER -> "Rougher than usual"
    }

    public fun bandGlyph(band: Band): String = when (band) {
        Band.SMOOTHER -> "▲"
        Band.USUAL -> "●"
        Band.ROUGHER -> "▼"
    }

    /**
     * The contributor's one-line reason for [reasonKey] (`"braking.rougher"`, pipeline `TripScorer`), with [events] the
     * channel's counted events. Braking, acceleration and cornering count their events in every band (the band chip
     * says how that compares); consistency has no events, so its reason follows its band. While calibrating there is
     * no band, so no key.
     */
    public fun reason(id: ContributorId, reasonKey: String?, events: Int): String = when (reasonKey) {
        "consistency.smoother" -> "Steadier speed than usual"
        "consistency.usual" -> "Your usual rhythm"
        "consistency.rougher" -> "More speed changes than usual"
        else -> counted(id, events)
    }

    /** "2 abrupt stops", "1 sharp start", "No hard corners this drive". */
    private fun counted(id: ContributorId, events: Int): String {
        val (one, many, none) = when (id) {
            ContributorId.BRAKING -> Triple("abrupt stop", "abrupt stops", "No abrupt stops this drive")
            ContributorId.ACCELERATION -> Triple("sharp start", "sharp starts", "No sharp starts this drive")
            ContributorId.CORNERING -> Triple("hard corner", "hard corners", "No hard corners this drive")
            ContributorId.CONSISTENCY -> return "How steady your speed was"
        }
        return when (events) {
            0 -> none
            1 -> "1 $one"
            else -> "$events $many"
        }
    }

    /** "Hard braking" */
    public fun eventLabel(type: EventType): String = when (type) {
        EventType.BRAKE -> "Hard braking"
        EventType.ACCEL -> "Hard acceleration"
        EventType.CORNER -> "Hard cornering"
    }

    /** The count chip's word: "Braking". */
    public fun eventCountLabel(type: EventType): String = when (type) {
        EventType.BRAKE -> "Braking"
        EventType.ACCEL -> "Acceleration"
        EventType.CORNER -> "Cornering"
    }

    public const val NOT_SCORED: String = "Not scored"

    /** F-SCO-07 AC2: a run at an unusual pace for its route sets no record. */
    public const val UNUSUAL_PACE: String = "Unusual pace — not counted for records"

    public const val TIP_KICKER: String = "One thing for next time"

    public const val NO_EVENTS_TITLE: String = "No abrupt inputs on this drive."
    public const val NO_EVENTS_BODY: String = "Nothing crossed the braking, acceleration or cornering thresholds."
    public const val EVENTS_UNSCORED: String = "Events aren’t detected when the phone moves around freely."

    public const val COMPARISON_FOOTER: String = "Every comparison is against your own drives. Never anyone else’s."
    public const val CONTRIBUTORS_FOOTER: String =
        "Each contributor is compared with your usual. Tap one with events to see the evidence."
    public const val NOT_SCORED_FOOTER: String = "Unscored drives don’t count toward your baseline, routes or weekly average."
}
