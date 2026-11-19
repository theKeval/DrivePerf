package com.driveperf.presentation.drives

import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.LONG_GAP_LABEL
import com.driveperf.domain.trip.OverlayStyle
import com.driveperf.domain.trip.Provenance
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.QualityReason
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.SegmentReason
import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripSegment
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S-TRP-01 and S-DQ-01: DRIVE-170 (badge), DRIVE-172 (provenance), DRIVE-100 (question, corrections), DRIVE-109. */
class TripSheetTest {
    private val sheet = TripSheet(UnitSystem.METRIC, JavaTimeFormatter())
    private val quality = QualitySheet(UnitSystem.METRIC)

    @Test
    fun `tiles use the shared catalogue for provenance and precision`() {
        val tiles = sheet.tiles(trip(estimatedM = 429.0))
        assertEquals(listOf("Distance", "Duration", "Moving", "Stops", "Max speed"), tiles.map { it.label })
        val distance = tiles[0]
        assertEquals("39.0", distance.value)
        assertEquals("km", distance.unit)
        assertEquals(Provenance.ESTIMATED, distance.provenance)
        assertTrue(distance.estimated)
        assertEquals("≈ 0.4 km estimated", distance.note)
        assertEquals("33", tiles[1].value)
        assertEquals("min", tiles[1].unit)
        assertEquals("103", tiles[4].value)
        assertEquals(Provenance.DERIVED, tiles[4].provenance)
        val plain = sheet.tiles(trip())[0]
        assertEquals(Provenance.DERIVED, plain.provenance)
        assertNull(plain.note)
        assertEquals("Distance, 39.0 kilometres. Derived.", plain.accessibilityLabel)
    }

    @Test
    fun `every stat the core computed gets a tile, in components md order`() {
        val full = trip().copy(stoppedS = 280.0, avgMovingSpeedMps = 22.9, elevGainM = 41.0, elevLossM = 37.4, movingS = 3_700.0)
        val tiles = sheet.tiles(full)
        assertEquals(
            listOf("Distance", "Duration", "Moving", "Stopped", "Stops", "Avg moving speed", "Max speed", "Climb", "Descent"),
            tiles.map { it.label },
        )
        assertEquals("1 h 02", tiles[2].value)
        assertEquals("5", tiles[3].value)
        assertEquals("82", tiles[5].value)
        assertEquals(listOf("elevationGainLoss", "elevationLoss"), tiles.takeLast(2).map { it.key })
        assertEquals("37", tiles.last().value)
        assertEquals("m", tiles.last().unit)
        assertEquals(tiles.size, tiles.map { it.key }.toSet().size, "tile keys are unique")
    }

    @Test
    fun `imperial units convert tiles and the distance note`() {
        val tiles = TripSheet(UnitSystem.IMPERIAL, JavaTimeFormatter()).tiles(trip(estimatedM = 1_609.344))
        assertEquals("24.2", tiles[0].value)
        assertEquals("mi", tiles[0].unit)
        assertEquals("≈ 1.0 mi estimated", tiles[0].note)
        assertEquals("64", tiles[4].value)
        assertEquals("Max speed, 64 miles per hour. Derived.", tiles[4].accessibilityLabel)
    }

    @Test
    fun `the data-quality sheet shows every AC2 row`() {
        val q = quality(
            QualityBadge.LIMITED, QualityReason.LOW_SCORED_SHARE,
            spans = listOf(
                TripSegment(SegmentKind.ESTIMATED, 0.0, 49.0, SegmentReason.ESTIMATED_START),
                TripSegment(SegmentKind.EXCLUDED, 900.0, 1_080.0, SegmentReason.PHONE_MOVED),
                TripSegment(SegmentKind.GAP, 1_899.0, 1_940.0, SegmentReason.NO_GNSS),
            ),
        )
        val s = quality.sheet(q)
        assertEquals("Limited data", s.title)
        assertEquals("Your phone moved around for about 3 minutes, so we left that part out of the score.", s.hero)
        assertEquals(
            listOf("Phone", "Sensor rate", "GPS coverage", "Phone alignment", "Left out of the score", "Estimated start"),
            s.rows.map { it.label },
        )
        assertEquals("Moved", s.rows[0].value)
        assertTrue(s.rows[0].lowersBadge)
        assertEquals("50", s.rows[1].value)
        assertEquals(Provenance.OBSERVED, s.rows[1].provenance)
        assertFalse(s.rows[1].lowersBadge)
        assertEquals("97", s.rows[2].value)
        assertEquals("%", s.rows[2].unit)
        assertEquals("Settled after 40 s", s.rows[3].value)
        assertEquals("3", s.rows[4].value)
        assertEquals("Phone moved 3 min", s.rows[4].note)
        assertTrue(s.rows[4].lowersBadge)
        assertEquals("0.4", s.rows[5].value)
        assertTrue(s.rows[5].estimated)
        assertEquals("Scored 31 of 33 min", s.scoredLine)
        assertEquals(listOf(OverlayStyle.ESTIMATED_START, OverlayStyle.EXCLUDED, OverlayStyle.SHORT_GAP), s.coverage.map { it.style })
        assertEquals(49.0 / 1_980, s.coverage[0].to, 1e-9)
        assertEquals(
            "Drive coverage, 33 minutes. Start estimated. 41 seconds without GPS. 3 minutes left out of the score.",
            s.coverageDescription,
        )
        assertEquals(Provenance.entries, s.legend.map { it.provenance })
        assertEquals("Calculated from measured data, the same way every time.", s.legend[1].meaning)
        assertEquals("Derived", s.legend[1].label)
        assertNotNull(s.estimatedStart).let { assertTrue(it.startsWith("The first ≈ 0.4 km is estimated.")) }
    }

