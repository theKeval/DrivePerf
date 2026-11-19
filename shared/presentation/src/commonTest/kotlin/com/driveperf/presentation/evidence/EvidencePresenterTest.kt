package com.driveperf.presentation.evidence

import com.driveperf.domain.replay.TripEvidence
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.InaccurateReason
import com.driveperf.domain.trip.Provenance
import com.driveperf.domain.trip.RawState
import com.driveperf.presentation.replay.ReplayTimeFormat
import com.driveperf.presentation.score.TipCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent, setMain, UnconfinedTestDispatcher
class EvidencePresenterTest {
    private val world = EvidenceWorld()
    private val units = MutableStateFlow(UnitSystem.METRIC)
    private val format = ReplayTimeFormat { ms -> "T$ms" }

    private fun TestScope.presenter(eventId: String = "e1"): Pair<EvidencePresenter, MutableList<EvidenceEffect>> {
        val p = EvidencePresenter(
            "t1", eventId, world.trips, TripEvidence(world.trips, world.analysis), world.markNeeded, world.reportInaccurate,
            world.analytics, units, format, backgroundScope,
        )
        val effects = mutableListOf<EvidenceEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { p.effects.toList(effects) }
        runCurrent()
        return p to effects
    }

    @Test
    fun showsTheEventFactsTraceAndWords() = runTest {
        val (p, _) = presenter()
        val s = p.state.value
        assertFalse(s.loading)
        assertEquals(EventType.BRAKE, s.type)
        assertEquals("Hard braking", s.title)
        assertEquals("0:20 into the drive · T1020000", s.subtitle)
        assertEquals(listOf("Peak", "Duration", "Speed", "Confidence"), s.facts.map { it.label })
        assertEquals(listOf("0.42", "1.4", "48 → 0", "High"), s.facts.map { it.value })
        assertEquals("km/h", s.facts[2].unit)
        assertEquals(Provenance.DERIVED, s.facts[0].provenance)
        assertEquals("Peak 0.42 g. Derived.", s.facts[0].spoken)
        val trace = assertNotNull(s.trace)
        assertEquals(10.0, trace.t0S)
        assertEquals(30.0, trace.t1S)
        assertTrue(s.banners.isEmpty())
        assertEquals("Event 1 of 3", s.position)
        assertFalse(s.hasPrevious)
        assertTrue(s.hasNext)
        assertEquals(
            "Hard braking, T1020000. Peak 0.42 g over 1.4 seconds, from 48 to 0 kilometres per hour. Confidence high.",
            s.accessibilityLabel,
        )
        assertTrue(s.traceSummary.startsWith("Speed and G from 10 seconds before to 10 seconds after."))
        assertEquals("evidence_opened", world.tracked.first().name)
        assertEquals(mapOf("type" to "brake"), world.tracked.first().properties)
    }

    @Test
    fun imperialUnitsAndAMissingSpeed() = runTest {
        units.value = UnitSystem.IMPERIAL
        world.events[0] = world.events[0].copy(speedAfterMps = null)
        val (p, _) = presenter()
        assertEquals("30 → —", p.state.value.facts[2].value)
        assertEquals("mph", p.state.value.speedUnit)
        assertFalse(p.state.value.accessibilityLabel.contains("from"))
    }

    @Test
    fun lowConfidenceAndATrimmedTraceShowBanners() = runTest {
        world.trip = world.trip?.copy(rawState = RawState.SUMMARY_ONLY)
        val (p, _) = presenter("e2")
        val s = p.state.value
        assertEquals(listOf(EvidenceBannerKind.LOW_CONFIDENCE, EvidenceBannerKind.TRACE_REMOVED), s.banners.map { it.kind })
        assertNull(s.trace)
        assertEquals(EvidenceCopy.TRACE_REMOVED, s.traceSummary)
        assertEquals(0, world.analysis.reads)
    }

    @Test
    fun theRelatedTipIsThisDrivesTipWhenItIsAboutThisEvent() = runTest {
        val (own, _) = presenter("e2")
        assertEquals(TipCopy.text("accel_progressive")?.nextTime, own.state.value.tip?.text)
        own.state.value.tip?.let { assertEquals(EvidenceCopy.THIS_DRIVES_TIP, it.kicker) }
        val (other, _) = presenter("e3")
        val related = TipCopy.relatedTipId(EventType.CORNER)
        assertEquals(TipCopy.text(related)?.nextTime, other.state.value.tip?.text)
        other.state.value.tip?.let { assertEquals(EvidenceCopy.NEXT_TIME, it.kicker) }
    }

