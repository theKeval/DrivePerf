package com.driveperf.data.local

import com.driveperf.data.local.Fixed.ABSENT
import com.driveperf.data.local.Fixed.FrameReader
import com.driveperf.data.local.Fixed.delimited
import com.driveperf.data.local.Fixed.delta
import com.driveperf.data.local.Fixed.deltaLong
import com.driveperf.data.local.Fixed.dequantN
import com.driveperf.data.local.Fixed.undelta
import com.driveperf.data.local.Fixed.undeltaLong
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.tripfile.AnalysisFix
import com.driveperf.domain.tripfile.AnalysisTrace
import driveperf.tripfile.v1.Chunk
import driveperf.tripfile.v1.Gnss
import driveperf.tripfile.v1.Header
import driveperf.tripfile.v1.Motion
import kotlin.math.round

/**
 * Encodes and decodes the R1 analysis file (data-model.md §2.4, pipeline-and-scoring.md §0.3): the same container as R0
 * (magic `DPTF`, length-delimited header, length-delimited chunks), kind `R1`, `VEHICLE` frame at 25 Hz, one chunk per
 * minute. Motion values are quantised with round-half-even, as §0.5 requires for cross-platform determinism; a
 * non-finite value is stored as absent and read back as NaN. Fix times keep millisecond precision.
 */
internal object AnalysisFileCodec {
    const val FORMAT_VERSION: Int = 1
    private const val CHUNK_MS = 60_000L
    private const val MS = 1000.0
    private val MAGIC = byteArrayOf('D'.code.toByte(), 'P'.code.toByte(), 'T'.code.toByte(), 'F'.code.toByte())
    private val q = TripFileCodec.quantisation

    fun encode(trip: Trip, trace: AnalysisTrace): ByteArray {
        val header = Header(
            format_version = FORMAT_VERSION,
            kind = Header.Kind.R1,
            trip_id = trip.id,
            started_at_ms = trip.startedAt,
            tz_offset_min = trip.startTzOffsetMin,
            tz_id = trip.tzId.orEmpty(),
            pipeline_version = trip.pipelineVersion.orEmpty(),
            motion_rate_hz = trace.rateHz.toFloat(),
            motion_frame = Header.Frame.VEHICLE,
            q = q,
        )
        val perChunk = (trace.rateHz * CHUNK_MS / MS).toInt()
        val fixChunks = trace.fixes.groupBy { fixMs(it).floorDiv(CHUNK_MS) }
        val motionChunks = if (trace.size == 0) emptySet() else (0..(trace.size - 1) / perChunk).map { it.toLong() }.toSet()
        val out = okio.Buffer().write(MAGIC).write(delimited(Header.ADAPTER.encode(header)))
        for (c in (motionChunks + fixChunks.keys).sorted()) {
            val t0 = c * CHUNK_MS
            val from = (c * perChunk).toInt()
            val to = minOf(trace.size, from + perChunk)
            val chunk = Chunk(
                chunk_t0_ms = t0,
                gnss = fixChunks[c]?.let { gnss(t0, it) },
                motion = if (from < to && from >= 0) motion(trace, t0, from, to) else null,
            )
            out.write(delimited(Chunk.ADAPTER.encode(chunk)))
        }
        return out.readByteArray()
    }

