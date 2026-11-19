package com.driveperf.feature.replay

import com.driveperf.domain.feedback.EventFeedbackStore
import com.driveperf.domain.feedback.MarkEventNeeded
import com.driveperf.domain.feedback.ReportEventInaccurate
import com.driveperf.domain.replay.EventMarker
import com.driveperf.domain.replay.Replay
import com.driveperf.domain.replay.ReplayDetails
import com.driveperf.domain.replay.ReplayDetailsRepository
import com.driveperf.domain.replay.ReplayDistance
import com.driveperf.domain.replay.StopPin
import com.driveperf.domain.replay.TripEvidence
import com.driveperf.domain.repository.RouteRepository
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.route.PersonalBest
import com.driveperf.domain.route.Route
import com.driveperf.domain.route.RouteMatch
import com.driveperf.domain.score.Score
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.tripfile.AnalysisFileStore
import com.driveperf.domain.tripfile.AnalysisTrace
import com.driveperf.domain.tripfile.FileResult
import com.driveperf.domain.tripfile.TripFileRef
import com.driveperf.foundation.Clock
import com.driveperf.presentation.evidence.EvidenceIntent
import com.driveperf.presentation.evidence.EvidencePresenter
import com.driveperf.presentation.evidence.NeededStep
import com.driveperf.presentation.evidence.TipDetailIntent
import com.driveperf.presentation.evidence.TipDetailPresenter
import com.driveperf.presentation.replay.ReplayPresenter
import com.driveperf.presentation.replay.ReplaySelection
import com.driveperf.presentation.replay.ReplaySource
import com.driveperf.presentation.replay.ReplayTimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The Android side of the pins and sheets: pins open the shared sheets, and the sheets drive the replay underneath. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReplaySheetsTest {
    private val analytics = RecordingAnalytics()
    private val events = listOf(event("e1", 100.0), event("e2", 300.0))
    private val feedback = mutableMapOf<String, EventFeedback>()
    private var detailLoads = 0

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun event(id: String, t: Double) =
        DrivingEvent(id, "t1", EventType.BRAKE, t, t - 1, t + 1, 2.0, 0.4, null, 10.01, 20.0, 12.0, 2.0, Confidence.HIGH, "v1")

    private val trips = object : TripRepository {
        val trip = Trip(
            id = "t1", status = TripStatus.FINALISED, kind = TripKind.DRIVER, source = TripSource.AUTO,
            startedAt = 0L, startTzOffsetMin = 0, createdAt = 0L, updatedAt = 0L,
        )

        override fun observeTrips(): Flow<List<Trip>> = flowOf(listOf(trip))

        override suspend fun trip(id: String): Trip? = trip

        override suspend fun events(tripId: String): List<DrivingEvent> = events

        override suspend fun feedback(tripId: String): Map<String, EventFeedback> = feedback.toMap()

        override suspend fun score(tripId: String): Score? = null

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
            this@ReplaySheetsTest.feedback[feedback.eventId] = feedback
        }
    }

    private val clock = object : Clock {
        override fun monotonicNs(): Long = 0L

        override fun wallMs(): Long = 0L
    }

    private val noTrace = object : AnalysisFileStore {
        override suspend fun write(trip: Trip, trace: AnalysisTrace): TripFileRef? = null

        override suspend fun read(tripId: String): AnalysisTrace? = null
    }

    private val details = object : ReplayDetailsRepository {
        override suspend fun details(tripId: String, replay: Replay): ReplayDetails {
            detailLoads++
            val markers = events.map {
                EventMarker(it.id, it.type, it.tStartS, it.tPeakS, it.tEndS, it.lat, it.lng, feedback[it.id]?.needed == true, true)
            }
            return ReplayDetails(
                markers, events.associateBy { it.id }, null, listOf(StopPin(1, 50.0, 20.0, 10.004, 20.0)), null,
                ReplayDistance(replay.track, null), 0L,
            )
        }
    }

    private val units = MutableStateFlow(UnitSystem.METRIC)

    private fun viewModel() = ReplayViewModel(
        presenterFactory = { scope ->
            ReplayPresenter(
                "t1", ReplaySource.TRIP_DETAIL, FakeReplayRepository(), { false }, analytics, { 0L }, scope, details, units,
                ReplayTimeFormat.None,
            )
        },
        maps = ReplayMapConfig(false, null, MutableStateFlow(true)),
        units = units,
        sheets = ReplaySheets(
            evidence = { eventId, scope ->
                EvidencePresenter(
                    "t1", eventId, trips, TripEvidence(trips, noTrace),
                    MarkEventNeeded(trips, routes, store, { it.score }, clock),
                    ReportEventInaccurate(trips, routes, store, { it.score }, clock),
                    analytics, units, ReplayTimeFormat.None, scope,
                )
            },
            tip = { tipId, eventId, scope ->
                TipDetailPresenter("t1", tipId, eventId, trips, TripEvidence(trips, noTrace), analytics, units, scope)
            },
        ),
    )

    private val ReplayViewModel.ready: ReplayUiState.Ready get() = state.value as ReplayUiState.Ready

    @Test fun `an event pin pauses on it and opens the evidence sheet`() {
        val vm = viewModel()
        assertEquals(2, vm.ready.overlay?.eventPins?.size)
        vm.selectEvent("e2")
        assertEquals(ReplaySelection.Event("e2"), vm.ready.selection)
        assertEquals(300.0, vm.time.doubleValue, 1e-9)
        val evidence = checkNotNull(vm.ready.evidence)
        assertEquals("e2", evidence.eventId)
        assertNotNull("the trace was not kept, so the sheet says so", evidence.banners.firstOrNull())
        vm.closeEvidence()
        assertNull(vm.ready.evidence)
        assertNull(vm.ready.selection)
    }

    @Test fun `stepping in the sheet moves the replay underneath`() {
        val vm = viewModel()
        vm.selectEvent("e1")
        vm.evidence(EvidenceIntent.Next)
        assertEquals("e2", vm.ready.evidence?.eventId)
        assertEquals(ReplaySelection.Event("e2"), vm.ready.selection)
        assertEquals(300.0, vm.time.doubleValue, 1e-9)
    }

    @Test fun `marking an event needed reloads the pins`() {
        val vm = viewModel()
        vm.selectEvent("e1")
        val loads = detailLoads
        vm.evidence(EvidenceIntent.AskNeeded)
        vm.evidence(EvidenceIntent.ConfirmNeeded)
        assertEquals(NeededStep.DONE, vm.ready.evidence?.neededSheet?.step)
        assertTrue(detailLoads > loads)
        assertTrue(vm.ready.overlay?.eventPins?.first()?.marker?.needed == true)
    }

    @Test fun `not accurate closes the sheet with a message`() = kotlinx.coroutines.test.runTest {
        val vm = viewModel()
        val messages = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { messages += it } }
        vm.selectEvent("e1")
        vm.evidence(EvidenceIntent.OpenReasons)
        vm.evidence(EvidenceIntent.SelectReason(com.driveperf.domain.trip.InaccurateReason.OTHER))
        vm.evidence(EvidenceIntent.SendReason)
        assertNull(vm.ready.evidence)
        assertEquals(listOf(com.driveperf.presentation.evidence.EvidenceCopy.THANKS), messages)
        job.cancel()
    }

    @Test fun `a stop pin shows its sheet and jump moves the marker`() {
        val vm = viewModel()
        vm.selectStop(1)
        assertEquals("Stop", vm.ready.pinSheet?.title)
        vm.jumpToPin()
        assertNull(vm.ready.pinSheet)
        assertEquals(50.0, vm.time.doubleValue, 1e-9)
        vm.selectStop(1)
        vm.clearSelection()
        assertNull(vm.ready.pinSheet)
    }

    @Test fun `the tip sheet opens and closes over the evidence`() {
        val vm = viewModel()
        vm.selectEvent("e1")
        vm.tip(TipDetailIntent.Back)
        assertNull(vm.ready.tip)
        vm.closeTip()
        assertFalse(vm.ready.evidence == null)
    }
}
