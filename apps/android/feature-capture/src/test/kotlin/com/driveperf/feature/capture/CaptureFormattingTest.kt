package com.driveperf.feature.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureFormattingTest {
    @Test fun `elapsed under an hour is minutes and seconds`() {
        assertEquals("0:00", CaptureFormatting.elapsed(0))
        assertEquals("0:59", CaptureFormatting.elapsed(59_999))
        assertEquals("24:05", CaptureFormatting.elapsed((24 * 60 + 5) * 1000L))
    }

    @Test fun `elapsed over an hour adds hours`() {
        assertEquals("1:02:09", CaptureFormatting.elapsed((3600 + 2 * 60 + 9) * 1000L))
    }

    @Test fun `negative elapsed from a clock step is shown as zero`() {
        assertEquals("0:00", CaptureFormatting.elapsed(-5_000))
    }

    @Test fun `distance is kilometres with one decimal`() {
        assertEquals("0.0", CaptureFormatting.distanceKm(0.0))
        assertEquals("12.4", CaptureFormatting.distanceKm(12_449.0))
    }
}
