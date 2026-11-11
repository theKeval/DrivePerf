package com.driveperf.domain.trip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DRIVE-100 (F-CAP-04): what a correction means for the trip. */
class TripCorrectionTest {
    private val trip = Trip(
        id = "t", status = TripStatus.FINALISED, kind = TripKind.DRIVER, source = TripSource.AUTO, startedAt = 0L,
        startTzOffsetMin = 0, createdAt = 0L, updatedAt = 0L,
    )

    @Test
    fun correctionsSetTheKindAndOnlyNonDrivesLeaveHistory() {
        assertEquals(
            listOf(false, false, true, true, true, true),
            TripCorrection.entries.map { it.isNotADrive },
        )
        TripCorrection.entries.forEach { assertEquals(it.isNotADrive, trip.copy(kind = it.kind).isNotADrive, it.name) }
        assertFalse(trip.copy(kind = TripKind.TAXI).isNotADrive)
    }

    @Test
    fun yesConfirmsWhatWasSuspected() {
        assertEquals(TripCorrection.TRANSIT, TripCorrection.confirming(Suspect.TRANSIT))
        assertEquals(TripCorrection.BIKE, TripCorrection.confirming(Suspect.CYCLING))
        assertNull(TripCorrection.confirming(Suspect.NONE))
    }

    @Test
    fun onlyAnUnansweredDriverTripAsksItsQuestion() {
        assertNull(trip.openQuestion)
        val bus = trip.copy(suspect = Suspect.TRANSIT)
        assertEquals(Suspect.TRANSIT, bus.openQuestion)
        assertNull(bus.copy(suspectAnswer = SuspectAnswer.NO).openQuestion)
        assertNull(bus.copy(kind = TripKind.PASSENGER).openQuestion)
        assertNull(bus.copy(correction = TripCorrection.OTHER, kind = TripKind.NOT_DRIVING).openQuestion)
    }

    @Test
    fun notDrivesAreKeptThirtyDays() {
        assertEquals(30, NotDrives.RETENTION_DAYS)
        assertEquals(2_592_000_000L, NotDrives.RETENTION_MS)
        assertTrue(TripCorrection.WALK.kind == TripKind.NOT_DRIVING)
    }
}
