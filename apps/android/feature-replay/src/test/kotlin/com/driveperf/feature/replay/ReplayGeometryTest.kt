package com.driveperf.feature.replay

import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.BoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayGeometryTest {
    @Test fun `the projection fits the box inside the insets, north up`() {
        val box = BoundingBox(10.0, 20.0, 10.1, 20.1)
        val p = ReplayProjection(box, 1000f, 2000f, ReplayProjection.Insets(50f, 300f, 50f, 400f))
        assertEquals(50f, p.x(20.0), 0.5f)
        assertEquals(950f, p.x(20.1), 0.5f)
        assertTrue("north is up", p.y(10.1) < p.y(10.0))
        assertTrue(p.y(10.1) >= 300f && p.y(10.0) <= 1600f)
    }

    @Test fun `the zoom bucket grows as the route gets shorter`() {
        val wide = ReplayProjection(BoundingBox(10.0, 20.0, 11.0, 21.0), 400f, 800f, ReplayProjection.Insets(0f, 0f, 0f, 0f))
        val narrow = ReplayProjection(BoundingBox(10.0, 20.0, 10.01, 20.01), 400f, 800f, ReplayProjection.Insets(0f, 0f, 0f, 0f))
        assertTrue(narrow.zoomBucket(1f) > wide.zoomBucket(1f))
        assertEquals(wide.zoomBucket(1f) - 1, wide.zoomBucket(2f))
    }

    @Test fun `distance so far integrates the track`() {
        val timeline = ReplayTimeline(TestReplays.trip())
        assertEquals(0.0, timeline.distanceAt(0.0), 0.0)
        // 0.00009° of latitude ≈ 10 m per second.
        assertEquals(1000.0, timeline.distanceAt(100.0), 5.0)
        assertEquals(timeline.totalM, timeline.distanceAt(10_000.0), 0.0)
        assertEquals(6000.0, timeline.totalM, 30.0)
    }

    @Test fun `readouts use the display units`() {
        assertEquals("36", ReplayFormatting.speed(10.0, UnitSystem.METRIC))
        assertEquals("22", ReplayFormatting.speed(10.0, UnitSystem.IMPERIAL))
        assertEquals("mph", ReplayFormatting.speedUnit(UnitSystem.IMPERIAL))
        assertEquals(null, ReplayFormatting.speed(null, UnitSystem.METRIC))
        assertEquals("1.6", ReplayFormatting.distance(1609.344, UnitSystem.METRIC))
        assertEquals("1.0", ReplayFormatting.distance(1609.344, UnitSystem.IMPERIAL))
        assertEquals("mi", ReplayFormatting.distanceUnit(UnitSystem.IMPERIAL))
    }

    @Test fun `the clock reads minutes and seconds, with hours on long drives`() {
        assertEquals("0:00", ReplayFormatting.clock(0.0))
        assertEquals("12:05", ReplayFormatting.clock(725.9))
        assertEquals("1:02:09", ReplayFormatting.clock(3729.0))
    }
}
