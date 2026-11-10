package com.driveperf.pipeline.fixtures

import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.capture.FinalisedTrip
import com.driveperf.domain.repository.ScoredTrip
import com.driveperf.domain.repository.TripHistory
import com.driveperf.domain.trip.InterruptionReason
import com.driveperf.domain.trip.TripKind
import com.driveperf.pipeline.PipelineFinaliser
import com.driveperf.pipeline.capture.FinaliseRequest
import com.driveperf.pipeline.capture.FinaliseResult

/** Runs the full finaliser on a capture with an optional history. */
internal class PipelineHarness(private val history: List<ScoredTrip> = emptyList()) {
    private val finaliser = PipelineFinaliser(
        object : TripHistory {
            override suspend fun finishedBefore(beforeStartedAt: Long, limit: Int): List<ScoredTrip> =
                history.filter { it.trip.startedAt < beforeStartedAt }.sortedByDescending { it.trip.startedAt }.take(limit)
        },
    )

    suspend fun run(
        capture: CapturedTrip,
        manual: Boolean = true,
        kind: TripKind = TripKind.DRIVER,
        interruption: InterruptionReason? = null,
    ): FinaliseResult = finaliser.finalise(FinaliseRequest(capture, null, kind, manual, interruption, 1L))

    suspend fun trip(capture: CapturedTrip, manual: Boolean = true, kind: TripKind = TripKind.DRIVER): FinalisedTrip =
        checkNotNull(run(capture, manual, kind).trip)
}
