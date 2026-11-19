package com.driveperf.presentation.drives

import com.driveperf.domain.trip.OverlayStyle
import com.driveperf.domain.trip.Provenance
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.TripCorrection

// What the Drives screens draw (S-HOME-01 latest drives, S-HIS-01 all drives, S-TRP-01 trip sheet, S-DQ-01 data
// quality), already formatted. Copy is from docs/design (components.md §5, §10, §12; DRIVE-100, DRIVE-109, DRIVE-170,
// DRIVE-172); accessibility labels follow accessibility.md (units spelled out).

/** How many drives the Drives tab lists before "All drives". */
public const val LATEST_DRIVES: Int = 5

/** Which list All drives shows (S-HIS-01 filter, F-CAP-04 AC3). */
public enum class DrivesFilter { DRIVES, NOT_DRIVES }

/** One trip card (components.md §12, reduced to what the app has: no route label, score or car yet). */
public data class TripRowUi(
    val id: String,
    /** "Thursday morning" */
    val title: String,
    /** "5 Nov · 8:14 a.m." */
    val subtitle: String,
    /** "37.9 km" */
    val distance: String = "",
    /** "30 min" */
    val duration: String = "",
    /** One line under the card: short, interrupted or started by you. */
    val note: String? = null,
    val accessibilityLabel: String = "",
    /** The compact data-quality badge (components.md §10.1); null before analysis. */
    val quality: QualityBadge? = null,
    /** "Interrupted" tag (F-CAP-06). */
    val interrupted: Boolean = false,
    /** A manual trip too short to score (pipeline §1.6). */
    val short: Boolean = false,
    /** The open "Was this a bus or train?" question, answered on the card (F-CAP-04). */
    val question: Suspect? = null,
    /** "Passenger", "Bus or train"…: the card is dimmed and doesn't count. */
    val kindLabel: String? = null,
) {
    /** Passenger or not a drive: drawn at 60 %. */
    val dimmed: Boolean get() = kindLabel != null

    /** "Usable"; null before analysis. */
    val qualityWord: String? get() = quality?.let(TripCopy::badgeWord)

    /** The stats row: "37.9 km · 30 min". */
    val stats: String get() = "$distance · $duration"
}

/** A week of the All drives list (S-HIS-01). */
public data class TripSectionUi(
    val id: String,
    /** "This week", "Last week", "19–25 Oct" */
    val title: String,
    /** "3 drives" */
    val meta: String,
    val rows: List<TripRowUi>,
)

/** One number with how it was obtained (F-DQ-05); long-press shows [provenance] and [meaning]. */
public data class ProvenancedValue(
    val key: String,
    val label: String,
    /** At the metric's precision, without "≈". */
    val value: String,
    val unit: String,
    val provenance: Provenance,
    val meaning: String,
    val estimated: Boolean = provenance == Provenance.ESTIMATED,
    /** A supporting line: "≈ 0.4 km estimated". */
    val note: String? = null,
    /** The value lowered the quality badge (S-DQ-01 warn tone). */
    val lowersBadge: Boolean = false,
) {
    /** "Distance, 39.0 kilometres. Derived." (accessibility.md: units spelled out). */
    val accessibilityLabel: String
        get() {
            val shown = if (estimated) "about $value" else value
            val unit = TripCopy.spokenUnit(unit)
            return "$label, $shown${if (unit.isEmpty()) "" else " $unit"}. ${provenance.label}."
        }
}

/** What a banner's button does. */
public sealed interface BannerAction {
    /** "Was this a bus or train?" Yes / No. */
    public data class Answer(val question: Suspect) : BannerAction

    /** Restore a passenger or not-a-drive trip as a drive. */
    public data object Restore : BannerAction
}

/** A banner under the trip sheet's title (S-TRP-01 state banners). */
public data class TripBannerUi(val text: String, val attention: Boolean = false, val action: BannerAction? = null)

/** The trip sheet's content (S-TRP-01, the parts the app has). */
public data class TripDetailUi(
    val id: String,
    /** "Thursday morning" */
    val title: String,
    val banners: List<TripBannerUi>,
    val tiles: List<ProvenancedValue>,
    val quality: QualityBadge?,
    /** The current answer to "What was this?", for the picker's checkmark. */
    val correction: TripCorrection,
    /** "5 Nov · 8:14 a.m." */
    val subtitle: String = "",
) {
    /** "Usable"; null before analysis. */
    val qualityWord: String? get() = quality?.let(TripCopy::badgeWord)
}

/** One stretch of the S-DQ-01 coverage strip, as fractions of the drive. */
public data class CoverageSpanUi(val style: OverlayStyle, val from: Double, val to: Double)

/** One line of "What the labels mean" (S-DQ-01). */
public data class LegendEntryUi(val provenance: Provenance, val label: String, val meaning: String)

