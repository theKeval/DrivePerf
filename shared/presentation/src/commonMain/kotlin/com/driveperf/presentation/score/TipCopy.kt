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
 * The tip library's copy, by the `tipId` the pipeline chooses (`TipSelector`, pipeline-and-scoring.md §6.1: 14 tips).
 * Adapted from `prototypes/shared/data.js` `tips`, without the `{place}` and `{road}` slots: v1 has no geocoder
 * (coordinator decision 2). Tips never set speed targets.
 */
public object TipCopy {
    private val library: Map<String, TipText> = listOf(
        // Braking
        tip(
            "brake_light", TipCategory.BRAKING, "Ease off earlier",
            "Next time, ease off a little earlier before the light. That stop cost the most smoothness.",
            "Lifting off sooner lets the car slow on its own, so the stop needs less brake at the end.",
        ),
        tip(
            "brake_ease_end", TipCategory.BRAKING, "Soften the finish",
            "Next time, release the brake a touch in the last metre of your stop. It takes the nod out of the finish.",
            "Most of a stop’s jolt comes in the final moment, while the brake is still on firmly.",
        ),
        tip(
            "brake_longer_ramp", TipCategory.BRAKING, "Spread the stop out",
            "Next time, start braking a little sooner and build it up gently, so the stop is one long, even squeeze.",
            "The same slowing spread over more time keeps every change gentle.",
        ),
        // Acceleration
        tip(
            "accel_progressive", TipCategory.ACCELERATION, "Squeeze, don’t step",
            "Next time, build speed more progressively. A longer, steadier squeeze gets you there just as well.",
            "A sudden push on the throttle presses everyone back in their seats; a steady build feels calm.",
        ),
        tip(
            "accel_first_second", TipCategory.ACCELERATION, "Roll away gently",
            "Next time, roll onto the throttle as you pull away instead of stepping in. Your coffee will thank you.",
            "The first second after a stop is where a start feels sharpest.",
        ),
        tip(
            "accel_merge_build", TipCategory.ACCELERATION, "Use the whole ramp",
            "Next time, build speed steadily up the on-ramp. A longer, even squeeze merges just as well.",
            "Using the whole ramp gives you time to match the traffic without a sudden surge.",
        ),
        // Cornering
        tip(
            "corner_brake_before", TipCategory.CORNERING, "Brake before the bend",
            "Next time, do your braking in a straight line before the bend, then roll through on a steady throttle.",
            "Braking while turning stacks two pushes at once; finishing it first keeps the turn smooth.",
        ),
        tip(
            "corner_smooth_entry", TipCategory.CORNERING, "Settle before turning in",
            "Next time, settle your speed before turning in, then hold one steady arc through.",
            "A calm entry means nothing needs correcting halfway round.",
        ),
        tip(
            "corner_unwind_exit", TipCategory.CORNERING, "Unwind smoothly",
            "Next time, unwind the wheel gradually as you leave the corner and add throttle gently.",
            "The exit is where a quick unwind or a burst of throttle jolts the car sideways.",
        ),
        // Consistency
        tip(
            "consist_hold_steady", TipCategory.CONSISTENCY, "Hold a steady speed",
            "Next time, try holding a steadier speed on the open road. Small, early adjustments beat big corrections.",
            "Fewer speed changes mean fewer pushes and pulls for everyone inside.",
        ),
        tip(
            "consist_longer_look", TipCategory.CONSISTENCY, "Look further ahead",
            "Next time, look further up the road so you can make small, early speed changes instead of big ones.",
            "Seeing a change sooner lets you adjust a little at a time.",
        ),
        // Anticipation
        tip(
            "anticip_read_ahead", TipCategory.ANTICIPATION, "Read the road ahead",
            "Next time, look two lights ahead. Coasting up to a red is smoother than braking at it.",
            "The earlier you see a slowdown coming, the less you need the brake.",
        ),
        tip(
            "anticip_coast_red", TipCategory.ANTICIPATION, "Coast up to the red",
            "Next time, lift off early when a light ahead turns red and let the car coast most of the way.",
            "This drive had a lot of stop-and-go; every stop you coast into is a gentler one.",
        ),
        tip(
            "anticip_leave_gap", TipCategory.ANTICIPATION, "Leave a longer gap",
            "Next time, leave a longer gap so you can lift off instead of braking when traffic bunches up.",
            "A bigger gap turns sudden stops into gentle slow-downs.",
        ),
    ).associateBy { it.tipId }

    /** Every tip ID with copy, in library order. */
    public val tipIds: List<String> get() = library.keys.toList()

    /** The words for [tipId], or null for an unknown tip (the screens then hide the tip card). */
    public fun text(tipId: String): TipText? = library[tipId]

    /** The tip an event of [type] links to (S-SCO-03 "Related tip"): the first tip of its category. */
    public fun relatedTipId(type: EventType): String = when (type) {
        EventType.BRAKE -> "brake_light"
        EventType.ACCEL -> "accel_progressive"
        EventType.CORNER -> "corner_brake_before"
    }

    private fun tip(id: String, category: TipCategory, title: String, nextTime: String, why: String) =
        TipText(id, category, title, nextTime, why)
}
