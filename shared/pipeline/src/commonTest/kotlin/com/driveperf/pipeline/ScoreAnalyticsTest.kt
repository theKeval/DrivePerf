package com.driveperf.pipeline

import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.capture.FinalisedTrip
import com.driveperf.domain.repository.ScoredTrip
import com.driveperf.domain.repository.TripHistory
import com.driveperf.domain.score.ScoreBasis
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripSource
import com.driveperf.pipeline.capture.FinaliseRequest
import com.driveperf.pipeline.capture.FinaliseResult
import com.driveperf.pipeline.capture.TripFinaliser
import com.driveperf.pipeline.fixtures.DriveFixtures
import com.driveperf.pipeline.fixtures.PipelineHarness
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        assertEquals(emptyList(), named("events_detected"))
        assertEquals(2, named("trip_finalised").size, "both are still finalised trips")
        assertTrue(named("trip_finalised").none { "score_bucket" in it }, "no score, no score_bucket")
    }

    @Test
    fun everyFinalisedTripReportsTripFinalisedOnceWithItsBuckets() = runTest {
        val d = drive()
        val smoothness = assertNotNull(d.score?.smoothness)
        finalise(FinaliseResult(d, null))
        val props = named("trip_finalised").single()
        assertEquals("manual", props["mode"])
        assertEquals("manual", props["trigger"])
        assertEquals(d.trip.quality?.badge?.name?.lowercase(), props["quality"])
        assertEquals(com.driveperf.domain.score.scoreBucket(smoothness), props["score_bucket"])
        assertEquals(setOf("mode", "trigger", "quality", "duration_bucket", "distance_bucket", "score_bucket"), props.keys)
        // A split: one event per part.
        tracked.clear()
        finalise(FinaliseResult(listOf(d, d.copy(trip = d.trip.copy(id = "part2", startedAt = d.trip.startedAt + 1))), null))
        assertEquals(2, named("trip_finalised").size)
    }

    @Test
    fun durationDistanceAndQualityUseTheSpecBuckets() = runTest {
        val d = drive()
        fun props(minutes: Double, km: Double, badge: QualityBadge?): Map<String, String> {
            val trip = d.trip.copy(durationS = minutes * 60, distanceM = km * 1000)
            val rated = if (badge == null) trip.copy(quality = null) else trip.copy(quality = trip.quality?.copy(badge = badge))
            return ScoreEvent.tripFinalised(d.copy(trip = rated)).properties
        }
        val cases = listOf(4.9 to 1.9, 5.0 to 2.0, 15.0 to 10.0, 30.0 to 25.0, 60.0 to 50.0, 119.0 to 99.0, 121.0 to 101.0)
        assertEquals(
            listOf("lt_5", "5_15", "15_30", "30_60", "60_120", "60_120", "gt_120"),
            cases.map { (m, k) -> props(m, k, QualityBadge.GOOD)["duration_bucket"] },
        )
        assertEquals(
            listOf("lt_2", "2_10", "10_25", "25_50", "50_100", "50_100", "gt_100"),
            cases.map { (m, k) -> props(m, k, QualityBadge.GOOD)["distance_bucket"] },
        )
        val qualities = listOf(
            QualityBadge.USABLE, QualityBadge.LIMITED,
            QualityBadge.UNSCORABLE, null,
        ).map { props(10.0, 5.0, it)["quality"] }
        assertEquals(listOf("usable", "poor", "unscorable", "unscorable"), qualities)
        val auto = ScoreEvent.tripFinalised(d.copy(trip = d.trip.copy(source = TripSource.AUTO, captureMeta = null)))
        assertEquals("auto", auto.properties["mode"])
        assertEquals("unknown", auto.properties["trigger"])
    }

    @Test
    fun aReScoreSendsNothing() = runTest {
        val d = drive()
        finalise(FinaliseResult(d, null))
        val sent = tracked.size
        val history = history(ScoredTrip(d.trip, d.score, null))
        val trips = object : com.driveperf.domain.repository.TripRepository {
            override fun observeTrips() = kotlinx.coroutines.flow.emptyFlow<List<com.driveperf.domain.trip.Trip>>()
            override suspend fun trip(id: String) = d.trip.takeIf { it.id == id }
            override suspend fun events(tripId: String) = d.events
            override suspend fun feedback(tripId: String) = emptyMap<String, com.driveperf.domain.trip.EventFeedback>()
            override suspend fun score(tripId: String) = d.score
            override suspend fun delete(tripId: String) = com.driveperf.domain.tripfile.FileResult.OK
        }
        val store = object : com.driveperf.domain.score.ScoreHistoryStore {
            override suspend fun finishedFrom(startedAt: Long) =
                listOf(ScoredTrip(d.trip.copy(kind = TripKind.PASSENGER), d.score, null))
            override suspend fun saveScores(scores: List<com.driveperf.domain.score.Score>) = Unit
        }
        com.driveperf.pipeline.score.HistoryRecompute(history, store, trips, com.driveperf.testing.TestClock()).fromTrip(d.trip.id)
        assertEquals(sent, tracked.size, "corrections and feedback re-score without passing through the finaliser")
    }

    @Test
    fun countBucketsAreFixed() {
        val buckets = listOf(0, 1, 2, 3, 4, 6, 7, 10, 11, 40)
            .map { ScoreEvent.eventsDetected(EventType.BRAKE, it).properties["count_bucket"] }
        assertEquals(listOf("0", "1", "2_3", "2_3", "4_6", "4_6", "7_10", "7_10", "11_plus", "11_plus"), buckets, "§2 count_bucket")
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
