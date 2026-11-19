package com.driveperf.presentation.drives

import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.score.Score
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripVehicle
import com.driveperf.domain.trip.TripVehicles
import com.driveperf.domain.tripfile.FileResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The core's car port, over a fixed garage, moving trips in [trips]. */
class FakeTripVehicles(private val trips: MutableStateFlow<List<Trip>>) : TripVehicles {
    val cars = MutableStateFlow(
        listOf(
            TripVehicle("gti", "GTI", "Volkswagen", "Golf GTI", 2019, isDefault = true),
            TripVehicle("ioniq", "Ioniq", "Hyundai", "Ioniq 5", 2023, isDefault = false),
        ),
    )
    val moves = mutableListOf<Pair<String, String?>>()

    override fun observeVehicles(): Flow<List<TripVehicle>> = cars

    override suspend fun setVehicle(tripId: String, vehicleId: String?): Boolean {
        moves += tripId to vehicleId
        trips.update { list -> list.map { if (it.id == tripId) it.copy(vehicleId = vehicleId) else it } }
        return true
    }
}

/** The core's trip repository: only `delete` is used, and it removes the trip from [trips] unless [result] fails. */
class FakeTripDeletes(private val trips: MutableStateFlow<List<Trip>>) : TripRepository {
    var result = FileResult.OK
    val deleted = mutableListOf<String>()

    override fun observeTrips(): Flow<List<Trip>> = trips
    override suspend fun trip(id: String): Trip? = trips.value.firstOrNull { it.id == id }
    override suspend fun events(tripId: String): List<DrivingEvent> = emptyList()
    override suspend fun feedback(tripId: String): Map<String, EventFeedback> = emptyMap()
    override suspend fun score(tripId: String): Score? = null

    override suspend fun delete(tripId: String): FileResult {
        deleted += tripId
        if (result == FileResult.OK) trips.update { list -> list.filterNot { it.id == tripId } }
        return result
    }
}

/** S-TRP-02 Edit trip and S-TRP-03 Delete trip (DRIVE-131, F-HIS-02). */
@OptIn(ExperimentalCoroutinesApi::class)
class TripEditTest {
    private val trips = MutableStateFlow(
        listOf(trip("a", startedAt = toronto(2026, 11, 10, 8)).copy(vehicleId = "gti"), trip("b", startedAt = toronto(2026, 11, 9, 8))),
    )
    private val corrections = FakeCorrections()
    private val vehicles = FakeTripVehicles(trips)
    private val deletes = FakeTripDeletes(trips)
    private val analytics = RecordingAnalytics()
    private val clock = FixedClock(toronto(2026, 11, 10, 18))

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.presenter(edits: TripEditPorts? = TripEditPorts(vehicles, deletes)) = DrivesPresenter(
        trips, corrections, MutableStateFlow(UnitSystem.METRIC), JavaTimeFormatter(), clock, backgroundScope, null, analytics, edits,
    )

    private fun DrivesPresenter.edit(action: TripEditAction) = dispatch(DrivesIntent.Edit(action))

    private fun TestScope.effects(p: DrivesPresenter): List<DrivesEffect> =
        mutableListOf<DrivesEffect>().also { list -> backgroundScope.launch { p.effects.collect { list += it } } }

    @Test
    fun `edit opens on the trip with its type, car and the pickers`() = runTest(UnconfinedTestDispatcher()) {
        val p = presenter()
        assertTrue(p.state.value.canEdit)
        p.edit(TripEditAction.Open("a", TripEditPage.MAIN))

        val edit = assertNotNull(p.state.value.edit)
        assertEquals("Edit trip", edit.title)
        assertEquals(TripEditPage.MAIN, edit.page)
        assertEquals("Tuesday morning", edit.tripTitle)
        assertTrue(edit.tripStats.endsWith("km · 33 min"), edit.tripStats)
        assertEquals("Driver", edit.typeValue)
        assertEquals("Scored", edit.typeDetail)
        assertEquals(
            listOf("Driver", "Passenger", "Bus or train", "Taxi or rideshare", "Other (not driving)"),
            edit.types.map { it.title },
        )
        assertEquals(listOf(true, false, false, false, false), edit.types.map { it.current })
        assertEquals("Not scored. Stays in your history.", edit.types[1].detail)
        assertEquals("Passenger. Not scored. Stays in your history. Not selected.", edit.types[1].accessibilityLabel)
        assertEquals("GTI", edit.carValue)
        assertEquals("2019 Volkswagen Golf GTI", edit.carDetail)
        assertEquals(listOf("gti", "ioniq"), edit.cars.map { it.id })
        assertTrue(edit.cars[0].isDefault && edit.cars[0].selected && edit.cars[0].current)
        assertFalse(edit.changed)
        assertNull(edit.changedBanner)
        assertNull(edit.typeBanner)
        assertEquals("Delete drive", edit.deleteLabel)

        p.edit(TripEditAction.ShowPage(TripEditPage.TYPE))
        assertEquals(TripEditPage.TYPE, p.state.value.edit?.page)
        p.edit(TripEditAction.Close)
        assertNull(p.state.value.edit)
    }

