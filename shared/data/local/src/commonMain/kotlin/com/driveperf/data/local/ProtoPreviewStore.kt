package com.driveperf.data.local

import com.driveperf.data.local.Fixed.dequantN
import com.driveperf.data.local.Fixed.delta
import com.driveperf.data.local.Fixed.quantN
import com.driveperf.data.local.Fixed.undelta
import com.driveperf.domain.capture.PreviewStore
import com.driveperf.domain.trip.PreviewRow
import com.driveperf.domain.tripfile.FileResult
import com.driveperf.domain.tripfile.TripFileKind
import com.driveperf.domain.tripfile.TripFileStore
import driveperf.tripfile.v1.PreviewTrack
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToLong

/** Preview tracks as `preview.pb` (data-model.md §2.5) through the platform [TripFileStore]. */
public class ProtoPreviewStore(private val files: TripFileStore, private val io: CoroutineContext) : PreviewStore {
    private val q = TripFileCodec.quantisation

    override suspend fun write(tripId: String, rows: List<PreviewRow>): Boolean = withContext(io) {
        val t = rows.map { (it.tS * MS).roundToLong() }
        val track = PreviewTrack(
            format_version = TripFileCodec.FORMAT_VERSION,
            t_ms = t.mapIndexed { i, x -> if (i == 0) x else x - t[i - 1] },
            lat = delta(rows.map { quantN(it.lat, q.latlng_deg_per_unit) }),
            lng = delta(rows.map { quantN(it.lng, q.latlng_deg_per_unit) }),
            speed = delta(rows.map { quantN(it.speedMps, q.speed_mps_per_unit) }),
            lon_g = delta(rows.map { quantN(it.lonG, q.accel_g_per_unit) }),
            lat_g = delta(rows.map { quantN(it.latG, q.accel_g_per_unit) }),
            elev = delta(rows.map { quantN(it.elevM, q.alt_m_per_unit) }),
            quality = rows.map { it.quality },
        )
        files.write(tripId, TripFileKind.PREVIEW, PreviewTrack.ADAPTER.encode(track)) == FileResult.OK
    }

    override suspend fun read(tripId: String): List<PreviewRow>? = withContext(io) {
        val bytes = files.read(tripId, TripFileKind.PREVIEW) ?: return@withContext null
        val track = decodeOrNull(bytes) ?: return@withContext null
        var t = 0L
        val lat = undelta(track.lat)
        val lng = undelta(track.lng)
        val speed = undelta(track.speed)
        val lon = undelta(track.lon_g)
        val latG = undelta(track.lat_g)
        val elev = undelta(track.elev)
        track.t_ms.indices.map { i ->
            t += track.t_ms[i]
            PreviewRow(
                tS = t / MS,
                lat = lat[i] * q.latlng_deg_per_unit,
                lng = lng[i] * q.latlng_deg_per_unit,
                speedMps = dequantN(speed[i], q.speed_mps_per_unit),
                lonG = dequantN(lon[i], q.accel_g_per_unit),
                latG = dequantN(latG[i], q.accel_g_per_unit),
                elevM = dequantN(elev[i], q.alt_m_per_unit),
                quality = track.quality.getOrElse(i) { 0 },
            )
        }
    }

    private fun decodeOrNull(bytes: ByteArray): PreviewTrack? = try {
        PreviewTrack.ADAPTER.decode(bytes)
    } catch (expected: okio.IOException) {
        null
    }

    private companion object {
        const val MS = 1000.0
    }
}
