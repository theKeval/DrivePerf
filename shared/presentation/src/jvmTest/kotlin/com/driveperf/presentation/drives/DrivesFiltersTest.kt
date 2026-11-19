package com.driveperf.presentation.drives

import com.driveperf.domain.route.RoutePreset
import com.driveperf.domain.score.RouteName
import com.driveperf.domain.score.ScoreLine
import com.driveperf.domain.score.ScoreLines
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.domain.trip.TripKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.system.measureTimeMillis
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S-HIS-01's filter chips and S-HIS-02 Filter sheet (DRIVE-126 AC3, F-HIS-01 AC3). */
@OptIn(ExperimentalCoroutinesApi::class)
class DrivesFiltersTest {
    private val trips = MutableStateFlow<List<Trip>>(emptyList())
    private val corrections = FakeCorrections()
    private val reader = FakeScoreReader()
    private val analytics = RecordingAnalytics()
    private val clock = FixedClock(toronto(2026, 11, 10, 18))
    private val vehicles = FakeTripVehicles(trips)

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.presenter() = DrivesPresenter(
        trips, corrections, MutableStateFlow(UnitSystem.METRIC), JavaTimeFormatter(), clock, backgroundScope, reader, analytics,
        TripEditPorts(vehicles, FakeTripDeletes(trips)),
    )

    private fun DrivesPresenter.filter(action: HistoryFilterAction) = dispatch(DrivesIntent.History(action))

    private val commute = RouteName("r1", null, RoutePreset.COMMUTE)
    private val gym = RouteName("r2", "Gym run", null)
    private val unnamed = RouteName("r3", null, null)

    private fun line(id: String, route: RouteName?) = id to ScoreLine(id, 80, 1, null, false, route, null)

    /** Six drives over two cars and three routes, one passenger trip, and one bus ride under "Not drives". */
    private fun given() {
        fun t(id: String, day: Int, car: String?, badge: QualityBadge, kind: TripKind = TripKind.DRIVER) =
            trip(id, startedAt = toronto(2026, 11, day, 8), quality = quality(badge), kind = kind).copy(vehicleId = car)
        trips.value = listOf(
            t("a", 10, "gti", QualityBadge.GOOD),
            t("b", 9, "gti", QualityBadge.GOOD),
            t("c", 8, "ioniq", QualityBadge.USABLE),
            t("d", 7, "gti", QualityBadge.LIMITED),
            t("e", 6, "ioniq", QualityBadge.GOOD),
            t("p", 5, null, QualityBadge.GOOD, TripKind.PASSENGER),
        )
        corrections.notDrives.value = listOf(
            trip("bus", startedAt = toronto(2026, 11, 4, 8), kind = TripKind.TRANSIT, correction = TripCorrection.TRANSIT,
                quality = quality(QualityBadge.USABLE)),
        )
        reader.lines.value = ScoreLines(
            mapOf(line("a", commute), line("b", commute), line("c", commute), line("d", gym), line("e", unnamed), line("p", null)),
            carCount = 2,
        )
    }

    private fun DrivesState.shownIds() = sections.flatMap { s -> s.rows.map { it.id } }

    @Test
    fun `without filters the list is every drive and the quick filters are offered`() = runTest(UnconfinedTestDispatcher()) {
        given()
        val p = presenter()
        val state = p.state.value
        assertEquals(listOf("a", "b", "c", "d", "e", "p"), state.shownIds())
        assertFalse(state.history.active)
        assertTrue(state.history.applied.isEmpty())
        assertNull(state.history.sheet)
        assertNull(state.history.subtitle)
        assertEquals(listOf("GTI", "Ioniq", "The Commute", "Gym run", "Passenger"), state.history.suggestions.map { it.label })
        assertEquals("GTI, 3 drives", state.history.suggestions.first().accessibilityLabel)

        // Clearing the last filter brings them back.
        p.filter(HistoryFilterAction.Toggle(FilterKey(FilterGroup.CAR, "gti")))
        p.filter(HistoryFilterAction.Toggle(FilterKey(FilterGroup.CAR, "gti")))
        assertFalse(p.state.value.history.active)
        assertEquals(5, p.state.value.history.suggestions.size)
    }

    @Test
    fun `quick filters leave out cars without drives`() = runTest(UnconfinedTestDispatcher()) {
        trips.value = listOf(trip("a").copy(vehicleId = "gti"), trip("b", startedAt = START - 86_400_000L).copy(vehicleId = "gti"))
        val p = presenter()
        // Both cars are in the garage, but the Ioniq has no drives, so only the GTI is offered.
        assertEquals(listOf("GTI"), p.state.value.history.suggestions.map { it.label })
    }