    @Test
    fun `nothing opens without the ports or for a trip that is not listed`() = runTest(UnconfinedTestDispatcher()) {
        val bare = presenter(edits = null)
        assertFalse(bare.state.value.canEdit)
        bare.edit(TripEditAction.Open("a", TripEditPage.MAIN))
        assertNull(bare.state.value.edit)
        bare.edit(TripEditAction.AskDelete("a"))
        assertNull(bare.state.value.deleting)

        val p = presenter()
        p.edit(TripEditAction.Open("missing", TripEditPage.MAIN))
        assertNull(p.state.value.edit)
    }

    @Test
    fun `choosing passenger shows the banner and save corrects the trip with analytics and a toast`() = runTest(UnconfinedTestDispatcher()) {
        val p = presenter()
        val effects = effects(p)
        p.edit(TripEditAction.Open("a", TripEditPage.TYPE))
        p.edit(TripEditAction.PickType(TripEditType.PASSENGER))

        val edit = assertNotNull(p.state.value.edit)
        assertEquals("Passenger", edit.typeValue)
        assertEquals("Not scored", edit.typeDetail)
        assertTrue(edit.types[1].selected)
        assertEquals(
            "Passenger: not a drive you made. Your scores, baseline, route records and recaps recompute without it, within 2 seconds.",
            edit.typeBanner,
        )
        assertTrue(edit.changed)
        assertNotNull(edit.changedBanner)

        p.edit(TripEditAction.Save)

        assertNull(p.state.value.edit)
        assertEquals(listOf("a" to TripCorrection.PASSENGER), corrections.corrections)
        assertTrue(vehicles.moves.isEmpty())
        assertEquals(listOf(mapOf("to" to "passenger", "source" to "trip_detail")), analytics.named("trip_corrected"))
        assertEquals(listOf<DrivesEffect>(DrivesEffect.TripEdited("a", "Marked as passenger · scores updated")), effects)
    }

    @Test
    fun `taxi and other save their corrections`() = runTest(UnconfinedTestDispatcher()) {
        val p = presenter()
        p.edit(TripEditAction.Open("a", TripEditPage.TYPE))
        p.edit(TripEditAction.PickType(TripEditType.TAXI))
        p.edit(TripEditAction.Save)
        p.edit(TripEditAction.Open("b", TripEditPage.TYPE))
        p.edit(TripEditAction.PickType(TripEditType.OTHER))
        p.edit(TripEditAction.Save)

        assertEquals(listOf("a" to TripCorrection.TAXI, "b" to TripCorrection.OTHER), corrections.corrections)
        assertEquals(listOf("taxi", "other"), analytics.named("trip_corrected").map { it["to"] })
    }

    @Test
    fun `a car change moves the trip and undo puts it back`() = runTest(UnconfinedTestDispatcher()) {
        val p = presenter()
        val effects = effects(p)
        p.edit(TripEditAction.Open("a", TripEditPage.CAR))
        p.edit(TripEditAction.PickCar("ioniq"))

        val edit = assertNotNull(p.state.value.edit)
        assertEquals("Ioniq", edit.carValue)
        assertEquals("Moves this drive to the Ioniq. Per-car stats and the Story tint update.", edit.carNote)
        assertTrue(edit.cars[1].selected && !edit.cars[1].current)

        p.edit(TripEditAction.Save)

        assertTrue(corrections.corrections.isEmpty())
        assertEquals(listOf<Pair<String, String?>>("a" to "ioniq"), vehicles.moves)
        assertEquals(listOf(mapOf("source" to "trip_detail")), analytics.named("trip_car_changed"))
        assertEquals(listOf<DrivesEffect>(DrivesEffect.TripEdited("a", "Moved to the Ioniq · stats updated")), effects)

        p.edit(TripEditAction.Undo)
        assertEquals(listOf<Pair<String, String?>>("a" to "ioniq", "a" to "gti"), vehicles.moves)
        assertTrue(corrections.corrections.isEmpty())
        p.edit(TripEditAction.Undo)
        assertEquals(2, vehicles.moves.size)
    }

