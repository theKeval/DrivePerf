package com.driveperf.di

import com.driveperf.domain.capture.RecorderState
import com.driveperf.presentation.replay.ReplayPresenter
import com.driveperf.presentation.replay.ReplaySource
import kotlinx.coroutines.CoroutineScope

/**
 * The shared Replay presenter for [tripId] (S-RPL-01), running in a child of [scope]. Android passes
 * `viewModelScope`. Call `close()` when the screen goes away.
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

/** True while [tripId] is the trip being recorded (F-RPL-01 AC6). */
private fun CoreServices.recordingCheck(tripId: String): () -> Boolean =
    { recorder.status.value.let { it.tripId == tripId && it.state != RecorderState.IDLE } }
