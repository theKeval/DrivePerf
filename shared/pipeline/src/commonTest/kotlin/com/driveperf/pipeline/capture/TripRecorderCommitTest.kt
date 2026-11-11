package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.capture.FinalisedTrip
import com.driveperf.domain.capture.WakeEvent
import com.driveperf.domain.capture.WakeKind
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.tripfile.AnalysisFileStore
import com.driveperf.domain.tripfile.AnalysisTrace
import com.driveperf.domain.tripfile.TripFileKind
import com.driveperf.domain.tripfile.TripFileRef
import com.driveperf.pipeline.capture.CaptureFixtures.fix
import com.driveperf.pipeline.capture.CaptureFixtures.parking
import com.driveperf.testing.FakeActivitySource
import com.driveperf.testing.FakeLocationSource
import com.driveperf.testing.FakeMotionSource
import com.driveperf.testing.FakeParkedRegionMonitor
import com.driveperf.testing.FakePowerState
import com.driveperf.testing.FixedTimeZone
import com.driveperf.testing.InMemoryCaptureLog
import com.driveperf.testing.InMemoryTripFileStore
import com.driveperf.testing.InMemoryTripStore
import com.driveperf.testing.TestClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the recorder commits for a finished trip: R1, file rows and, for a split trip, each part's own R0. */
@OptIn(ExperimentalCoroutinesApi::class)
class TripRecorderCommitTest {
    private val clock = TestClock()
    private val location = FakeLocationSource()
    private val files = InMemoryTripFileStore()
    private val store = InMemoryTripStore(parking)
    private val log = InMemoryCaptureLog()
    private val trace = AnalysisTrace(25, DoubleArray(2), DoubleArray(2), DoubleArray(2), DoubleArray(2), IntArray(2), emptyList())

    private val analysis = object : AnalysisFileStore {
        val written = mutableMapOf<String, AnalysisTrace>()

        override suspend fun write(trip: Trip, trace: AnalysisTrace): TripFileRef {
            written[trip.id] = trace
            return TripFileRef(TripFileKind.R1, 1, "sha-${trip.id}", 2L, "trips/${trip.id}/r1.v1.pb.gz")
        }

        override suspend fun read(tripId: String): AnalysisTrace? = written[tripId]
    }

    private fun TestScope.engine(finaliser: TripFinaliser) = TripRecorderEngine(
        CapturePorts(location, FakeMotionSource(), FakeActivitySource(), FakePowerState(), FakeParkedRegionMonitor(), files),
        CapturePersistence(store, log, finaliser, analysis = analysis),
        clock,
        FixedTimeZone("Etc/UTC", 0),
        backgroundScope,
    )

    /** Records a manual trip with 20 fixes and stops it; returns its ID. */
    private fun TestScope.record(engine: TripRecorderEngine): String {
        engine.onLaunch()
        runCurrent()
        clock.advanceS(10.0)
        engine.onWake(WakeEvent(WakeKind.MANUAL, clock.monotonicNs(), clock.wallMs(), "app"))
        runCurrent()
        repeat(20) {
            clock.advanceS(1.0)
            location.emit(fix(clock.monotonicNs() / 1e9, 15.0, it * 15.0))
            runCurrent()
        }
        val id = store.trips.keys.single()
        engine.stop()
        runCurrent()
        return id
    }

    /** Splits whatever it gets into two parts of ten fixes each, the second with a new ID. */
    private fun splitting(dropFirst: Boolean = false) = TripFinaliser { request ->
        val c = request.capture
        val (a, b) = c.fixes.chunked(c.fixes.size / 2)
        val first = c.copy(fixes = a)
        val second = CapturedTrip(c.header.copy(tripId = "part-2"), b, emptyList(), emptyList(), emptyList(), emptyList())
        suspend fun part(capture: CapturedTrip) = FinalisedTrip(
            TripSummariser().finalise(request.copy(capture = capture)).trip!!.trip,
            analysis = trace,
            splitCapture = capture,
        )
        FinaliseResult(listOfNotNull(if (dropFirst) null else part(first), part(second)), null)
    }

    @Test
    fun aFinishedTripCommitsWithItsR1Reference() = runTest {
        val id = record(engine { TripSummariser().finalise(it).let { r -> r.copy(trips = r.trips.map { t -> t.copy(analysis = trace) }) } })
        val committed = store.finalised.single()
        assertEquals(id, committed.trip.id)
        assertEquals(listOf(TripFileKind.R1), committed.files.map { it.kind })
        assertEquals("sha-$id", committed.files.single().sha256)
        assertNull(committed.analysis, "the trace is not kept in memory after it is written")
        assertTrue(analysis.written.containsKey(id))
    }

    @Test
    fun aSplitTripRewritesEachPartsCaptureLog() = runTest {
        val id = record(engine(splitting()))
        assertEquals(listOf(id, "part-2"), store.finalised.map { it.trip.id })
        assertEquals(10, assertNotNull(log.read(id)).fixes.size, "the first part's R0 keeps only its own samples")
        assertEquals(10, assertNotNull(log.read("part-2")).fixes.size)
        assertEquals("part-2", log.read("part-2")!!.header.tripId)
        assertTrue(store.finalised.all { it.splitCapture == null && it.files.isNotEmpty() })
    }

    @Test
    fun aSplitWhoseFirstPartIsDiscardedDropsTheOriginalTrip() = runTest {
        val id = record(engine(splitting(dropFirst = true)))
        assertEquals(listOf("part-2"), store.finalised.map { it.trip.id })
        assertNull(store.trips[id])
        assertNull(log.read(id))
        assertNotNull(log.read("part-2"))
    }

    @Test
    fun theR0ReferenceIsRecordedWhenTheLogIsAFile() = runTest {
        files.write("r0-trip", TripFileKind.R0, byteArrayOf(1, 2, 3))
        val finaliser = TripFinaliser { request ->
            val trip = TripSummariser().finalise(request).trip!!.trip.copy(id = "r0-trip")
            FinaliseResult(FinalisedTrip(trip), null)
        }
        val id = record(engine(finaliser))
        assertNull(log.read(id), "the recorder's own trip was not kept, so its log is gone")
        val r0 = store.finalised.single().files.single()
        assertEquals(TripFileKind.R0, r0.kind)
        assertEquals("trips/r0-trip/r0.v1.pb.gz", r0.localPath)
        assertTrue(assertNotNull(files.describe("r0-trip", TripFileKind.R0, false)).compressed)
    }
}
