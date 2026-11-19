package com.driveperf.presentation.replay

import com.driveperf.domain.replay.EventMarker
import com.driveperf.domain.replay.MaxSpeedPin
import com.driveperf.domain.replay.ReplayDetails
import com.driveperf.domain.replay.StopPin
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.DisplayUnit
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.Metric
import com.driveperf.domain.trip.Provenance
import kotlin.math.roundToInt

/**
 * The only formatting Replay and the evidence sheet leave to the app (ADR-002 section 4): the local time of day in the
 * user's time zone and locale. Numbers, units and every sentence are shared. All times are UTC epoch milliseconds.
 */
public fun interface ReplayTimeFormat {
    /** The short local time: "5:56 pm". */
    public fun time(epochMs: Long): String

    public companion object {
        /** No time of day (previews and fixtures): sentences leave it out. */
        public val None: ReplayTimeFormat = ReplayTimeFormat { "" }
    }
}

/** An event pin or timeline tick with the words VoiceOver and TalkBack read for it (S-RPL-01 accessibility). */
public data class ReplayEventPin(val marker: EventMarker, val accessibilityLabel: String)

/** What a pin tap shows on S-RPL-03: a stop or the max speed. */
public enum class ReplayPinKind { STOP, MAX_SPEED }

/** One row of S-RPL-03's list: "Into the drive · 12:04". */
public data class ReplayPinRow(val label: String, val value: String)

/**
 * S-RPL-03, the stop or max-speed popover (F-RPL-02 AC2–3). [value] is shown in plain text, never accent or coloured
 * (speed-neutral rule, motion.md §3.6). [tS] is where "Jump to this moment" puts the marker.
 */
public data class ReplayPinSheet(
    val kind: ReplayPinKind,
    val title: String,
    val subtitle: String,
    val value: String,
    val unit: String,
    val provenance: Provenance,
    val rows: List<ReplayPinRow>,
    val note: String,
    val tS: Double,
    val accessibilityLabel: String,
)

/**
 * Everything S-RPL-01 draws over the route besides the marker (F-RPL-02, F-RPL-03), with its words, in the user's
 * [units]. Built when the replay or its details load, never per frame.
 *
 * @property eventPins the events with a map pin: positioned and in measured time (AC5, EC-GPS).
 * @property ticks every event, pinned or not, as a scrubber tick at the same times (AC3).
 * @property stopPins positioned stops.
 * @property summary "103 km/h max · 2 stops · 2 events": neutral text (D5/D6).
 * @property elevationText "↗ 118 m ↘ 103 m", or null without elevation data.
 * @property noEventsText "No abrupt inputs on this drive." when there are no events at all.
 */
@Suppress("LongParameterList") // one property per thing drawn, built only by ReplayCopy.overlay
public class ReplayOverlay(
    public val details: ReplayDetails,
    public val units: UnitSystem,
    public val eventPins: List<ReplayEventPin>,
    public val ticks: List<ReplayEventPin>,
    public val stopPins: List<StopPin>,
    public val maxSpeed: MaxSpeedPin?,
    public val summary: String,
    public val elevationText: String?,
    public val noEventsText: String?,
    /** "Map of your drive, 39.0 km. 2 events, 2 stops." */
    public val mapSummary: String,
) {
    /** The distance readout's unit: "km" or "mi". */
    public val distanceUnit: String get() = units.display(DisplayUnit.KILOMETRES).symbol

    /** The speed readout's unit: "km/h" or "mph". */
    public val speedUnit: String get() = units.display(DisplayUnit.KM_PER_HOUR).symbol

    /** The distance readout at [tS]: "12.3" (in [distanceUnit]). */
    public fun distanceText(tS: Double): String = units.display(DisplayUnit.KILOMETRES).format(details.distance.at(tS))

    /** The speed readout: "92" (in [speedUnit]), or "—" with no measured speed (EC-GPS). */
    public fun speedText(speedMps: Double?): String =
        speedMps?.let { units.display(DisplayUnit.KM_PER_HOUR).format(it) } ?: ReplayCopy.NO_VALUE
}

/** Replay's shared words (decision 3: copy stays in the shared presenters). English only (Gate 2). */
public object ReplayCopy {
    /** No value (a GPS gap, no speed). */
    public const val NO_VALUE: String = "—"
    public const val NO_EVENTS: String = "No abrupt inputs on this drive."
    public const val JUMP: String = "Jump to this moment"
    private const val STOP_NOTE = "Stops of 10 seconds or more get a pin. Stopping fully and gently never costs Smoothness."
    private const val MAX_NOTE = "The highest speed with a good GPS fix on this drive. Shown as plain text for your own " +
        "review; it never appears on share cards, records or recaps."
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3600
    private const val MS_PER_S = 1000.0

    /** "Hard braking", "Hard acceleration", "Hard cornering". */
    public fun eventTitle(type: EventType): String = when (type) {
        EventType.BRAKE -> "Hard braking"
        EventType.ACCEL -> "Hard acceleration"
        EventType.CORNER -> "Hard cornering"
    }

    /** "12:05" or "1:02:05". */
    public fun clock(seconds: Double): String {
        val total = seconds.coerceAtLeast(0.0).toInt()
        val h = total / SECONDS_PER_HOUR
        val m = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val s = total % SECONDS_PER_MINUTE
        val mm = if (h > 0) m.toString().padStart(2, '0') else m.toString()
        return (if (h > 0) "$h:" else "") + "$mm:${s.toString().padStart(2, '0')}"
    }

