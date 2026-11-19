package com.driveperf.presentation.drives

import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.Metric
import com.driveperf.domain.trip.NotDrives
import com.driveperf.domain.trip.Provenance
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.RawState
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.openQuestion

/** Values at their catalogue precision and provenance (DRIVE-172), in the user's units. */
internal class MetricValues(private val units: UnitSystem) {
    /** A value for [metric] at its catalogue precision. */
    fun value(metric: Metric, label: String, si: Double, includesEstimate: Boolean = false): ProvenancedValue {
        val unit = metric.unit?.let(units::display)
        return ProvenancedValue(
            key = metric.key,
            label = label,
            value = unit?.format(si) ?: si.toString(),
            unit = unit?.symbol.orEmpty(),
            provenance = metric.provenance(includesEstimate),
            meaning = metric.meaning,
        )
    }

    /** A duration in whole minutes, "1 h 05" from an hour (brand.md); the unit is always "min". */
    fun minutes(metric: Metric, label: String, seconds: Double): ProvenancedValue {
        val m = wholeMinutes(seconds)
        val text = if (m >= MINUTES_PER_HOUR) "${m / MINUTES_PER_HOUR} h ${(m % MINUTES_PER_HOUR).toString().padStart(2, '0')}" else "$m"
        return value(metric, label, seconds).copy(value = text, unit = "min")
    }

    fun distance(metres: Double): String = distanceText(metres, units)

    private companion object {
        const val MINUTES_PER_HOUR = 60
    }
}

/** Trip detail (S-TRP-01) without its score: state banners, the meta row and the stat tiles. */
internal class TripSheet(units: UnitSystem, private val format: DrivesFormatter) {
    private val values = MetricValues(units)

    fun detail(trip: Trip, title: String, subtitle: String, car: String? = null): TripDetailUi = TripDetailUi(
        id = trip.id,
        title = title,
        banners = banners(trip),
        tiles = tiles(trip),
        quality = trip.quality?.badge,
        correction = trip.correction ?: if (trip.kind == TripKind.PASSENGER) TripCorrection.PASSENGER else TripCorrection.DRIVER,
        subtitle = subtitle,
        meta = TripMetaUi(
            car = car,
            passenger = trip.kind == TripKind.PASSENGER || trip.correction == TripCorrection.PASSENGER,
            source = if (trip.source == TripSource.MANUAL) "Started by you" else "Auto-detected",
            timeRange = "${format.time(trip.startedAt)}–${format.time(trip.endMs)}",
        ),
        scoresHidden = !trip.scoresShown,
        canAnalyse = trip.rawState != RawState.SUMMARY_ONLY,
    )

    /** S-TRP-01 state banners: the open question, passenger or not a drive, interrupted, short, Unscorable. */
    fun banners(trip: Trip): List<TripBannerUi> = buildList {
        trip.openQuestion?.let { add(TripBannerUi(TripCopy.questionText(it), action = BannerAction.Answer(it))) }
        val correction = trip.correction
        if (correction != null && correction.isNotADrive) {
            add(TripBannerUi(notADriveText(correction, trip.correctedAt), action = BannerAction.Restore))
        } else if (trip.kind == TripKind.PASSENGER) {
            add(TripBannerUi(PASSENGER, action = BannerAction.Restore))
        }
        when (trip.listedStatus) {
            ListedStatus.INTERRUPTED -> add(
                TripBannerUi(
                    "Interrupted at ${format.time(trip.endMs)}. ${format.platform.stoppedUs}. Everything before then is " +
                        "saved. The missing part is shown as a gap, never scored.",
                    attention = true,
                ),
            )
            ListedStatus.SHORT -> add(
                TripBannerUi(
                    "Short drive · ${values.distance(trip.distance)}. Short drives give the score less to work with, so " +
                        "expect it to move more from drive to drive.",
                ),
            )
            ListedStatus.FINALISED -> Unit
        }
        val quality = trip.quality
        if (quality != null && quality.badge == QualityBadge.UNSCORABLE) {
            add(
                TripBannerUi(
                    "We couldn’t score this one fairly. ${TripCopy.reasonText(quality.reason)}. Here’s the route and the " +
                        "stats anyway.",
                    attention = true,
                ),
            )
        }
    }

    private fun notADriveText(correction: TripCorrection, atMs: Long?): String {
        val what = when (correction) {
            TripCorrection.TRANSIT -> "a bus or train ride"
            TripCorrection.BIKE -> "a bike ride"
            TripCorrection.WALK -> "a walk"
            else -> "not a drive"
        }
        val deleted = atMs?.let { " It will be deleted on ${format.longDate(it + NotDrives.RETENTION_MS)}." }.orEmpty()
        return "Marked as $what. It’s out of your scores and history and kept under Not drives.$deleted"
    }

    /** S-TRP-01 stat tiles in components.md §5 order; speed is never first, and missing values are left out. */
    fun tiles(trip: Trip): List<ProvenancedValue> = buildList {
        val estimated = trip.estimatedDistanceM ?: 0.0
        add(
            values.value(Metric.DISTANCE, "Distance", trip.distance, includesEstimate = estimated > 0).copy(
                note = if (estimated > 0) "${TripCopy.ESTIMATED_PREFIX}${values.distance(estimated)} estimated" else null,
            ),
        )
        add(values.minutes(Metric.DURATION, "Duration", trip.seconds))
        trip.movingS?.let { add(values.minutes(Metric.MOVING_TIME, "Moving", it)) }
        trip.stoppedS?.let { add(values.minutes(Metric.STOPPED_TIME, "Stopped", it)) }
        trip.stopCount?.let { add(values.value(Metric.STOPS, "Stops", it.toDouble())) }
        trip.avgMovingSpeedMps?.let { add(values.value(Metric.AVG_MOVING_SPEED, "Avg moving speed", it)) }
        trip.maxSpeedMps?.let { add(values.value(Metric.MAX_SPEED, "Max speed", it)) }
        trip.elevGainM?.let { add(values.value(Metric.ELEVATION_GAIN_LOSS, "Climb", it)) }
        // Climb and descent share one metric; the descent tile needs a key of its own.
        trip.elevLossM?.let { add(values.value(Metric.ELEVATION_GAIN_LOSS, "Descent", it).copy(key = ELEVATION_LOSS_KEY)) }
    }

    private companion object {
        const val ELEVATION_LOSS_KEY = "elevationLoss"
        const val PASSENGER = "Marked as passenger. It isn’t scored and doesn’t count toward your baseline, routes or recaps."
    }
}

/** The legend under S-DQ-01: every provenance with its meaning. */
internal val provenanceLegend: List<LegendEntryUi> = Provenance.entries.map { LegendEntryUi(it, it.label, it.meaning) }
