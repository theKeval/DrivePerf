package com.driveperf.pipeline.fixtures

import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.CaptureHeader
import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.capture.WakeKind
import com.driveperf.domain.repository.ScoredTrip
import com.driveperf.domain.repository.TripHistory
import com.driveperf.domain.trip.InterruptionReason
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripStatus
import com.driveperf.pipeline.PipelineFinaliser
import com.driveperf.pipeline.capture.FinaliseRequest
import com.driveperf.pipeline.signal.SignalConfig
import com.driveperf.testing.SyntheticCapture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §1.6 "Trip > 12 h", run end to end with the thresholds scaled down (12 h → 10 min, 30 min → 2 min) so the synthetic
 * capture stays small; the rule is the same.
 */
class LongTripSplitTest {
    private val config = SignalConfig(splitAfterS = 600.0, splitStopMinS = 120.0)
    private val empty = object : TripHistory {
        override suspend fun finishedBefore(beforeStartedAt: Long, limit: Int): List<ScoredTrip> = emptyList()
    }
    private val finaliser = PipelineFinaliser(empty, signalConfig = config)

    /** About 5 min of driving, a parked [dwellS], about 5 min more. */
    private fun capture(dwellS: Double): CapturedTrip {
        val c = SyntheticCapture(seed = 3)
        c.activity(ActivityType.AUTOMOTIVE, 0.0)
        c.cruise(5.0)
        repeat(2) { leg ->
            c.speedTo(13.0, 1.5, 1.5)
            c.cruise(130.0)
            c.turn(if (leg == 0) 90.0 else -90.0, 8.0)
            c.cruise(130.0)
            c.stop(1.8, 1.5, if (leg == 0) dwellS else 10.0)
        }
        val s = c.streams()
        return CapturedTrip(
            CaptureHeader("long", SyntheticCapture.START_MONO_NS, SyntheticCapture.WALL0, 0, "Etc/UTC", WakeKind.MANUAL, "app", 50, null),
            s.fixes, s.motion, s.activity, emptyList(), emptyList(),
        )
    }

    private suspend fun run(capture: CapturedTrip, interruption: InterruptionReason? = null) =
        finaliser.finalise(FinaliseRequest(capture, null, TripKind.DRIVER, true, interruption, 1L))

    @Test
    fun aTripOverTheLimitSplitsAtItsLongStop() = runTest {
        val capture = capture(dwellS = 200.0)
        val result = run(capture, InterruptionReason.APP_KILLED)
        assertEquals(2, result.trips.size)
        val (first, second) = result.trips
        assertEquals("long", first.trip.id, "the first part keeps the trip's ID")
        assertNotEquals("long", second.trip.id)
        assertTrue(first.trip.endedAt!! <= second.trip.startedAt)
        val gapS = (second.trip.startedAt - first.trip.endedAt!!) / 1000.0
        assertTrue(gapS > 150.0, "the parked time belongs to neither part, was $gapS s")
        assertTrue(first.trip.durationS!! < 600.0 && second.trip.durationS!! < 600.0)
        assertEquals(TripStatus.FINALISED, first.trip.status, "only the last part keeps the interruption")
        assertEquals(TripStatus.INTERRUPTED, second.trip.status)

        val firstR0 = assertNotNull(first.splitCapture, "each part rewrites its R0")
        val secondR0 = assertNotNull(second.splitCapture)
        assertEquals("long", firstR0.header.tripId)
        assertEquals(second.trip.id, secondR0.header.tripId)
        assertNotNull(secondR0.header.parking, "the later part starts where the first parked")
        assertTrue(firstR0.fixes.last().tMonoNs < secondR0.fixes.first().tMonoNs)

        for (part in result.trips) {
            val r1 = assertNotNull(part.analysis)
            assertEquals(25, r1.rateHz)
            assertEquals(part.trip.durationS!! * 25, r1.size.toDouble(), 1.0)
        }
        assertEquals(result.parking, run(secondR0).parking, "the parking spot is where the last part ended")
    }

    @Test
    fun withoutALongEnoughStopTheTripStaysWhole() = runTest {
        val result = run(capture(dwellS = 100.0))
        val trip = result.trips.single()
        assertTrue(trip.trip.durationS!! > 600.0)
        assertNull(trip.splitCapture)
        assertNotNull(trip.analysis)
    }
}
