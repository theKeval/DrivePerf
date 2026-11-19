package com.driveperf.data.local

import app.cash.sqldelight.db.SqlDriver
import com.driveperf.domain.capture.FinalisedTrip
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.trip.TripVehicle
import com.driveperf.testing.InMemoryTripFileStore
import com.driveperf.testing.TestClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DRIVE-131 (F-HIS-02 AC2): the car picker's cars and moving a trip to another car. */
class LocalTripVehiclesTest {
    private val driver: SqlDriver = JvmDatabaseDriverFactory().create()
    private val db = openDatabase(driver)
    private val clock = TestClock()
    private val io = EmptyCoroutineContext
    private val vehicles = LocalTripVehicles(db, io, clock)
    private val trips = LocalTripRepository(db, InMemoryTripFileStore(), io)

    private fun trip(id: String, status: TripStatus = TripStatus.FINALISED) = Trip(
        id = id, status = status, kind = TripKind.DRIVER, source = TripSource.AUTO, vehicleId = "gti", startedAt = 1_000L,
        startTzOffsetMin = 0, endedAt = 601_000L, durationS = 600.0, distanceM = 5_000.0, createdAt = 1_000L, updatedAt = 1_000L,
    )

    private fun car(id: String, nickname: String?, addedAt: Long, active: Int = 1, isDefault: Int = 0) {
        driver.execute(
            null,
            """
            INSERT INTO vehicle(id, nickname, make, model, year, is_default, is_active, added_at, created_at, updated_at, origin_device_id)
            VALUES ('$id', ${nickname?.let { "'$it'" } ?: "NULL"}, 'Volkswagen', 'Golf', 2019, $isDefault, $active, $addedAt, 1, 1, 'test')
            """.trimIndent(),
            0,
        )
    }

    @Test
    fun onlyActiveCarsAreListedOldestFirst() = runTest {
        car("ioniq", "Ioniq", addedAt = 2)
        car("gti", "GTI", addedAt = 1, isDefault = 1)
        car("old", "Old", addedAt = 0, active = 0)
        assertEquals(
            listOf(
                TripVehicle("gti", "GTI", "Volkswagen", "Golf", 2019, isDefault = true),
                TripVehicle("ioniq", "Ioniq", "Volkswagen", "Golf", 2019, isDefault = false),
            ),
            vehicles.observeVehicles().first(),
        )
    }

    @Test
    fun aFinishedTripMovesToAnActiveCarOrToNoCar() = runTest {
        car("gti", "GTI", addedAt = 1)
        car("ioniq", "Ioniq", addedAt = 2)
        LocalTripStore(db, "device-1", io).saveFinalised(FinalisedTrip(trip("a")))
        assertTrue(vehicles.setVehicle("a", "ioniq"))
        val moved = trips.trip("a")
        assertEquals("ioniq", moved?.vehicleId)
        assertEquals(clock.wallMs(), moved?.updatedAt)
        assertTrue(vehicles.setVehicle("a", null))
        assertNull(trips.trip("a")?.vehicleId)
    }

    @Test
    fun missingCarsAndTripsChangeNothing() = runTest {
        car("gti", "GTI", addedAt = 1)
        car("old", "Old", addedAt = 0, active = 0)
        LocalTripStore(db, "device-1", io).saveFinalised(FinalisedTrip(trip("a")))
        LocalTripStore(db, "device-1", io).insertRecording(trip("live", TripStatus.RECORDING))
        assertFalse(vehicles.setVehicle("a", "old"))
        assertFalse(vehicles.setVehicle("a", "missing"))
        assertFalse(vehicles.setVehicle("nope", "gti"))
        assertFalse(vehicles.setVehicle("live", null))
        assertEquals("gti", trips.trip("a")?.vehicleId)
    }

    @Test
    fun undoRestoresARetiredCarButNotAMissingOne() = runTest {
        car("gti", "GTI", addedAt = 1)
        car("ioniq", "Ioniq", addedAt = 2)
        LocalTripStore(db, "device-1", io).saveFinalised(FinalisedTrip(trip("a")))
        assertTrue(vehicles.setVehicle("a", "ioniq"))
        driver.execute(null, "UPDATE vehicle SET is_active = 0 WHERE id = 'gti'", 0)
        assertFalse(vehicles.setVehicle("a", "gti"), "a retired car cannot be chosen")

        assertTrue(vehicles.restoreVehicle("a", "gti"))
        assertEquals("gti", trips.trip("a")?.vehicleId)
        assertTrue(vehicles.restoreVehicle("a", null))
        assertNull(trips.trip("a")?.vehicleId)
        assertFalse(vehicles.restoreVehicle("a", "missing"))
        assertFalse(vehicles.restoreVehicle("nope", "gti"))
        assertNull(trips.trip("a")?.vehicleId)
    }
}
