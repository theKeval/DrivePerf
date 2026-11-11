package com.driveperf.data.local

import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.tripfile.AnalysisFix
import com.driveperf.domain.tripfile.AnalysisTrace
import com.driveperf.domain.tripfile.FileResult
import com.driveperf.domain.tripfile.TripFileKind
import com.driveperf.domain.tripfile.TripFileStore
import com.driveperf.testing.InMemoryTripFileStore
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtoAnalysisFileStoreTest {
    private val files = InMemoryTripFileStore()
    private val trip = Trip(
        id = "trip-r1",
        status = TripStatus.FINALISED,
        kind = TripKind.DRIVER,
        source = TripSource.MANUAL,
        startedAt = 1_794_268_800_000L,
        startTzOffsetMin = -300,
        tzId = "Etc/GMT+5",
        pipelineVersion = "pipe-1.0.0",
        createdAt = 1L,
        updatedAt = 1L,
    )

    /** 150 s at 25 Hz, so the file spans three one-minute chunks, with one non-finite sample. */
    private val trace: AnalysisTrace = run {
        val n = 3750
        val lon = DoubleArray(n) { k -> 0.1234567 * kotlin.math.sin(k / 50.0) }
        lon[100] = Double.NaN
        AnalysisTrace(
            rateHz = 25,
            lonG = lon,
            latG = DoubleArray(n) { k -> -0.0015 + k * 1e-5 },
            vertG = DoubleArray(n) { 0.0025 },
            yawDps = DoubleArray(n) { k -> 12.345 * kotlin.math.cos(k / 80.0) },
            quality = IntArray(n) { k -> if (k < 50) 1 shl 3 else 0 },
            fixes = (0 until 150).map { s ->
                AnalysisFix(s + 0.1234, 13.0 + s * 0.01, 2.5 + s * 1e-4, if (s == 7) null else -27.5, if (s % 2 == 0) 0.4 else null)
            },
        )
    }

    @Test
    fun anR1FileRoundTripsWithinItsQuantisation() = runTest {
        val store = ProtoAnalysisFileStore(files, coroutineContext)
        val ref = assertNotNull(store.write(trip, trace))
        assertEquals(TripFileKind.R1, ref.kind)
        assertEquals(1, ref.formatVersion)
        assertEquals("trips/trip-r1/r1.v1.pb.gz", ref.localPath)
        assertTrue(assertNotNull(files.describe("trip-r1", TripFileKind.R1, false)).compressed, "gzip at finalisation")

        val back = assertNotNull(store.read("trip-r1"))
        assertEquals(25, back.rateHz)
        assertEquals(trace.size, back.size)
        assertTrue(back.lonG[100].isNaN(), "a non-finite sample is kept as absent")
        for (k in 0 until trace.size) {
            if (k != 100) assertTrue(abs(back.lonG[k] - trace.lonG[k]) <= 0.000501, "lon $k")
            assertTrue(abs(back.latG[k] - trace.latG[k]) <= 0.000501, "lat $k")
            assertTrue(abs(back.vertG[k] - trace.vertG[k]) <= 0.000501, "vert $k")
            assertTrue(abs(back.yawDps[k] - trace.yawDps[k]) <= 0.00501, "yaw $k")
        }
        assertEquals(trace.quality.toList(), back.quality.toList())
        assertEquals(trace.fixes.size, back.fixes.size)
        trace.fixes.zip(back.fixes).forEach { (a, b) ->
            assertEquals(a.tS, b.tS, 0.0005)
            assertEquals(a.speedMps, b.speedMps, 0.005)
            assertEquals(a.lat == null, b.lat == null)
            assertEquals(a.lat ?: 0.0, b.lat ?: 0.0, 1e-7)
            assertEquals(a.lng == null, b.lng == null)
            assertEquals(a.lng ?: 0.0, b.lng ?: 0.0, 1e-7)
            assertEquals(a.speedAccMps, b.speedAccMps)
        }
    }

    @Test
    fun quantisationRoundsHalfToEven() {
        val t = AnalysisTrace(25, doubleArrayOf(0.0025, 0.0035), doubleArrayOf(0.0, 0.0), doubleArrayOf(0.0, 0.0),
            doubleArrayOf(0.125, 0.135), intArrayOf(0, 0), emptyList())
        val back = assertNotNull(AnalysisFileCodec.decode(AnalysisFileCodec.encode(trip, t)))
        assertEquals(listOf(0.002, 0.004), back.lonG.toList())
        assertEquals(0.12, back.yawDps[0], 1e-9)
    }

    @Test
    fun anEmptyTraceAndForeignBytesAreHandled() = runTest {
        val empty = AnalysisTrace(25, DoubleArray(0), DoubleArray(0), DoubleArray(0), DoubleArray(0), IntArray(0), emptyList())
        assertEquals(0, assertNotNull(AnalysisFileCodec.decode(AnalysisFileCodec.encode(trip, empty))).size)
        assertNull(AnalysisFileCodec.decode(byteArrayOf(1, 2, 3, 4, 5)))
        files.write("r0-only", TripFileKind.R1, TripFileCodec.encodeHeader(
            com.driveperf.domain.capture.CaptureHeader(
                "r0-only", 0L, 0L, 0, "Etc/UTC", com.driveperf.domain.capture.WakeKind.MANUAL, null, 50, null,
            ),
        ))
        assertNull(ProtoAnalysisFileStore(files, coroutineContext).read("r0-only"), "an R0 header is not an R1 file")
        assertNull(ProtoAnalysisFileStore(files, coroutineContext).read("missing"))
    }

    @Test
    fun aWriteFailureLeavesNoFileAndNoReference() = runTest {
        val failing = object : TripFileStore by files {
            override fun compress(tripId: String, kind: TripFileKind): FileResult = FileResult.DISK_FULL
        }
        assertNull(ProtoAnalysisFileStore(failing, coroutineContext).write(trip, trace))
        assertNull(files.describe("trip-r1", TripFileKind.R1, false))
    }
}
