package com.driveperf.platform.ports

import kotlinx.coroutines.flow.Flow

/*
 * INTERIM PORTS (DRIVE-72). DRIVE-62 owns the real `LocationSource`, `MotionSource`, `ActivitySource`, `Clock` and
 * `TripFileStore` in `:shared:domain` / `:shared:foundation`. These mirror the names and shapes in
 * pipeline-and-scoring.md so the Android adapters can be written now. When DRIVE-62's interface PR merges:
 * delete this package, import the shared types, and fix signature differences in the adapters (no logic changes).
 */

/** Source of GNSS fixes. Collecting the flow starts location updates; cancelling it stops them. */
interface LocationSource {
    fun samples(): Flow<LocationSample>
}

/** Source of IMU samples. Collecting starts the sensors; cancelling stops them. */
interface MotionSource {
    val capabilities: MotionCapabilities
    fun samples(): Flow<MotionSample>
}

/** Source of OS activity transitions. Collecting registers for them; cancelling unregisters. */
interface ActivitySource {
    fun events(): Flow<ActivityEvent>
}

/** The only clock capture code may read. [monotonicNs] is the trip time base. */
interface Clock {
    fun monotonicNs(): Long
    fun wallMs(): Long
}

/** Append-only writer for one trip's R0 capture log. */
interface TripFileWriter {
    fun append(sample: LocationSample)
    fun append(sample: MotionSample)
    fun append(event: ActivityEvent)

    /** Forces everything written so far to disk. Called at least every 5 s while recording. */
    fun flush()
    fun close()
}

/** Where trip files live. */
interface TripFileStore {
    fun open(tripId: String): TripFileWriter

    /** Removes a trip file, for a trip that recorded nothing. */
    fun delete(tripId: String)
}
