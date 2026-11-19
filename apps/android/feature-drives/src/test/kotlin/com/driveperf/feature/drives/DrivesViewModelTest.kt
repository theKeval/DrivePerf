package com.driveperf.feature.drives

import app.cash.turbine.test
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.foundation.Clock
import com.driveperf.presentation.drives.DrivesEffect
import com.driveperf.presentation.drives.DrivesIntent
import com.driveperf.presentation.drives.DrivesPlatform
import com.driveperf.presentation.drives.DrivesPresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/** The Android adapter over the shared DrivesPresenter, and its `java.time` formatting. The state machine is tested in shared code. */
@OptIn(ExperimentalCoroutinesApi::class)
class DrivesViewModelTest {
    private val zone = ZoneId.of("America/Toronto")
    private val trips = MutableStateFlow<List<Trip>>(emptyList())
    private val clock = object : Clock {
        override fun monotonicNs(): Long = 0L
        override fun wallMs(): Long = at(2026, 11, 10, 18, 0)
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun trip(id: String, startedAt: Long) = Trip(
        id = id, status = TripStatus.FINALISED, kind = TripKind.DRIVER, source = TripSource.AUTO, startedAt = startedAt,
        startTzOffsetMin = -300, endedAt = startedAt + 1_800_000, distanceM = 19_900.0, durationS = 1_800.0, createdAt = 0L,
        updatedAt = 0L,
    )

    private fun vm() = DrivesViewModel { scope ->
        DrivesPresenter(trips, null, MutableStateFlow(UnitSystem.METRIC), JavaTimeDrivesFormatter(zone, Locale.CANADA), clock, scope)
    }

    @Test
    fun `the state is the shared presenter's`() = runTest {
        trips.value = listOf(trip("t", at(2026, 11, 5, 8, 14)))
        val row = vm().state.value.trips.single()
        assertEquals("Thursday morning", row.title)
        assertEquals("19.9 km · 30 min", row.stats)
    }

    @Test
    fun `Replay closes the trip sheet, then hands the trip to S-RPL-01 (DRIVE-200)`() = runTest {
        trips.value = listOf(trip("t", at(2026, 11, 10, 17, 42)))
        val model = vm()
        model.onAction(DrivesIntent.Open("t"))
        assertEquals("t", model.state.value.detail?.id)
        model.effects.test {
            model.onAction(DrivesIntent.OpenReplay("t"))
            assertEquals(DrivesEffect.OpenReplay("t", "Tuesday evening", "Map of your drive, 19.9 km."), awaitItem())
        }
        assertNull(model.state.value.detail)
    }

    @Test
    fun `java time formats in the device's zone and locale`() {
        val format = JavaTimeDrivesFormatter(zone, Locale.CANADA)
        val t = at(2026, 11, 5, 8, 14)
        assertEquals(DrivesPlatform.ANDROID, format.platform)
        assertEquals("Thursday", format.weekday(t))
        assertEquals(5, format.dayOfMonth(t))
        assertEquals(8, format.hour(t))
        assertTrue(format.time(t), format.time(t).startsWith("8:14"))
        assertEquals("November 5, 2026", format.longDate(t))
        assertEquals("5 November", format.dayAndMonth(t))
        assertTrue(format.month(t).startsWith("Nov"))
        // Canada starts the week on Sunday, the UK on Monday.
        assertEquals(at(2026, 11, 1, 0, 0), format.weekStart(t))
        assertEquals(at(2026, 11, 2, 0, 0), JavaTimeDrivesFormatter(zone, Locale.UK).weekStart(t))
    }
}
