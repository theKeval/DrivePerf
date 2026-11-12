package com.driveperf.platform.wake

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.ApplicationStartInfo
import android.content.Context
import android.os.Process
import com.driveperf.testing.TestClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowActivityManager
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WakeHealthTest {
    private val app = RuntimeEnvironment.getApplication()
    private val preferences = app.getSharedPreferences("wake_health", Context.MODE_PRIVATE)
    private val clock = TestClock().apply { advanceS(10.0) }
    private val health = WakeHealth(preferences, clock)

    @Test
    fun `only successful current registrations advance the persisted check-in`() {
        val first = health.refresh(optedIn = true, allowed = true)
        health.activityRegistrationFinished(first, armed = false)
        assertEquals(AutoDetectMode.NEEDS_ATTENTION, health.status.value.mode)
        assertNull(health.status.value.lastCheckInMs)

        val second = health.refresh(optedIn = true, allowed = true)
        health.activityRegistrationFinished(first, armed = true)
        assertNull(health.status.value.lastCheckInMs)
        health.activityRegistrationFinished(second, armed = true)
        assertEquals(AutoDetectMode.ON, health.status.value.mode)
        assertEquals(clock.wallMs(), health.status.value.lastCheckInMs)

        clock.advanceS(30.0)
        health.parkedRegionRegistered()
        assertEquals(clock.wallMs(), WakeHealth(preferences, clock).status.value.lastCheckInMs)
    }

    @Test
    fun `permission loss needs attention and opt-out is off even if an old registration succeeds`() {
        val first = health.refresh(optedIn = true, allowed = true)
        health.refresh(optedIn = true, allowed = false)
        assertEquals(AutoDetectMode.NEEDS_ATTENTION, health.status.value.mode)
        health.refresh(optedIn = false, allowed = false)
        health.activityRegistrationFinished(first, armed = true)
        assertEquals(AutoDetectMode.OFF, health.status.value.mode)
        assertNull(health.status.value.lastCheckInMs)
    }

    @Test
    fun `force-stop notice survives restarts until dismissed and old exits are not replayed`() {
        health.onLaunch(optedIn = true) { true }
        assertTrue(health.status.value.forceStopNotice)
        val lastLaunch = clock.wallMs()
        clock.advanceS(5.0)

        val reopened = WakeHealth(preferences, clock)
        reopened.onLaunch(optedIn = true) { false }
        assertTrue(reopened.status.value.forceStopNotice)
        reopened.dismissForceStopNotice()

        val later = WakeHealth(preferences, clock)
        later.onLaunch(optedIn = true) { sinceMs -> lastLaunch > sinceMs }
        assertFalse(later.status.value.forceStopNotice)
    }

    @Test
    fun `fallback distinguishes force stop from other user-requested exits`() {
        val userRequested = ApplicationExitInfo.REASON_USER_REQUESTED
        val packageName = "com.driveperf.app"
        assertTrue(ForceStopHistory.matchesForceStop(userRequested, "[FORCE STOP] stop com.driveperf.app", packageName))
        assertTrue(ForceStopHistory.matchesForceStop(userRequested, "stop com.driveperf.app due to from pid 42", packageName))
        assertFalse(ForceStopHistory.matchesForceStop(userRequested, "stop com.driveperf.app due to installPackage", packageName))
        assertFalse(ForceStopHistory.matchesForceStop(userRequested, "[REMOVE TASK]", packageName))
        assertFalse(ForceStopHistory.matchesForceStop(userRequested, "[PACKAGE UPDATE]", packageName))
        assertFalse(ForceStopHistory.matchesForceStop(userRequested, null, packageName))
        assertFalse(ForceStopHistory.matchesForceStop(ApplicationExitInfo.REASON_CRASH, "[FORCE STOP]", packageName))
    }

    @Test
    @Config(shadows = [StartHistory::class])
    fun `the current process start flag wins over a previous user-requested exit`() {
        val history = Shadow.extract<StartHistory>(app.getSystemService(ActivityManager::class.java))
        val start = Shadow.newInstanceOf(ApplicationStartInfo::class.java)
        ReflectionHelpers.setField(start, "mPid", Process.myPid())
        history.starts = listOf(start)
        history.addApplicationExitInfo(
            ShadowActivityManager.ApplicationExitInfoBuilder.newBuilder()
                .setReason(ApplicationExitInfo.REASON_USER_REQUESTED)
                .setDescription("[FORCE STOP]")
                .setTimestamp(5L)
                .build(),
        )

        assertFalse(ForceStopHistory.detected(app, sinceMs = 0L))
        ReflectionHelpers.setField(start, "mWasForceStopped", true)
        assertTrue(ForceStopHistory.detected(app, sinceMs = 0L))

        // If start history is unavailable, use only a newer exit from the fallback.
        history.starts = emptyList()
        assertTrue(ForceStopHistory.detected(app, sinceMs = 4L))
        assertFalse(ForceStopHistory.detected(app, sinceMs = 5L))
    }

    @Implements(ActivityManager::class)
    class StartHistory : ShadowActivityManager() {
        var starts = emptyList<ApplicationStartInfo>()

        @Implementation(minSdk = 35)
        fun getHistoricalProcessStartReasons(maxNum: Int): List<ApplicationStartInfo> = starts.take(maxNum)
    }
}
