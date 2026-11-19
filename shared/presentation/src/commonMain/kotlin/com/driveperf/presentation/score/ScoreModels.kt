package com.driveperf.presentation.score

import com.driveperf.domain.score.Band
import com.driveperf.domain.score.ContributorId
import com.driveperf.domain.score.TipCategory
import com.driveperf.domain.trip.EventType

// What the score surfaces draw (S-TRP-01 score card, S-SCO-01 breakdown, S-SCO-02 explainer, trip cards), already
// worded. Copy is from docs/design (components.md §3, §4, §12, §18; DRIVE-55, DRIVE-77, DRIVE-82, DRIVE-87,
// DRIVE-108, DRIVE-113). Comparisons are only ever with the user's own drives (F-SCO-01 AC3).

/** The comparison chip's look (components.md §4). Lower is never red. */
public enum class ComparisonTone { BETTER, LOWER, SAME, CALIBRATING }

/** The comparison chip: "▲ +2 vs your usual on The Commute", "Calibrating your baseline · 2 of 3". */
public data class ComparisonUi(
    val tone: ComparisonTone,
    /** "▲", "▼" or "●"; empty while calibrating (the dots carry it). */
    val glyph: String,
    val text: String,
    /** "k of 3" progress dots while calibrating; 0 otherwise. */
    val calibratingK: Int = 0,
    val calibratingOf: Int = 0,
)

/** One detected event as a row (components.md §9.2). */
public data class EventRowUi(
    val id: String,
    val type: EventType,
    /** "Hard braking" */
    val label: String,
    /** The time of day: "8:14 a.m." */
    val time: String,
    /** "0.42 g · 1.4 s" */
    val meta: String,
    /** Marked "Was it needed?": hollow glyph, doesn't count. */
    val needed: Boolean = false,
    /** Marked "Not accurate?": left out of the score. */
    val excluded: Boolean = false,
    val lowConfidence: Boolean = false,
    /** "Needed", "Not counted" or "Low confidence"; null for a plain event. */
    val tag: String? = null,
    val accessibilityLabel: String = "",
)

/** Event counts by type for the "Events · n" chips. */
public data class EventCountUi(val type: EventType, val label: String, val count: Int) {
    val accessibilityLabel: String get() = "$label, $count"
}

/** One of the four contributors (S-SCO-01 "What moved it", components.md §18). */
public data class ContributorUi(
    val id: ContributorId,
    /** "Braking" */
    val label: String,
    /** Null while calibrating: no bands before there is a baseline (F-SCO-02 AC1). */
    val band: Band?,
    /** "▲", "●" or "▼" */
    val bandGlyph: String?,
    /** "Smoother than usual" */
    val bandText: String?,
    /** "2 abrupt stops" (no place names in v1: there is no geocoder). */
    val reason: String,
    /** The channel's counted events, costliest first. Consistency has none. */
    val events: List<EventRowUi>,
    val accessibilityLabel: String,
)

/** The Smoothness ring and what sits around it (S-TRP-01 score card, S-SCO-01 hero). */
public data class ScoreUi(
    /** Null: "Not scored" (dashed ring, no arc). */
    val smoothness: Int?,
    /** The baseline notch; null while calibrating or without a score. */
    val baseline: Int?,
    val comparison: ComparisonUi?,
    /** "New route record · beat 89 from 14 Oct"; null without a new record. */
    val recordLine: String?,
    /** "Score based on 31 of 34 min" when part of the drive was left out. */
    val basedOn: String?,
    /** "Unusual pace — not counted for records" (F-SCO-07 AC2). */
    val paceNote: String?,
    /** Why it was not scored, as a sentence; null when scored. */
    val notScoredReason: String?,
    /** Braking, Acceleration, Cornering, Consistency; empty when not scored. */
    val contributors: List<ContributorUi>,
    /** "Smoothness 85. 2 above your usual on The Commute." */
    val accessibilityLabel: String,
) {
    val isScored: Boolean get() = smoothness != null
}

