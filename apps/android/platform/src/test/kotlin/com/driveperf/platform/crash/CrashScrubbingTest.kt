package com.driveperf.platform.crash

import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.RecorderStatus
import io.sentry.Breadcrumb
import io.sentry.SentryEvent
import io.sentry.protocol.Message
import io.sentry.protocol.SentryException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** DRIVE-196 (F-ANA-03, ADR-007): allow-listed tags, `in_trip`, and no location in what reaches Sentry. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CrashScrubbingTest {
    private fun status(state: RecorderState, manual: Boolean = false) =
        RecorderStatus(state, if (state == RecorderState.IDLE) null else "t1", manual, 0.0, 0.0, true)

    @Test
    fun `capture-time states are tagged in_trip, with the mode and the state and no trip id`() {
        assertEquals(
            mapOf(CrashTag.IN_TRIP to "true", CrashTag.CAPTURE_MODE to "auto", CrashTag.TRIP_STATE to "recording"),
            CrashTag.forTrip(status(RecorderState.RECORDING)),
        )
        assertEquals("manual", CrashTag.forTrip(status(RecorderState.PAUSED, manual = true))[CrashTag.CAPTURE_MODE])
        assertEquals("true", CrashTag.forTrip(status(RecorderState.CANDIDATE))[CrashTag.IN_TRIP])
        assertEquals("true", CrashTag.forTrip(status(RecorderState.FINISHING))[CrashTag.IN_TRIP])
        assertEquals(
            mapOf(CrashTag.IN_TRIP to "false", CrashTag.CAPTURE_MODE to "none", CrashTag.TRIP_STATE to "idle"),
            CrashTag.forTrip(status(RecorderState.IDLE)),
        )
        assertFalse(CrashTag.forTrip(status(RecorderState.RECORDING)).values.any { it == "t1" })
    }

    @Test
    fun `the allow-list is the ADR-007 list`() {
        assertEquals(setOf("in_trip", "capture_mode", "trip_state", "db_state", "app_build"), CrashTag.allowedKeys)
    }

    @Test
    fun `an event keeps only allow-listed tags, loses its extras and its coordinates`() {
        val event = SentryEvent().apply {
            message = Message().apply {
                formatted = "Fix rejected at 45.50170, -73.56730"
                message = "Fix rejected at %s"
                params = listOf("45.50170,-73.56730")
            }
            exceptions = listOf(SentryException().apply { value = "Bad sample 45.501701 -73.567301 in batch" })
            setTag("in_trip", "true")
            setTag("street", "Main")
            setExtra("where", "45.5017,-73.5673")
            breadcrumbs = mutableListOf(Breadcrumb("Screen S-DRV-01"), Breadcrumb("near 45.5017, -73.5673"))
        }

        val scrubbed = SentryCrashReporter.scrub(event)

        assertEquals("Fix rejected at [location removed]", scrubbed.message?.formatted)
        assertEquals(listOf("[location removed]"), scrubbed.message?.params)
        assertEquals("Bad sample [location removed] in batch", scrubbed.exceptions?.single()?.value)
        assertEquals(mapOf("in_trip" to "true"), scrubbed.tags)
        assertTrue(scrubbed.extras.isNullOrEmpty())
        assertEquals(listOf("Screen S-DRV-01"), scrubbed.breadcrumbs?.map { it.message })
    }

    @Test
    fun `a breadcrumb with a location in its data is dropped, others pass`() {
        val withData = Breadcrumb("fix").apply { setData("point", "45.50170;-73.56730") }
        assertNull(SentryCrashReporter.scrub(withData))
        assertNotNull(SentryCrashReporter.scrub(Breadcrumb("state recording -> finishing").apply { setData("from", "recording") }))
    }

    @Test
    fun `without a DSN the reporter is unavailable and ignores consent and trip changes`() {
        val reporter = SentryCrashReporter(RuntimeEnvironment.getApplication(), dsn = "", appBuild = "0.1.0")
        assertFalse(reporter.isAvailable)
        reporter.setEnabled(true)
        reporter.setTrip(status(RecorderState.RECORDING))
        assertFalse(io.sentry.Sentry.isEnabled())
    }
}
