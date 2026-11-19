package com.driveperf.presentation.score

import com.driveperf.domain.trip.EventType

/** A scoring version and the day it shipped (S-SCO-02 footer, F-SCO-08 AC2). */
public data class ScoringVersion(val id: String, val releasedAtMs: Long)

/**
 * S-SCO-02 How scores work (DRIVE-113, F-SCO-08), with the fairness rules it surfaces (DRIVE-108, F-SCO-07). Copy is
 * from docs/design DRIVE-113. The thresholds and the version mirror the pipeline's `ScoringConfig` and
 * `Versions.SCORING`; `ScoreExplainerAlignmentTest` in `:shared:di` fails when either changes without this.
 */
public object ScoreExplainer {
    /** `smooth-1.0.0`, shipped on 10 November 2026 (DRIVE-62; midday UTC, so every time zone shows that day). A new version replaces it. */
    public val CURRENT: ScoringVersion = ScoringVersion("smooth-1.0.0", 1_794_312_000_000L)

    /** The entry thresholds in g (pipeline-and-scoring.md §4.1): braking, acceleration, cornering (sideways). */
    public const val BRAKE_G: Double = 0.30
    public const val ACCEL_G: Double = 0.28
    public const val CORNER_G: Double = 0.36

    /** The screen's content; [date] formats the version's release day ("10 November 2026"). */
    public fun content(date: (Long) -> String): HowScoresWorkUi = HowScoresWorkUi(
        title = "How scores work",
        exampleScore = EXAMPLE_SCORE,
        exampleBaseline = EXAMPLE_BASELINE,
        exampleAccessibility = "Example: Smoothness $EXAMPLE_SCORE with your usual at $EXAMPLE_BASELINE",
        heroTitle = "Smoothness is how gently you drove",
        heroBody = "A score from 0 to 100 for how smoothly you braked, accelerated and turned. It compares you with you, " +
            "never with anyone else.",
        notchKey = "The small notch is your usual",
        sections = sections,
        thresholds = listOf(
            ThresholdUi(EventType.BRAKE, ScoreCopy.eventLabel(EventType.BRAKE), "${g(BRAKE_G)} g or more"),
            ThresholdUi(EventType.ACCEL, ScoreCopy.eventLabel(EventType.ACCEL), "${g(ACCEL_G)} g or more"),
            ThresholdUi(EventType.CORNER, ScoreCopy.eventLabel(EventType.CORNER), "${g(CORNER_G)} g or more sideways"),
        ),
        versionLine = "Scoring version ${CURRENT.id} · updated ${date(CURRENT.releasedAtMs)}",
    )

    private val sections: List<ExplainerSectionUi> = listOf(
        text(
            "Your usual",
            "Your first three scorable drives set your baseline. After that, each drive is compared with your recent " +
                "usual on the same route, or with similar city, highway or mixed drives.",
            "Passenger rides and unscored drives never count toward it.",
        ),
        text(
            "What moves it",
            "Four contributors: Braking, Acceleration, Cornering and Consistency. Each is smoother, usual or rougher than " +
                "your own usual, with the moments that caused it.",
        ),
        ExplainerSectionUi(
            ExplainerSectionKind.EVENTS,
            "Events",
            note = "G is how hard the car pushed you. A change to these thresholds ships as a new scoring version.",
        ),
        text(
            "Was it needed?",
            "Braked hard for a good reason, like a light changing or someone stepping out? Mark it as needed and it won’t " +
                "count. You can change your mind any time.",
        ),
        text(
            "Fair by design",
            "A full, gentle stop always scores at least as well as a rolling one. Speed never adds points; only how " +
                "abruptly you changed it. Drives at an unusual pace can’t set route records.",
        ),
        ExplainerSectionUi(
            ExplainerSectionKind.QUALITY,
            "Data quality",
            note = "Every drive gets one badge. When the phone moved or GPS dropped, we leave that part out instead of " +
                "guessing.",
        ),
        ExplainerSectionUi(
            ExplainerSectionKind.PROVENANCE,
            "Where numbers come from",
            note = "Touch and hold any number to see which one it is.",
        ),
        text(
            "Efficiency habits",
            "A habit rating from stop-starts, abrupt starts and how steady your speed was. It’s not a fuel or energy " +
                "measurement, so we never show litres, kWh or money.",
        ),
        text(
            "What it isn’t",
            "A smooth score is not a safety certificate. DrivePerf can’t see the road, the weather or other drivers.",
        ),
    )

    private fun text(title: String, vararg paragraphs: String) =
        ExplainerSectionUi(ExplainerSectionKind.TEXT, title, paragraphs.toList())

    /** "0.30" */
    private fun g(value: Double): String {
        val hundredths = kotlin.math.round(value * HUNDRED).toInt()
        return "${hundredths / HUNDRED_INT}.${(hundredths % HUNDRED_INT).toString().padStart(2, '0')}"
    }

    private const val EXAMPLE_SCORE = 85
    private const val EXAMPLE_BASELINE = 83
    private const val HUNDRED = 100.0
    private const val HUNDRED_INT = 100
}
