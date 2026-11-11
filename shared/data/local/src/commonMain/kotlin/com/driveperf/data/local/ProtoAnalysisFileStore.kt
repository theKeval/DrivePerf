package com.driveperf.data.local

import com.driveperf.domain.trip.Trip
import com.driveperf.domain.tripfile.AnalysisFileStore
import com.driveperf.domain.tripfile.AnalysisTrace
import com.driveperf.domain.tripfile.FileResult
import com.driveperf.domain.tripfile.TripFileKind
import com.driveperf.domain.tripfile.TripFileRef
import com.driveperf.domain.tripfile.TripFileStore
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * R1 analysis files (ADR-003) through the platform [TripFileStore]: written once at finalisation, gzip-compressed in
 * place and hashed, so the stored bytes are exactly what Pro backup uploads to `r1.v1.<sha16>.pb.gz` (ADR-004).
 */
public class ProtoAnalysisFileStore(private val files: TripFileStore, private val io: CoroutineContext) : AnalysisFileStore {
    override suspend fun write(trip: Trip, trace: AnalysisTrace): TripFileRef? = withContext(io) {
        val id = trip.id
        val stored = files.write(id, TripFileKind.R1, AnalysisFileCodec.encode(trip, trace)) == FileResult.OK &&
            files.compress(id, TripFileKind.R1) == FileResult.OK
        val described = if (stored) files.describe(id, TripFileKind.R1, withSha256 = true) else null
        val sha = described?.sha256
        if (described == null || sha == null) {
            files.delete(id, TripFileKind.R1)
            null
        } else {
            val v = AnalysisFileCodec.FORMAT_VERSION
            TripFileRef(TripFileKind.R1, v, sha, described.sizeBytes, "trips/$id/r1.v$v.pb.gz")
        }
    }

    override suspend fun read(tripId: String): AnalysisTrace? = withContext(io) {
        files.read(tripId, TripFileKind.R1)?.let { AnalysisFileCodec.decode(it) }
    }
}