    /** The local time of [tS] trip seconds into a trip that started at [startedAtMs]; "" without a formatter. */
    public fun timeOfDay(format: ReplayTimeFormat, startedAtMs: Long, tS: Double): String =
        format.time(startedAtMs + (tS * MS_PER_S).toLong())

    /** A unit as VoiceOver and TalkBack should say it: "kilometres per hour". */
    public fun spoken(symbol: String): String = when (symbol) {
        "km/h" -> "kilometres per hour"
        "mph" -> "miles per hour"
        "km" -> "kilometres"
        "mi" -> "miles"
        "m" -> "metres"
        "ft" -> "feet"
        "s" -> "seconds"
        "g" -> "g"
        else -> symbol
    }

    /** "a · b", leaving out empty parts. */
    public fun dot(vararg parts: String): String = parts.filter { it.isNotEmpty() }.joinToString(" · ")

    /** [count] [word], plural with an "s": "1 stop", "2 stops". */
    public fun count(count: Int, word: String): String = "$count $word" + if (count == 1) "" else "s"

    /** "Hard braking, marked as needed, 5:56 pm. Peak 0.40 g." */
    internal fun pinLabel(marker: EventMarker, peakG: Double?, time: String): String {
        val needed = if (marker.needed) ", marked as needed" else ""
        val at = if (time.isEmpty()) "" else ", $time"
        val peak = peakG?.let { " Peak ${DisplayUnit.G.format(it)} g." } ?: ""
        return "${eventTitle(marker.type)}$needed$at.$peak"
    }

    /** Builds the overlay for [details] in [units]. */
    public fun overlay(details: ReplayDetails, units: UnitSystem, format: ReplayTimeFormat): ReplayOverlay {
        val speed = units.display(DisplayUnit.KM_PER_HOUR)
        val distance = units.display(DisplayUnit.KILOMETRES)
        val metres = units.display(DisplayUnit.METRES)
        val pins = details.markers.map { m ->
            ReplayEventPin(m, pinLabel(m, details.events[m.eventId]?.peakG, timeOfDay(format, details.startedAtMs, m.tPeakS)))
        }
        val maxText = details.maxSpeed?.let { "${speed.format(it.speedMps)} ${speed.symbol} max" } ?: ""
        val events = details.countedEvents
        val stops = details.stops.size
        return ReplayOverlay(
            details = details,
            units = units,
            eventPins = pins.filter { it.marker.pinned },
            ticks = pins,
            stopPins = details.stops.filter { it.pinned },
            maxSpeed = details.maxSpeed,
            summary = dot(maxText, count(stops, "stop"), count(events, "event")),
            elevationText = details.elevation?.let { e ->
                "↗ ${metres.format(e.gainM)} ${metres.symbol} ↘ ${metres.format(e.lossM)} ${metres.symbol}"
            },
            noEventsText = NO_EVENTS.takeIf { details.markers.isEmpty() },
            mapSummary = "Map of your drive, ${distance.format(details.distance.totalM)} ${distance.symbol}. " +
                "${count(events, "event")}, ${count(stops, "stop")}.",
        )
    }

    /** S-RPL-03 for [stop] of [of] stops. */
    public fun stopSheet(stop: StopPin, of: Int, startedAtMs: Long, format: ReplayTimeFormat): ReplayPinSheet {
        val seconds = stop.durationS.roundToInt()
        val (value, unit, spoken) = if (seconds < 2 * SECONDS_PER_MINUTE) {
            Triple("$seconds", "s", "$seconds seconds")
        } else {
            val minutes = (stop.durationS / SECONDS_PER_MINUTE).roundToInt()
            Triple("$minutes", "min", "$minutes minutes")
        }
        val provenance = Metric.STOPS.provenance()
        return ReplayPinSheet(
            kind = ReplayPinKind.STOP,
            title = "Stop",
            subtitle = timeOfDay(format, startedAtMs, stop.atS),
            value = value,
            unit = unit,
            provenance = provenance,
            rows = listOf(ReplayPinRow("Into the drive", clock(stop.atS)), ReplayPinRow("Stop", "${stop.number} of $of")),
            note = STOP_NOTE,
            tS = stop.atS,
            accessibilityLabel = "Stopped for $spoken. ${provenance.label}.",
        )
    }

    /** S-RPL-03 for the max speed: plain text, no emphasis (D5/D6). */
    public fun maxSpeedSheet(pin: MaxSpeedPin, units: UnitSystem, startedAtMs: Long, format: ReplayTimeFormat): ReplayPinSheet {
        val unit = units.display(DisplayUnit.KM_PER_HOUR)
        val value = unit.format(pin.speedMps)
        val provenance = Metric.MAX_SPEED.provenance()
        return ReplayPinSheet(
            kind = ReplayPinKind.MAX_SPEED,
            title = "Max speed",
            subtitle = dot("${clock(pin.tS)} into the drive", timeOfDay(format, startedAtMs, pin.tS)),
            value = value,
            unit = unit.symbol,
            provenance = provenance,
            rows = emptyList(),
            note = MAX_NOTE,
            tS = pin.tS,
            accessibilityLabel = "Max speed $value ${spoken(unit.symbol)}. ${provenance.label}.",
        )
    }
}