    @Test
    fun aMissingEventIsMissingAndOnlyCloses() = runTest {
        val (p, effects) = presenter("nope")
        assertTrue(p.state.value.missing)
        p.dispatch(EvidenceIntent.AskNeeded)
        assertNull(p.state.value.neededSheet)
        p.dispatch(EvidenceIntent.Close)
        runCurrent()
        assertEquals(listOf<EvidenceEffect>(EvidenceEffect.Close), effects)
    }

    @Test
    fun markingNeededRescoresAndShowsTheDoneSheet() = runTest {
        val (p, effects) = presenter()
        p.dispatch(EvidenceIntent.AskNeeded)
        val ask = assertNotNull(p.state.value.neededSheet)
        assertEquals(NeededStep.ASK, ask.step)
        assertTrue(ask.body.startsWith("If you braked for a good reason"))
        assertEquals(EvidenceCopy.RECORD_CAP, ask.capNote)
        p.dispatch(EvidenceIntent.ConfirmNeeded)
        runCurrent()
        val s = p.state.value
        assertTrue(s.needed)
        assertTrue(s.facts[0].struck)
        assertEquals("Peak 0.42 g, not counted. Derived.", s.facts[0].spoken)
        assertEquals(EvidenceBannerKind.NEEDED, s.banners.single().kind)
        val done = assertNotNull(s.neededSheet)
        assertEquals(NeededStep.DONE, done.step)
        assertEquals(79, done.score)
        assertEquals("It doesn’t count. Smoothness recomputed without it: 79. Tips won’t be about this stop.", done.body)
        assertTrue(world.feedback.getValue("e1").needed)
        assertEquals(listOf<EvidenceEffect>(EvidenceEffect.FeedbackChanged), effects)
        assertEquals(mapOf("type" to "brake", "source" to "evidence"), world.tracked.last().properties)
        p.dispatch(EvidenceIntent.DismissNeeded)
        assertNull(p.state.value.neededSheet)
    }

    @Test
    fun countingAgainRestoresTheEventWithAToast() = runTest {
        world.feedback["e3"] = EventFeedback("e3", "t1", true, null, 1L)
        val (p, effects) = presenter("e3")
        p.dispatch(EvidenceIntent.AskCountAgain)
        assertEquals(NeededStep.COUNT_AGAIN, p.state.value.neededSheet?.step)
        p.dispatch(EvidenceIntent.ConfirmCountAgain)
        runCurrent()
        assertFalse(p.state.value.needed)
        assertNull(p.state.value.neededSheet)
        assertFalse(world.feedback.getValue("e3").needed)
        assertEquals(listOf(EvidenceEffect.FeedbackChanged, EvidenceEffect.Toast(EvidenceCopy.COUNTED_AGAIN)), effects)
        assertEquals("event_unmarked", world.names().last())
    }

    @Test
    fun overTheRecordCapTheSheetSaysSo() = runTest {
        world.feedback["e2"] = EventFeedback("e2", "t1", true, null, 1L)
        world.feedback["e3"] = EventFeedback("e3", "t1", true, null, 1L)
        val (p, _) = presenter()
        p.dispatch(EvidenceIntent.AskNeeded)
        p.dispatch(EvidenceIntent.ConfirmNeeded)
        runCurrent()
        assertEquals(EvidenceCopy.overRecordCap(3), p.state.value.neededSheet?.capNote)
        assertEquals(NeededStep.DONE, p.state.value.neededSheet?.step)
    }

    @Test
    fun notAccurateStoresTheReasonAndCloses() = runTest {
        val (p, effects) = presenter()
        p.dispatch(EvidenceIntent.OpenReasons)
        assertTrue(p.state.value.reasonsOpen)
        assertEquals(4, p.state.value.reasons.size)
        p.dispatch(EvidenceIntent.SendReason)
        runCurrent()
        assertTrue(effects.isEmpty(), "nothing to send before a reason is picked")
        p.dispatch(EvidenceIntent.SelectReason(InaccurateReason.POTHOLE))
        assertEquals(InaccurateReason.POTHOLE, p.state.value.selectedReason)
        p.dispatch(EvidenceIntent.SendReason)
        runCurrent()
        assertEquals(InaccurateReason.POTHOLE, world.feedback.getValue("e1").inaccurate)
        assertEquals(
            listOf(EvidenceEffect.FeedbackChanged, EvidenceEffect.Toast(EvidenceCopy.THANKS), EvidenceEffect.Close),
            effects,
        )
        assertEquals(mapOf("type" to "brake", "reason" to "pothole"), world.tracked.last().properties)
        assertFalse(p.state.value.reasonsOpen)
    }

