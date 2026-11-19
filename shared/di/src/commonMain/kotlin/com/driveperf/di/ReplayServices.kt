package com.driveperf.di

import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.feedback.ReportEventInaccurate
import com.driveperf.domain.replay.TripEvidence
import com.driveperf.domain.replay.TripReplayDetails
import com.driveperf.presentation.evidence.EvidencePresenter
import com.driveperf.presentation.evidence.TipDetailPresenter
import com.driveperf.presentation.replay.ReplayPresenter
import com.driveperf.presentation.replay.ReplaySource
import com.driveperf.presentation.replay.ReplayTimeFormat
import kotlinx.coroutines.CoroutineScope

/**
 * The shared Replay presenter for [tripId] (S-RPL-01), running in a child of [scope]. Android passes
 * `viewModelScope`. Call `close()` when the screen goes away. The route only: see the overload with a region for the
 * pins and stats.
 */
public fun CoreServices.replayPresenter(tripId: String, source: ReplaySource, scope: CoroutineScope): ReplayPresenter =
    ReplayPresenter(
        tripId = tripId,
        source = source,
        replays = replay,
        isRecording = recordingCheck(tripId),
        analytics = analytics,
        monoNowNs = clock::monotonicNs,
        scope = scope,
    )

/** [replayPresenter] on the main thread with its own scope, for Swift. Call `close()` on deinit. */
public fun CoreServices.replayPresenter(tripId: String, source: ReplaySource): ReplayPresenter =
    ReplayPresenter.onMain(tripId, source, replay, recordingCheck(tripId), analytics, clock::monotonicNs)

/**
 * The Replay presenter with the event, stop and max-speed pins, the stats and the elevation profile (DRIVE-34,
 * DRIVE-203). [region] picks the default units when the user has not chosen; [format] is the app's time of day.
 */
public fun CoreServices.replayPresenter(
    tripId: String,
    source: ReplaySource,
    region: String,
    format: ReplayTimeFormat,
    scope: CoroutineScope,
): ReplayPresenter = ReplayPresenter(
    tripId = tripId,
    source = source,
    replays = replay,
    isRecording = recordingCheck(tripId),
    analytics = analytics,
    monoNowNs = clock::monotonicNs,
    scope = scope,
    details = TripReplayDetails(trips, previews),
    units = units(region),
    format = format,
)

/** The full [replayPresenter] on the main thread with its own scope, for Swift. Call `close()` on deinit. */
public fun CoreServices.replayPresenter(
    tripId: String,
    source: ReplaySource,
    region: String,
    format: ReplayTimeFormat,
): ReplayPresenter = ReplayPresenter.onMain(
    tripId, source, replay, recordingCheck(tripId), analytics, clock::monotonicNs,
    TripReplayDetails(trips, previews), units(region), format,
)

/**
 * The shared S-RPL-02 event evidence sheet for [eventId] (DRIVE-39, with S-SCO-03), in a child of [scope]. Its
 * feedback goes through the feedback store, so the trip and every later one re-score (§5.7).
 */
public fun CoreServices.evidencePresenter(
    tripId: String,
    eventId: String,
    region: String,
    format: ReplayTimeFormat,
    scope: CoroutineScope,
): EvidencePresenter = EvidencePresenter(
    tripId, eventId, trips, TripEvidence(trips, analysisFiles), markEventNeeded, reportEventInaccurate(), analytics,
    units(region), format, scope,
)

/** [evidencePresenter] on the main thread with its own scope, for Swift. Call `close()` on deinit. */
public fun CoreServices.evidencePresenter(
    tripId: String,
    eventId: String,
    region: String,
    format: ReplayTimeFormat,
): EvidencePresenter = EvidencePresenter.onMain(
    tripId, eventId, trips, TripEvidence(trips, analysisFiles), markEventNeeded, reportEventInaccurate(), analytics,
    units(region), format,
)

/**
 * The shared S-SCO-04 tip detail (DRIVE-102) in a child of [scope]: the trip's own tip when [tipId] is null, or the
 * library tip [tipId] about [eventId].
 */
public fun CoreServices.tipDetailPresenter(
    tripId: String,
    tipId: String?,
    eventId: String?,
    region: String,
    scope: CoroutineScope,
): TipDetailPresenter =
    TipDetailPresenter(tripId, tipId, eventId, trips, TripEvidence(trips, analysisFiles), analytics, units(region), scope)

/** [tipDetailPresenter] on the main thread with its own scope, for Swift. Call `close()` on deinit. */
public fun CoreServices.tipDetailPresenter(tripId: String, tipId: String?, eventId: String?, region: String): TipDetailPresenter =
    TipDetailPresenter.onMain(tripId, tipId, eventId, trips, TripEvidence(trips, analysisFiles), analytics, units(region))

/** "Not accurate?" (F-RPL-04 AC4), through the same feedback store as "Was it needed?". */
private fun CoreServices.reportEventInaccurate(): ReportEventInaccurate =
    ReportEventInaccurate(trips, routes, feedbackStore, rescorer, clock)

/** True while [tripId] is the trip being recorded (F-RPL-01 AC6). */
private fun CoreServices.recordingCheck(tripId: String): () -> Boolean =
    { recorder.status.value.let { it.tripId == tripId && it.state != RecorderState.IDLE } }
