package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.WakeKind
import com.driveperf.domain.trip.TripKind
import com.driveperf.pipeline.capture.CaptureFixtures.fix
import com.driveperf.pipeline.capture.CaptureFixtures.header
import com.driveperf.pipeline.capture.CaptureFixtures.ns
import com.driveperf.pipeline.capture.CaptureFixtures.wallAt
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TripSummariserTest {
    private fun request(fixes: List<LocationSample>, endS: Double?) = FinaliseRequest(
        capture = CapturedTrip(header(100.0, WakeKind.MANUAL), fixes, emptyList(), emptyList(), emptyList(), emptyList()),
        endNs = endS?.let { ns(it) },
        kind = TripKind.DRIVER,
        manual = true,
        interruption = null,
        nowWallMs = wallAt(400.0),
    )

    @Test
    fun cachedFixesFromBeforeTheWakeAreNotPartOfTheDistance() = runTest {
        val cached = fix(80.0, 10.0, northM = -5000.0)
        val drive = (100..300).map { fix(it.toDouble(), 10.0, northM = (it - 100) * 10.0) }
        val trip = assertNotNull(TripSummariser().finalise(request(listOf(cached) + drive, 300.0)).trip).trip
        assertEquals(2000.0, trip.distanceM!!, 1.0)
        assertEquals(200.0, trip.durationS!!, 0.01)
    }

    @Test
    fun aStopBeforeAnyPostStartFixHasNoParkingSpotBeforeTheStart() = runTest {
        val result = TripSummariser().finalise(request(listOf(fix(90.0, 0.0)), 100.5))
        assertNull(result.parking)
        assertEquals(0.0, result.trip?.trip?.distanceM)
    }
}
