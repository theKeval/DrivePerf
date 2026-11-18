package com.driveperf.domain.replay

import com.driveperf.domain.capture.PreviewStore
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.trip.PreviewRow
import com.driveperf.domain.trip.RawState
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.tripfile.AnalysisFileStore

/**
 * [ReplayRepository] over the stores a finished trip already has: the trip row and its quality spans, the R1 file's
 * gated 1 Hz fixes, and the preview track (data-model.md §2.5).
 *
 * - The route is the R1 fixes. When the R1 file is gone (`SUMMARY_ONLY`, EC-STO) or unreadable, it is the preview
 *   track instead and the replay is [Replay.summaryOnly].
 * - The parking spot of an estimated start is the preview track's first row, which carries the estimated quality code.
 * - Trips still recording and discarded trips have no replay.
 */
public class TripReplayRepository(
    private val trips: TripRepository,
    private val analysis: AnalysisFileStore,
    private val previews: PreviewStore,
) : ReplayRepository {
    override suspend fun replay(tripId: String): Replay? {
        val trip = trips.trip(tripId)?.takeIf { it.status == TripStatus.FINALISED || it.status == TripStatus.INTERRUPTED }
            ?: return null
        val trace = if (trip.rawState == RawState.SUMMARY_ONLY) null else analysis.read(tripId)
        val estimated = trip.quality?.spans.orEmpty().any { it.kind == SegmentKind.ESTIMATED }
        val preview = if (trace == null || estimated) previews.read(tripId).orEmpty() else emptyList()
        val parking = preview.firstOrNull()?.takeIf { estimated && it.quality == ESTIMATED_ROW }?.toPoint()
        val fixes = if (trace != null) {
            trace.fixes.mapNotNull { f -> if (f.lat != null && f.lng != null) ReplayPoint(f.tS, f.lat, f.lng, f.speedMps) else null }
        } else {
            preview.drop(if (parking != null) 1 else 0).map { it.toPoint() }
        }
        return ReplayBuilder.build(trip, parking, fixes, summaryOnly = trace == null)
    }

    private fun PreviewRow.toPoint(): ReplayPoint = ReplayPoint(tS, lat, lng, speedMps)

    private companion object {
        /** The preview track's `q` code for estimated rows (data-model.md §2.5). */
        const val ESTIMATED_ROW = 1
    }
}
