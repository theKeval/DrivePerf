package com.driveperf.feature.evidence

import app.cash.turbine.test
import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.feedback.EventFeedbackStore
import com.driveperf.domain.feedback.MarkEventNeeded
import com.driveperf.domain.feedback.ReportEventInaccurate
import com.driveperf.domain.feedback.TripRescorer
import com.driveperf.domain.replay.TripEvidence
import com.driveperf.domain.repository.RouteRepository
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.route.PersonalBest
import com.driveperf.domain.route.Route
import com.driveperf.domain.route.RouteMatch
import com.driveperf.domain.score.Score
import com.driveperf.domain.score.Tip
import com.driveperf.domain.score.TipCategory
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.InaccurateReason
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.tripfile.AnalysisFileStore
import com.driveperf.domain.tripfile.AnalysisTrace
import com.driveperf.domain.tripfile.FileResult
import com.driveperf.domain.tripfile.TripFileRef
import com.driveperf.foundation.Clock
import com.driveperf.presentation.evidence.EvidenceCopy
import com.driveperf.presentation.evidence.EvidenceIntent
import com.driveperf.presentation.evidence.EvidencePresenter
import com.driveperf.presentation.evidence.TipDetailIntent
import com.driveperf.presentation.evidence.TipDetailPresenter
import com.driveperf.presentation.replay.ReplayTimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The evidence and tip sheets opened from trip detail, the breakdown and Analysis (DRIVE-87 AC2, DRIVE-55, DRIVE-102,
 * DRIVE-39 AC1), over B's real presenters and a synthetic trip (no real place).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EvidenceSheetsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private val world = World()

    private fun viewModel() = EvidenceSheetsViewModel(
        TripSheetFactories(
            evidence = { eventId, scope ->
                EvidencePresenter("t1", eventId, world.trips, world.evidence, world.markNeeded, world.reportInaccurate, world.analytics,
                    flowOf(UnitSystem.METRIC), ReplayTimeFormat { "8:14 AM" }, scope)
            },
            tip = { tipId, eventId, scope ->
                TipDetailPresenter("t1", tipId, eventId, world.trips, world.evidence, world.analytics, flowOf(UnitSystem.METRIC), scope)
            },
        ),
    )

    @Test fun `an event opens its evidence, steps inside the sheet and closes`() {
        val vm = viewModel()
        assertNull(vm.evidence.value)
        vm.openEvidence("e2")
        assertEquals("e2", vm.evidence.value?.eventId)
        vm.evidence(EvidenceIntent.Previous)
        assertEquals("e1", vm.evidence.value?.eventId)
        vm.closeEvidence()
        assertNull(vm.evidence.value)
    }

    @Test fun `the trip's tip opens on its own, and See the moment opens its event`() {
        val vm = viewModel()
        vm.openTip(null, null)
        assertEquals("brake_light", vm.tip.value?.tipId)
        assertNull(vm.evidence.value)
        vm.tip(TipDetailIntent.SeeTheMoment)
        assertNull(vm.tip.value)
        assertEquals("e1", vm.evidence.value?.eventId)
        assertEquals(listOf("tip_viewed", "tip_evidence_opened", "evidence_opened", "tip_viewed"), world.names())
    }

    @Test fun `the evidence's tip goes back to the same evidence, and Back closes only the tip`() {
        val vm = viewModel()
        vm.openEvidence("e1")
        vm.evidence(EvidenceIntent.OpenTip)
        assertNotNull(vm.tip.value)
        vm.tip(TipDetailIntent.SeeTheMoment)
        assertNull(vm.tip.value)
        assertEquals("e1", vm.evidence.value?.eventId)
        vm.evidence(EvidenceIntent.OpenTip)
        vm.tip(TipDetailIntent.Back)
        assertNull(vm.tip.value)
        vm.closeTip()
        assertEquals("e1", vm.evidence.value?.eventId)
    }

    @Test fun `Not accurate tells the screen underneath, thanks the user and closes`() = runTest {
        val vm = viewModel()
        vm.openEvidence("e2")
        vm.feedbackChanged.test {
            vm.evidence(EvidenceIntent.OpenReasons)
            vm.evidence(EvidenceIntent.SelectReason(InaccurateReason.PHONE_MOVED))
            vm.evidence(EvidenceIntent.SendReason)
            assertEquals(Unit, awaitItem())
        }
        vm.messages.test { assertEquals(EvidenceCopy.THANKS, awaitItem()) }
        assertNull(vm.evidence.value)
        assertEquals(InaccurateReason.PHONE_MOVED, world.feedback.getValue("e2").inaccurate)
    }
}

/** A finished trip with two braking events and a tip on the first; no trace is kept. */
private class World {
    private val trip = Trip(
        id = "t1", status = TripStatus.FINALISED, kind = TripKind.DRIVER, source = TripSource.AUTO,
        startedAt = 1_000_000L, startTzOffsetMin = 0, createdAt = 1L, updatedAt = 1L,
    )
    private val events = listOf(event("e1", 20.0), event("e2", 40.0))
    val feedback = mutableMapOf<String, EventFeedback>()
    private var score: Score? = Score(
        "t1", 80, 80.0, null, null, null, null, null, emptyList(), null, null,
        Tip("brake_light", TipCategory.BRAKING, "e1", null), null, "v1", 1L,
    )
    private val tracked = mutableListOf<AnalyticsEvent>()

    val trips = object : TripRepository {
        override fun observeTrips(): Flow<List<Trip>> = flowOf(listOf(trip))

        override suspend fun trip(id: String): Trip? = trip.takeIf { it.id == id }

        override suspend fun events(tripId: String): List<DrivingEvent> = events

        override suspend fun feedback(tripId: String): Map<String, EventFeedback> = feedback.toMap()

        override suspend fun score(tripId: String): Score? = score

        override suspend fun delete(tripId: String): FileResult = FileResult.OK
    }

    private val routes = object : RouteRepository {
        override fun observeRoutes(): Flow<List<Route>> = flowOf(emptyList())

        override suspend fun route(id: String): Route? = null

        override suspend fun matchFor(tripId: String): RouteMatch? = null

        override suspend fun personalBest(routeId: String): PersonalBest? = null
    }

    private val store = object : EventFeedbackStore {
        override suspend fun save(feedback: EventFeedback, score: Score?) {
            this@World.feedback[feedback.eventId] = feedback
            if (score != null) this@World.score = score
        }
    }

    private val clock = object : Clock {
        override fun monotonicNs(): Long = 0L

        override fun wallMs(): Long = 7L
    }

    private val rescorer = TripRescorer { r -> r.score }

    val evidence = TripEvidence(
        trips,
        object : AnalysisFileStore {
            override suspend fun write(trip: Trip, trace: AnalysisTrace): TripFileRef? = null

            override suspend fun read(tripId: String): AnalysisTrace? = null
        },
    )
    val markNeeded = MarkEventNeeded(trips, routes, store, rescorer, clock)
    val reportInaccurate = ReportEventInaccurate(trips, routes, store, rescorer, clock)

    val analytics = object : Analytics {
        override fun track(event: AnalyticsEvent) {
            tracked += event
        }

        override fun setUsageConsent(allowed: Boolean) = Unit

        override fun resetId() = Unit
    }

    fun names(): List<String> = tracked.map { it.name }

    private fun event(id: String, peak: Double) = DrivingEvent(
        id, "t1", EventType.BRAKE, peak, peak - 0.8, peak + 0.6, 1.4, -0.42, null, null, null, 13.3, 0.0, Confidence.HIGH, "v1",
    )
}
