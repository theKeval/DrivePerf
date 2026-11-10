package com.driveperf.data.local

import com.driveperf.domain.capture.CaptureBatch
import com.driveperf.domain.capture.CaptureHeader
import com.driveperf.domain.capture.CaptureLog
import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.tripfile.FileResult
import com.driveperf.domain.tripfile.TripFileKind
import com.driveperf.domain.tripfile.TripFileStore
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * The R0 capture log (ADR-003) in the `tripfile.proto` format, stored through the platform [TripFileStore]. Each
 * append is one chunk; the recorder syncs at most every 5 s, so a kill loses at most the unsynced tail.
 */
public class ProtoCaptureLog(private val files: TripFileStore, private val io: CoroutineContext) : CaptureLog {
    private val headers = mutableMapOf<String, CaptureHeader>()

    override suspend fun open(header: CaptureHeader): Boolean = withContext(io) {
        headers[header.tripId] = header
        files.write(header.tripId, TripFileKind.R0, TripFileCodec.encodeHeader(header)) == FileResult.OK
    }

    override suspend fun append(tripId: String, batch: CaptureBatch): Boolean = withContext(io) {
        val header = headerOf(tripId)
        header != null && files.append(tripId, TripFileKind.R0, TripFileCodec.encodeChunk(header, batch)) == FileResult.OK
    }

    override suspend fun sync(tripId: String) {
        withContext(io) { files.sync(tripId, TripFileKind.R0) }
    }

    override suspend fun read(tripId: String): CapturedTrip? = withContext(io) {
        files.read(tripId, TripFileKind.R0)?.let { TripFileCodec.decode(it) }
    }

    override suspend fun delete(tripId: String) {
        withContext(io) {
            headers.remove(tripId)
            files.delete(tripId, TripFileKind.R0)
        }
    }

    /** After a relaunch the header is read back from the file. */
    private fun headerOf(tripId: String): CaptureHeader? = headers[tripId]
        ?: files.read(tripId, TripFileKind.R0)?.let { TripFileCodec.decode(it)?.header }?.also { headers[tripId] = it }
}
