package com.driveperf.pipeline.score

import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventType
import com.driveperf.testing.FixtureCsv
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * One labelled or detected event: its type and peak time on the trip time base (seconds from `startedAt`). An
 * [uncertain] label (A19: a reference peak too close to a threshold or a gate to call) counts neither way: a detection
 * it matches is neither a true nor a false positive, and missing it is no false negative.
 */
internal data class LabelledEvent(val type: EventType, val tPeakS: Double, val uncertain: Boolean = false)

/** Trip-time spans, in seconds, where the reference covers the drive (A19 "eligible spans"). Empty: the whole trip. */
internal data class Coverage(val spans: List<ClosedFloatingPointRange<Double>> = emptyList()) {
    operator fun contains(tS: Double): Boolean = spans.isEmpty() || spans.any { tS in it }
}

/** Matched and unmatched events of one type. Precision and recall are null when nothing was detected or labelled. */
internal data class TypeCounts(val truePositives: Int, val falsePositives: Int, val falseNegatives: Int) {
    val precision: Double? get() = (truePositives + falsePositives).takeIf { it > 0 }?.let { truePositives.toDouble() / it }
    val recall: Double? get() = (truePositives + falseNegatives).takeIf { it > 0 }?.let { truePositives.toDouble() / it }

    operator fun plus(o: TypeCounts): TypeCounts =
        TypeCounts(truePositives + o.truePositives, falsePositives + o.falsePositives, falseNegatives + o.falseNegatives)
}

/**
 * The DRIVE-93 AC2 precision and recall harness (F-SCO-04, pipeline-and-scoring.md §8.5). Detected events are matched
 * one-to-one with labels of the same type whose peak is within [TOLERANCE_S], nearest first. Run it over the A19
 * labelled field set once Keval supplies it (`-PlabelledSet=<dir>`, see `LabelledSetTest`); until then it runs over the
 * committed synthetic set.
 */
internal object EventHarness {
    /** Labels come from people, so a match allows the same ±1.0 s that feedback survives reprocessing with (§0.5). */
    const val TOLERANCE_S = 1.0

    /** Q20 targets per type, provisional until A19. */
    const val PRECISION = 0.85
    const val RECALL = 0.75

    /**
     * The A19 minimum: a type with fewer confirmed (not uncertain) labels than this over the set is reported as
     * insufficient data rather than judged against the targets.
     */
    const val MIN_LABELS = 10

    /**
     * Counts per event type for one trip. Detections and labels outside [coverage] are ignored. Uncertain labels take
     * part in the nearest-first matching, so a detection they match is set aside, but they never count themselves.
     */
    fun evaluate(
        detected: List<LabelledEvent>,
        labels: List<LabelledEvent>,
        toleranceS: Double = TOLERANCE_S,
        coverage: Coverage = Coverage(),
    ): Map<EventType, TypeCounts> = EventType.entries.associateWith { type ->
        val d = detected.filter { it.type == type && it.tPeakS in coverage }
        val l = labels.filter { it.type == type && it.tPeakS in coverage }
        val pairs = d.indices.flatMap { i -> l.indices.map { j -> Triple(i, j, abs(d[i].tPeakS - l[j].tPeakS)) } }
            .filter { it.third <= toleranceS }
            .sortedWith(compareBy({ it.third }, { l[it.second].tPeakS }, { d[it.first].tPeakS }))
        val usedD = mutableSetOf<Int>()
        val usedL = mutableSetOf<Int>()
        pairs.forEach { (i, j, _) -> if (i !in usedD && j !in usedL) { usedD += i; usedL += j } }
        val toUncertain = usedL.count { l[it].uncertain }
        TypeCounts(
            truePositives = usedD.size - toUncertain,
            falsePositives = d.size - usedD.size,
            falseNegatives = l.indices.count { it !in usedL && !l[it].uncertain },
        )
    }

    /** Counts summed over trips. */
    fun total(trips: List<Map<EventType, TypeCounts>>): Map<EventType, TypeCounts> =
        EventType.entries.associateWith { type -> trips.fold(TypeCounts(0, 0, 0)) { sum, t -> sum + (t[type] ?: TypeCounts(0, 0, 0)) } }

