package com.driveperf.feature.drives

import app.cash.turbine.test
import com.driveperf.domain.trip.LONG_GAP_LABEL
import com.driveperf.domain.trip.OverlayStyle
import com.driveperf.domain.trip.Placement
import com.driveperf.domain.trip.Provenance
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.QualityReason
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.SegmentReason
import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripQuality
import com.driveperf.domain.trip.TripSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/** DRIVE-170 (badge and S-DQ-01), DRIVE-172 (provenance), DRIVE-100 (question, corrections, Not drives), DRIVE-109. */
@OptIn(ExperimentalCoroutinesApi::class)
class TripQualityTest {
    private val start = 1_762_340_040_000L
    private val presenter = TripPresenter(ZoneOffset.UTC, Locale.CANADA)

    private val source = object : TripListSource {
        val flow = MutableStateFlow<List<RecordedTrip>>(emptyList())
        override val trips = flow
    }

    private val corrector = object : TripCorrector {
        val flow = MutableStateFlow<List<RecordedTrip>>(emptyList())
        val answers = mutableListOf<Pair<String, Boolean>>()
        val corrections = mutableListOf<Pair<String, TripCorrection>>()
        override val notDrives = flow
        override suspend fun answer(tripId: String, yes: Boolean) {
            answers += tripId to yes
        }

        override suspend fun correct(tripId: String, to: TripCorrection) {
            corrections += tripId to to
        }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun quality(
        badge: QualityBadge,
        reason: QualityReason? = null,
        imuHz: Double = 50.0,
        spans: List<TripSegment> = emptyList(),
    ) = TripQuality(badge, reason, Placement.MOUNTED, imuHz, 96.6, 40.0, 429.0, 1_860.0, 120.0, 1_980.0, spans)

    @Suppress("LongParameterList") // one named knob per trip state the tests vary
    private fun trip(
        id: String = "t",
        status: RecordedStatus = RecordedStatus.FINALISED,
        quality: TripQuality? = null,
        question: Suspect? = null,
        kind: TripKind = TripKind.DRIVER,
        correction: TripCorrection? = null,
        estimatedM: Double = 0.0,
    ) = RecordedTrip(
        id, start, 1_980, 38_950.0, status, start + 1_980_000, quality, question, kind, correction,
        correction?.let { start }, estimatedM, movingS = 1_700.0, stopCount = 2, maxSpeedMps = 28.6,
    )

    @Test
    fun `tiles use the shared catalogue for provenance and precision`() {
        val tiles = presenter.tiles(trip(estimatedM = 429.0))
        assertEquals(listOf("Distance", "Duration", "Moving", "Stops", "Max speed"), tiles.map { it.label })
        val distance = tiles[0]
        assertEquals("39.0", distance.value)
        assertEquals("km", distance.unit)
        assertEquals(Provenance.ESTIMATED, distance.provenance)
        assertTrue(distance.estimated)
        assertEquals("≈ 0.4 km estimated", distance.note)
        assertEquals("33", tiles[1].value)
        assertEquals("103", tiles[4].value)
        assertEquals(Provenance.DERIVED, presenter.tiles(trip())[0].provenance)
        assertEquals("Distance, 39.0 km. Derived.", presenter.tiles(trip())[0].contentDescription)
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
        val sheet = checkNotNull(presenter.quality(trip(quality = q)))
        assertEquals("Limited data", sheet.title)
        assertEquals("Your phone moved around for about 3 minutes, so we left that part out of the score.", sheet.hero)
        assertEquals(
            listOf("Phone", "Sensor rate", "GPS coverage", "Phone alignment", "Left out of the score", "Estimated start"),
            sheet.rows.map { it.label },
        )
        assertEquals("Moved", sheet.rows[0].value)
        assertTrue(sheet.rows[0].lowersBadge)
        assertEquals("50", sheet.rows[1].value)
        assertEquals(Provenance.OBSERVED, sheet.rows[1].provenance)
        assertEquals("97", sheet.rows[2].value)
        assertEquals("3", sheet.rows[4].value)
        assertEquals("Phone moved 3 min", sheet.rows[4].note)
        assertTrue(sheet.rows[4].lowersBadge)
        assertEquals("0.4", sheet.rows[5].value)
        assertTrue(sheet.rows[5].estimated)
        assertEquals("Scored 31 of 33 min", sheet.scoredLine)
        assertEquals(listOf(OverlayStyle.ESTIMATED_START, OverlayStyle.EXCLUDED, OverlayStyle.SHORT_GAP), sheet.coverage.map { it.style })
        assertEquals(
            "Drive coverage, 33 minutes. Start estimated. 41 seconds without GPS. 3 minutes left out of the score.",
            sheet.coverageDescription,
        )
        assertEquals(Provenance.entries, sheet.legend)
        assertNotNull(sheet.estimatedStart)
    }

    @Test
    fun `each badge has its own sentence`() {
        assertTrue(presenter.quality(trip(quality = quality(QualityBadge.GOOD)))!!.hero.startsWith("Everything"))
        val gap = listOf(TripSegment(SegmentKind.GAP, 10.0, 51.0, SegmentReason.NO_GNSS))
        assertEquals(
            "Mostly solid, with 41 s without GPS. The score is fair; that stretch is drawn dotted.",
            presenter.quality(trip(quality = quality(QualityBadge.USABLE, spans = gap)))!!.hero,
        )
        val battery = presenter.quality(trip(quality = quality(QualityBadge.USABLE, imuHz = 25.0)))!!
        assertTrue(battery.hero.startsWith("Battery saver: sensors at 25 Hz"))
        assertEquals(
            "We couldn’t score this one fairly. No motion sensor data reached the app.",
            presenter.quality(trip(quality = quality(QualityBadge.UNSCORABLE, QualityReason.NO_IMU)))!!.hero,
        )
        val long = listOf(TripSegment(SegmentKind.GAP, 400.0, 640.0, SegmentReason.NO_GNSS, LONG_GAP_LABEL))
        val labelled = presenter.quality(trip(quality = quality(QualityBadge.USABLE, spans = long)))!!
        assertEquals(OverlayStyle.LONG_GAP, labelled.coverage.single().style)
        assertNull(presenter.quality(trip()))
    }

    @Test
    fun `banners ask the question and explain interruptions and corrections`() {
        assertEquals(BannerAction.Answer(Suspect.TRANSIT), presenter.banners(trip(question = Suspect.TRANSIT)).first().action)
        val interrupted = presenter.banners(trip(status = RecordedStatus.INTERRUPTED)).single()
        assertTrue(interrupted.attention)
        assertTrue(interrupted.text.endsWith("The missing part is shown as a gap, never scored."))
        val bus = presenter.banners(trip(kind = TripKind.TRANSIT, correction = TripCorrection.TRANSIT)).single()
        assertEquals(BannerAction.Restore, bus.action)
        assertTrue(bus.text, bus.text.contains("deleted on December 5, 2025"))
        assertEquals(BannerAction.Restore, presenter.banners(trip(kind = TripKind.PASSENGER)).single().action)
    }

    @Test
    fun `rows carry the badge, tags and question, and actions reach the core`() = runTest {
        source.flow.value = listOf(
            trip("a", RecordedStatus.INTERRUPTED, quality(QualityBadge.USABLE)),
            trip("b", kind = TripKind.PASSENGER),
            trip("c", question = Suspect.CYCLING),
            trip("d", RecordedStatus.SHORT),
        )
        corrector.flow.value = listOf(trip("bus", kind = TripKind.TRANSIT, correction = TripCorrection.TRANSIT))
        val vm = DrivesViewModel(source, ZoneOffset.UTC, Locale.CANADA, { LocalDate.of(2026, 11, 10) }, corrector)
        vm.state.test {
            val state = expectMostRecentItem()
            assertTrue(state.canCorrect)
            val rows = state.trips.associateBy { it.id }
            assertEquals(QualityBadge.USABLE, rows.getValue("a").quality)
            assertTrue(rows.getValue("a").interrupted)
            assertEquals("Passenger", rows.getValue("b").kindLabel)
            assertEquals(Suspect.CYCLING, rows.getValue("c").question)
            assertTrue(rows.getValue("d").short)
            assertEquals("Bus or train", state.notDrives.single().kindLabel)

            vm.onAction(DrivesAction.Filter(DrivesFilter.NOT_DRIVES))
            assertEquals(listOf("bus"), awaitItem().shown.map { it.id })
            vm.onAction(DrivesAction.Open("bus"))
            assertEquals(TripCorrection.TRANSIT, awaitItem().detail?.correction)
            vm.onAction(DrivesAction.OpenQuality("a"))
            assertEquals(QualityBadge.USABLE, awaitItem().quality?.badge)
            vm.onAction(DrivesAction.CloseDetail)
            val closed = awaitItem()
            assertNull(closed.detail)
            assertNull(closed.quality)
        }
        vm.onAction(DrivesAction.Answer("c", true))
        vm.onAction(DrivesAction.Correct("a", TripCorrection.WALK))
        assertEquals(listOf("c" to true), corrector.answers)
        assertEquals(listOf("a" to TripCorrection.WALK), corrector.corrections)
    }

    @Test
    fun `without a corrector nothing can be corrected`() = runTest {
        DrivesViewModel(source, ZoneOffset.UTC, Locale.CANADA).state.test {
            val state = expectMostRecentItem()
            assertFalse(state.canCorrect)
            assertTrue(state.notDrives.isEmpty())
        }
    }
}
