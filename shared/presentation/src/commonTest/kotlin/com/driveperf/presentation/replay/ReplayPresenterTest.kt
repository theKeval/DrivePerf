package com.driveperf.presentation.replay

import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.replay.PlaybackSpeed
import com.driveperf.domain.replay.Replay
import com.driveperf.domain.replay.ReplayPoint
import com.driveperf.domain.replay.ReplayRepository
import com.driveperf.domain.trip.BoundingBox
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent
class ReplayPresenterTest {
    private val replay = Replay(
        tripId = "trip-1",
        bbox = BoundingBox(0.0, 0.0, 0.01, 0.0),
        durationS = 600.0,
        spans = emptyList(),
        summaryOnly = false,
        track = listOf(ReplayPoint(0.0, 0.0, 0.0, 10.0), ReplayPoint(600.0, 0.01, 0.0, 10.0)),
    )

    private class Events : Analytics {
        val tracked = mutableListOf<AnalyticsEvent>()

        override fun track(event: AnalyticsEvent) {
            tracked += event
        }

        override fun setUsageConsent(allowed: Boolean): Unit = Unit

        override fun resetId(): Unit = Unit

        fun names(): List<String> = tracked.map { it.name }
    }

    private class FakeClock(var ns: Long = 5_000_000_000L) {
        fun advanceS(s: Double) {
            ns += (s * 1e9).toLong()
        }
    }

    private val clock = FakeClock()
    private val events = Events()

    private fun TestScope.presenter(found: Replay? = replay, recording: Boolean = false): ReplayPresenter {
        val repo = object : ReplayRepository {
            override suspend fun replay(tripId: String): Replay? = found?.takeIf { tripId == "trip-1" }
        }
        return ReplayPresenter("trip-1", ReplaySource.TRIP_DETAIL, repo, { recording }, events, { clock.ns }, backgroundScope)
            .also { runCurrent() }
    }

    private fun ReplayPresenter.playhead(): Double = state.value.playheadAt(nowNs())

    @Test
    fun loadsPausedAtTheStartAndReportsTheOpen() = runTest {
        val p = presenter()
        val s = p.state.value
        assertFalse(s.loading)
        assertEquals(replay, s.replay)
        assertNull(s.unavailable)
        assertFalse(s.playing)
        assertEquals(PlaybackSpeed.AUTO, s.speed)
        assertEquals(0.0, p.playhead())
        assertEquals(listOf<AnalyticsEvent>(ReplayEvent("replay_opened", mapOf("source" to "trip_detail"))), events.tracked)
    }

    @Test
    fun playAndPauseAnchorThePlayhead() = runTest {
        val p = presenter()
        p.dispatch(ReplayIntent.Play)
        assertTrue(p.state.value.playing)
        clock.advanceS(2.0)
        assertEquals(20.0, p.playhead(), "AUTO plays 600 s in 60 s: 10 trip seconds per second")
        p.dispatch(ReplayIntent.Pause)
        clock.advanceS(5.0)
        assertEquals(20.0, p.playhead(), "paused in place")
        assertEquals(20.0, p.state.value.anchorTS)
        p.dispatch(ReplayIntent.Play)
        clock.advanceS(1.0)
        assertEquals(30.0, p.playhead())
        p.dispatch(ReplayIntent.Play)
        assertEquals(listOf("replay_opened", "replay_played", "replay_played"), events.names(), "a second Play while playing is a no-op")
        assertEquals(mapOf("speed" to "auto"), events.tracked[1].properties)
    }

    @Test
    fun everySpeedAdvancesAtItsRateWithoutAJump() = runTest {
        val p = presenter()
        p.dispatch(ReplayIntent.Play)
        clock.advanceS(1.0)
        assertEquals(10.0, p.playhead())
        val rates = listOf(PlaybackSpeed.X1 to 1.0, PlaybackSpeed.X10 to 10.0, PlaybackSpeed.X30 to 30.0, PlaybackSpeed.AUTO to 10.0)
        for ((speed, rate) in rates) {
            val before = p.playhead()
            p.dispatch(ReplayIntent.SetSpeed(speed))
            assertEquals(before, p.playhead(), "changing speed keeps the playhead")
            clock.advanceS(1.0)
            assertEquals(before + rate, p.playhead(), "$speed")
        }
        p.dispatch(ReplayIntent.Pause)
        p.dispatch(ReplayIntent.SetSpeed(PlaybackSpeed.X30))
        p.dispatch(ReplayIntent.Play)
        assertEquals(mapOf("speed" to "30x"), events.tracked.last().properties)
    }

