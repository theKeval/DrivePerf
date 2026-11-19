package com.driveperf.presentation.drives

import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.SuspectAnswer
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Drives state machine: lists, filter, sheets, answers and corrections, Replay hand-off (F-CAP-04, DRIVE-200). */
@OptIn(ExperimentalCoroutinesApi::class)
class DrivesPresenterTest {
    private val trips = MutableStateFlow<List<Trip>>(emptyList())
    private val units = MutableStateFlow(UnitSystem.METRIC)
    private val corrections = FakeCorrections()
    private val clock = FixedClock(toronto(2026, 11, 10, 18))

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.presenter(corrector: FakeCorrections? = corrections, scope: CoroutineScope = backgroundScope) =
        DrivesPresenter(trips, corrector, units, JavaTimeFormatter(), clock, scope)

    @Test
    fun `the screen is empty only once the first list has arrived`() = runTest(UnconfinedTestDispatcher()) {
        assertFalse(DrivesState().isEmpty)
        val p = presenter()
        assertTrue(p.state.value.loaded)
        assertTrue(p.state.value.isEmpty)
        assertEquals("0 drives", p.state.value.countText)
        assertNull(p.state.value.sinceText)
    }

    @Test
    fun `rows are newest first, with the count and the oldest date`() = runTest(UnconfinedTestDispatcher()) {
        trips.value = listOf(
            trip("a", startedAt = toronto(2026, 11, 9, 17, 5), durationS = 3_900.0, distanceM = 80_000.0),
            trip("b", startedAt = toronto(2026, 11, 10, 7, 42), durationS = 25 * 60 + 10.0, distanceM = 12_449.0),
        )
        val state = presenter().state.value
        assertEquals(listOf("b", "a"), state.trips.map { it.id })
        assertEquals("12.4 km · 25 min", state.trips[0].stats)
        assertEquals("80.0 km · 1 h 05 min", state.trips[1].stats)
        assertEquals("2 drives", state.countText)
        assertEquals("That’s every drive since 9 November.", state.sinceText)
        assertEquals(listOf("This week"), state.sections.map { it.title })
        assertFalse(state.seeAll)
    }

    @Test
    fun `the Drives tab lists the latest five and links to the rest`() = runTest(UnconfinedTestDispatcher()) {
        trips.value = (1..6).map { trip("t$it", startedAt = toronto(2026, 11, it, 8)) }
        val state = presenter().state.value
        assertEquals(listOf("t6", "t5", "t4", "t3", "t2"), state.latest.map { it.id })
        assertTrue(state.seeAll)
    }

    @Test
    fun `a change of units updates the cards and the open sheets without new trip data`() = runTest(UnconfinedTestDispatcher()) {
        trips.value = listOf(trip("t", durationS = 600.0, distanceM = 1_609.344, quality = quality(QualityBadge.GOOD)))
        val p = presenter()
        assertEquals("1.6 km · 10 min", p.state.value.trips.single().stats)
        p.dispatch(DrivesIntent.Open("t"))
        p.dispatch(DrivesIntent.OpenQuality("t"))
        assertEquals("km", p.state.value.detail?.tiles?.first()?.unit)
        units.value = UnitSystem.IMPERIAL
        val changed = p.state.value
        assertEquals("1.0 mi · 10 min", changed.trips.single().stats)
        assertEquals("mi", changed.detail?.tiles?.first()?.unit)
        assertTrue(changed.quality!!.estimatedStart!!.contains("0.3 mi"))
    }

    @Test
    fun `sheets open, close and follow their trip`() = runTest(UnconfinedTestDispatcher()) {
        trips.value = listOf(trip("a", quality = quality(QualityBadge.USABLE)), trip("b"))
        val p = presenter()
        p.dispatch(DrivesIntent.Open("a"))
        val detail = assertNotNull(p.state.value.detail)
        assertEquals("a", detail.id)
        assertEquals("Wednesday morning", detail.title)
        assertTrue(detail.subtitle.startsWith("5 Nov · 5:54"))
        p.dispatch(DrivesIntent.OpenQuality("a"))
        assertEquals(QualityBadge.USABLE, p.state.value.quality?.badge)
        p.dispatch(DrivesIntent.CloseQuality)
        assertNull(p.state.value.quality)
        assertEquals("a", p.state.value.detail?.id)
        p.dispatch(DrivesIntent.OpenQuality("b"))
        assertNull(p.state.value.quality, "a trip without analysis has no quality sheet")
        p.dispatch(DrivesIntent.OpenQuality("a"))
        p.dispatch(DrivesIntent.CloseDetail)
        assertNull(p.state.value.detail)
        assertNull(p.state.value.quality)

        p.dispatch(DrivesIntent.Open("a"))
        trips.value = listOf(trip("b"))
        assertNull(p.state.value.detail, "the sheet closes when its trip is gone")
        corrections.notDrives.value = listOf(trip("a", kind = TripKind.TRANSIT, correction = TripCorrection.TRANSIT))
        assertEquals(TripCorrection.TRANSIT, p.state.value.detail?.correction, "a trip just marked as not a drive keeps its sheet")
    }

