package com.driveperf.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.driveperf.data.local.db.DrivePerfDatabase
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.trip.NotDrives
import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.SuspectAnswer
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.domain.trip.TripCorrections
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.tripfile.FileResult
import com.driveperf.foundation.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * [TripCorrections] on SQLDelight (DRIVE-100). [clock] stamps corrections and dates the "Not drives" purge, which
 * deletes through [trips] so files go first, as for any other deletion.
 */
public class LocalTripCorrections(
    private val db: DrivePerfDatabase,
    private val trips: TripRepository,
    private val io: CoroutineContext,
    private val clock: Clock,
) : TripCorrections {
    private val q get() = db.tripQueries

    override fun observeNotDrives(): Flow<List<Trip>> =
        q.notDrives().asFlow().mapToList(io).map { rows -> rows.map { it.toDomain(spans(it.id)) } }

    override suspend fun answerSuspect(tripId: String, answer: SuspectAnswer): Trip? = withContext(io) {
        db.transactionWithResult {
            val trip = finished(tripId) ?: return@transactionWithResult null
            when (answer) {
                SuspectAnswer.YES -> TripCorrection.confirming(trip.suspect)?.let { write(trip, it) }
                SuspectAnswer.NO -> if (trip.suspect == Suspect.NONE) null else write(trip, TripCorrection.DRIVER)
            }
        }
    }

    override suspend fun correct(tripId: String, to: TripCorrection): Trip? = withContext(io) {
        db.transactionWithResult { finished(tripId)?.let { write(it, to) } }
    }

    override suspend fun purgeExpiredNotDrives(): Int {
        val due = withContext(io) { q.expiredNotDrives(clock.wallMs() - NotDrives.RETENTION_MS).executeAsList() }
        return due.count { trips.delete(it) == FileResult.OK }
    }

    /** A finished, live trip: only those can be corrected. */
    private fun finished(tripId: String): Trip? = q.tripById(tripId).executeAsOneOrNull()
        ?.takeIf { it.status == TripStatus.FINALISED.name || it.status == TripStatus.INTERRUPTED.name }
        ?.toDomain(spans(tripId))

    /**
     * Sets [to] on [trip]. A suspected trip's question is answered by the correction itself: "Yes" when it matches the
     * suspicion, otherwise "No" (it was something else, or a drive after all). [TripCorrection.DRIVER] clears the
     * correction, so the trip is an ordinary drive again. Any other correction takes the trip off its route (§7.4).
     */
    private fun write(trip: Trip, to: TripCorrection): Trip {
        val now = clock.wallMs()
        val answer = when {
            trip.suspect == Suspect.NONE -> trip.suspectAnswer
            TripCorrection.confirming(trip.suspect) == to -> SuspectAnswer.YES
            else -> SuspectAnswer.NO
        }
        val correction = to.takeUnless { it == TripCorrection.DRIVER }
        val correctedAt = correction?.let { now }
        q.setCorrection(to.kind.name, correction?.name, correctedAt, answer?.name, now, trip.id)
        if (to.kind != TripKind.DRIVER) db.removeFromRoute(trip.id, now)
        return trip.copy(
            kind = to.kind, correction = correction, correctedAt = correctedAt, suspectAnswer = answer, updatedAt = now,
        )
    }

    private fun spans(tripId: String) = q.segmentsForTrip(tripId).executeAsList().map { it.toDomain() }
}
