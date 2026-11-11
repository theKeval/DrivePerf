package com.driveperf.data.local

import com.driveperf.domain.capture.FinalisedTrip
import com.driveperf.domain.trip.NotDrives
import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.SuspectAnswer
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.domain.trip.TripCorrections
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.trip.openQuestion
import com.driveperf.domain.tripfile.FileResult
import com.driveperf.domain.tripfile.TripFileKind
import com.driveperf.domain.tripfile.TripFileStore
import com.driveperf.testing.InMemoryTripFileStore
import com.driveperf.testing.TestClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DRIVE-100 (F-CAP-04): answering "Was this a bus or train?", correcting a trip, the "Not drives" filter and its purge. */
class TripCorrectionsTest {
    private val db = openDatabase(JvmDatabaseDriverFactory().create())
    private val files = InMemoryTripFileStore()
    private val clock = TestClock()

    private fun trip(id: String, startedAt: Long, suspect: Suspect = Suspect.NONE) = Trip(
        id = id, status = TripStatus.FINALISED, kind = TripKind.DRIVER, source = TripSource.AUTO, startedAt = startedAt,
        startTzOffsetMin = 0, endedAt = startedAt + 600_000, durationS = 600.0, distanceM = 5_000.0, suspect = suspect,
        createdAt = startedAt, updatedAt = startedAt,
    )

    /** The repository and the corrections over the same database, after saving [trips]. */
    private class Repos(val trips: LocalTripRepository, corrections: LocalTripCorrections) : TripCorrections by corrections {
        suspend fun trip(id: String) = trips.trip(id)
        fun observeTrips() = trips.observeTrips()
    }

    private suspend fun saved(vararg trips: Trip, files: TripFileStore = this.files): Repos {
        val io = kotlin.coroutines.EmptyCoroutineContext
        val store = LocalTripStore(db, "device-1", io)
        trips.forEach { store.saveFinalised(FinalisedTrip(it)) }
        val repo = LocalTripRepository(db, files, io)
        return Repos(repo, LocalTripCorrections(db, repo, io, clock))
    }

    @Test
    fun yesToTheTransitQuestionMovesTheTripToNotDrives() = runTest {
        val repo = saved(trip("bus", 1_000L, Suspect.TRANSIT), trip("drive", 2_000L))
        assertEquals(Suspect.TRANSIT, repo.trip("bus")?.openQuestion)

        val answered = repo.answerSuspect("bus", SuspectAnswer.YES)

        assertEquals(TripKind.TRANSIT, answered?.kind)
        assertEquals(TripCorrection.TRANSIT, answered?.correction)
        assertEquals(SuspectAnswer.YES, answered?.suspectAnswer)
        assertEquals(clock.wallMs(), answered?.correctedAt)
        assertNull(answered?.openQuestion)
        assertEquals(listOf("drive"), repo.observeTrips().first().map { it.id })
        assertEquals(listOf("bus"), repo.observeNotDrives().first().map { it.id })
        assertEquals(answered, repo.trip("bus"))
    }

    @Test
    fun yesToTheBikeQuestionIsABikeCorrection() = runTest {
        val repo = saved(trip("bike", 1_000L, Suspect.CYCLING))
        val answered = repo.answerSuspect("bike", SuspectAnswer.YES)
        assertEquals(TripKind.NOT_DRIVING, answered?.kind)
        assertEquals(TripCorrection.BIKE, answered?.correction)
    }

    @Test
    fun noKeepsTheTripADriveAndClosesTheQuestion() = runTest {
        val repo = saved(trip("car", 1_000L, Suspect.TRANSIT))
        val answered = repo.answerSuspect("car", SuspectAnswer.NO)
        assertEquals(TripKind.DRIVER, answered?.kind)
        assertEquals(SuspectAnswer.NO, answered?.suspectAnswer)
        assertNull(answered?.correction)
        assertNull(answered?.openQuestion)
        assertEquals(listOf("car"), repo.observeTrips().first().map { it.id })
    }

    @Test
    fun aTripWithoutASuspicionHasNoQuestionToAnswer() = runTest {
        val repo = saved(trip("drive", 1_000L))
        assertNull(repo.answerSuspect("drive", SuspectAnswer.YES))
        assertNull(repo.answerSuspect("drive", SuspectAnswer.NO))
        assertNull(repo.answerSuspect("missing", SuspectAnswer.YES))
    }

    @Test
    fun anyTripCanBeCorrectedAndRestored() = runTest {
        val repo = saved(trip("walk", 1_000L), trip("ride", 2_000L, Suspect.TRANSIT))

        assertEquals(TripKind.NOT_DRIVING, repo.correct("walk", TripCorrection.WALK)?.kind)
        val passenger = repo.correct("ride", TripCorrection.PASSENGER)
        assertEquals(TripKind.PASSENGER, passenger?.kind)
        assertEquals(SuspectAnswer.NO, passenger?.suspectAnswer, "it was not a bus after all")
        assertEquals(listOf("ride"), repo.observeTrips().first().map { it.id }, "passenger trips stay in history")
        assertEquals(listOf("walk"), repo.observeNotDrives().first().map { it.id })

        val restored = repo.correct("walk", TripCorrection.DRIVER)
        assertEquals(TripKind.DRIVER, restored?.kind)
        assertNull(restored?.correction)
        assertNull(restored?.correctedAt)
        assertTrue(repo.observeNotDrives().first().isEmpty())
    }

    @Test
    fun recordingAndMissingTripsCannotBeCorrected() = runTest {
        val repo = saved()
        LocalTripStore(db, "device-1", coroutineContext).insertRecording(trip("live", 1_000L).copy(status = TripStatus.RECORDING))
        assertNull(repo.correct("live", TripCorrection.OTHER))
        assertNull(repo.correct("missing", TripCorrection.OTHER))
    }

    @Test
    fun notDrivesArePurgedThirtyDaysAfterTheCorrectionFilesFirst() = runTest {
        val repo = saved(trip("old", 1_000L), trip("new", 2_000L), trip("kept", 3_000L))
        files.write("old", TripFileKind.R0, byteArrayOf(1, 2, 3))
        repo.correct("old", TripCorrection.TRANSIT)
        clock.advanceMs(NotDrives.RETENTION_MS - 1)
        repo.correct("new", TripCorrection.OTHER)

        assertEquals(0, repo.purgeExpiredNotDrives(), "not yet 30 days")
        clock.advanceMs(1)
        assertEquals(1, repo.purgeExpiredNotDrives())

        assertNull(repo.trip("old"))
        assertTrue("old" !in files.tripIds())
        assertEquals(listOf("new"), repo.observeNotDrives().first().map { it.id })
        assertEquals(listOf("kept"), repo.observeTrips().first().map { it.id })
    }

    @Test
    fun aNotDriveWhoseFilesCannotBeDeletedIsKeptForTheNextRun() = runTest {
        val failing = object : TripFileStore by files {
            override fun deleteTrip(tripId: String): FileResult = FileResult.IO_ERROR
        }
        val repo = saved(trip("bus", 1_000L), files = failing)
        repo.correct("bus", TripCorrection.TRANSIT)
        clock.advanceMs(NotDrives.RETENTION_MS)
        assertEquals(0, repo.purgeExpiredNotDrives())
        assertEquals(listOf("bus"), repo.observeNotDrives().first().map { it.id })
    }
}