    @Test
    fun `the sheet lists every group with live counts and applies its choices as chips`() = runTest(UnconfinedTestDispatcher()) {
        given()
        val p = presenter()
        p.filter(HistoryFilterAction.OpenSheet)

        val sheet = assertNotNull(p.state.value.history.sheet)
        assertEquals("Filter drives", sheet.title)
        assertEquals("All 6 drives", sheet.subtitle)
        assertEquals("Show 6 drives", sheet.showLabel)
        assertFalse(sheet.canReset)
        assertEquals(listOf("Trip type", "Car", "Route", "Data quality"), sheet.groups.map { it.title })
        val type = sheet.groups[0].chips
        assertEquals(listOf("Driver", "Passenger", "Bus or train", "Taxi or rideshare", "Not a drive"), type.map { it.label })
        assertEquals(listOf(5, 1, 1, 0, 0), type.map { it.count })
        // Counts cover every listed trip: the bus ride under "Not drives" has no car either.
        assertEquals(listOf("GTI" to 3, "Ioniq" to 2, "No car" to 2), sheet.groups[1].chips.map { it.label to it.count })
        assertEquals(
            listOf("The Commute" to 3, "Gym run" to 1, "Route 1" to 1, "Not on a route" to 2),
            sheet.groups[2].chips.map { it.label to it.count },
        )
        assertEquals(listOf(4, 2, 1, 0), sheet.groups[3].chips.map { it.count })
        assertEquals(QualityBadge.GOOD, sheet.groups[3].chips[0].quality)
        assertEquals("The Commute, 3 drives, not selected", sheet.groups[2].chips[0].accessibilityLabel)

        p.filter(HistoryFilterAction.ToggleDraft(FilterKey(FilterGroup.ROUTE, "r1")))
        p.filter(HistoryFilterAction.ToggleDraft(FilterKey.quality(QualityBadge.GOOD)))
        val chosen = assertNotNull(p.state.value.history.sheet)
        assertEquals("2 filters · 2 drives", chosen.subtitle)
        assertEquals("Show 2 drives", chosen.showLabel)
        assertTrue(chosen.canReset)
        assertTrue(chosen.groups[2].chips[0].selected)
        // Nothing applies until "Show".
        assertEquals(6, p.state.value.shownIds().size)

        p.filter(HistoryFilterAction.Apply)

        val history = p.state.value.history
        assertNull(history.sheet)
        assertEquals(listOf("a", "b"), p.state.value.shownIds())
        assertEquals(2, history.activeCount)
        assertEquals("Filters, 2 applied", history.buttonAccessibilityLabel)
        assertEquals("2 drives · filtered", history.subtitle)
        assertEquals(listOf("The Commute", "Good data"), history.applied.map { it.label }.sorted().reversed())
        assertTrue(history.applied.all { it.selected })
        assertTrue(history.applied.any { it.accessibilityLabel == "Remove filter The Commute" })
        assertEquals(
            listOf(mapOf("filter" to "route", "count_bucket" to "2_3"), mapOf("filter" to "quality", "count_bucket" to "2_3")),
            analytics.named("history_filtered"),
        )
        assertEquals(1, analytics.named("history_filter_opened").size)
    }

    @Test
    fun `choices widen within a group and narrow across groups`() = runTest(UnconfinedTestDispatcher()) {
        given()
        val p = presenter()
        p.filter(HistoryFilterAction.Toggle(FilterKey(FilterGroup.CAR, "gti")))
        assertEquals(listOf("a", "b", "d"), p.state.value.shownIds())
        p.filter(HistoryFilterAction.Toggle(FilterKey(FilterGroup.CAR, "ioniq")))
        assertEquals(listOf("a", "b", "c", "d", "e"), p.state.value.shownIds())
        p.filter(HistoryFilterAction.Toggle(FilterKey.quality(QualityBadge.USABLE)))
        assertEquals(listOf("c"), p.state.value.shownIds())
        p.filter(HistoryFilterAction.Toggle(FilterKey(FilterGroup.CAR, "ioniq")))
        assertTrue(p.state.value.shownIds().isEmpty())
        assertEquals("No drives match.", p.state.value.history.emptyTitle)
        assertEquals("0 drives · filtered", p.state.value.history.subtitle)
        // Only additions are recorded, never a name.
        assertEquals(listOf("car", "car", "quality"), analytics.named("history_filtered").map { it["filter"] })
        assertTrue(analytics.events.none { e -> e.properties.values.any { it.contains("GTI") || it.contains("Commute") } })

        p.filter(HistoryFilterAction.Clear)
        assertEquals(6, p.state.value.shownIds().size)
        assertFalse(p.state.value.history.active)
    }

