package com.driveperf.testing

import com.driveperf.domain.capture.CaptureBatch
import com.driveperf.domain.capture.CaptureHeader
import com.driveperf.domain.capture.CaptureLog
import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.capture.FinalisedTrip
import com.driveperf.domain.capture.Parking
import com.driveperf.domain.capture.TimeZoneProvider
import com.driveperf.domain.capture.TripStore
import com.driveperf.domain.capture.WatchdogState
import com.driveperf.domain.capture.WatchdogStore
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripStatus

/** A [CaptureLog] in memory. [syncs] counts `fsync` calls. */
public class InMemoryCaptureLog : CaptureLog {
    private val logs = mutableMapOf<String, CapturedTrip>()

    /** Number of [sync] calls. */
    public var syncs: Int = 0
        private set

    /** When false, [append] fails as on a full disk. */
    public var writable: Boolean = true

    /** When false, [open] fails as when the file cannot be created. */
    public var openable: Boolean = true

    override suspend fun open(header: CaptureHeader): Boolean {
        if (!openable) return false
        logs[header.tripId] = CapturedTrip(header, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        return true
    }

    override suspend fun append(tripId: String, batch: CaptureBatch): Boolean {
        val log = logs[tripId]
        if (log == null || !writable) return false
        logs[tripId] = log.copy(
            fixes = log.fixes + batch.fixes,
            motion = log.motion + batch.motion,
            activity = log.activity + batch.activity,
            recordedAccel = log.recordedAccel + batch.recordedAccel,
            marks = log.marks + batch.marks,
        )
        return true
    }

    override suspend fun sync(tripId: String) {
        syncs++
    }

    override suspend fun read(tripId: String): CapturedTrip? = logs[tripId]

    /** When false, [replace] fails as on a full disk and leaves the log as it was. */
    public var replaceable: Boolean = true

    override suspend fun replace(header: CaptureHeader, batches: List<CaptureBatch>): Boolean {
        if (!replaceable) return false
        logs[header.tripId] = CapturedTrip(
            header,
            batches.flatMap { it.fixes },
            batches.flatMap { it.motion },
            batches.flatMap { it.activity },
            batches.flatMap { it.recordedAccel },
            batches.flatMap { it.marks },
        )
        return true
    }

    override suspend fun delete(tripId: String) {
        logs.remove(tripId)
    }

    /** IDs of every log still present. */
    public fun tripIds(): Set<String> = logs.keys.toSet()
}

/** A [TripStore] in memory. */
public class InMemoryTripStore(public var parking: Parking? = null) : TripStore {
    /** Every trip row by ID. */
    public val trips: MutableMap<String, Trip> = mutableMapOf()

    /** Every committed result, in commit order. */
    public val finalised: MutableList<FinalisedTrip> = mutableListOf()

    override suspend fun insertRecording(trip: Trip) {
        trips[trip.id] = trip
    }

    override suspend fun updateRecording(trip: Trip) {
        trips[trip.id] = trip
    }

    override suspend fun recordingTrip(): Trip? = trips.values.firstOrNull { it.status == TripStatus.RECORDING }

    override suspend fun deleteNow(tripId: String) {
        trips.remove(tripId)
    }

    override suspend fun lastParking(): Parking? = parking

    override suspend fun saveFinalised(result: FinalisedTrip) {
        trips[result.trip.id] = result.trip
        finalised += result
    }

    override suspend fun setLastParking(parking: Parking) {
        this.parking = parking
    }
}

/** A fixed time zone for tests. */
public class FixedTimeZone(private val id: String = "Etc/UTC", private val offsetMin: Int = 0) : TimeZoneProvider {
    override fun zoneId(): String = id

    override fun offsetMinAt(wallMs: Long): Int = offsetMin
}

/** A [WatchdogStore] in memory. */
public class InMemoryWatchdogStore(public var state: WatchdogState = WatchdogState()) : WatchdogStore {
    override suspend fun read(): WatchdogState = state

    override suspend fun write(state: WatchdogState) {
        this.state = state
    }
}
