package com.driveperf.presentation.drives

import com.driveperf.domain.trip.PhoneState
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.QualityReason
import com.driveperf.domain.trip.SegmentReason
import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.domain.trip.TripKind

/** The Drives screens' fixed words (docs/design DRIVE-100, DRIVE-170, DRIVE-172; components.md §10, §12). */
public object TripCopy {
    /** "≈" and a thin space: an estimated number (brand.md). */
    public const val ESTIMATED_PREFIX: String = "≈ "

    public fun badgeWord(badge: QualityBadge): String = when (badge) {
        QualityBadge.GOOD -> "Good"
        QualityBadge.USABLE -> "Usable"
        QualityBadge.LIMITED -> "Limited"
        QualityBadge.UNSCORABLE -> "Unscorable"
    }

    public fun questionText(question: Suspect): String = when (question) {
        Suspect.CYCLING -> "Was this a bike ride? Yes takes it out of your scores and history."
        else -> "Was this a bus or train? Yes takes it out of your scores and history."
    }

    public fun correctionTitle(correction: TripCorrection): String = when (correction) {
        TripCorrection.DRIVER -> "I was driving"
        TripCorrection.PASSENGER -> "I was a passenger"
        TripCorrection.TRANSIT -> "Bus or train"
        TripCorrection.BIKE -> "Bike ride"
        TripCorrection.WALK -> "Walk"
        TripCorrection.OTHER -> "Something else"
    }

    /** Every "What was this?" choice, in order (S-TRP-02). */
    public val correctionOptions: List<CorrectionOption> =
        TripCorrection.entries.map { CorrectionOption(it, correctionTitle(it)) }

    public fun phoneWord(phone: PhoneState): String = when (phone) {
        PhoneState.MOUNTED -> "Mounted"
        PhoneState.LOOSE -> "Loose"
        PhoneState.MOVED -> "Moved"
        PhoneState.UNKNOWN -> "Not sure"
    }

    /** Why a stretch was left out, in two words: "Phone moved". */
    public fun reasonWord(reason: SegmentReason): String = when (reason) {
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

    /** Why a drive is Unscorable, as a sentence without the full stop. */
    public fun reasonText(reason: QualityReason?): String = when (reason) {
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

    /** "Passenger", "Bus or train"…: why a card is dimmed. Null for a driver trip. */
    public fun kindLabel(trip: Trip): String? = when (trip.correction) {
        TripCorrection.TRANSIT -> "Bus or train"
        TripCorrection.BIKE -> "Bike ride"
        TripCorrection.WALK -> "Walk"
        TripCorrection.OTHER -> "Not a drive"
        TripCorrection.PASSENGER -> "Passenger"
        TripCorrection.DRIVER, null -> if (trip.kind == TripKind.PASSENGER) "Passenger" else null
    }

    /** A unit symbol as a screen reader should say it: "km" → "kilometres" (accessibility.md). */
    public fun spokenUnit(symbol: String): String = when (symbol) {
        "km" -> "kilometres"
        "km/h" -> "kilometres per hour"
        "mi" -> "miles"
        "mph" -> "miles per hour"
        "ft" -> "feet"
        "m" -> "metres"
        "min" -> "minutes"
        "%" -> "percent"
        "Hz" -> "hertz"
        else -> symbol
    }
}
