package com.driveperf.pipeline.score

import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventType
import kotlin.math.abs

/** One labelled or detected event: its type and peak time on the trip time base (seconds from `startedAt`). */
internal data class LabelledEvent(val type: EventType, val tPeakS: Double)

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

    /** Counts per event type for one trip. */
    fun evaluate(detected: List<LabelledEvent>, labels: List<LabelledEvent>, toleranceS: Double = TOLERANCE_S): Map<EventType, TypeCounts> =
        EventType.entries.associateWith { type ->
            val d = detected.filter { it.type == type }
            val l = labels.filter { it.type == type }
            val pairs = d.indices.flatMap { i -> l.indices.map { j -> Triple(i, j, abs(d[i].tPeakS - l[j].tPeakS)) } }
                .filter { it.third <= toleranceS }
                .sortedWith(compareBy({ it.third }, { l[it.second].tPeakS }, { d[it.first].tPeakS }))
            val usedD = mutableSetOf<Int>()
            val usedL = mutableSetOf<Int>()
            pairs.forEach { (i, j, _) -> if (i !in usedD && j !in usedL) { usedD += i; usedL += j } }
            TypeCounts(usedD.size, d.size - usedD.size, l.size - usedL.size)
        }

    /** Counts summed over trips. */
    fun total(trips: List<Map<EventType, TypeCounts>>): Map<EventType, TypeCounts> =
        EventType.entries.associateWith { type -> trips.fold(TypeCounts(0, 0, 0)) { sum, t -> sum + (t[type] ?: TypeCounts(0, 0, 0)) } }

    /** Every type below a target, as readable lines; empty when the set passes. A type with nothing to judge passes. */
    fun failures(counts: Map<EventType, TypeCounts>): List<String> = counts.flatMap { (type, c) ->
        listOfNotNull(
            c.precision?.takeIf { it < PRECISION }?.let { "$type precision $it < $PRECISION ($c)" },
            c.recall?.takeIf { it < RECALL }?.let { "$type recall $it < $RECALL ($c)" },
        )
    }

    fun of(events: List<DrivingEvent>): List<LabelledEvent> = events.map { LabelledEvent(it.type, it.tPeakS) }

    /** `<id>.labels.csv`: `#` comment lines, a `type,t_peak_s` header, then one event per line. */
    fun writeLabels(id: String, labels: List<LabelledEvent>): String = buildString {
        append("# id: ").append(id).append('\n')
        append("type,t_peak_s\n")
        labels.sortedBy { it.tPeakS }.forEach { append(it.type.name).append(',').append(fixed2(it.tPeakS)).append('\n') }
    }

    fun parseLabels(text: String): List<LabelledEvent> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("type,") }
        .map { line ->
            val (type, t) = line.split(',')
            LabelledEvent(EventType.valueOf(type.trim()), t.trim().toDouble())
        }
        .toList()

    private fun fixed2(x: Double): String {
        val cents = kotlin.math.round(x * 100).toLong()
        return "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    }
}
