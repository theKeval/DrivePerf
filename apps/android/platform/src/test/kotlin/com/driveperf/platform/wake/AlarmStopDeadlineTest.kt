package com.driveperf.platform.wake

import android.app.AlarmManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmStopDeadlineTest {
    private val context = RuntimeEnvironment.getApplication()
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val deadline = AlarmStopDeadline(context)

    @Test
    fun `the stop deadline is a wakeup alarm on elapsed realtime that fires in Doze`() {
        deadline.schedule(atMonoNs = 3_600_123_456_789L) {}
        val alarm = alarms.scheduledAlarms.single()
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, alarm.getType())
        assertEquals(3_600_123L, alarm.triggerAtMs)
        assertTrue("allowed while idle, so Doze does not hold it to the next drive", alarm.isAllowWhileIdle)
    }

    @Test
    fun `scheduling again replaces the alarm and its callback`() {
        var first = 0
        var second = 0
        deadline.schedule(1_000_000_000L) { first++ }
        deadline.schedule(2_000_000_000L) { second++ }
        assertEquals(2_000L, alarms.scheduledAlarms.single().triggerAtMs)
        assertTrue(AlarmStopDeadline.fire())
        assertEquals(0, first)
        assertEquals(1, second)
        assertFalse("it fires once", AlarmStopDeadline.fire())
    }

    @Test
    fun `cancelling removes the alarm and the callback`() {
        var fired = false
        deadline.schedule(1_000_000_000L) { fired = true }
        deadline.cancel()
        assertTrue(alarms.scheduledAlarms.isEmpty())
        assertFalse(AlarmStopDeadline.fire())
        assertFalse(fired)
    }
}