    @Test
    fun `type filters reach passengers and the trips under Not drives`() = runTest(UnconfinedTestDispatcher()) {
        given()
        val p = presenter()
        p.filter(HistoryFilterAction.Toggle(FilterKey.type(TripEditType.PASSENGER)))
        assertEquals(listOf("p"), p.state.value.shownIds())
        p.filter(HistoryFilterAction.Toggle(FilterKey.type(TripEditType.TRANSIT)))
        assertEquals(listOf("p", "bus"), p.state.value.shownIds())
        p.filter(HistoryFilterAction.Toggle(FilterKey(FilterGroup.ROUTE, FilterKey.NONE)))
        assertEquals(listOf("p", "bus"), p.state.value.shownIds())
        // The Drives tab and the Not drives list are not filtered.
        assertEquals(6, p.state.value.trips.size)
        assertEquals(listOf("bus"), p.state.value.notDrives.map { it.id })
    }

    @Test
    fun `quick chips offer the cars, the most driven named routes and Passenger`() = runTest(UnconfinedTestDispatcher()) {
        given()
        val p = presenter()
        p.filter(HistoryFilterAction.Toggle(FilterKey(FilterGroup.CAR, "gti")))
        val history = p.state.value.history
        assertEquals(listOf("Ioniq", "The Commute", "Gym run", "Passenger"), history.suggestions.map { it.label })
        assertTrue(history.suggestions.none { it.selected })
        assertEquals(listOf("GTI"), history.applied.map { it.label })

        p.filter(HistoryFilterAction.Toggle(FilterKey(FilterGroup.CAR, "gti")))
        assertFalse(p.state.value.history.active)
    }

    @Test
    fun `reset clears the sheet and closing it applies nothing`() = runTest(UnconfinedTestDispatcher()) {
        given()
        val p = presenter()
        p.filter(HistoryFilterAction.Toggle(FilterKey(FilterGroup.CAR, "gti")))
        p.filter(HistoryFilterAction.OpenSheet)
        assertTrue(p.state.value.history.sheet!!.groups[1].chips[0].selected)
        p.filter(HistoryFilterAction.ResetDraft)
        assertEquals("All 6 drives", p.state.value.history.sheet?.subtitle)
        assertEquals(1, analytics.named("history_filter_reset").size)
        p.filter(HistoryFilterAction.ToggleDraft(FilterKey.type(TripEditType.TAXI)))
        val none = p.state.value.history.sheet!!
        assertEquals("No drives match", none.showLabel)
        assertFalse(none.canShow)

        p.filter(HistoryFilterAction.CloseSheet)
        assertNull(p.state.value.history.sheet)
        assertEquals(listOf("a", "b", "d"), p.state.value.shownIds())
    }

    @Test
    fun `one car hides the car group`() = runTest(UnconfinedTestDispatcher()) {
        trips.value = listOf(trip("a").copy(vehicleId = "gti"), trip("b", startedAt = START - 86_400_000L).copy(vehicleId = "gti"))
        vehicles.cars.value = vehicles.cars.value.take(1)
        val p = presenter()
        p.filter(HistoryFilterAction.OpenSheet)
        assertEquals(listOf("Trip type", "Data quality"), p.state.value.history.sheet!!.groups.map { it.title })
        assertTrue(p.state.value.history.suggestions.isEmpty())
    }

    @Test
    fun `a thousand drives filter well under a frame budget per change`() = runTest(UnconfinedTestDispatcher()) {
        trips.value = (0 until 1_000).map { n ->
            trip("t$n", startedAt = START - n * 3_600_000L, quality = quality(QualityBadge.entries[n % 4]))
                .copy(vehicleId = if (n % 2 == 0) "gti" else "ioniq")
        }
        reader.lines.value = ScoreLines(trips.value.associate { line(it.id, if (it.id.last() == '0') commute else null) }, 2)
        val p = presenter()
        val ms = measureTimeMillis {
            p.filter(HistoryFilterAction.Toggle(FilterKey(FilterGroup.CAR, "gti")))
            p.filter(HistoryFilterAction.Toggle(FilterKey.quality(QualityBadge.GOOD)))
        }
        assertEquals(250, p.state.value.shownIds().size)
        assertTrue(ms < 1_000, "two filter changes over 1,000 drives took $ms ms")
    }

    @Test
    fun `count buckets follow the analytics spec`() {
        assertEquals(
            listOf("0", "1", "2_3", "2_3", "4_6", "4_6", "7_10", "7_10", "11_plus"),
            listOf(0, 1, 2, 3, 4, 6, 7, 10, 11).map(::countBucket),
        )
    }
}