    /** Decodes a whole R1 file, or null when the magic, header or kind is wrong. A cut-off last chunk is dropped. */
    fun decode(bytes: ByteArray): AnalysisTrace? {
        val magicOk = bytes.size >= MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)
        val reader = FrameReader(bytes, MAGIC.size)
        val header = if (magicOk) reader.next()?.let { decodeOrNull { Header.ADAPTER.decode(it) } } else null
        return header?.takeIf { it.kind == Header.Kind.R1 }?.let { body(it, reader) }
    }

    private fun body(header: Header, reader: FrameReader): AnalysisTrace {
        val rate = header.motion_rate_hz.toInt()
        val hq = header.q ?: q
        val samples = mutableMapOf<Int, DoubleArray>()
        val quality = mutableMapOf<Int, Int>()
        val fixes = mutableListOf<AnalysisFix>()
        while (true) {
            val chunk = reader.next()?.let { decodeOrNull { Chunk.ADAPTER.decode(it) } } ?: break
            chunk.motion?.let { m ->
                val t = undeltaLong(m.dt_ms, chunk.chunk_t0_ms)
                val ch = listOf(m.lon_g, m.lat_g, m.vert_g, m.yaw_dps).map { undelta(it) }
                val units = doubleArrayOf(hq.accel_g_per_unit, hq.accel_g_per_unit, hq.accel_g_per_unit, hq.gyro_dps_per_unit)
                for (i in t.indices) {
                    val k = round(t[i] * rate / MS).toInt()
                    samples[k] = DoubleArray(ch.size) { c -> value(ch[c][i], units[c]) }
                    quality[k] = m.quality.getOrElse(i) { 0 }
                }
            }
            chunk.gnss?.let { g -> fixes += fixes(chunk.chunk_t0_ms, g) }
        }
        val n = (samples.keys.maxOrNull() ?: -1) + 1
        fun channel(c: Int) = DoubleArray(n) { k -> samples[k]?.get(c) ?: Double.NaN }
        return AnalysisTrace(
            rate, channel(0), channel(1), channel(2), channel(3), IntArray(n) { quality[it] ?: 0 }, fixes.sortedBy { it.tS },
        )
    }

    private fun motion(trace: AnalysisTrace, t0: Long, from: Int, to: Int): Motion {
        val idx = from until to
        val a = q.accel_g_per_unit
        return Motion(
            dt_ms = deltaLong(idx.map { round(it * MS / trace.rateHz).toLong() }, t0),
            lon_g = delta(idx.map { even(trace.lonG[it], a) }),
            lat_g = delta(idx.map { even(trace.latG[it], a) }),
            vert_g = delta(idx.map { even(trace.vertG[it], a) }),
            yaw_dps = delta(idx.map { even(trace.yawDps[it], q.gyro_dps_per_unit) }),
            quality = idx.map { trace.quality[it] },
        )
    }

    private fun gnss(t0: Long, f: List<AnalysisFix>): Gnss = Gnss(
        dt_ms = deltaLong(f.map { fixMs(it) }, t0),
        lat = delta(f.map { it.lat?.let { v -> even(v, q.latlng_deg_per_unit) } ?: ABSENT }),
        lng = delta(f.map { it.lng?.let { v -> even(v, q.latlng_deg_per_unit) } ?: ABSENT }),
        speed = delta(f.map { even(it.speedMps, q.speed_mps_per_unit) }),
        speed_acc = delta(f.map { it.speedAccMps?.let { v -> even(v, q.speed_mps_per_unit) } ?: ABSENT }),
        quality = f.map { 0 },
    )

    private fun fixes(t0: Long, g: Gnss): List<AnalysisFix> {
        val t = undeltaLong(g.dt_ms, t0)
        val lat = undelta(g.lat)
        val lng = undelta(g.lng)
        val speed = undelta(g.speed)
        val speedAcc = undelta(g.speed_acc)
        return t.indices.map { i ->
            AnalysisFix(
                tS = t[i] / MS,
                speedMps = dequantN(speed[i], q.speed_mps_per_unit) ?: 0.0,
                lat = dequantN(lat[i], q.latlng_deg_per_unit),
                lng = dequantN(lng[i], q.latlng_deg_per_unit),
                speedAccMps = dequantN(speedAcc[i], q.speed_mps_per_unit),
            )
        }
    }

    private fun fixMs(f: AnalysisFix): Long = round(f.tS * MS).toLong()

    /** Round-half-even quantisation (§0.5); non-finite values become absent. */
    private fun even(x: Double, unit: Double): Int = if (x.isFinite()) round(x / unit).toLong().toInt() else ABSENT

    private fun value(v: Int, unit: Double): Double = dequantN(v, unit) ?: Double.NaN

    private inline fun <T> decodeOrNull(block: () -> T): T? = try {
        block()
    } catch (expected: okio.IOException) {
        null
    } catch (expected: IllegalStateException) {
        null
    }
}