/** One choice of S-SCO-01 "Compared with": the route's runs, or the usual drives of the same kind. */
public data class BasisOptionUi(val title: String, val detail: String, val selected: Boolean, val value: String?)

/** S-SCO-01 beyond the hero. */
public data class BreakdownUi(
    /** "Compared with your usual on The Commute: 83." or the calibrating explanation. */
    val basisLine: String,
    /** Empty while calibrating or not scored. */
    val options: List<BasisOptionUi>,
    /** "Every comparison is against your own drives. Never anyone else’s." */
    val optionsFooter: String,
    /** "Your last 6 runs of The Commute"; null with fewer than three runs. */
    val runsTitle: String?,
    /** Smoothness of those runs, oldest first, ending with this drive. */
    val runs: List<Int>,
    /** "Smoothness on your last 6 runs: 80, 82, 85." */
    val runsSummary: String?,
    /** "Each contributor is compared with your usual. Tap one with events to see the evidence." */
    val contributorsFooter: String,
    /** The unscored drive's explanation, under "Not scored". */
    val notScoredText: String?,
    /** "Unscored drives don’t count toward your baseline, routes or weekly average." */
    val notScoredFooter: String?,
)

/** The trip's one coaching tip (components.md §18), after the positive card. */
public data class TipUi(
    val tipId: String,
    val category: TipCategory,
    /** "One thing for next time" */
    val kicker: String,
    /** "Ease off earlier" */
    val title: String,
    /** "Next time, ease off a little earlier before the light. …" */
    val text: String,
    val why: String,
    /** The event "See the moment" opens (S-RPL-02); null for a tip with no counted event. */
    val eventId: String? = null,
)

/** A trip card's score badge (components.md §12). */
public data class ScoreBadgeUi(
    /** "85"; null for "Not scored". */
    val score: String?,
    /** "+2", "−3" or "0"; null while calibrating or not scored. */
    val delta: String?,
    val tone: ComparisonTone,
    /** The gold rosette. */
    val newRecord: Boolean = false,
    /** "Not scored" */
    val notScoredText: String? = null,
)

/** What an S-SCO-02 section draws besides its paragraphs. */
public enum class ExplainerSectionKind {
    TEXT,

    /** The three event glyphs with their thresholds ([HowScoresWorkUi.thresholds]). */
    EVENTS,

    /** The four quality badges. */
    QUALITY,

    /** Every provenance with its meaning, as a list. */
    PROVENANCE,
}

/** One section of S-SCO-02, in order. [note] is the small print under its content. */
public data class ExplainerSectionUi(
    val kind: ExplainerSectionKind,
    val title: String,
    val paragraphs: List<String> = emptyList(),
    val note: String? = null,
)

/** One event threshold on S-SCO-02: "Hard braking · 0.30 g or more". */
public data class ThresholdUi(val type: EventType, val label: String, val text: String)

/** One provenance with its meaning, for S-SCO-02 "Where numbers come from". */
public data class ProvenanceLineUi(val provenance: com.driveperf.domain.trip.Provenance, val label: String, val meaning: String)

/** S-SCO-02 How scores work. */
public data class HowScoresWorkUi(
    val title: String,
    /** The example ring: 85 with the usual notch at 83. */
    val exampleScore: Int,
    val exampleBaseline: Int,
    val exampleAccessibility: String,
    val heroTitle: String,
    val heroBody: String,
    /** "The small notch is your usual" */
    val notchKey: String,
    /** Your usual · What moves it · Events · Was it needed? · Fair by design · Data quality · … */
    val sections: List<ExplainerSectionUi>,
    val thresholds: List<ThresholdUi>,
    /** Observed, Derived and Estimated with their meanings. */
    val provenance: List<ProvenanceLineUi>,
    /** "Scoring version smooth-1.0.0 · updated 10 November 2026" */
    val versionLine: String,
)