    @Test
    fun `Replay hands the trip over and keeps the trip sheet`() = runTest(UnconfinedTestDispatcher()) {
        trips.value = listOf(trip("t", distanceM = 19_900.0, quality = quality(QualityBadge.GOOD)))
        val p = presenter()
        p.dispatch(DrivesIntent.Open("t"))
        p.dispatch(DrivesIntent.OpenQuality("t"))
        p.dispatch(DrivesIntent.OpenReplay("t"))
        assertEquals(DrivesEffect.OpenReplay("t", "Wednesday morning", "Map of your drive, 19.9 km."), p.effects.first())
        assertEquals("t", p.state.value.detail?.id)
        assertNull(p.state.value.quality)
        p.dispatch(DrivesIntent.OpenReplay("gone"))
        assertNull(withTimeoutOrNull(100) { p.effects.first() }, "an unknown trip opens nothing")
    }

    @Test
    fun `trips marked as not a drive stay reachable however few drives are left`() = runTest(UnconfinedTestDispatcher()) {
        trips.value = listOf(trip("a"), trip("b"))
        val p = presenter()
        assertFalse(p.state.value.seeAll)
        corrections.notDrives.value = listOf(trip("bus", kind = TripKind.TRANSIT, correction = TripCorrection.TRANSIT))
        assertTrue(p.state.value.seeAll)
        assertEquals("Bus or train", p.state.value.notDrives.single().kindLabel)
    }

    @Test
    fun `with no drives left All drives opens on Not drives until the user picks a filter`() = runTest(UnconfinedTestDispatcher()) {
        corrections.notDrives.value = listOf(trip("walk", kind = TripKind.NOT_DRIVING, correction = TripCorrection.WALK))
        val p = presenter()
        val state = p.state.value
        assertTrue(state.isEmpty)
        assertTrue(state.seeAll)
        assertEquals(DrivesFilter.NOT_DRIVES, state.filter)
        assertEquals(listOf("walk"), state.shown.map { it.id })
        p.dispatch(DrivesIntent.Filter(DrivesFilter.DRIVES))
        assertEquals(DrivesFilter.DRIVES, p.state.value.filter)
        assertEquals(emptyList(), p.state.value.shown)
    }

    @Test
    fun `answers and corrections reach the core`() = runTest(UnconfinedTestDispatcher()) {
        trips.value = listOf(
            trip("a", status = TripStatus.INTERRUPTED, quality = quality(QualityBadge.USABLE)),
            trip("c", suspect = Suspect.CYCLING),
        )
        val p = presenter()
        val state = p.state.value
        assertTrue(state.canCorrect)
        assertEquals(TripCopy.correctionOptions, state.corrections)
        assertEquals(Suspect.CYCLING, state.trips.first { it.id == "c" }.question)
        p.dispatch(DrivesIntent.Answer("c", yes = true))
        p.dispatch(DrivesIntent.Answer("c", yes = false))
        p.dispatch(DrivesIntent.Correct("a", TripCorrection.WALK))
        assertEquals(listOf("c" to SuspectAnswer.YES, "c" to SuspectAnswer.NO), corrections.answers)
        assertEquals(listOf("a" to TripCorrection.WALK), corrections.corrections)
    }

    @Test
    fun `without a corrections port nothing can be corrected`() = runTest(UnconfinedTestDispatcher()) {
        trips.value = listOf(trip("a"))
        val p = presenter(corrector = null)
        assertFalse(p.state.value.canCorrect)
        assertTrue(p.state.value.notDrives.isEmpty())
        p.dispatch(DrivesIntent.Answer("a", yes = true))
        p.dispatch(DrivesIntent.Correct("a", TripCorrection.WALK))
        assertTrue(corrections.answers.isEmpty())
        assertTrue(corrections.corrections.isEmpty())
    }

    @Test
    fun `close stops following the lists and keeps the last state`() = runTest(UnconfinedTestDispatcher()) {
        trips.value = listOf(trip("a"))
        val p = presenter()
        p.close()
        trips.value = emptyList()
        assertEquals(listOf("a"), p.state.value.trips.map { it.id })
    }

    @Test
    fun `onMain runs on the main thread in a scope of its own`() = runTest(UnconfinedTestDispatcher()) {
        val fixed = DrivesPresenter.onMain(listOf(trip("fixture")), UnitSystem.IMPERIAL, JavaTimeFormatter(), clock)
        assertEquals("24.2 mi", fixed.state.value.trips.single().distance)
        assertFalse(fixed.state.value.canCorrect)
        fixed.close()
        val live = DrivesPresenter.onMain(trips, corrections, units, JavaTimeFormatter(), clock)
        trips.value = listOf(trip("x"))
        assertEquals(listOf("x"), live.state.value.trips.map { it.id })
        live.close()
        trips.value = emptyList()
        assertEquals(listOf("x"), live.state.value.trips.map { it.id }, "closed: the owned scope is cancelled")
    }
}
