package com.driveperf.data.local

import com.driveperf.domain.capture.CaptureBatch
import com.driveperf.domain.capture.CaptureHeader
import com.driveperf.domain.capture.FixProvider
import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.WakeKind
import driveperf.tripfile.v1.Chunk
import driveperf.tripfile.v1.Gnss
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** DRIVE-167 (F-DQ-03): every fix keeps its provider, age and accuracy in R0, and older R0 files still read. */
class FixMetadataCodecTest {
    private val start = 7_000_000_000_000L
    private val header = CaptureHeader("trip-167", start, 1_794_268_800_000L, 0, "UTC", WakeKind.MANUAL, "app", 50, null)

    private fun fix(s: Int, provider: FixProvider?, ageMs: Long?, hAccM: Double? = 4.0) = LocationSample(
        start + s * 1_000_000_000L, 1_794_268_800_000L + s * 1000L, 2.0 + s * 1e-4, -27.5, null, hAccM, null, 12.0, null,
        null, null, provider, ageMs,
    )

    @Test
    fun providerAgeAndAccuracyRoundTrip() {
        val fixes = listOf(
            fix(1, FixProvider.GNSS, 120L),
            fix(2, FixProvider.FUSED, 0L, hAccM = 35.5),
            fix(3, FixProvider.NETWORK, 2_500L),
            fix(4, FixProvider.ACCESSORY, 15L),
            fix(5, FixProvider.SIMULATED, null),
            fix(6, null, 40L, hAccM = null),
        )
        val bytes = TripFileCodec.encodeHeader(header) + TripFileCodec.encodeChunk(header, CaptureBatch(fixes = fixes))
        val back = assertNotNull(TripFileCodec.decode(bytes)).fixes

        assertEquals(fixes.map { it.provider }, back.map { it.provider })
        assertEquals(fixes.map { it.ageMs }, back.map { it.ageMs })
        assertEquals(35.5, back[1].hAccM!!, 0.051)
        assertNull(back[5].hAccM)
    }

    @Test
    fun anAgeBeyondTheFieldRangeIsClampedNotWrapped() {
        val bytes = TripFileCodec.encodeHeader(header) +
            TripFileCodec.encodeChunk(header, CaptureBatch(fixes = listOf(fix(1, FixProvider.GNSS, Long.MAX_VALUE))))
        assertEquals(Int.MAX_VALUE.toLong(), TripFileCodec.decode(bytes)?.fixes?.single()?.ageMs)
    }

    @Test
    fun filesWrittenBeforeDrive167ReadWithUnknownProviderAndAge() {
        // A chunk exactly as the format-1 writer produced it before Gnss fields 13-14 existed.
        val old = Chunk(
            chunk_t0_ms = 1_000,
            gnss = Gnss(
                dt_ms = listOf(0L, 1_000L), wall_ms_delta = listOf(0L, 0L), lat = listOf(20_000_000, 1_000),
                lng = listOf(-275_000_000, 0), alt = listOf(Fixed.ABSENT, 0), h_acc = listOf(40, 5), v_acc = listOf(Fixed.ABSENT, 0),
                speed = listOf(1_200, 10), speed_acc = listOf(Fixed.ABSENT, 0), course = listOf(Fixed.ABSENT, 0),
                course_acc = listOf(Fixed.ABSENT, 0),
            ),
        )
        val bytes = TripFileCodec.encodeHeader(header) + Fixed.delimited(Chunk.ADAPTER.encode(old))

        val fixes = assertNotNull(TripFileCodec.decode(bytes)).fixes
        assertEquals(2, fixes.size)
        assertEquals(listOf(4.0, 4.5), fixes.map { it.hAccM!! }.map { kotlin.math.round(it * 10) / 10 })
        assertEquals(listOf(null, null), fixes.map { it.provider })
        assertEquals(listOf(null, null), fixes.map { it.ageMs })
    }

    @Test
    fun unknownProviderCodesFromNewerWritersReadAsUnknown() {
        assertNull(FixProviderCode.provider(0))
        assertNull(FixProviderCode.provider(99))
        FixProvider.entries.forEach { assertEquals(it, FixProviderCode.provider(FixProviderCode.of(it))) }
        assertEquals(0, FixProviderCode.of(null))
    }
}