    /**
     * Every type below a target, as readable lines; empty when the set passes. A type with nothing to judge passes, and
     * so does one with fewer than [minLabels] confirmed labels (see [insufficient]).
     */
    fun failures(counts: Map<EventType, TypeCounts>, minLabels: Int = 0): List<String> = counts.flatMap { (type, c) ->
        if (c.truePositives + c.falseNegatives < minLabels) return@flatMap emptyList()
        listOfNotNull(
            c.precision?.takeIf { it < PRECISION }?.let { "$type precision $it < $PRECISION ($c)" },
            c.recall?.takeIf { it < RECALL }?.let { "$type recall $it < $RECALL ($c)" },
        )
    }

    /** Types with fewer than [minLabels] confirmed labels: too few to judge, reported as insufficient data. */
    fun insufficient(counts: Map<EventType, TypeCounts>, minLabels: Int = MIN_LABELS): List<EventType> =
        counts.filter { (_, c) -> c.truePositives + c.falseNegatives < minLabels }.keys.toList()

    /** Wilson score 95 % interval of [k] successes in [n] trials, or null when [n] is 0. */
    fun wilson95(k: Int, n: Int): ClosedFloatingPointRange<Double>? {
        if (n == 0) return null
        val p = k.toDouble() / n
        val z2 = Z95 * Z95
        val centre = (p + z2 / (2 * n)) / (1 + z2 / n)
        val half = Z95 * sqrt(p * (1 - p) / n + z2 / (4.0 * n * n)) / (1 + z2 / n)
        return (centre - half).coerceAtLeast(0.0)..(centre + half).coerceAtMost(1.0)
    }

    fun of(events: List<DrivingEvent>): List<LabelledEvent> = events.map { LabelledEvent(it.type, it.tPeakS) }

    /**
     * `<id>.labels.csv`: the `# privacy-trimmed` header first (ADR-008: every committed CSV declares it in its first 5
     * lines, and `tools/ci/check-fixture-headers.sh` rejects one without it), `#` comment lines, a `type,t_peak_s`
     * header, then one event per line. Every labelled file, the A19 set's included, carries the same header.
     *
     * When any label is uncertain, a third `certainty` column says `certain` or `uncertain`. Each [coverage] span is a
     * `# covered_s: <from> <to>` line; with none the whole trip counts.
     */
    fun writeLabels(id: String, labels: List<LabelledEvent>, coverage: Coverage = Coverage()): String = buildString {
        val certainty = labels.any { it.uncertain }
        append(FixtureCsv.PRIVACY_HEADER).append('\n')
        append("# id: ").append(id).append('\n')
        coverage.spans.forEach { append(COVERED).append(fixed2(it.start)).append(' ').append(fixed2(it.endInclusive)).append('\n') }
        append(if (certainty) "type,t_peak_s,certainty\n" else "type,t_peak_s\n")
        labels.sortedBy { it.tPeakS }.forEach {
            append(it.type.name).append(',').append(fixed2(it.tPeakS))
            if (certainty) append(',').append(if (it.uncertain) UNCERTAIN else CERTAIN)
            append('\n')
        }
    }

    /** Reads [writeLabels]' format: `#` lines, the privacy header among them, and the column header are skipped. */
    fun parseLabels(text: String): List<LabelledEvent> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("type,") }
        .map { line ->
            val c = line.split(',').map { it.trim() }
            val certainty = c.getOrNull(2)?.takeIf { it.isNotEmpty() } ?: CERTAIN
            require(certainty == CERTAIN || certainty == UNCERTAIN) { "certainty must be $CERTAIN or $UNCERTAIN: $line" }
            LabelledEvent(EventType.valueOf(c[0]), c[1].toDouble(), certainty == UNCERTAIN)
        }
        .toList()

    /** The `# covered_s:` spans of a labels file. */
    fun parseCoverage(text: String): Coverage = Coverage(
        text.lineSequence().map { it.trim() }.filter { it.startsWith(COVERED) }.map { line ->
            val (from, to) = line.removePrefix(COVERED).trim().split(' ').filter { it.isNotEmpty() }.map { it.toDouble() }
            require(from <= to) { "covered span ends before it starts: $line" }
            from..to
        }.toList(),
    )

    private const val COVERED = "# covered_s: "
    private const val CERTAIN = "certain"
    private const val UNCERTAIN = "uncertain"
    private const val Z95 = 1.959964

    private fun fixed2(x: Double): String {
        val cents = kotlin.math.round(x * 100).toLong()
        return "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    }
}
