package com.driveperf.data.local

import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.CaptureBatch
import com.driveperf.domain.capture.CaptureHeader
import com.driveperf.domain.capture.CaptureMark
import com.driveperf.domain.capture.CaptureMarkType
import com.driveperf.domain.capture.GeoPoint
import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.MotionSample
import com.driveperf.domain.capture.Parking
import com.driveperf.domain.capture.Quat
import com.driveperf.domain.capture.RecordedAccelSample
import com.driveperf.domain.capture.Vec3
import com.driveperf.domain.capture.WakeKind
import com.driveperf.domain.tripfile.TripFileKind
import com.driveperf.testing.InMemoryTripFileStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtoCaptureLogTest {
    private val files = InMemoryTripFileStore()
    private val start = 5_000_000_000_000L
    private val header = CaptureHeader(
        "trip-1", start, 1_794_268_800_000L, -300, "Etc/GMT+5", WakeKind.PARKED_EXIT, "app", 50,
        Parking(GeoPoint(2.1234567, -27.7654321), 1_794_265_200_000L),
    )

    private fun t(s: Double): Long = start + (s * 1e9).toLong()

    private val fix = LocationSample(t(1.0), 1_794_268_801_003L, 2.1234567, -27.7654321, -12.3, 4.5, null, 13.89, 0.3, 359.9, 5.0)
    private val bareFix = LocationSample(t(2.0), 1_794_268_802_000L, 2.1235, -27.765, null, null, null, null, null, null, null)
    private val motion = listOf(
        MotionSample(t(1.0), Vec3(0.012, -0.034, 0.5), Vec3(0.0, 0.0, 1.0), Vec3(1.25, -3.5, 0.0), Quat(1.0, 0.0, 0.0, 0.0)),
        MotionSample(t(1.02), Vec3(-0.012, 0.034, -0.5), Vec3(0.0, 0.7071, 0.7071), Vec3(0.0, 0.0, 90.0), null),
    )

    @Test
    fun everyChannelRoundTripsWithinQuantisation() = runTest {
        val log = ProtoCaptureLog(files, coroutineContext)
        assertTrue(log.open(header))
        assertTrue(
            log.append(
                "trip-1",
                CaptureBatch(
                    fixes = listOf(fix, bareFix),
                    motion = motion,
                    activity = listOf(ActivitySample(t(-30.0), 1_794_268_770_000L, ActivityType.AUTOMOTIVE, ActivityConfidence.HIGH)),
                    recordedAccel = listOf(RecordedAccelSample(t(-60.0), Vec3(0.0, 0.0, 1.001))),
                    marks = listOf(CaptureMark(t(0.5), CaptureMarkType.CONFIRMED), CaptureMark(t(0.6), CaptureMarkType.IMU_RATE, "25")),
                ),
            ),
        )
        val back = assertNotNull(log.read("trip-1"))
        assertEquals(header, back.header.copy(parking = header.parking))
        assertEquals(2.1234567, back.header.parking!!.point.lat, 1e-9)
        val f = back.fixes[0]
        assertEquals(fix.tMonoNs, f.tMonoNs)
        assertEquals(fix.wallMs, f.wallMs)
        assertEquals(fix.lat, f.lat, 1e-9)
        assertEquals(fix.lng, f.lng, 1e-9)
        assertEquals(-12.3, f.altM!!, 0.051)
        assertEquals(13.89, f.speedMps!!, 0.0051)
        assertEquals(359.9, f.courseDeg!!, 0.051)
        assertNull(f.vAccM)
        val bare = back.fixes[1]
        assertNull(bare.speedMps)
        assertNull(bare.hAccM)
        assertNull(bare.altM)
        assertEquals(0.012, back.motion[0].userAccelG.x, 0.0005)
        assertEquals(90.0, back.motion[1].rotRateDps.z, 0.005)
        assertEquals(1.0, back.motion[0].attitude!!.w, 1e-4)
        assertNull(back.motion[1].attitude)
        assertEquals(ActivityType.AUTOMOTIVE, back.activity.single().type)
        assertEquals(t(-30.0), back.activity.single().tMonoNs)
        assertEquals(1.001, back.recordedAccel.single().accelG.z, 0.0005)
        assertEquals(listOf(CaptureMarkType.CONFIRMED, CaptureMarkType.IMU_RATE), back.marks.map { it.type })
        assertEquals("25", back.marks[1].detail)
    }

    @Test
    fun aTruncatedLastChunkIsDroppedAndTheRestKept() = runTest {
        val log = ProtoCaptureLog(files, coroutineContext)
        log.open(header)
        log.append("trip-1", CaptureBatch(fixes = listOf(fix)))
        log.sync("trip-1")
        log.append("trip-1", CaptureBatch(fixes = listOf(bareFix)))
        val full = files.read("trip-1", TripFileKind.R0)!!
        files.write("trip-1", TripFileKind.R0, full.copyOfRange(0, full.size - 3))
        assertEquals(1, log.read("trip-1")!!.fixes.size)
    }

    @Test
    fun aRelaunchedLogReadsItsHeaderBackFromTheFile() = runTest {
        ProtoCaptureLog(files, coroutineContext).open(header)
        val relaunched = ProtoCaptureLog(files, coroutineContext)
        assertTrue(relaunched.append("trip-1", CaptureBatch(fixes = listOf(fix))))
        assertEquals(1, relaunched.read("trip-1")!!.fixes.size)
        assertTrue(!relaunched.append("missing", CaptureBatch(fixes = listOf(fix))))
        relaunched.delete("trip-1")
        assertNull(relaunched.read("trip-1"))
    }

    @Test
    fun garbageIsNotATripFile() {
        assertNull(TripFileCodec.decode(byteArrayOf(1, 2, 3)))
        assertNull(TripFileCodec.decode("DPTF".encodeToByteArray() + byteArrayOf(0x7F)))
        val headerOnly = TripFileCodec.encodeHeader(header)
        assertEquals(0, TripFileCodec.decode(headerOnly)!!.fixes.size)
        val corrupt = headerOnly + byteArrayOf(3, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        assertEquals("trip-1", TripFileCodec.decode(corrupt)!!.header.tripId)
    }
}

class ProtoPreviewStoreTest {
    @Test
    fun previewRowsRoundTripWithinQuantisation() = kotlinx.coroutines.test.runTest {
        val store = ProtoPreviewStore(InMemoryTripFileStore(), coroutineContext)
        val rows = listOf(
            com.driveperf.domain.trip.PreviewRow(0.0, 2.0000001, -27.5, null, null, null, null, 1),
            com.driveperf.domain.trip.PreviewRow(12.345, 2.0001, -27.4999, 13.89, -0.123, 0.045, 10.2, 0),
        )
        assertTrue(store.write("t1", rows))
        val back = assertNotNull(store.read("t1"))
        assertEquals(2, back.size)
        assertEquals(12.345, back[1].tS, 0.0005)
        assertEquals(2.0001, back[1].lat, 1e-9)
        assertEquals(13.89, back[1].speedMps!!, 0.005)
        assertEquals(-0.123, back[1].lonG!!, 0.0005)
        assertNull(back[0].speedMps)
        assertEquals(1, back[0].quality)
        assertNull(store.read("missing"))
    }
}
