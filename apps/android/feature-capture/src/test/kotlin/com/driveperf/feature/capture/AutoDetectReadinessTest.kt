package com.driveperf.feature.capture

import com.driveperf.feature.capture.AttentionReason.ActivityUpdatesFailed
import com.driveperf.feature.capture.AttentionReason.NoActivityRecognition
import com.driveperf.feature.capture.AttentionReason.NoBackgroundLocation
import com.driveperf.feature.capture.AttentionReason.NoLocation
import com.driveperf.feature.capture.AttentionReason.NoPreciseLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoDetectReadinessTest {
    private val all = CapturePermissions.ALL
    private val none = CapturePermissions(location = false, precise = false, background = false, activity = false)

    @Test
    fun `everything granted needs nothing`() {
        assertEquals(emptyList<AttentionReason>(), AutoDetectReadiness.reasons(all))
    }

    @Test
    fun `each missing permission is its own reason`() {
        assertEquals(listOf(NoBackgroundLocation), AutoDetectReadiness.reasons(all.copy(background = false)))
        assertEquals(listOf(NoActivityRecognition), AutoDetectReadiness.reasons(all.copy(activity = false)))
        assertEquals(listOf(NoPreciseLocation), AutoDetectReadiness.reasons(all.copy(precise = false)))
    }

    @Test
    fun `reasons come most important first, as on iOS`() {
        val approximateWhileUsingNoActivity = all.copy(precise = false, background = false, activity = false)
        assertEquals(
            listOf(NoBackgroundLocation, NoActivityRecognition, NoPreciseLocation),
            AutoDetectReadiness.reasons(approximateWhileUsingNoActivity),
        )
    }

    @Test
    fun `without location there is nothing to be precise about or to allow all the time`() {
        assertEquals(listOf(NoLocation, NoActivityRecognition), AutoDetectReadiness.reasons(none))
        assertEquals(listOf(NoLocation), AutoDetectReadiness.reasons(all.copy(location = false, precise = false)))
    }

    @Test
    fun `opted out is off whatever is granted, and lists no reasons`() {
        val status = AutoDetectReadiness.status(optedIn = false, permissions = none)
        assertEquals(AutoDetectMode.Off, status.mode)
        assertEquals(emptyList<AttentionReason>(), status.reasons)
    }

    @Test
    fun `opted in with a permission missing needs attention, not off`() {
        val status = AutoDetectReadiness.status(optedIn = true, permissions = all.copy(activity = false))
        assertEquals(AutoDetectMode.NeedsAttention, status.mode)
        assertEquals(listOf(NoActivityRecognition), status.reasons)
    }

    @Test
    fun `opted in with everything granted is ready and carries the core check-in`() {
        val status = AutoDetectReadiness.status(optedIn = true, permissions = all, lastCheckInMs = 42L)
        assertEquals(AutoDetectMode.Ready, status.mode)
        assertEquals(42L, status.lastCheckInMs)
    }

    @Test
    fun `a failed Play services registration needs attention only when no permission explains it`() {
        assertEquals(
            listOf(ActivityUpdatesFailed),
            AutoDetectReadiness.status(optedIn = true, permissions = all, activityUpdatesFailed = true).reasons,
        )
        assertEquals(
            listOf(NoBackgroundLocation),
            AutoDetectReadiness.status(optedIn = true, permissions = all.copy(background = false), activityUpdatesFailed = true).reasons,
        )
        assertNull(ActivityUpdatesFailed.fix)
    }

    @Test
    fun `checks put failing rows first, then the passing ones`() {
        val checks = AutoDetectReadiness.checks(listOf(NoActivityRecognition))
        assertEquals(
            listOf(CaptureCheck.Kind.Activity, CaptureCheck.Kind.Location, CaptureCheck.Kind.Precise),
            checks.map { it.kind },
        )
        assertEquals(NoActivityRecognition, checks.first().reason)
        assertTrue(checks.drop(1).all { it.ok })
    }

    @Test
    fun `the location row says which location problem it is, and precise is hidden without location`() {
        val whileUsing = AutoDetectReadiness.checks(listOf(NoBackgroundLocation))
        assertEquals(NoBackgroundLocation, whileUsing.first { it.kind == CaptureCheck.Kind.Location }.reason)
        val off = AutoDetectReadiness.checks(listOf(NoLocation))
        assertEquals(listOf(CaptureCheck.Kind.Location, CaptureCheck.Kind.Activity), off.map { it.kind })
    }

    @Test
    fun `fixes map to the right step`() {
        assertEquals(AttentionFix.Location, NoLocation.fix)
        assertEquals(AttentionFix.Location, NoPreciseLocation.fix)
        assertEquals(AttentionFix.Background, NoBackgroundLocation.fix)
        assertEquals(AttentionFix.Activity, NoActivityRecognition.fix)
    }

    @Test
    fun `check-in age buckets`() {
        val now = 10 * 24 * 3_600_000L
        assertEquals(CheckInAge.JustNow, CheckInAge.of(now - 59_000, now))
        assertEquals(CheckInAge.JustNow, CheckInAge.of(now + 5_000, now)) // a clock step backwards is not negative
        assertEquals(CheckInAge.Minutes(18), CheckInAge.of(now - 18 * 60_000L, now))
        assertEquals(CheckInAge.Hours(3), CheckInAge.of(now - 3 * 3_600_000L - 1, now))
        assertEquals(CheckInAge.Older, CheckInAge.of(now - 24 * 3_600_000L, now))
    }

    @Test
    fun `a pause shows over a missing permission and the checks still list it`() {
        val paused = AutoDetectReadiness.status(
            optedIn = true,
            permissions = all.copy(activity = false),
            paused = true,
            pausedUntilMs = 42L,
        )
        assertEquals(AutoDetectMode.Paused, paused.mode)
        assertEquals(42L, paused.pausedUntilMs)
        assertEquals(listOf(NoActivityRecognition), paused.reasons)
        assertEquals(1, paused.checks.count { !it.ok })
    }

    @Test
    fun `automatic trips off is off even while the core still reports a pause`() {
        val off = AutoDetectReadiness.status(optedIn = false, permissions = all, paused = true, pausedUntilMs = 42L)
        assertEquals(AutoDetectMode.Off, off.mode)
        assertNull(off.pausedUntilMs)
        val ready = AutoDetectReadiness.status(optedIn = true, permissions = all, paused = false, pausedUntilMs = 42L)
        assertEquals(AutoDetectMode.Ready, ready.mode)
        assertNull(ready.pausedUntilMs)
    }
}
