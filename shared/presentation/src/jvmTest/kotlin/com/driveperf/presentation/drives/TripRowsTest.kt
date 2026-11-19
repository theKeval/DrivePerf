package com.driveperf.presentation.drives

import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S-HOME-01 and S-HIS-01 trip cards and weeks (components.md §12). */
class TripRowsTest {
    /** Tuesday 10 Nov 2026, 18:00 in Toronto. */
    private val now = toronto(2026, 11, 10, 18)
    private val rows = TripRows(UnitSystem.METRIC, JavaTimeFormatter(), now)

    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0) = toronto(2026, month, day, hour, minute)

    @Test
    fun `cards show part of day, date, distance and duration`() {
        val row = rows.row(trip("a", startedAt = at(11, 5, 8, 14), durationS = 30 * 60.0, distanceM = 37_940.0))
        assertEquals("Thursday morning", row.title)
        assertTrue(row.subtitle.startsWith("5 Nov · 8:14"), row.subtitle)
        assertEquals("37.9 km", row.distance)
        assertEquals("30 min", row.duration)
        assertEquals("37.9 km · 30 min", row.stats)
        assertNull(row.note)
        assertEquals("Thursday morning, 37.9 km, 30 minutes", row.accessibilityLabel)
        assertFalse(row.dimmed)
        assertEquals("Monday midday", rows.title(trip(startedAt = at(11, 9, 12, 30))))
        assertEquals("Monday afternoon", rows.title(trip(startedAt = at(11, 9, 14))))
        assertEquals("Monday evening", rows.title(trip(startedAt = at(11, 9, 20, 59))))
        assertEquals("Monday night", rows.title(trip(startedAt = at(11, 9, 21))))
        assertEquals("Monday night", rows.title(trip(startedAt = at(11, 9, 4, 59))))
    }

    @Test
    fun `durations are whole minutes, in hours from an hour`() {
        assertEquals("25 min", durationText(25 * 60 + 10.0))
        assertEquals("1 h 05 min", durationText(3_900.0))
        assertEquals("< 1 min", durationText(20.0))
        assertEquals("0 min", durationText(0.0))
        assertEquals("less than a minute", spokenDuration(20.0))
        assertEquals("1 minute", spokenDuration(60.0))
        assertEquals("1 hour 5 minutes", spokenDuration(3_900.0))
        assertEquals("2 hours", spokenDuration(7_200.0))
        assertEquals("1 hour 1 minute", spokenDuration(3_660.0))
        assertEquals("0 minutes", spokenDuration(0.0))
    }

    @Test
    fun `units follow the user's choice`() {
        val imperial = TripRows(UnitSystem.IMPERIAL, JavaTimeFormatter(), now).row(trip(distanceM = 1_609.344))
        assertEquals("1.0 mi", imperial.distance)
    }

    @Test
    fun `cards carry the badge, tags, question and a note`() {
        val usable = rows.row(trip("a", status = TripStatus.INTERRUPTED, quality = quality(QualityBadge.USABLE)))
        assertEquals(QualityBadge.USABLE, usable.quality)
        assertEquals("Usable", usable.qualityWord)
        assertTrue(usable.interrupted)
        assertTrue(usable.note!!.startsWith("Android stopped DrivePerf in the background. Saved up to 6:27"), usable.note)
        assertTrue(usable.accessibilityLabel.endsWith("Usable data"))
        val passenger = rows.row(trip("b", kind = TripKind.PASSENGER))
        assertEquals("Passenger", passenger.kindLabel)
        assertTrue(passenger.dimmed)
        assertEquals(Suspect.CYCLING, rows.row(trip("c", suspect = Suspect.CYCLING)).question)
        assertNull(rows.row(trip("c", suspect = Suspect.CYCLING, kind = TripKind.PASSENGER)).question)
        val short = rows.row(trip("d", source = TripSource.MANUAL, distanceM = 300.0))
        assertTrue(short.short)
        assertEquals("Short trip · not scored", short.note)
        assertEquals("Started by you", rows.row(trip("e", source = TripSource.MANUAL)).note)
        val ios = TripRows(UnitSystem.METRIC, JavaTimeFormatter(platform = DrivesPlatform.IOS), now)
        assertTrue(ios.row(trip(status = TripStatus.INTERRUPTED)).note!!.startsWith("iOS ended DrivePerf in the background."))
    }

    @Test
    fun `only manual trips under 500 m or 2 minutes are short`() {
        assertEquals(ListedStatus.FINALISED, trip().listedStatus)
        assertEquals(ListedStatus.INTERRUPTED, trip(status = TripStatus.INTERRUPTED).listedStatus)
        assertEquals(ListedStatus.SHORT, trip(source = TripSource.MANUAL, distanceM = 300.0).listedStatus)
        assertEquals(ListedStatus.SHORT, trip(source = TripSource.MANUAL, durationS = 90.0).listedStatus)
        assertEquals(ListedStatus.FINALISED, trip(source = TripSource.MANUAL).listedStatus)
        assertEquals(ListedStatus.FINALISED, trip(distanceM = 300.0).listedStatus, "automatic trips are never short")
        assertEquals(
            ListedStatus.INTERRUPTED,
            trip(source = TripSource.MANUAL, distanceM = 300.0, status = TripStatus.INTERRUPTED).listedStatus,
        )
    }

    @Test
    fun `every correction has a kind label`() {
        fun label(c: TripCorrection?, kind: TripKind = TripKind.NOT_DRIVING) = rows.row(trip(kind = kind, correction = c)).kindLabel
        assertEquals("Bus or train", label(TripCorrection.TRANSIT, TripKind.TRANSIT))
        assertEquals("Bike ride", label(TripCorrection.BIKE))
        assertEquals("Walk", label(TripCorrection.WALK))
        assertEquals("Not a drive", label(TripCorrection.OTHER))
        assertEquals("Passenger", label(TripCorrection.PASSENGER, TripKind.PASSENGER))
        assertNull(label(TripCorrection.DRIVER, TripKind.DRIVER))
        assertNull(label(null, TripKind.DRIVER))
    }

    @Test
    fun `sections group by week, newest first`() {
        val trips = listOf(
            trip("this", startedAt = at(11, 9, 8)),
            trip("last", startedAt = at(11, 5, 8)),
            trip("older", startedAt = at(10, 21, 8)),
            trip("cross", startedAt = at(10, 29, 8)),
        ).sortedByDescending { it.startedAt }
        val sections = rows.sections(trips, trips.map(rows::row))
        assertEquals(listOf("This week", "Last week", "26 Oct – 1 Nov", "19–25 Oct"), sections.map { it.title })
        assertEquals("1 drive", sections.first().meta)
        assertEquals(listOf("this"), sections.first().rows.map { it.id })
        assertEquals(sections.size, sections.map { it.id }.toSet().size)
        assertEquals("3 drives", drivesCount(3))
        assertEquals("That’s every drive since 21 October.", rows.since(trips.last()))
    }

    @Test
    fun `weeks follow the locale's first weekday`() {
        val sunday = TripRows(UnitSystem.METRIC, JavaTimeFormatter(firstDay = DayOfWeek.SUNDAY), now)
        // Sunday 8 Nov starts this week when weeks start on Sunday, and belongs to last week when they start on Monday.
        val trips = listOf(trip("sun", startedAt = at(11, 8, 10)))
        assertEquals("This week", sunday.sections(trips, trips.map(sunday::row)).single().title)
        assertEquals("Last week", rows.sections(trips, trips.map(rows::row)).single().title)
    }
}
