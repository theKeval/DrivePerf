package com.driveperf.domain.replay

import com.driveperf.domain.replay.ReplayFixtures.trip
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.score.Score
import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.RawState
import com.driveperf.domain.trip.Trip
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventEvidenceTest {
    /** 60 s at 5 Hz: lonG = −k/100, latG = k/200, one fix per second at 20 − t/3 m/s. */
    private fun trace(rateHz: Int = 5, seconds: Int = 60): AnalysisTrace {
        val n = rateHz * seconds
        return AnalysisTrace(
            rateHz,
            DoubleArray(n) { -it / 100.0 },
            DoubleArray(n) { it / 200.0 },
            DoubleArray(n),
            DoubleArray(n),
            IntArray(n),
            (0 until seconds).map { AnalysisFix(it.toDouble(), 20 - it / 3.0, null, null, null) },
        )
    }

    private fun event(peak: Double) =
        DrivingEvent(
            "e1", "trip-1", EventType.BRAKE, peak, peak - 1, peak + 1, 2.0, 0.4, null, null, null, 15.0, 5.0, Confidence.HIGH, "v1",
        )

    @Test
    fun theWindowIsTenSecondsEitherSideOfThePeak() {
        val evidence = assertNotNull(EvidenceWindows.cut(trace(), event(30.0)))
        assertEquals(20.0, evidence.t0S)
        assertEquals(40.0, evidence.t1S)
        assertEquals(101, evidence.g.size, "5 Hz over 20 s, both ends included")
        assertEquals(EvidenceSample(20.0, -1.0, 0.5), evidence.g.first())
        assertEquals(21, evidence.speed.size)
        assertEquals(29.0, evidence.tStartS)
        assertEquals(31.0, evidence.tEndS)
        assertEquals(2.0, evidence.maxAbsG, 1e-9)
        assertEquals(20 - 20 / 3.0, evidence.maxSpeedMps, 1e-9)
    }

    @Test
    fun anAbsentSampleStaysAGapAndDoesNotPoisonTheRanges() {
        // AnalysisFileCodec decodes an absent sample as NaN: here the window's largest |G| and fastest fix.
        val base = trace()
        val lonG = base.lonG.copyOf().also { it[200] = Double.NaN }
        val fixes = base.fixes.map { if (it.tS == 20.0) it.copy(speedMps = Double.NaN) else it }
        val gappy = AnalysisTrace(base.rateHz, lonG, base.latG, base.vertG, base.yawDps, base.quality, fixes)
        val evidence = assertNotNull(EvidenceWindows.cut(gappy, event(30.0)))
        assertTrue(evidence.g.last().lonG.isNaN(), "the gap reaches the chart, which leaves it undrawn")
        assertEquals(1.99, evidence.maxAbsG, 1e-9)
        assertEquals(20 - 21 / 3.0, evidence.maxSpeedMps, 1e-9)
        val allAbsent = EventEvidence("e", 0.0, 1.0, 0.0, 0.5, 1.0, listOf(EvidenceSample(0.5, Double.NaN, Double.NaN)), emptyList())
        assertEquals(0.0, allAbsent.maxAbsG)
    }

    @Test
    fun theWindowIsClampedToTheTrace() {
        val early = assertNotNull(EvidenceWindows.cut(trace(), event(3.0)))
        assertEquals(0.0, early.t0S)
        assertEquals(0.0, early.g.first().tS)
        val late = assertNotNull(EvidenceWindows.cut(trace(), event(55.0)))
        assertEquals(59.8, late.g.last().tS, 1e-9)
    }

    @Test
    fun anEventTheTraceDoesNotCoverHasNoEvidence() {
        assertNull(EvidenceWindows.cut(trace(), event(500.0)))
        assertNull(EvidenceWindows.cut(trace(rateHz = 0), event(5.0)))
        val empty = EventEvidence("e", 0.0, 1.0, 0.0, 0.5, 1.0, emptyList(), emptyList())
        assertEquals(0.0, empty.maxAbsG)
        assertEquals(0.0, empty.maxSpeedMps)
    }

    private class Trips(private val trip: Trip?) : TripRepository {
        override fun observeTrips(): Flow<List<Trip>> = flowOf(listOfNotNull(trip))

        override suspend fun trip(id: String): Trip? = trip?.takeIf { it.id == id }

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

    @Test
    fun theTraceIsReadOncePerTrip() = runTest {
        val analysis = Analysis(trace())
        val evidence = TripEvidence(Trips(trip(durationS = 60.0)), analysis)
        assertNotNull(evidence.evidence("trip-1", event(30.0)))
        assertNotNull(evidence.evidence("trip-1", event(40.0)))
        assertEquals(1, analysis.reads)
    }

    @Test
    fun aTraceThatWasNotKeptGivesNoEvidence() = runTest {
        val trimmed = Analysis(trace())
        val summaryOnly = TripEvidence(Trips(trip(durationS = 60.0, rawState = RawState.SUMMARY_ONLY)), trimmed)
        assertNull(summaryOnly.evidence("trip-1", event(30.0)))
        assertEquals(0, trimmed.reads, "a summary-only trip has no R1 file to read")
        assertNull(TripEvidence(Trips(trip(durationS = 60.0)), Analysis(null)).evidence("trip-1", event(30.0)))
        assertNull(TripEvidence(Trips(null), Analysis(trace())).evidence("trip-1", event(30.0)))
    }
}
