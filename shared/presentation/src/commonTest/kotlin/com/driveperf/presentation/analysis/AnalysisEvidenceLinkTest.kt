package com.driveperf.presentation.analysis

import com.driveperf.domain.analysis.AnalysisRepository
import com.driveperf.domain.analysis.TripAnalysis
import com.driveperf.domain.settings.UnitSystem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** S-ANL-01 ticks and S-ANL-02 peak rows open the S-RPL-02 evidence sheet (DRIVE-39 AC1). */
@OptIn(ExperimentalCoroutinesApi::class) // runCurrent
class AnalysisEvidenceLinkTest {
    private val events = RecordedAnalytics()

    private fun TestScope.presenter(analyses: AnalysisRepository): AnalysisPresenter =
        AnalysisPresenter(
            "t1", AnalysisSource.TRIP_DETAIL, analyses, FakePreferences(), MutableStateFlow(UnitSystem.METRIC), events,
            backgroundScope, UnconfinedTestDispatcher(testScheduler),
        ).also { runCurrent() }

    private fun TestScope.presenter(found: TripAnalysis? = AnalysisTestData.analysis()) = presenter { found }

    @Test
    fun aTickOrPeakRowClosesTheGgMovesTheCursorAndOpensTheEvidence() = runTest {
        val p = presenter()
        p.dispatch(AnalysisIntent.ExpandGg)
        p.dispatch(AnalysisIntent.OpenEvidence("brake"))
        assertEquals(AnalysisEffect.OpenEvidence("brake"), p.effects.first())
        assertFalse(p.state.value.ggExpanded)
        assertEquals(150.0, p.state.value.cursorS)
        // A needed event still has its tick, so it still opens.
        p.dispatch(AnalysisIntent.OpenEvidence("corner"))
        assertEquals(AnalysisEffect.OpenEvidence("corner"), p.effects.first())
        assertEquals(300.0, p.state.value.cursorS)
    }

    @Test
    fun anEventWithNoTickOpensNothing() = runTest {
        val p = presenter()
        p.dispatch(AnalysisIntent.OpenEvidence("missing"))
        p.dispatch(AnalysisIntent.OpenReplay)
        assertEquals(AnalysisEffect.OpenReplay("t1"), p.effects.first())
        assertEquals(0.0, p.state.value.cursorS)
    }

    @Test
    fun nothingOpensBeforeTheAnalysisHasLoaded() = runTest {
        val p = presenter(found = null)
        p.dispatch(AnalysisIntent.OpenEvidence("brake"))
        p.dispatch(AnalysisIntent.Back)
        assertEquals(AnalysisEffect.Close, p.effects.first())
    }

    @Test
    fun reloadDropsAnEventReportedNotAccurateAndKeepsTheView() = runTest {
        val full = AnalysisTestData.analysis()
        val withoutBrake = TripAnalysis(
            full.trip, full.replay, full.data, full.markers.filterNot { it.eventId == "brake" }, full.events, full.score,
        )
        var current = full
        val p = presenter { current }
        assertTrue(p.state.value.timeline!!.ticks.any { it.eventId == "brake" })
        p.dispatch(AnalysisIntent.ZoomIn)
        current = withoutBrake
        p.dispatch(AnalysisIntent.Reload)
        runCurrent()
        val s = p.state.value
        assertEquals(2.0, s.zoom)
        assertTrue(s.timeline!!.ticks.none { it.eventId == "brake" })
        assertTrue(s.gg!!.pins.none { it.eventId == "brake" })
        assertTrue(s.map!!.pins.none { it.eventId == "brake" })
        // `analysis_opened` is sent once, for the screen, not for the reload.
        assertEquals(listOf("analysis_opened"), events.names())
        p.dispatch(AnalysisIntent.OpenEvidence("brake"))
        p.dispatch(AnalysisIntent.OpenReplay)
        assertEquals(AnalysisEffect.OpenReplay("t1"), p.effects.first())
    }
}
