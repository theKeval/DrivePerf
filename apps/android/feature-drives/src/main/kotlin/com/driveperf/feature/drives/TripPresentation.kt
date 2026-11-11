package com.driveperf.feature.drives

import com.driveperf.designsystem.DpEstimated
import com.driveperf.domain.trip.Metric
import com.driveperf.domain.trip.NotDrives
import com.driveperf.domain.trip.OverlayStyle
import com.driveperf.domain.trip.PhoneState
import com.driveperf.domain.trip.Provenance
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.QualityReason
import com.driveperf.domain.trip.SegmentReason
import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripQuality
import com.driveperf.domain.trip.leftOut
import com.driveperf.domain.trip.overlays
import com.driveperf.domain.trip.phoneState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

// What the trip sheet (S-TRP-01, the parts the shell has) and the data-quality sheet (S-DQ-01) show, built from a
// RecordedTrip without Compose so it is unit-tested. Provenance and precision come from the shared Metric catalogue
// (DRIVE-172). Copy is from docs/design/DRIVE-170 and DRIVE-172.

/** One number with how it was obtained (F-DQ-05); long-press shows [provenance] and [meaning]. */
data class ProvenancedValue(
    val key: String,
    val label: String,
    /** At the metric's precision, without "≈". */
    val value: String,
    val unit: String,
    val provenance: Provenance,
    val meaning: String,
    val estimated: Boolean = provenance == Provenance.ESTIMATED,
    val note: String? = null,
    /** The value lowered the quality badge (S-DQ-01 warn tone). */
    val lowersBadge: Boolean = false,
) {
    /** "Distance, 39.0 km. Derived." */
    val contentDescription: String
        get() = "$label, ${if (estimated) "about $value" else value}${if (unit.isEmpty()) "" else " $unit"}. ${provenance.label}."
}

/** A banner on the trip sheet. */
data class TripBannerUi(val text: String, val attention: Boolean = false, val action: BannerAction? = null)

sealed interface BannerAction {
    data class Answer(val question: Suspect) : BannerAction
    data object Restore : BannerAction
}

/** The trip sheet's content. */
data class TripDetailUi(
    val id: String,
    val title: String,
    val banners: List<TripBannerUi>,
    val tiles: List<ProvenancedValue>,
    val quality: QualityBadge?,
    val correction: TripCorrection,
)

/** One stretch of the S-DQ-01 coverage strip, as fractions of the drive. */
data class CoverageSpanUi(val style: OverlayStyle, val from: Float, val to: Float)

/** The data-quality sheet's content (S-DQ-01). */
data class QualitySheetUi(
    val badge: QualityBadge,
    val title: String,
    val hero: String,
    val estimatedStart: String?,
    val coverage: List<CoverageSpanUi>,
    val scoredLine: String,
    val coverageDescription: String,
    val rows: List<ProvenancedValue>,
    val legend: List<Provenance>,
)

