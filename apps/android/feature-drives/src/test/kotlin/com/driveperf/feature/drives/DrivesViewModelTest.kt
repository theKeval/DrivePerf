package com.driveperf.feature.drives

import app.cash.turbine.test
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
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class DrivesViewModelTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 10)
    private val source = object : TripListSource {
        val flow = MutableStateFlow<List<RecordedTrip>>(emptyList())
        override val trips = flow
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = DrivesViewModel(source, zone, Locale.CANADA, { today })

    private fun at(day: LocalDate, hour: Int, minute: Int) = day.atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `an empty list is reported as empty once loaded`() = runTest {
        vm().state.test {
            val state = expectMostRecentItem()
            assertTrue(state.loaded)
            assertTrue(state.isEmpty)
        }
    }

    @Test
    fun `before the first list arrives the screen is not empty`() {
        assertFalse(DrivesUiState().isEmpty)
    }

    @Test
    fun `rows show when, how long and how far, newest first as supplied`() = runTest {
        source.flow.value = listOf(
            RecordedTrip("b", at(today, 7, 42), durationS = 25 * 60 + 10, distanceM = 12_449.0),
            RecordedTrip("a", at(today.minusDays(1), 17, 5), durationS = 3_900, distanceM = 80_000.0),
        )
        vm().state.test {
            val rows = expectMostRecentItem().trips
            assertEquals(listOf("b", "a"), rows.map { it.id })
            assertTrue(rows[0].start.startsWith("Today, "))
            assertEquals("25 min · 12.4 km", rows[0].summary)
            assertTrue(rows[1].start.startsWith("Yesterday, "))
            assertEquals("1 h 05 min · 80.0 km", rows[1].summary)
        }
    }

    @Test
    fun `older trips show a full date`() = runTest {
        source.flow.value = listOf(RecordedTrip("c", at(today.minusDays(9), 8, 0), 600, 3_000.0))
        vm().state.test {
            val start = expectMostRecentItem().trips.single().start
            assertTrue("was $start", !start.startsWith("Today") && !start.startsWith("Yesterday"))
        }
    }

    @Test
    fun `a very short drive says under a minute`() = runTest {
        source.flow.value = listOf(RecordedTrip("d", at(today, 9, 0), 20, 100.0))
        vm().state.test {
            assertEquals("< 1 min · 0.1 km", expectMostRecentItem().trips.single().summary)
        }
    }
}