    @Test
    fun `a clean drive leaves nothing out and has no estimated start`() {
        val s = quality.sheet(quality(QualityBadge.GOOD, estimatedStartM = null).copy(alignmentSettledS = null))
        assertEquals("Nothing", s.rows[4].value)
        assertEquals("", s.rows[4].unit)
        assertNull(s.rows[4].note)
        assertEquals("Didn’t settle", s.rows[3].value)
        assertEquals(5, s.rows.size)
        assertNull(s.estimatedStart)
        assertEquals("Drive coverage, 33 minutes.", s.coverageDescription)
        assertTrue(quality.sheet(quality(QualityBadge.GOOD).copy(totalS = 0.0)).coverage.isEmpty())
    }

    @Test
    fun `each badge has its own sentence`() {
        assertTrue(quality.sheet(quality(QualityBadge.GOOD)).hero.startsWith("Everything"))
        val gap = listOf(TripSegment(SegmentKind.GAP, 10.0, 51.0, SegmentReason.NO_GNSS))
        assertEquals(
            "Mostly solid, with 41 s without GPS. The score is fair; that stretch is drawn dotted.",
            quality.sheet(quality(QualityBadge.USABLE, spans = gap)).hero,
        )
        assertEquals("Mostly solid. The score is fair.", quality.sheet(quality(QualityBadge.USABLE)).hero)
        assertTrue(quality.sheet(quality(QualityBadge.USABLE, imuHz = 25.0)).hero.startsWith("Battery saver: sensors at 25 Hz"))
        assertEquals(
            "We couldn’t score this one fairly. No motion sensor data reached the app.",
            quality.sheet(quality(QualityBadge.UNSCORABLE, QualityReason.NO_IMU)).hero,
        )
        assertEquals(
            "Part of this drive couldn’t be scored fairly, so the score is based on 31 of 33 min.",
            quality.sheet(quality(QualityBadge.LIMITED, QualityReason.LOW_GNSS)).hero,
        )
        val long = listOf(TripSegment(SegmentKind.GAP, 400.0, 640.0, SegmentReason.NO_GNSS, LONG_GAP_LABEL))
        assertEquals(OverlayStyle.LONG_GAP, quality.sheet(quality(QualityBadge.USABLE, spans = long)).coverage.single().style)
    }

    @Test
    fun `the rows that lowered the badge are marked`() {
        fun lowered(reason: QualityReason) =
            quality.sheet(quality(QualityBadge.LIMITED, reason)).rows.filter { it.lowersBadge }.map { it.key }
        assertEquals(listOf("imuRate"), lowered(QualityReason.LOW_IMU_RATE))
        assertEquals(listOf("imuRate"), lowered(QualityReason.NO_IMU))
        assertEquals(listOf("gnssCoverage"), lowered(QualityReason.LOW_GNSS))
        assertEquals(listOf("gnssCoverage"), lowered(QualityReason.NO_PRECISE_LOCATION))
        assertEquals(listOf("alignment"), lowered(QualityReason.NOT_ALIGNED))
        assertEquals(listOf("excludedTime"), lowered(QualityReason.TOO_LITTLE_SCORED_TIME))
        assertEquals(emptyList(), lowered(QualityReason.NO_GYRO))
    }