/** Builds the sheets' content from a [RecordedTrip]. */
internal class TripPresenter(private val zone: ZoneId, private val locale: Locale) {
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
    private val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).withZone(zone)

    fun detail(trip: RecordedTrip, title: String): TripDetailUi = TripDetailUi(
        id = trip.id,
        title = title,
        banners = banners(trip),
        tiles = tiles(trip),
        quality = trip.quality?.badge,
        correction = trip.correction ?: if (trip.kind == TripKind.PASSENGER) TripCorrection.PASSENGER else TripCorrection.DRIVER,
    )

    fun banners(trip: RecordedTrip): List<TripBannerUi> = buildList {
        trip.question?.let { add(TripBannerUi(questionText(it), action = BannerAction.Answer(it))) }
        val correction = trip.correction
        if (correction != null && correction.isNotADrive) {
            add(TripBannerUi(notADriveText(correction, trip.correctedAtMs), action = BannerAction.Restore))
        } else if (trip.kind == TripKind.PASSENGER) {
            add(
                TripBannerUi(
                    "Marked as passenger. It isn’t scored and doesn’t count toward your baseline, routes or recaps.",
                    action = BannerAction.Restore,
                ),
            )
        }
        when (trip.status) {
            RecordedStatus.INTERRUPTED -> add(TripBannerUi(interruptedText(trip), attention = true))
            RecordedStatus.SHORT -> add(
                TripBannerUi(
                    "Short drive · ${km(trip.distanceM)} km. Short drives give the score less to work with, so expect " +
                        "it to move more from drive to drive.",
                ),
            )
            RecordedStatus.FINALISED -> Unit
        }
        val q = trip.quality
        if (q != null && q.badge == QualityBadge.UNSCORABLE) {
            add(
                TripBannerUi(
                    "We couldn’t score this one fairly. ${reasonText(q.reason)}. Here’s the route and the stats anyway.",
                    attention = true,
                ),
            )
        }
    }

    private fun interruptedText(trip: RecordedTrip): String {
        val at = trip.endedWallMs ?: (trip.startedWallMs + trip.durationS * MS_PER_S)
        return "Interrupted at ${time.format(Instant.ofEpochMilli(at))}. Android stopped DrivePerf in the background. " +
            "Everything before then is saved. The missing part is shown as a gap, never scored."
    }

    private fun notADriveText(correction: TripCorrection, atMs: Long?): String {
        val what = when (correction) {
            TripCorrection.TRANSIT -> "a bus or train ride"
            TripCorrection.BIKE -> "a bike ride"
            TripCorrection.WALK -> "a walk"
            else -> "not a drive"
        }
        val deleted = atMs?.let { " It will be deleted on ${date.format(Instant.ofEpochMilli(it + NotDrives.RETENTION_MS))}." }.orEmpty()
        return "Marked as $what. It’s out of your scores and history and kept under Not drives.$deleted"
    }

    /** S-TRP-01 stat tiles in PRD order; speed is never first, and missing values are left out. */
    fun tiles(trip: RecordedTrip): List<ProvenancedValue> = buildList {
        val estimatedPart = trip.estimatedDistanceM > 0
        add(
            value(Metric.DISTANCE, "Distance", trip.distanceM, estimatedPart).copy(
                note = if (estimatedPart) DpEstimated.text("${km(trip.estimatedDistanceM)} km estimated") else null,
            ),
        )
        add(minutes(Metric.DURATION, "Duration", trip.durationS.toDouble()))
        trip.movingS?.let { add(minutes(Metric.MOVING_TIME, "Moving", it)) }
        trip.stopCount?.let { add(value(Metric.STOPS, "Stops", it.toDouble())) }
        trip.maxSpeedMps?.let { add(value(Metric.MAX_SPEED, "Max speed", it)) }
    }

    fun quality(trip: RecordedTrip): QualitySheetUi? {
        val q = trip.quality ?: return null
        val overlays = q.overlays()
        val gapS = overlays.filter { it.style == OverlayStyle.SHORT_GAP || it.style == OverlayStyle.LONG_GAP }.sumOf { it.t1S - it.t0S }
        val coverage = if (q.totalS <= 0) emptyList() else overlays.mapNotNull {
            val from = (it.t0S / q.totalS).coerceIn(0.0, 1.0).toFloat()
            val to = (it.t1S / q.totalS).coerceIn(0.0, 1.0).toFloat()
            if (to > from) CoverageSpanUi(it.style, from, to) else null
        }
        return QualitySheetUi(
            badge = q.badge,
            title = "${badgeWord(q.badge)} data",
            hero = hero(q, gapS),
            estimatedStart = q.estimatedStartM?.takeIf { it > 0 }?.let {
                "The first ${DpEstimated.PREFIX}${km(it)} km is estimated. Automatic trips start a moment after you set off. " +
                    "We fill in the start from your phone’s location history and draw it dashed. It’s never scored and never " +
                    "gets event pins."
            },
            coverage = coverage,
            scoredLine = "Scored ${wholeMinutes(q.scoredS)} of ${wholeMinutes(q.totalS)} min",
            coverageDescription = coverageDescription(q, gapS, overlays.any { it.style == OverlayStyle.ESTIMATED_START }),
            rows = rows(q),
            legend = Provenance.entries,
        )
    }

    private fun rows(q: TripQuality): List<ProvenancedValue> = buildList {
        val reason = q.reason
        add(
            ProvenancedValue(
                "phoneState", "Phone", phoneWord(q.phoneState), "", Provenance.DERIVED,
                "From how steady the motion sensors were while the car moved.", lowersBadge = q.phoneState == PhoneState.MOVED,
            ),
        )
        add(
            value(Metric.IMU_RATE, "Sensor rate", q.imuHz)
                .copy(lowersBadge = reason == QualityReason.LOW_IMU_RATE || reason == QualityReason.NO_IMU),
        )
        add(
            value(Metric.GNSS_COVERAGE, "GPS coverage", q.gnssCoveragePct)
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
        val leftOut = q.leftOut()
        val leftS = leftOut.sumOf { it.seconds }
        val left = minutes(Metric.EXCLUDED_TIME, "Left out of the score", leftS).copy(
            lowersBadge = reason == QualityReason.LOW_SCORED_SHARE || reason == QualityReason.TOO_LITTLE_SCORED_TIME,
        )
        add(
            if (leftS > 0) {
                left.copy(note = leftOut.joinToString(" · ") { "${reasonWord(it.reason)} ${maxOf(wholeMinutes(it.seconds), 1)} min" })
            } else {
                left.copy(value = "Nothing", unit = "")
            },
        )
        q.estimatedStartM?.takeIf { it > 0 }?.let { add(value(Metric.ESTIMATED_START, "Estimated start", it)) }
    }

    /** A value for [metric] at its catalogue precision. */
    fun value(metric: Metric, label: String, si: Double, includesEstimate: Boolean = false): ProvenancedValue {
        val provenance = metric.provenance(includesEstimate)
        return ProvenancedValue(
            metric.key, label, metric.unit?.format(si) ?: si.toString(), metric.unit?.symbol.orEmpty(), provenance,
            metric.meaning,
        )
    }

    /** Durations in whole minutes, "1 h 05" from an hour (brand.md). */
    private fun minutes(metric: Metric, label: String, seconds: Double): ProvenancedValue {
        val m = wholeMinutes(seconds)
        val text = if (m >= MINUTES_PER_HOUR) String.format(Locale.US, "%d h %02d", m / MINUTES_PER_HOUR, m % MINUTES_PER_HOUR) else "$m"
        return value(metric, label, seconds).copy(value = text, unit = "min")
    }

    companion object {
        const val LOW_IMU_HZ = 40.0
        private const val MS_PER_S = 1_000L
        private const val MINUTES_PER_HOUR = 60

        fun badgeWord(badge: QualityBadge): String = when (badge) {
            QualityBadge.GOOD -> "Good"
            QualityBadge.USABLE -> "Usable"
            QualityBadge.LIMITED -> "Limited"
            QualityBadge.UNSCORABLE -> "Unscorable"
        }

        fun questionText(question: Suspect): String = when (question) {
            Suspect.CYCLING -> "Was this a bike ride? Yes takes it out of your scores and history."
            else -> "Was this a bus or train? Yes takes it out of your scores and history."
        }

        fun correctionTitle(c: TripCorrection): String = when (c) {
            TripCorrection.DRIVER -> "I was driving"
            TripCorrection.PASSENGER -> "I was a passenger"
            TripCorrection.TRANSIT -> "Bus or train"
            TripCorrection.BIKE -> "Bike ride"
            TripCorrection.WALK -> "Walk"
            TripCorrection.OTHER -> "Something else"
        }

        fun phoneWord(p: PhoneState): String = when (p) {
            PhoneState.MOUNTED -> "Mounted"
            PhoneState.LOOSE -> "Loose"
            PhoneState.MOVED -> "Moved"
            PhoneState.UNKNOWN -> "Not sure"
        }

        fun reasonWord(r: SegmentReason): String = when (r) {
            SegmentReason.PHONE_MOVED -> "Phone moved"
            SegmentReason.NOT_ALIGNED -> "Lining up"
            SegmentReason.LOW_IMU_RATE -> "Slow sensors"
            SegmentReason.IMU_GAP -> "Sensor gap"
            SegmentReason.APP_KILLED -> "App stopped"
            SegmentReason.LOW_BATTERY -> "Low battery"
            SegmentReason.THERMAL -> "Phone too warm"
            SegmentReason.USER_PAUSE -> "Paused"
            SegmentReason.NO_GNSS, SegmentReason.TUNNEL -> "No GPS"
            SegmentReason.ESTIMATED_START -> "Estimated start"
        }

        fun reasonText(reason: QualityReason?): String = when (reason) {
            QualityReason.NO_IMU -> "No motion sensor data reached the app"
            QualityReason.NO_PRECISE_LOCATION -> "Precise location was off"
            QualityReason.NOT_ALIGNED -> "We couldn’t work out which way your phone faced"
            QualityReason.LOW_SCORED_SHARE -> "Too much of the drive had to be left out"
            QualityReason.TOO_LITTLE_SCORED_TIME -> "Too little of the drive could be scored"
            QualityReason.LOW_GNSS -> "GPS was weak for much of the drive"
            QualityReason.NO_GYRO -> "This phone has no gyroscope"
            QualityReason.LOW_IMU_RATE -> "The sensors ran slower than usual"
            null -> "Not enough good data"
        }

        fun kindLabel(trip: RecordedTrip): String? = when (trip.correction) {
            TripCorrection.TRANSIT -> "Bus or train"
            TripCorrection.BIKE -> "Bike ride"
            TripCorrection.WALK -> "Walk"
            TripCorrection.OTHER -> "Not a drive"
            TripCorrection.PASSENGER -> "Passenger"
            TripCorrection.DRIVER, null -> if (trip.kind == TripKind.PASSENGER) "Passenger" else null
        }
    }
}

