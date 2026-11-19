package com.driveperf.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.driveperf.data.local.db.DrivePerfDatabase
import com.driveperf.domain.trip.TripVehicle
import com.driveperf.domain.trip.TripVehicles
import com.driveperf.foundation.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * [TripVehicles] on SQLDelight (DRIVE-131, F-HIS-02 AC2): writes `trip.vehicle_id`, stamped with [clock]. The trip
 * list and its score lines read the car through `vehicle_id`, so they update on their own.
 */
public class LocalTripVehicles(
    private val db: DrivePerfDatabase,
    private val io: CoroutineContext,
    private val clock: Clock,
) : TripVehicles {
    private val q get() = db.tripVehicleQueries

    override fun observeVehicles(): Flow<List<TripVehicle>> =
        q.activeTripVehicles().asFlow().mapToList(io).map { rows ->
            rows.map { TripVehicle(it.id, it.nickname, it.make, it.model, it.year?.toInt(), it.is_default != 0L) }
        }

    override suspend fun setVehicle(tripId: String, vehicleId: String?): Boolean = withContext(io) {
        db.transactionWithResult {
            if (vehicleId != null && q.isActiveVehicle(vehicleId).executeAsOne() == 0L) return@transactionWithResult false
            q.setTripVehicle(vehicleId, clock.wallMs(), tripId)
            q.changedRows().executeAsOne() > 0L
        }
    }
}
