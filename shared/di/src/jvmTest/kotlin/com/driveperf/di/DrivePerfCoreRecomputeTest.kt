package com.driveperf.di

import com.driveperf.data.local.LocalTripStore
import com.driveperf.domain.capture.FinalisedTrip
import com.driveperf.domain.score.Score
import com.driveperf.domain.trip.DriveContext
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.Placement
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripQuality
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.tripfile.FileResult
import com.driveperf.testing.FakeActivitySource
import com.driveperf.testing.FakeLocationSource
import com.driveperf.testing.FakeMotionSource
import com.driveperf.testing.FakeParkedRegionMonitor
import com.driveperf.testing.FakePowerState
import com.driveperf.testing.InMemoryTripFileStore
import com.driveperf.testing.TestClock
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** DRIVE-131 wiring: the started core recomputes later baselines after a deletion, feedback and a correction. */
class DrivePerfCoreRecomputeTest {
    private val clock = TestClock()
    private val bindings = object : PlatformBindings {
        override fun clock() = clock

        override fun location() = FakeLocationSource()

        override fun motion() = FakeMotionSource()

        override fun activity() = FakeActivitySource()

        override fun power() = FakePowerState()

        override fun parkedRegion() = FakeParkedRegionMonitor()

        override fun tripFiles() = InMemoryTripFileStore()

        override fun deviceId() = "device-1"

        override fun analyticsSink() = RecordingAnalyticsSink()
    }

    @AfterTest
    fun reset() = DrivePerfCore.reset()

    private fun trip(id: String, startedAt: Long) = Trip(
        id = id, status = TripStatus.FINALISED, kind = TripKind.DRIVER, source = TripSource.AUTO, startedAt = startedAt,
        startTzOffsetMin = 0, endedAt = startedAt + 600_000, durationS = 600.0, distanceM = 5_000.0, context = DriveContext.CITY,
        quality = TripQuality(QualityBadge.GOOD, null, Placement.MOUNTED, 50.0, 95.0, null, null, 600.0, 0.0, 600.0, emptyList()),
        createdAt = startedAt, updatedAt = startedAt,
    )

    private fun score(id: String, smoothness: Int) =
        Score(id, smoothness, null, null, null, null, null, null, emptyList(), null, null, null, null, "smooth-1.0.0", 0L)

    @Test
    fun deletionsFeedbackAndCorrectionsMoveLaterBaselines() = runBlocking {
        val core = DrivePerfCore.start(bindings)
        val store = LocalTripStore(core.db, "device-1", core.io)
        listOf("t1" to 70, "t2" to 88, "t3" to 52, "t4" to 97, "t5" to 97).forEachIndexed { i, (id, s) ->
            store.saveFinalised(FinalisedTrip(trip(id, 1_000L * (i + 1)), score = score(id, s)))
        }

        assertEquals(FileResult.OK, core.trips.delete("t1"))
        assertEquals(88, core.trips.score("t5")?.baseline, "t2, t3 and t4: median 88")

        core.feedbackStore.save(EventFeedback("e1", "t3", needed = true, inaccurate = null, at = 2L), score("t3", 99))
        assertEquals(97, core.trips.score("t5")?.baseline, "t3 re-scored to 99: median of 88, 99, 97")

        core.corrections.correct("t2", TripCorrection.PASSENGER)
        assertNull(core.trips.score("t2")?.smoothness, "a passenger trip has no score")
        assertNull(core.trips.score("t5")?.baseline, "two prior drives are not a baseline")
        assertEquals(3, core.trips.score("t5")?.calibrating?.k)
        assertEquals("smooth-1.0.0", core.trips.score("t5")?.algorithmVersion)
    }
}