    @Test
    fun `banners ask the question and explain interruptions and corrections`() {
        val asked = sheet.banners(trip(suspect = Suspect.TRANSIT)).first()
        assertEquals(BannerAction.Answer(Suspect.TRANSIT), asked.action)
        assertEquals("Was this a bus or train? Yes takes it out of your scores and history.", asked.text)
        assertEquals(
            "Was this a bike ride? Yes takes it out of your scores and history.",
            sheet.banners(trip(suspect = Suspect.CYCLING)).single().text,
        )
        val interrupted = sheet.banners(trip(status = TripStatus.INTERRUPTED)).single()
        assertTrue(interrupted.attention)
        assertTrue(interrupted.text.startsWith("Interrupted at 6:27"), interrupted.text)
        assertTrue(interrupted.text.contains(". Android stopped DrivePerf in the background. Everything before then is saved."))
        assertTrue(interrupted.text.endsWith("The missing part is shown as a gap, never scored."))
        val bus = sheet.banners(trip(kind = TripKind.TRANSIT, correction = TripCorrection.TRANSIT)).single()
        assertEquals(BannerAction.Restore, bus.action)
        assertEquals(
            "Marked as a bus or train ride. It’s out of your scores and history and kept under Not drives. " +
                "It will be deleted on December 5, 2025.",
            bus.text,
        )
        assertEquals(BannerAction.Restore, sheet.banners(trip(kind = TripKind.PASSENGER)).single().action)
        fun marked(c: TripCorrection) = sheet.banners(trip(kind = TripKind.NOT_DRIVING, correction = c)).single().text
        assertTrue(marked(TripCorrection.WALK).startsWith("Marked as a walk."))
        assertTrue(marked(TripCorrection.BIKE).startsWith("Marked as a bike ride."))
        assertTrue(marked(TripCorrection.OTHER).startsWith("Marked as not a drive."))
    }

    @Test
    fun `iOS names itself in the interrupted banner`() {
        val ios = TripSheet(UnitSystem.METRIC, JavaTimeFormatter(platform = DrivesPlatform.IOS))
        val text = ios.banners(trip(status = TripStatus.INTERRUPTED, endedAt = null)).single().text
        assertTrue(text.contains(". iOS ended DrivePerf in the background. "), text)
        assertTrue(text.startsWith("Interrupted at 6:27"), "no end time: start plus duration")
    }

    @Test
    fun `short manual drives and Unscorable drives explain themselves`() {
        val short = sheet.banners(trip(source = TripSource.MANUAL, distanceM = 300.0, durationS = 200.0)).single()
        assertEquals(
            "Short drive · 0.3 km. Short drives give the score less to work with, so expect it to move more from drive to drive.",
            short.text,
        )
        assertFalse(short.attention)
        val unscorable = sheet.banners(trip(quality = quality(QualityBadge.UNSCORABLE, QualityReason.NO_GYRO))).single()
        assertTrue(unscorable.attention)
        assertEquals(
            "We couldn’t score this one fairly. This phone has no gyroscope. Here’s the route and the stats anyway.",
            unscorable.text,
        )
    }

    @Test
    fun `the detail carries the answer to What was this`() {
        assertEquals(TripCorrection.DRIVER, sheet.detail(trip(), "T", "S").correction)
        assertEquals(TripCorrection.PASSENGER, sheet.detail(trip(kind = TripKind.PASSENGER), "T", "S").correction)
        val walked = trip(kind = TripKind.NOT_DRIVING, correction = TripCorrection.WALK, quality = quality(QualityBadge.USABLE))
        val walk = sheet.detail(walked, "T", "S")
        assertEquals(TripCorrection.WALK, walk.correction)
        assertEquals("Usable", walk.qualityWord)
        assertEquals("S", walk.subtitle)
        assertNull(sheet.detail(trip(), "T", "S").qualityWord)
    }

    @Test
    fun `copy covers every reason and correction`() {
        assertEquals(
            listOf("I was driving", "I was a passenger", "Bus or train", "Bike ride", "Walk", "Something else"),
            TripCopy.correctionOptions.map { it.title },
        )
        assertEquals(
            setOf(
                "Phone moved", "Lining up", "Slow sensors", "Sensor gap", "App stopped", "Low battery", "Phone too warm", "Paused",
                "No GPS", "Estimated start",
            ),
            SegmentReason.entries.map(TripCopy::reasonWord).toSet(),
        )
        assertEquals(QualityReason.entries.size + 1, (QualityReason.entries + null).map(TripCopy::reasonText).toSet().size)
        assertEquals(listOf("Good", "Usable", "Limited", "Unscorable"), QualityBadge.entries.map(TripCopy::badgeWord))
        assertEquals(
            listOf("kilometres per hour", "feet", "metres", "minutes", "percent", "hertz", "miles", "g"),
            listOf("km/h", "ft", "m", "min", "%", "Hz", "mi", "g").map(TripCopy::spokenUnit),
        )
        val stops = ProvenancedValue("stops", "Stops", "2", "", Provenance.DERIVED, "")
        assertEquals("Stops, 2. Derived.", stops.accessibilityLabel)
        val start = quality.sheet(quality(QualityBadge.GOOD)).rows.last()
        assertEquals("Estimated start, about 0.4 kilometres. Estimated.", start.accessibilityLabel)
    }
}
