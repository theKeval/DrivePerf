package com.driveperf.feature.drives

import app.cash.turbine.test
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.domain.trip.TripKind
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/** F-CAP-04 AC3: trips marked as not a drive stay reachable from the Drives tab, however few drives are left. */
@OptIn(ExperimentalCoroutinesApi::class)
class NotDrivesReachTest {
    private val trips = MutableStateFlow<List<RecordedTrip>>(emptyList())
    private val notDrives = MutableStateFlow<List<RecordedTrip>>(emptyList())
    private val source = object : TripListSource {
        override val trips = this@NotDrivesReachTest.trips
    }
    private val corrector = object : TripCorrector {
        override val notDrives = this@NotDrivesReachTest.notDrives
        override suspend fun answer(tripId: String, yes: Boolean) = Unit
        override suspend fun correct(tripId: String, to: TripCorrection) = Unit
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun trip(id: String, kind: TripKind = TripKind.DRIVER, correction: TripCorrection? = null) = RecordedTrip(
        id, 1_762_340_040_000L, 600, 5_000.0, RecordedStatus.FINALISED, 1_762_340_640_000L, null, null, kind, correction,
        correction?.let { 1_762_340_640_000L },
    )

    private fun vm() = DrivesViewModel(source, ZoneOffset.UTC, Locale.CANADA, { LocalDate.of(2026, 11, 10) }, corrector)

    @Test
    fun `the link to All drives shows with few drives once any trip is marked not a drive`() = runTest {
        trips.value = listOf(trip("a"), trip("b"))
        vm().state.test {
            assertFalse(expectMostRecentItem().seeAll)
            notDrives.value = listOf(trip("bus", TripKind.TRANSIT, TripCorrection.TRANSIT))
            assertTrue(awaitItem().seeAll)
        }
    }

    @Test
    fun `with no drives left All drives opens on Not drives until the user picks a filter`() = runTest {
        notDrives.value = listOf(trip("walk", TripKind.NOT_DRIVING, TripCorrection.WALK))
        val vm = vm()
        vm.state.test {
            val state = expectMostRecentItem()
            assertTrue(state.isEmpty)
            assertTrue(state.seeAll)
            assertEquals(DrivesFilter.NOT_DRIVES, state.filter)
            assertEquals(listOf("walk"), state.shown.map { it.id })
            vm.onAction(DrivesAction.Filter(DrivesFilter.DRIVES))
            assertEquals(DrivesFilter.DRIVES, awaitItem().filter)
        }
    }
}