    @Test
    fun aScrubHoldsPlaybackAndResumesThePreviousState() = runTest {
        val p = presenter()
        p.dispatch(ReplayIntent.Play)
        clock.advanceS(1.0)
        p.dispatch(ReplayIntent.ScrubStart)
        assertTrue(p.state.value.scrubbing)
        assertFalse(p.state.value.playing)
        p.dispatch(ReplayIntent.ScrubTo(100.0))
        clock.advanceS(3.0)
        p.dispatch(ReplayIntent.ScrubTo(250.0))
        clock.advanceS(3.0)
        assertEquals(250.0, p.playhead(), "held while the finger is down")
        p.dispatch(ReplayIntent.ScrubEnd)
        assertTrue(p.state.value.playing, "it was playing before, so it plays again")
        clock.advanceS(1.0)
        assertEquals(260.0, p.playhead())

        p.dispatch(ReplayIntent.Pause)
        p.dispatch(ReplayIntent.ScrubStart)
        p.dispatch(ReplayIntent.ScrubTo(-50.0))
        p.dispatch(ReplayIntent.ScrubEnd)
        p.dispatch(ReplayIntent.ScrubEnd)
        assertFalse(p.state.value.playing, "it was paused before, so it stays paused")
        assertEquals(0.0, p.playhead(), "clamped to the trip")
        assertEquals(2, events.names().count { it == "replay_scrubbed" }, "once per gesture, not per move")
    }

    @Test
    fun aSeekOutsideAGestureKeepsPlayingAndCounts() = runTest {
        val p = presenter()
        p.dispatch(ReplayIntent.Play)
        p.dispatch(ReplayIntent.ScrubTo(1_000.0))
        assertEquals(600.0, p.playhead())
        assertTrue(p.state.value.playing)
        assertEquals(1, events.names().count { it == "replay_scrubbed" })
    }

    @Test
    fun reachingTheEndPausesOnceAndPlayRestarts() = runTest {
        val p = presenter()
        p.dispatch(ReplayIntent.Play)
        clock.advanceS(30.0)
        p.dispatch(ReplayIntent.Tick(p.nowNs()))
        assertTrue(p.state.value.playing, "half way")
        clock.advanceS(40.0)
        p.dispatch(ReplayIntent.Tick(p.nowNs()))
        p.dispatch(ReplayIntent.Tick(p.nowNs()))
        assertFalse(p.state.value.playing)
        assertEquals(600.0, p.playhead())
        assertEquals(1, events.names().count { it == "replay_completed" })

        p.dispatch(ReplayIntent.Play)
        assertEquals(0.0, p.playhead(), "Play at the end restarts")
        clock.advanceS(1.0)
        assertEquals(10.0, p.playhead())
    }

    @Test
    fun aScrubEndingAtTheEndStaysPaused() = runTest {
        val p = presenter()
        p.dispatch(ReplayIntent.Play)
        p.dispatch(ReplayIntent.ScrubStart)
        p.dispatch(ReplayIntent.ScrubTo(600.0))
        p.dispatch(ReplayIntent.ScrubEnd)
        assertFalse(p.state.value.playing)
        p.dispatch(ReplayIntent.Tick(p.nowNs()))
        assertEquals(0, events.names().count { it == "replay_completed" })
    }

    @Test
    fun aTripStillRecordingShowsTheDriveScreen() = runTest {
        val p = presenter(recording = true)
        assertEquals(ReplayUnavailable.RECORDING, p.state.value.unavailable)
        assertFalse(p.state.value.loading)
        assertEquals(ReplayEffect.ShowDriveScreen, p.effects.first())
        assertTrue(events.tracked.isEmpty(), "Replay was not opened")
    }

    @Test
    fun aMissingTripIsNotFoundAndIgnoresPlayback() = runTest {
        val p = presenter(found = null)
        assertEquals(ReplayUnavailable.NOT_FOUND, p.state.value.unavailable)
        p.dispatch(ReplayIntent.Play)
        p.dispatch(ReplayIntent.ScrubStart)
        assertFalse(p.state.value.playing)
        assertEquals(0.0, p.playhead())
        assertTrue(events.tracked.isEmpty())
    }

    @Test
    fun theLegendIsCounted() = runTest {
        val p = presenter()
        p.dispatch(ReplayIntent.OpenLegend)
        assertEquals("quality_overlay_legend_opened", events.tracked.last().name)
        assertEquals(emptyMap(), events.tracked.last().properties)
    }

    @Test
    fun beforeLoadingThePlayheadIsZero() = runTest {
        assertEquals(0.0, ReplayState().playheadAt(123L))
        assertTrue(ReplayState().loading)
    }

    @Test
    fun closeStopsLoading() = runTest {
        val repo = object : ReplayRepository {
            override suspend fun replay(tripId: String): Replay? = replay
        }
        val p = ReplayPresenter("trip-1", ReplaySource.STORY, repo, { false }, events, { clock.ns }, backgroundScope)
        p.close()
        runCurrent()
        assertTrue(p.state.value.loading, "closed before the load ran")
        assertTrue(events.tracked.isEmpty())
    }
}
