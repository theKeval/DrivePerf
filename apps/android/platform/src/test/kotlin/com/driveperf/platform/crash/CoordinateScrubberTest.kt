package com.driveperf.platform.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateScrubberTest {
    @Test
    fun `detects a coordinate pair`() {
        assertTrue(CoordinateScrubber.containsCoordinates("fix at 12.34567, -76.54321 failed"))
        assertTrue(CoordinateScrubber.containsCoordinates("12.34567,-76.54321"))
        assertTrue(CoordinateScrubber.containsCoordinates("1.2345678 2.3456789"))
    }

    @Test
    fun `leaves ordinary numbers alone`() {
        assertFalse(CoordinateScrubber.containsCoordinates("distance 12.4 km in 24 min"))
        assertFalse(CoordinateScrubber.containsCoordinates("version 1.2.3, build 456"))
        assertFalse(CoordinateScrubber.containsCoordinates(null))
        assertFalse(CoordinateScrubber.containsCoordinates(""))
    }

    @Test
    fun `scrub replaces the pair and keeps the rest`() {
        assertEquals("fix at [location removed] failed", CoordinateScrubber.scrub("fix at 12.34567, -76.54321 failed"))
    }
}
