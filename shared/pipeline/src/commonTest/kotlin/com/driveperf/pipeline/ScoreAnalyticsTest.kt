package com.driveperf.pipeline

import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.capture.FinalisedTrip
import com.driveperf.domain.repository.ScoredTrip
import com.driveperf.domain.repository.TripHistory
import com.driveperf.domain.score.ScoreBasis
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.TripKind
import com.driveperf.pipeline.capture.FinaliseRequest
import com.driveperf.pipeline.capture.FinaliseResult
import com.driveperf.pipeline.capture.TripFinaliser
import com.driveperf.pipeline.fixtures.DriveFixtures
import com.driveperf.pipeline.fixtures.PipelineHarness
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** DRIVE-93 `events_detected {type, count_bucket}` and DRIVE-82 `baseline_established {trips}`. */
class ScoreAnalyticsTest {
    private val tracked = mutableListOf<AnalyticsEvent>()
    private val analytics = object : Analytics {
        override fun track(event: AnalyticsEvent) {
            tracked += event
        }

        override fun setUsageConsent(allowed: Boolean) = Unit

        override fun resetId() = Unit
    }

    private suspend fun drive(): FinalisedTrip = PipelineHarness().trip(DriveFixtures.city.capture())

    private fun history(vararg trips: ScoredTrip) = object : TripHistory {
        override suspend fun finishedBefore(beforeStartedAt: Long, limit: Int): List<ScoredTrip> =
            trips.filter { it.trip.startedAt < beforeStartedAt }.take(limit)
    }

    /** Runs the decorator over [result] with [past] as the committed history. */
    private suspend fun finalise(result: FinaliseResult, past: TripHistory = history()) {
        val inner = TripFinaliser { result }
        val request = FinaliseRequest(DriveFixtures.city.capture(), null, TripKind.DRIVER, true, null, 1L)
        assertEquals(result, ScoreAnalyticsFinaliser(inner, analytics, past).finalise(request))
    }

    private fun named(name: String) = tracked.filter { it.name == name }.map { it.properties }

    @Test
    fun aScoredDriveReportsItsEventCountsPerType() = runTest {
        val d = drive()
        assertNotNull(d.score?.smoothness)
        finalise(FinaliseResult(d, null))
        val expected = EventType.entries.map { type ->
            val n = d.events.count { it.type == type }
            mapOf("type" to type.name.lowercase(), "count_bucket" to if (n <= 1) "$n" else "2_3")
        }
        assertEquals(expected, named("events_detected"), "one per type, zero included")
        assertEquals(emptyList(), named("baseline_established"), "the first drive calibrates")
    }

    @Test
    fun aPassengerTripOrAnUnscoredOneReportsNoEvents() = runTest {
        val d = drive()
        finalise(FinaliseResult(listOf(d.copy(trip = d.trip.copy(kind = TripKind.PASSENGER)), d.copy(score = null)), null))
        assertEquals(emptyList(), tracked)
    }

    @Test
    fun countBucketsAreFixed() {
        val buckets = listOf(0, 1, 2, 3, 4, 9, 10, 40).map { ScoreEvent.eventsDetected(EventType.BRAKE, it).properties["count_bucket"] }
        assertEquals(listOf("0", "1", "2_3", "2_3", "4_9", "4_9", "10_plus", "10_plus"), buckets)
        val trips = listOf(3, 4, 5, 6, 7, 10, 11, 20, 21).map { ScoreEvent.baselineEstablished(it).properties["trips"] }
        assertEquals(listOf("lt_4", "4", "5_6", "5_6", "7_10", "7_10", "11_20", "11_20", "gt_20"), trips)
    }

    @Test
    fun theFirstDriveWithABaselineReportsHowManyScoredDrivesItTook() = runTest {
        val d = drive()
        val compared = d.copy(score = d.score?.copy(baseline = 90, delta = 2, basis = ScoreBasis.CONTEXT, calibrating = null))
        val earlier = (1..5).map { i ->
            val t = d.trip.copy(id = "p$i", startedAt = d.trip.startedAt - i * 1_000L)
            ScoredTrip(t, d.score?.copy(tripId = "p$i"), null)
        }
        val passenger = ScoredTrip(d.trip.copy(id = "pass", startedAt = 1L, kind = TripKind.PASSENGER), d.score, null)

        finalise(FinaliseResult(compared, null), history(*earlier.toTypedArray(), passenger))

        assertEquals(listOf(mapOf("trips" to "5_6")), named("baseline_established"), "five scored drives before it, plus it")
    }

    @Test
    fun aBaselineAlreadyEstablishedIsNotReportedAgain() = runTest {
        val d = drive()
        val compared = d.copy(score = d.score?.copy(baseline = 90))
        val before = ScoredTrip(d.trip.copy(id = "p1", startedAt = d.trip.startedAt - 1_000L), compared.score, null)

        finalise(FinaliseResult(compared, null), history(before))
        // A split: the first part established it, so the second does not report it again.
        val second = compared.copy(trip = compared.trip.copy(id = "part2", startedAt = compared.trip.startedAt + 1))
        finalise(FinaliseResult(listOf(compared, second), null))

        assertEquals(listOf(mapOf("trips" to "lt_4")), named("baseline_established"), "only the split's first part")
    }
}
