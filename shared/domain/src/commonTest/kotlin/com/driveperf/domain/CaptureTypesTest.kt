package com.driveperf.domain

import com.driveperf.domain.capture.CaptureBatch
import com.driveperf.domain.capture.CaptureHeader
import com.driveperf.domain.capture.CaptureMark
import com.driveperf.domain.capture.CaptureMarkType
import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.capture.FinalisedTrip
import com.driveperf.domain.capture.GeoPoint
import com.driveperf.domain.capture.Parking
import com.driveperf.domain.capture.WakeKind
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptureTypesTest {
    @Test
    fun anEmptyBatchHasNoSamples() {
        val batch = CaptureBatch()
        assertTrue(batch.fixes.isEmpty() && batch.motion.isEmpty() && batch.activity.isEmpty())
        assertTrue(batch.recordedAccel.isEmpty() && batch.marks.isEmpty())
    }

    @Test
    fun headersCarryTheParkingThatBoundsTheMotionSearch() {
        val parking = Parking(GeoPoint(2.0, -27.0), 5L)
        val header = CaptureHeader("t1", 1L, 2L, -300, "Etc/GMT+5", WakeKind.PARKED_EXIT, null, 50, parking)
        val mark = CaptureMark(3L, CaptureMarkType.CONFIRMED)
        val trip = CapturedTrip(header, emptyList(), emptyList(), emptyList(), emptyList(), listOf(mark))
        assertEquals(parking, trip.header.parking)
        assertNull(mark.detail)
    }

    @Test
    fun aFinalisedTripDefaultsToNoDerivedRows() {
        val trip = Trip(
            id = "t1", status = TripStatus.FINALISED, kind = TripKind.DRIVER, source = TripSource.MANUAL,
            startedAt = 1L, startTzOffsetMin = 0, createdAt = 1L, updatedAt = 1L,
        )
        val result = FinalisedTrip(trip)
        assertTrue(result.events.isEmpty())
        assertNull(result.score)
        assertNull(result.routeMatch)
        assertNull(result.newRoute)
    }
}