/** The data-quality sheet's content (S-DQ-01). */
public data class QualitySheetUi(
    val badge: QualityBadge,
    /** "Usable data" */
    val title: String,
    /** One honest sentence per badge. */
    val hero: String,
    /** The estimated-start explanation, when there is one. */
    val estimatedStart: String?,
    val coverage: List<CoverageSpanUi>,
    /** "Scored 31 of 34 min" */
    val scoredLine: String,
    /** "Drive coverage, 33 minutes. Start estimated. 41 seconds without GPS." */
    val coverageDescription: String,
    val rows: List<ProvenancedValue>,
    val legend: List<LegendEntryUi>,
)

/** One choice of "What was this?" (S-TRP-02 type picker). */
public data class CorrectionOption(val correction: TripCorrection, val title: String)

/** What the Drives tab, All drives, the trip sheet and the data-quality sheet draw. */
public data class DrivesState(
    /** The first trip list has arrived. */
    val loaded: Boolean = false,
    /** Drives, newest first. */
    val trips: List<TripRowUi> = emptyList(),
    /** Trips marked as not a drive, kept 30 days (F-CAP-04 AC3). Newest first. */
    val notDrives: List<TripRowUi> = emptyList(),
    /** [trips] grouped by calendar week, newest first (S-HIS-01). */
    val sections: List<TripSectionUi> = emptyList(),
    val filter: DrivesFilter = DrivesFilter.DRIVES,
    /** Answers and corrections are possible (the core's corrections port is wired). */
    val canCorrect: Boolean = false,
    /** The open trip sheet (S-TRP-01), if any. */
    val detail: TripDetailUi? = null,
    /** The open data-quality sheet (S-DQ-01), if any. */
    val quality: QualitySheetUi? = null,
    /** S-HIS-01 subtitle: "3 drives". */
    val countText: String = "0 drives",
    /** S-HIS-01 footer: "That’s every drive since 5 November."; null without drives. */
    val sinceText: String? = null,
) {
    val isEmpty: Boolean get() = loaded && trips.isEmpty()

    /** The Drives tab's "Latest drives". */
    val latest: List<TripRowUi> get() = trips.take(LATEST_DRIVES)

    /**
     * The Drives tab links to All drives when it lists only some drives, or when there are trips marked as not a drive:
     * All drives holds their filter, and they must stay restorable before their 30-day deletion (F-CAP-04 AC3).
     */
    val seeAll: Boolean get() = trips.size > LATEST_DRIVES || notDrives.isNotEmpty()

    /** The rows of the current filter. */
    val shown: List<TripRowUi> get() = if (filter == DrivesFilter.DRIVES) trips else notDrives

    /** "What was this?" choices, in order. */
    val corrections: List<CorrectionOption> get() = TripCopy.correctionOptions
}

/** What the user does on the Drives screens. */
public sealed interface DrivesIntent {
    /** Opens the trip sheet (S-TRP-01). */
    public data class Open(val tripId: String) : DrivesIntent

    /** Closes the trip sheet and the data-quality sheet over it. */
    public data object CloseDetail : DrivesIntent

    /** Opens the data-quality sheet (S-DQ-01). */
    public data class OpenQuality(val tripId: String) : DrivesIntent

    public data object CloseQuality : DrivesIntent

    /** Replay on the trip sheet (DRIVE-200): [DrivesEffect.OpenReplay] hands the trip to S-RPL-01. */
    public data class OpenReplay(val tripId: String) : DrivesIntent

    /** Analysis on the trip detail: [DrivesEffect.OpenAnalysis] hands the trip to S-ANL-01. */
    public data class OpenAnalysis(val tripId: String) : DrivesIntent

    /** "Was this a bus or train?" Yes or No. */
    public data class Answer(val tripId: String, val yes: Boolean) : DrivesIntent

    /** "What was this?"; [TripCorrection.DRIVER] restores a trip as a drive. */
    public data class Correct(val tripId: String, val to: TripCorrection) : DrivesIntent

    public data class Filter(val filter: DrivesFilter) : DrivesIntent
}

/** One-shot navigation. */
public sealed interface DrivesEffect {
    /**
     * Open S-RPL-01 for [tripId]. [title] is the trip sheet's title ("Thursday morning"), shown under "Replay";
     * [mapSummary] is the map's accessibility summary ("Map of your drive, 19.9 km.").
     */
    public data class OpenReplay(val tripId: String, val title: String, val mapSummary: String) : DrivesEffect

    /** Open S-ANL-01 for [tripId]. [title] is the trip detail's title ("Thursday morning"), shown under "Analysis". */
    public data class OpenAnalysis(val tripId: String, val title: String) : DrivesEffect
}