    @Test
    fun cancellingTheReasonsChangesNothing() = runTest {
        val (p, _) = presenter()
        p.dispatch(EvidenceIntent.OpenReasons)
        p.dispatch(EvidenceIntent.SelectReason(InaccurateReason.OTHER))
        p.dispatch(EvidenceIntent.CancelReasons)
        assertFalse(p.state.value.reasonsOpen)
        assertNull(p.state.value.selectedReason)
        assertTrue(world.feedback.isEmpty())
    }

    @Test
    fun aRefusedWriteSaysSoAndKeepsTheSheet() = runTest {
        val (p, effects) = presenter()
        world.events.removeAt(0) // the event went away under the sheet: the use case refuses it
        p.dispatch(EvidenceIntent.AskNeeded)
        p.dispatch(EvidenceIntent.ConfirmNeeded)
        runCurrent()
        assertFalse(p.state.value.needed)
        assertFalse(p.state.value.busy)
        assertEquals(listOf<EvidenceEffect>(EvidenceEffect.Toast(EvidenceCopy.SAVE_FAILED)), effects)
    }

    @Test
    fun theStepperMovesThroughTheTripsEventsAndTheReplayFollows() = runTest {
        val (p, effects) = presenter()
        p.dispatch(EvidenceIntent.Previous)
        runCurrent()
        assertEquals("e1", p.state.value.eventId, "no event before the first")
        p.dispatch(EvidenceIntent.Next)
        runCurrent()
        assertEquals("e2", p.state.value.eventId)
        assertEquals("Event 2 of 3", p.state.value.position)
        assertTrue(p.state.value.hasPrevious)
        p.dispatch(EvidenceIntent.Next)
        runCurrent()
        assertFalse(p.state.value.hasNext)
        p.dispatch(EvidenceIntent.Next)
        runCurrent()
        assertEquals(listOf<EvidenceEffect>(EvidenceEffect.ShowEvent("e2"), EvidenceEffect.ShowEvent("e3")), effects)
        assertEquals(1, world.analysis.reads, "the trace is read once for the whole sheet")
        assertEquals(3, world.names().count { it == "evidence_opened" })
    }

    @Test
    fun inaccurateEventsAreNotSteppedTo() = runTest {
        world.feedback["e2"] = EventFeedback("e2", "t1", false, InaccurateReason.NOT_ME, 1L)
        val (p, _) = presenter()
        assertEquals("Event 1 of 2", p.state.value.position)
        p.dispatch(EvidenceIntent.Next)
        runCurrent()
        assertEquals("e3", p.state.value.eventId)
    }

    @Test
    fun theTipOpensWhenThereIsOne() = runTest {
        val (p, effects) = presenter("e2")
        p.dispatch(EvidenceIntent.OpenTip)
        runCurrent()
        val tip = p.state.value.tip
        if (tip == null) {
            assertTrue(effects.isEmpty())
        } else {
            assertEquals(listOf<EvidenceEffect>(EvidenceEffect.OpenTip("t1", tip.tipId, "e2")), effects)
        }
    }

    @Test
    fun onMainRunsOnTheMainDispatcher() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val p = EvidencePresenter.onMain(
                "t1", "e1", world.trips, TripEvidence(world.trips, world.analysis), world.markNeeded, world.reportInaccurate,
                world.analytics, units, ReplayTimeFormat.None,
            )
            runCurrent()
            assertEquals("0:20 into the drive", p.state.value.subtitle)
            p.close()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun eachCategoryHasThreeSteps() {
        com.driveperf.domain.score.TipCategory.entries.forEach { assertEquals(3, EvidenceCopy.steps(it).size) }
        assertTrue(EvidenceCopy.askBody(EventType.ACCEL).startsWith("If you sped up"))
        assertTrue(EvidenceCopy.askBody(EventType.CORNER).startsWith("If you turned hard"))
        assertEquals("It doesn’t count. Tips won’t be about this one.", EvidenceCopy.doneBody(EventType.CORNER, null))
    }
}