private const val SECONDS_PER_MINUTE = 60.0

private fun wholeMinutes(seconds: Double): Int = (maxOf(seconds, 0.0) / SECONDS_PER_MINUTE).roundToInt()

/** Distance in km at the catalogue's 0.1 km. */
private fun km(metres: Double): String = Metric.DISTANCE.unit?.format(metres).orEmpty()

private fun hero(q: TripQuality, gapS: Double): String = when (q.badge) {
    QualityBadge.GOOD -> "Everything we needed, the whole way. Scores and events are as reliable as a phone gets."
    QualityBadge.USABLE -> when {
        q.reason == QualityReason.LOW_IMU_RATE || q.imuHz < TripPresenter.LOW_IMU_HZ ->
            "Battery saver: sensors at ${q.imuHz.roundToInt()} Hz. The score is fair, with a little less detail in short events."
        gapS > 0 -> "Mostly solid, with ${gapS.roundToInt()} s without GPS. The score is fair; that stretch is drawn dotted."
        else -> "Mostly solid. The score is fair."
    }
    QualityBadge.LIMITED -> q.leftOut().firstOrNull { it.reason == SegmentReason.PHONE_MOVED }?.let {
        "Your phone moved around for about ${maxOf(wholeMinutes(it.seconds), 1)} minutes, so we left that part out of the score."
    } ?: "Part of this drive couldn’t be scored fairly, so the score is based on ${wholeMinutes(q.scoredS)} of " +
        "${wholeMinutes(q.totalS)} min."
    QualityBadge.UNSCORABLE -> "We couldn’t score this one fairly. ${TripPresenter.reasonText(q.reason)}."
}

private fun coverageDescription(q: TripQuality, gapS: Double, estimatedStart: Boolean): String = buildList {
    add("Drive coverage, ${wholeMinutes(q.totalS)} minutes.")
    if (estimatedStart) add("Start estimated.")
    if (gapS > 0) add("${gapS.roundToInt()} seconds without GPS.")
    val left = q.leftOut().sumOf { it.seconds }
    if (left > 0) add("${maxOf(wholeMinutes(left), 1)} minutes left out of the score.")
}.joinToString(" ")
