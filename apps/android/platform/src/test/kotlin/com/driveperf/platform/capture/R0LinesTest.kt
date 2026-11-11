package com.driveperf.platform.capture

import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.ActivityType
import com.driveperf.platform.Fixtures
import com.driveperf.platform.Fixtures.fix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class R0LinesTest {
    @Test
    fun `a fix is one line with an empty field for each null`() {
        val line = R0Lines.locations(listOf(fix(1.0, speedMps = null))).trimEnd('\n').split(",")
        assertEquals(12, line.size)
        assertEquals("L", line[0])
        assertEquals("1000000000", line[1])
        assertTrue("null speed is an empty field", line[8].isEmpty())
    }

    @Test
    fun `a batch is one line per sample`() {
        val text = R0Lines.motion(listOf(Fixtures.motion(1.0), Fixtures.motion(1.02), Fixtures.motion(1.04)))
        assertEquals(3, text.lines().count { it.isNotEmpty() })
    }

    @Test
    fun `an activity change carries its type and confidence`() {
        val text = R0Lines.activity(listOf(ActivitySample(5, 6, ActivityType.AUTOMOTIVE, ActivityConfidence.HIGH)))
        assertEquals("A,5,6,AUTOMOTIVE,HIGH\n", text)
    }
}
