package com.driveperf.domain.analysis

import com.driveperf.domain.capture.PreviewStore
import com.driveperf.domain.replay.Replay
import com.driveperf.domain.replay.ReplayPoint
import com.driveperf.domain.replay.ReplayRepository
import com.driveperf.domain.replay.ReplaySpan
import com.driveperf.domain.replay.SegmentStyle
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.score.Score
import com.driveperf.domain.trip.BoundingBox
import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.Placement
import com.driveperf.domain.trip.PreviewRow
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.RawState
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.SegmentReason
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripQuality
import com.driveperf.domain.trip.TripSegment
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.tripfile.AnalysisFileStore
import com.driveperf.domain.tripfile.AnalysisTrace
import com.driveperf.domain.tripfile.FileResult
import com.driveperf.domain.tripfile.TripFileRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TripAnalysisRepositoryTest {
    private fun trip(status: TripStatus = TripStatus.FINALISED, raw: RawState = RawState.FULL) = Trip(
        id = "t1", status = status, kind = TripKind.DRIVER, source = TripSource.AUTO, startedAt = 0, startTzOffsetMin = 0,
        durationS = 20.0, distanceM = 190.0, rawState = raw, createdAt = 0, updatedAt = 0,
    )

    private val event = DrivingEvent(
        "e1", "t1", EventType.BRAKE, 6.0, 5.0, 7.0, 2.0, -0.3, null, 0.0005, 0.0, 12.0, 6.0, Confidence.HIGH, "v1",
    )

    private class FakeTrips(var trip: Trip?, val events: List<DrivingEvent>) : TripRepository {
        override fun observeTrips(): Flow<List<Trip>> = flowOf(listOfNotNull(trip))
        override suspend fun trip(id: String): Trip? = trip?.takeIf { it.id == id }
        override suspend fun events(tripId: String): List<DrivingEvent> = events
        override suspend fun feedback(tripId: String): Map<String, EventFeedback> =
            mapOf("e1" to EventFeedback("e1", tripId, needed = true, inaccurate = null, at = 1))
        override suspend fun score(tripId: String): Score? = null
        override suspend fun delete(tripId: String): FileResult = error("unused")
    }

    private class FakeFiles(val trace: AnalysisTrace?) : AnalysisFileStore {
        var reads = 0
        override suspend fun write(trip: Trip, trace: AnalysisTrace): TripFileRef? = null
        override suspend fun read(tripId: String): AnalysisTrace? = trace.also { reads++ }
    }

    private class FakePreviews(val rows: List<PreviewRow>?) : PreviewStore {
        override suspend fun write(tripId: String, rows: List<PreviewRow>): Boolean = true
        override suspend fun read(tripId: String): List<PreviewRow>? = rows
    }

    private fun replays(r: Replay?) = object : ReplayRepository {
        override suspend fun replay(tripId: String): Replay? = r
    }

    private val replay = Replay(
        "t1", BoundingBox(0.0, 0.0, 0.002, 0.0), 20.0, listOf(ReplaySpan(SegmentStyle.ESTIMATED, 0.0, 1.0)), false,
        (0..20).map { ReplayPoint(it.toDouble(), it * 0.00009, 0.0, 10.0) },
    )

    @Test
    fun aFinishedTripGetsItsChannelsMarkersAndMap() = runTest {
        val files = FakeFiles(AnalysisFixtures.trace(20))
        val previews = FakePreviews(AnalysisFixtures.preview(4))
        val repo = TripAnalysisRepository(FakeTrips(trip(), listOf(event)), replays(replay), files, previews)
        val a = repo.analysis("t1")!!
        assertSame(replay, a.replay)
        assertFalse(a.data.summaryOnly)
        assertEquals(1.0, a.data.estimatedUntilS)
        assertEquals(listOf("e1"), a.markers.map { it.eventId })
        assertTrue(a.markers.single().needed)
        assertEquals(listOf(event), a.events)
        assertNull(a.score)
        assertEquals(20.0, a.data.durationS)
    }

    @Test
    fun aSummaryOnlyTripNeverReadsItsTraceAndUsesThePreview() = runTest {
        val files = FakeFiles(AnalysisFixtures.trace(20))
        val noReplay = replays(null)
        val repo = TripAnalysisRepository(FakeTrips(trip(raw = RawState.SUMMARY_ONLY), emptyList()), noReplay, files, FakePreviews(null))
        val a = repo.analysis("t1")!!
        assertEquals(0, files.reads)
        assertTrue(a.data.summaryOnly)
        assertNull(a.replay)
    }

    @Test
    fun withoutAReplayTheBandsComeFromTheTripsQualitySpans() = runTest {
        val spans = listOf(
            TripSegment(SegmentKind.ESTIMATED, 0.0, 2.0, SegmentReason.ESTIMATED_START),
            TripSegment(SegmentKind.GAP, 4.0, 6.0, SegmentReason.TUNNEL),
            TripSegment(SegmentKind.EXCLUDED, 10.0, 15.0, SegmentReason.PHONE_MOVED),
            TripSegment(SegmentKind.PAUSED, 16.0, 18.0, SegmentReason.USER_PAUSE),
            TripSegment(SegmentKind.INTERRUPTED, 18.0, 30.0, SegmentReason.APP_KILLED),
        )
        val quality = TripQuality(
            QualityBadge.USABLE, null, Placement.MOUNTED, 50.0, 0.0, null, null, 13.0, 5.0, 20.0, spans,
        )
        // Motion data, but no positioned fix and no preview: the Replay builder has nothing to draw.
        val trace = AnalysisFixtures.trace(20, fixes = emptyList())
        val trips = FakeTrips(trip().copy(quality = quality), emptyList())
        val repo = TripAnalysisRepository(trips, replays(null), FakeFiles(trace), FakePreviews(null))
        val a = repo.analysis("t1")!!
        assertNull(a.replay)
        assertEquals(
            listOf(
                ReplaySpan(SegmentStyle.ESTIMATED, 0.0, 2.0),
                ReplaySpan(SegmentStyle.NO_GPS, 4.0, 6.0),
                ReplaySpan(SegmentStyle.EXCLUDED, 10.0, 15.0, SegmentReason.PHONE_MOVED),
                ReplaySpan(SegmentStyle.NO_GPS, 18.0, 20.0),
            ),
            a.data.bands,
        )
        assertEquals(2.0, a.data.estimatedUntilS)
        assertNull(a.data.gg.dotAt(12.0))
        assertTrue(a.data.gg.moments > 0)
    }

    @Test
    fun missingRecordingAndDiscardedTripsHaveNoAnalysis() = runTest {
        val trips = FakeTrips(null, emptyList())
        val repo = TripAnalysisRepository(trips, replays(replay), FakeFiles(null), FakePreviews(null))
        assertNull(repo.analysis("t1"))
        trips.trip = trip(TripStatus.RECORDING)
        assertNull(repo.analysis("t1"))
        trips.trip = trip(TripStatus.DISCARDED)
        assertNull(repo.analysis("t1"))
        trips.trip = trip(TripStatus.INTERRUPTED)
        assertTrue(repo.analysis("t1")!!.data.summaryOnly)
    }
}