    @Test
    fun `undo restores the type the trip had`() = runTest(UnconfinedTestDispatcher()) {
        trips.value = listOf(trip("w", kind = TripKind.NOT_DRIVING, correction = TripCorrection.WALK))
        corrections.notDrives.value = trips.value
        val p = presenter()
        p.edit(TripEditAction.Open("w", TripEditPage.TYPE))
        assertEquals(TripEditType.OTHER, p.state.value.edit?.type)
        p.edit(TripEditAction.PickType(TripEditType.DRIVER))
        p.edit(TripEditAction.Save)
        p.edit(TripEditAction.Undo)

        assertEquals(listOf("w" to TripCorrection.DRIVER, "w" to TripCorrection.WALK), corrections.corrections)
    }

    @Test
    fun `saving with nothing changed writes nothing`() = runTest(UnconfinedTestDispatcher()) {
        val p = presenter()
        val effects = effects(p)
        p.edit(TripEditAction.Open("a", TripEditPage.MAIN))
        p.edit(TripEditAction.PickType(TripEditType.DRIVER))
        p.edit(TripEditAction.PickCar("gti"))
        assertFalse(p.state.value.edit!!.changed)
        p.edit(TripEditAction.Save)

        assertNull(p.state.value.edit)
        assertTrue(corrections.corrections.isEmpty() && vehicles.moves.isEmpty() && effects.isEmpty())
        p.edit(TripEditAction.Save)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `delete asks first, then deletes, closes everything and reports it`() = runTest(UnconfinedTestDispatcher()) {
        val p = presenter()
        val effects = effects(p)
        p.dispatch(DrivesIntent.Open("a"))
        p.edit(TripEditAction.Open("a", TripEditPage.MAIN))
        p.edit(TripEditAction.AskDelete("a"))

        val dialog = assertNotNull(p.state.value.deleting)
        assertEquals("Delete this drive?", dialog.title)
        assertEquals("Its route, score, Story and raw sensor data are removed from this phone. This can’t be undone.", dialog.message)
        assertEquals("Tuesday morning", dialog.tripTitle)
        assertEquals("Delete", dialog.confirm)
        assertEquals("Cancel", dialog.cancel)
        assertFalse(dialog.deleting)

        p.edit(TripEditAction.CancelDelete)
        assertNull(p.state.value.deleting)
        assertNotNull(p.state.value.edit)
        assertTrue(deletes.deleted.isEmpty())

        p.edit(TripEditAction.AskDelete("a"))
        p.edit(TripEditAction.ConfirmDelete)

        assertEquals(listOf("a"), deletes.deleted)
        assertNull(p.state.value.deleting)
        assertNull(p.state.value.edit)
        assertNull(p.state.value.detail)
        assertEquals(listOf("b"), p.state.value.trips.map { it.id })
        assertEquals(listOf(mapOf("source" to "edit")), analytics.named("trip_deleted"))
        assertEquals(listOf<DrivesEffect>(DrivesEffect.TripDeleted("a", "Drive deleted")), effects)
    }

    @Test
    fun `a failed delete keeps the dialog with an error and can be retried`() = runTest(UnconfinedTestDispatcher()) {
        deletes.result = FileResult.IO_ERROR
        val p = presenter()
        val effects = effects(p)
        p.edit(TripEditAction.AskDelete("b"))
        p.edit(TripEditAction.ConfirmDelete)

        val dialog = assertNotNull(p.state.value.deleting)
        assertEquals("Couldn’t delete this drive. Try again.", dialog.error)
        assertFalse(dialog.deleting)
        assertTrue(effects.isEmpty())
        assertTrue(analytics.named("trip_deleted").isEmpty())

        deletes.result = FileResult.OK
        p.edit(TripEditAction.ConfirmDelete)
        assertEquals(listOf("b", "b"), deletes.deleted)
        assertEquals(listOf(mapOf("source" to "detail")), analytics.named("trip_deleted"))
    }
}
