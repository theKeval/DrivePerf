package com.driveperf.presentation.evidence

import com.driveperf.domain.replay.TripEvidence
import com.driveperf.domain.score.TipCategory
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.QualityReason
import com.driveperf.domain.trip.Stretch
import com.driveperf.presentation.score.TipCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent, setMain, UnconfinedTestDispatcher
class TipDetailPresenterTest {
    private val world = EvidenceWorld()

    private fun TestScope.presenter(
        tipId: String? = null,
        eventId: String? = null,
    ): Pair<TipDetailPresenter, MutableList<TipDetailEffect>> {
        val p = TipDetailPresenter(
            "t1", tipId, eventId, world.trips, TripEvidence(world.trips, world.analysis), world.analytics,
            flowOf(UnitSystem.METRIC), backgroundScope,
        )
        val effects = mutableListOf<TipDetailEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { p.effects.toList(effects) }
        runCurrent()
        return p to effects
    }

    @Test
    fun theTripsOwnTipWithItsEventAndSteps() = runTest {
        world.trip = world.trip?.copy(cleanest = Stretch(4_230.0, 0.0, 300.0))
        val (p, effects) = presenter()
        val s = p.state.value
        assertEquals("accel_progressive", s.tipId)
        assertEquals(EvidenceCopy.THIS_DRIVES_TIP, s.kicker)
        assertEquals(TipCopy.text("accel_progressive")?.nextTime, s.tipText)
        assertEquals("4.2 km with no abrupt inputs.", s.cleanest)
        assertEquals(
            "It’s the moment that cost the most smoothness on this drive: hard acceleration, " +
                "peaking at 0.42 g from 48 to 0 km/h.",
            s.why,
        )
        assertEquals(EvidenceCopy.steps(TipCopy.text("accel_progressive")?.category ?: TipCategory.ACCELERATION), s.steps)
        assertEquals("e2", s.eventId)
        assertEquals(101, s.sparkline.size)
        assertEquals(50, s.sparklinePeakIndex)
        assertNull(s.noTip)
        assertEquals(mapOf("tip_id" to "accel_progressive"), world.tracked.single().properties)
        p.dispatch(TipDetailIntent.SeeTheMoment)
        p.dispatch(TipDetailIntent.Back)
        runCurrent()
        assertEquals(listOf(TipDetailEffect.OpenEvidence("t1", "e2"), TipDetailEffect.Close), effects)
        assertEquals("tip_evidence_opened", world.names().last())
    }

    @Test
    fun aLibraryTipForAnotherEventIsNextTime() = runTest {
        val (p, _) = presenter(tipId = "corner_brake_before", eventId = "e3")
        val s = p.state.value
        assertEquals("corner_brake_before", s.tipId)
        assertEquals(EvidenceCopy.NEXT_TIME, s.kicker)
        assertTrue(s.why.contains("around this moment: hard cornering"))
        assertEquals("e3", s.eventId)
    }

    @Test
    fun aTipWithNoEventExplainsTheFirmestStop() = runTest {
        world.score = world.score?.copy(tip = world.score?.tip?.copy(eventId = null, category = TipCategory.ANTICIPATION))
        val (p, effects) = presenter()
        assertEquals(EvidenceCopy.WHY_NO_EVENT, p.state.value.why)
        assertTrue(p.state.value.sparkline.isEmpty())
        p.dispatch(TipDetailIntent.SeeTheMoment)
        runCurrent()
        assertTrue(effects.isEmpty())
    }

    @Test
    fun anUnscorableTripHasNoTip() = runTest {
        world.score = world.score?.copy(smoothness = null)
        world.trip = world.trip?.copy(quality = null)
        val (p, _) = presenter()
        assertEquals(EvidenceCopy.noTipBody("not enough good data"), p.state.value.noTip)
        assertTrue(world.tracked.isEmpty())
    }

    @Test
    fun noTipReasonsAreLowerCase() = runTest {
        world.score = null
        QualityReason.entries.forEach { reason ->
            val q = com.driveperf.domain.trip.TripQuality(
                com.driveperf.domain.trip.QualityBadge.UNSCORABLE, reason, com.driveperf.domain.trip.Placement.UNKNOWN,
                0.0, 0.0, null, null, 0.0, 0.0, 0.0, emptyList(),
            )
            world.trip = world.trip?.copy(quality = q)
            val (p, _) = presenter()
            val text = p.state.value.noTip ?: ""
            assertTrue(text.contains("fairly ("), text)
        }
    }

    @Test
    fun onMainRunsOnTheMainDispatcher() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val p = TipDetailPresenter.onMain(
                "t1", null, null, world.trips, TripEvidence(world.trips, world.analysis), world.analytics, flowOf(UnitSystem.METRIC),
            )
            runCurrent()
            assertEquals("accel_progressive", p.state.value.tipId)
            p.close()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
