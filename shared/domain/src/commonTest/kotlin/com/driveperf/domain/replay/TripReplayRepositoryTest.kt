package com.driveperf.domain.replay

import com.driveperf.domain.capture.PreviewStore
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.replay.ReplayFixtures.STEP_DEG
import com.driveperf.domain.replay.ReplayFixtures.parking
import com.driveperf.domain.replay.ReplayFixtures.span
import com.driveperf.domain.replay.ReplayFixtures.trip
import com.driveperf.domain.score.Score
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.PreviewRow
import com.driveperf.domain.trip.RawState
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.tripfile.AnalysisFileStore
import com.driveperf.domain.tripfile.AnalysisFix
import com.driveperf.domain.tripfile.AnalysisTrace
import com.driveperf.domain.tripfile.FileResult
import com.driveperf.domain.tripfile.TripFileRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripReplayRepositoryTest {
    private class Trips(vararg trips: Trip) : TripRepository {
        private val byId = trips.associateBy { it.id }

        override fun observeTrips(): Flow<List<Trip>> = flowOf(byId.values.toList())

        override suspend fun trip(id: String): Trip? = byId[id]

        override suspend fun events(tripId: String): List<DrivingEvent> = emptyList()

        override suspend fun feedback(tripId: String): Map<String, EventFeedback> = emptyMap()

        override suspend fun score(tripId: String): Score? = null

        override suspend fun delete(tripId: String): FileResult = FileResult.OK
    }

    private class Analysis(private val trace: AnalysisTrace?) : AnalysisFileStore {
        var reads = 0

        override suspend fun write(trip: Trip, trace: AnalysisTrace): TripFileRef? = null

        override suspend fun read(tripId: String): AnalysisTrace? = trace.also { reads++ }
    }

    private class Previews(private val rows: List<PreviewRow>?) : PreviewStore {
        var reads = 0

        override suspend fun write(tripId: String, rows: List<PreviewRow>): Boolean = true

        override suspend fun read(tripId: String): List<PreviewRow>? = rows.also { reads++ }
    }

    private fun trace(fixes: List<AnalysisFix>) =
        AnalysisTrace(1, DoubleArray(0), DoubleArray(0), DoubleArray(0), DoubleArray(0), IntArray(0), fixes)

    private fun r1(from: Int, to: Int) = (from..to).map { AnalysisFix(it.toDouble(), 11.0, it * STEP_DEG, 0.0, 0.5) }

    private fun row(t: Double, lat: Double, lng: Double, q: Int = 0, speed: Double? = 11.0) =
        PreviewRow(t, lat, lng, speed, null, null, null, q)

    private val parkingRow = row(0.0, parking.lat, parking.lng, q = 1, speed = null)
    private val estimatedSpans = listOf(span(SegmentKind.ESTIMATED, 0.0, 60.0))

    @Test
    fun theRouteIsTheR1FixesWithTheParkingSpotFromThePreview() = runTest {
        val analysis = Analysis(trace(r1(60, 180) + AnalysisFix(181.0, 11.0, null, null, null)))
        val previews = Previews(listOf(parkingRow, row(60.0, 0.006, 0.0)))
        val replay = assertNotNull(TripReplayRepository(Trips(trip(estimatedSpans, 181.0)), analysis, previews).replay("trip-1"))
        assertFalse(replay.summaryOnly)
        assertEquals(parking, replay.track.first())
        assertEquals(122, replay.track.size, "parking + 121 positioned fixes; the fix without a position is skipped")
        assertEquals(SegmentStyle.ESTIMATED, replay.segments(15).first().style)
        assertEquals(1, previews.reads)
    }

    @Test
    fun withoutAnEstimateThePreviewIsNotRead() = runTest {
        val previews = Previews(listOf(parkingRow))
        val repo = TripReplayRepository(Trips(trip(durationS = 60.0)), Analysis(trace(r1(0, 60))), previews)
        val replay = assertNotNull(repo.replay("trip-1"))
        assertEquals(0, previews.reads)
        assertEquals(61, replay.track.size)
    }

    @Test
    fun anEstimateWhosePreviewHasNoParkingRowStartsAtTheFirstFix() = runTest {
        val previews = Previews(listOf(row(60.0, 0.006, 0.0)))
        val replay = assertNotNull(
            TripReplayRepository(Trips(trip(estimatedSpans, 180.0)), Analysis(trace(r1(60, 180))), previews).replay("trip-1"),
        )
        assertEquals(60.0, replay.track.first().tS)
        assertEquals(listOf(ReplaySpan(SegmentStyle.ESTIMATED, 0.0, 60.0)), replay.spans)
        assertEquals(SegmentStyle.ESTIMATED, replay.frameAt(10.0).style)
    }

    @Test
    fun aSummaryOnlyTripReplaysItsPreviewTrack() = runTest {
        val analysis = Analysis(trace(r1(60, 180)))
        val rows = listOf(parkingRow) + (0..10).map { row(60.0 + it * 12, (60 + it * 12) * STEP_DEG, 0.0) }
        val trip = trip(estimatedSpans, 180.0, rawState = RawState.SUMMARY_ONLY)
        val replay = assertNotNull(TripReplayRepository(Trips(trip), analysis, Previews(rows)).replay("trip-1"))
        assertTrue(replay.summaryOnly)
        assertEquals(0, analysis.reads, "the R1 file is gone")
        assertEquals(12, replay.track.size)
        assertEquals(parking, replay.track.first())
        assertEquals(listOf(SegmentStyle.ESTIMATED, SegmentStyle.OBSERVED), replay.segments(15).map { it.style })
    }

    @Test
    fun anUnreadableR1FileFallsBackToThePreview() = runTest {
        val rows = (0..10).map { row(it * 6.0, it * STEP_DEG, 0.0) }
        val replay = assertNotNull(TripReplayRepository(Trips(trip(durationS = 60.0)), Analysis(null), Previews(rows)).replay("trip-1"))
        assertTrue(replay.summaryOnly)
        assertEquals(11, replay.track.size)
    }

    @Test
    fun noReplayForMissingUnfinishedOrPositionlessTrips() = runTest {
        val trips = Trips(
            trip(id = "recording", status = TripStatus.RECORDING),
            trip(id = "discarded", status = TripStatus.DISCARDED),
            trip(id = "interrupted", status = TripStatus.INTERRUPTED, durationS = 60.0),
            trip(id = "nothing", durationS = 60.0),
        )
        val repo = TripReplayRepository(trips, Analysis(trace(r1(0, 60))), Previews(null))
        assertNull(repo.replay("missing"))
        assertNull(repo.replay("recording"))
        assertNull(repo.replay("discarded"))
        assertNotNull(repo.replay("interrupted"))
        assertNull(TripReplayRepository(trips, Analysis(null), Previews(null)).replay("nothing"))
    }
}
