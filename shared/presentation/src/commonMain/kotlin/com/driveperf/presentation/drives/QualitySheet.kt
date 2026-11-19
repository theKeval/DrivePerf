package com.driveperf.presentation.drives

import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.Metric
import com.driveperf.domain.trip.OverlayStyle
import com.driveperf.domain.trip.PhoneState
import com.driveperf.domain.trip.Provenance
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.QualityReason
import com.driveperf.domain.trip.SegmentReason
import com.driveperf.domain.trip.TrackOverlay
import com.driveperf.domain.trip.TripQuality
import com.driveperf.domain.trip.leftOut
import com.driveperf.domain.trip.overlays
import com.driveperf.domain.trip.phoneState
import kotlin.math.roundToInt

/** The data-quality sheet (S-DQ-01, F-DQ-04 AC2). */
internal class QualitySheet(units: UnitSystem) {
    private val values = MetricValues(units)

    fun sheet(quality: TripQuality): QualitySheetUi {
        val overlays = quality.overlays()
        val gapS = overlays.filter { it.style == OverlayStyle.SHORT_GAP || it.style == OverlayStyle.LONG_GAP }.sumOf { it.t1S - it.t0S }
        return QualitySheetUi(
            badge = quality.badge,
            title = "${TripCopy.badgeWord(quality.badge)} data",
            hero = hero(quality, gapS),
            estimatedStart = quality.estimatedStartM?.takeIf { it > 0 }?.let {
                "The first ${TripCopy.ESTIMATED_PREFIX}${values.distance(it)} is estimated. Automatic trips start a moment " +
                    "after you set off. We fill in the start from your phone’s location history and draw it dashed. It’s " +
                    "never scored and never gets event pins."
            },
            coverage = coverage(quality.totalS, overlays),
            scoredLine = "Scored ${wholeMinutes(quality.scoredS)} of ${wholeMinutes(quality.totalS)} min",
            coverageDescription = coverageDescription(quality, gapS, overlays.any { it.style == OverlayStyle.ESTIMATED_START }),
            rows = rows(quality),
            legend = provenanceLegend,
        )
    }

    private fun coverage(totalS: Double, overlays: List<TrackOverlay>): List<CoverageSpanUi> =
        if (totalS <= 0) {
            emptyList()
        } else {
            overlays.mapNotNull {
                val from = (it.t0S / totalS).coerceIn(0.0, 1.0)
                val to = (it.t1S / totalS).coerceIn(0.0, 1.0)
                if (to > from) CoverageSpanUi(it.style, from, to) else null
            }
        }

    private fun rows(q: TripQuality): List<ProvenancedValue> = buildList {
        val reason = q.reason
        add(
            ProvenancedValue(
                "phoneState", "Phone", TripCopy.phoneWord(q.phoneState), "", Provenance.DERIVED,
                "From how steady the motion sensors were while the car moved.", lowersBadge = q.phoneState == PhoneState.MOVED,
            ),
        )
        add(
            values.value(Metric.IMU_RATE, "Sensor rate", q.imuHz)
                .copy(lowersBadge = reason == QualityReason.LOW_IMU_RATE || reason == QualityReason.NO_IMU),
        )
        add(
            values.value(Metric.GNSS_COVERAGE, "GPS coverage", q.gnssCoveragePct)
                .copy(lowersBadge = reason == QualityReason.LOW_GNSS || reason == QualityReason.NO_PRECISE_LOCATION),
        )
        add(
            ProvenancedValue(
                "alignment", "Phone alignment",
                q.alignmentSettledS?.let { "Settled after ${it.roundToInt()} s" } ?: "Didn’t settle", "", Provenance.DERIVED,
                "How long it took to work out which way your phone faces in the car.",
                lowersBadge = reason == QualityReason.NOT_ALIGNED,
            ),
        )
        add(leftOutRow(q))
        q.estimatedStartM?.takeIf { it > 0 }?.let { add(values.value(Metric.ESTIMATED_START, "Estimated start", it)) }
    }

    private fun leftOutRow(q: TripQuality): ProvenancedValue {
        val leftOut = q.leftOut()
        val leftS = leftOut.sumOf { it.seconds }
        val row = values.minutes(Metric.EXCLUDED_TIME, "Left out of the score", leftS).copy(
            lowersBadge = q.reason == QualityReason.LOW_SCORED_SHARE || q.reason == QualityReason.TOO_LITTLE_SCORED_TIME,
        )
        return if (leftS > 0) {
            row.copy(note = leftOut.joinToString(" · ") { "${TripCopy.reasonWord(it.reason)} ${maxOf(wholeMinutes(it.seconds), 1)} min" })
        } else {
            row.copy(value = "Nothing", unit = "")
        }
    }

    private companion object {
        /** Below this the sensors ran in battery-saver mode. */
        const val LOW_IMU_HZ = 40.0

        /** One honest sentence per badge. */
        fun hero(q: TripQuality, gapS: Double): String = when (q.badge) {
            QualityBadge.GOOD -> "Everything we needed, the whole way. Scores and events are as reliable as a phone gets."
            QualityBadge.USABLE -> when {
                q.reason == QualityReason.LOW_IMU_RATE || q.imuHz < LOW_IMU_HZ ->
                    "Battery saver: sensors at ${q.imuHz.roundToInt()} Hz. The score is fair, with a little less detail in short events."
                gapS > 0 -> "Mostly solid, with ${gapS.roundToInt()} s without GPS. The score is fair; that stretch is drawn dotted."
                else -> "Mostly solid. The score is fair."
            }
            QualityBadge.LIMITED -> q.leftOut().firstOrNull { it.reason == SegmentReason.PHONE_MOVED }?.let {
                "Your phone moved around for about ${maxOf(wholeMinutes(it.seconds), 1)} minutes, so we left that part out of the score."
            } ?: (
                "Part of this drive couldn’t be scored fairly, so the score is based on ${wholeMinutes(q.scoredS)} of " +
                    "${wholeMinutes(q.totalS)} min."
                )
            QualityBadge.UNSCORABLE -> "We couldn’t score this one fairly. ${TripCopy.reasonText(q.reason)}."
        }

        /** "Drive coverage, 33 minutes. Start estimated. 41 seconds without GPS. 3 minutes left out of the score." */
        fun coverageDescription(q: TripQuality, gapS: Double, estimatedStart: Boolean): String = buildList {
            add("Drive coverage, ${wholeMinutes(q.totalS)} minutes.")
            if (estimatedStart) add("Start estimated.")
            if (gapS > 0) add("${gapS.roundToInt()} seconds without GPS.")
            val left = q.leftOut().sumOf { it.seconds }
            if (left > 0) add("${maxOf(wholeMinutes(left), 1)} minutes left out of the score.")
        }.joinToString(" ")
    }
}
