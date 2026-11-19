package com.driveperf.domain.trip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** DRIVE-131 (F-HIS-02 AC2): how a car is named in the car picker and the car filter. */
class TripVehicleTest {
    private val gti = TripVehicle("v1", "GTI", "Volkswagen", "Golf", 2019, isDefault = true)

    @Test
    fun theNicknameWinsThenMakeAndModel() {
        assertEquals("GTI", gti.name)
        assertEquals("Volkswagen Golf", gti.copy(nickname = " ").name)
        assertEquals("Golf", gti.copy(nickname = null, make = null).name)
        assertEquals("Car", TripVehicle("v2", null, null, null, null, isDefault = false).name)
    }

    @Test
    fun theDescriptionIsYearMakeAndModel() {
        assertEquals("2019 Volkswagen Golf", gti.description)
        assertEquals("Volkswagen Golf", gti.copy(year = null).description)
        assertNull(TripVehicle("v2", "Ioniq", null, "", null, isDefault = false).description)
    }
}
