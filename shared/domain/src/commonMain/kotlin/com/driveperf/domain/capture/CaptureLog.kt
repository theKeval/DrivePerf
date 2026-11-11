package com.driveperf.domain.capture

import com.driveperf.domain.route.PersonalBest
import com.driveperf.domain.route.Route
import com.driveperf.domain.route.RouteMatch
import com.driveperf.domain.score.Score
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.PreviewRow
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.tripfile.AnalysisTrace
import com.driveperf.domain.tripfile.TripFileRef

/** State changes written into the capture log (data-model.md §2.4 `Mark.Type`). */
public enum class CaptureMarkType {
    RECORDING_STARTED, PAUSED, RESUMED, APP_KILLED, PHONE_MOVED, ALIGNMENT_SETTLED, LOW_BATTERY, THERMAL, GNSS_LOST,
    GNSS_RESTORED, CONFIRMED, IMU_RATE, IMU_STOPPED,
}

/** One mark. [detail] is an enum key or a number, never free text. */
public data class CaptureMark(val tMonoNs: Long, val type: CaptureMarkType, val detail: String? = null)

/**
 * Fixed facts about a capture, written once when the trip is created (the R0 header).
 *
 * @property parking where and when the car was parked before this trip, if known. It bounds the motion-start search
 *   and anchors the estimated start (§2.8, Spike 1 change 3).
 */
public data class CaptureHeader(
    val tripId: String,
    val startMonoNs: Long,
    val startWallMs: Long,
    val tzOffsetMin: Int,
    val tzId: String,
    val wakeKind: WakeKind,
    val sourceDetail: String?,
    val nominalImuHz: Int,
    val parking: Parking?,
)

/** Samples captured since the previous append, in time order per stream. */
public data class CaptureBatch(
    val fixes: List<LocationSample> = emptyList(),
    val motion: List<MotionSample> = emptyList(),
    val activity: List<ActivitySample> = emptyList(),
    val recordedAccel: List<RecordedAccelSample> = emptyList(),
    val marks: List<CaptureMark> = emptyList(),
)

/** A whole R0 capture log, decoded. A truncated last chunk has already been dropped (§1.7). */
public data class CapturedTrip(
    val header: CaptureHeader,
    val fixes: List<LocationSample>,
    val motion: List<MotionSample>,
    val activity: List<ActivitySample>,
    val recordedAccel: List<RecordedAccelSample>,
    val marks: List<CaptureMark>,
)

/**
 * The R0 capture log of a trip (ADR-003): length-delimited protobuf chunks appended while recording. Implemented in
 * `:shared:data:local` on top of [com.driveperf.domain.tripfile.TripFileStore]; not a platform port.
 */
public interface CaptureLog {
    /** Starts a new log with its header. */
    public suspend fun open(header: CaptureHeader): Boolean

    /** Appends one chunk. Returns false when the disk is full or the file cannot be written. */
    public suspend fun append(tripId: String, batch: CaptureBatch): Boolean

    /** Makes everything appended so far durable (`fsync`). */
    public suspend fun sync(tripId: String)

    /** Reads the valid prefix of the log, or null when there is none. */
    public suspend fun read(tripId: String): CapturedTrip?

    /** Deletes the log. */
    public suspend fun delete(tripId: String)
}

/** Where and when the car was last parked: the end of the previous trip. */
public data class Parking(val point: GeoPoint, val wallMs: Long)

/**
 * Trip rows the recorder writes (ADR-002 section 3). Implemented in `:shared:data:local` on SQLDelight; not a
 * platform port.
 */
public interface TripStore {
    /** Inserts a trip in `RECORDING` (a candidate or a confirmed trip). */
    public suspend fun insertRecording(trip: Trip)

    /** Updates a recording trip, for example when a candidate is confirmed or changes source. */
    public suspend fun updateRecording(trip: Trip)

    /** The trip still `RECORDING` after a kill, if any. */
    public suspend fun recordingTrip(): Trip?

    /** Deletes a candidate or an unsaved passenger trip and its rows immediately. */
    public suspend fun deleteNow(tripId: String)

    /** The previous trip's end, for the parked region and the motion-start bound. */
    public suspend fun lastParking(): Parking?

    /**
     * Commits a finished trip and everything derived from it in one transaction (data-model.md §2.3 step 2): the
     * trip row with its quality and spans, events, score, route match, a newly created route and the file rows.
     */
    public suspend fun saveFinalised(result: FinalisedTrip)

    /** Remembers the parking spot (called at trip end, also for discarded trips that moved the car). */
    public suspend fun setLastParking(parking: Parking)
}

/**
 * Everything a finished trip commits. [trip] has status `FINALISED` or `INTERRUPTED`.
 *
 * @property newRoute a route to insert or update: a new candidate, or a matched route whose centroids, run count
 *   state or name changed (§7.4).
 * @property extraMatches matches for other trips, written in the same transaction: the older trip of a newly paired
 *   route candidate (§7.4).
 * @property personalBest the route record after this trip, when it has a route (§7.5).
 * @property preview the preview track (data-model.md §2.5), stored as a file beside the trip.
 * @property analysis the R1 analysis signal, written as the trip's R1 file at finalisation (ADR-003). Null when the
 *   finaliser does not produce one (the summary-only finaliser).
 * @property splitCapture set when finalisation split a trip longer than 12 h (§1.6): the capture log this trip keeps,
 *   which replaces its R0 (or creates it for the later part, which has a new ID).
 * @property files the trip's stored files, filled in by the recorder just before the commit.
 */
public data class FinalisedTrip(
    val trip: Trip,
    val events: List<DrivingEvent> = emptyList(),
    val score: Score? = null,
    val routeMatch: RouteMatch? = null,
    val newRoute: Route? = null,
    val extraMatches: List<RouteMatch> = emptyList(),
    val personalBest: PersonalBest? = null,
    val preview: List<PreviewRow> = emptyList(),
    val analysis: AnalysisTrace? = null,
    val splitCapture: CapturedTrip? = null,
    val files: List<TripFileRef> = emptyList(),
)

/** The device's time zone, for trip start offsets and part of day (data-model.md P4). */
public interface TimeZoneProvider {
    /** IANA zone ID, for example `America/Toronto`. */
    public fun zoneId(): String

    /** UTC offset in minutes at [wallMs], for example −300 for EST. */
    public fun offsetMinAt(wallMs: Long): Int
}

/** Preview tracks (data-model.md §2.5): at most 400 rows per trip, kept as a file. Implemented in `:shared:data:local`. */
public interface PreviewStore {
    public suspend fun write(tripId: String, rows: List<PreviewRow>): Boolean

    public suspend fun read(tripId: String): List<PreviewRow>?
}
